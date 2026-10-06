package com.simulation.airdefense.simulation;

import com.simulation.airdefense.config.DefenseConfig;
import com.simulation.airdefense.config.SimulationConfig;
import com.simulation.airdefense.config.ThreatConfig;
import com.simulation.airdefense.event.EventBus;
import com.simulation.airdefense.event.EventType;
import com.simulation.airdefense.event.SimulationEvent;
import com.simulation.airdefense.model.Geometry;
import com.simulation.airdefense.model.Target;
import com.simulation.airdefense.model.TargetFactory;
import com.simulation.airdefense.model.TargetType;
import com.simulation.airdefense.model.ThreatLevel;
import com.simulation.airdefense.model.ThreatResult;
import com.simulation.airdefense.radar.Radar;
import com.simulation.airdefense.scenario.Scenario;
import com.simulation.airdefense.scenario.ScenarioContext;
import com.simulation.airdefense.service.DefenseSystem;
import com.simulation.airdefense.service.EngagementOutcome;
import com.simulation.airdefense.service.PriorityService;
import com.simulation.airdefense.service.ThreatAssessmentService;
import com.simulation.airdefense.simulation.SimulationSnapshot.DefenseView;
import com.simulation.airdefense.simulation.SimulationSnapshot.Effect;
import com.simulation.airdefense.simulation.SimulationSnapshot.EffectType;
import com.simulation.airdefense.simulation.SimulationSnapshot.PriorityEntry;
import com.simulation.airdefense.simulation.SimulationSnapshot.RadarView;
import com.simulation.airdefense.simulation.SimulationSnapshot.Stats;
import com.simulation.airdefense.simulation.SimulationSnapshot.TargetView;
import com.simulation.airdefense.swarm.DroneSwarm;
import com.simulation.airdefense.swarm.SwarmConfig;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Tüm simülasyonu yöneten motor.
 *
 * <h2>Concurrency mimarisi</h2>
 * <ul>
 *   <li><b>Simülasyon döngüsü</b>: tek thread'li {@link ScheduledExecutorService}, sabit tick
 *       (varsayılan 60 Hz). Hız çarpanı simülasyon zamanının akışına uygulanır: her tick'te
 *       çarpan kadar sabit {@code dt} adımı atılır (fixed timestep), bu da sonuçları
 *       hızdan bağımsız ve tekrar edilebilir kılar.</li>
 *   <li><b>Tehdit hesaplaması</b>: her tick'te algılanmış hedefler sabit boyutlu bir
 *       thread havuzunda {@code invokeAll} ile paralel değerlendirilir.</li>
 *   <li><b>Komutlar</b>: arayüzden gelen istekler (start, spawn, menzil…) bir
 *       {@link ConcurrentLinkedQueue}'ya eklenir ve tick başında simülasyon thread'inde uygulanır.
 *       Böylece model nesneleri yalnızca tek bir thread tarafından değiştirilir.</li>
 *   <li><b>Durum aktarımı</b>: her tick sonunda değiştirilemez bir {@link SimulationSnapshot}
 *       üretilip {@link AtomicReference} içine konur; arayüz yalnızca bunu okur.</li>
 *   <li><b>Olaylar</b>: {@link EventBus} üzerinden yayınlanır (Observer pattern).</li>
 * </ul>
 */
public class SimulationEngine {

    private static final double SPAWN_RANGE_FACTOR = 1.10;
    private static final double MANUAL_HEADING_OFFSET = 5.0;
    private static final double PING_DURATION = 0.9;
    private static final double NEUTRALIZED_DURATION = 1.2;
    private static final double MISS_DURATION = 0.6;
    private static final double ESCAPED_DURATION = 1.5;

    private final SimulationConfig config;
    private final EventBus eventBus;
    private final ThreatAssessmentService threatService;
    private final PriorityService priorityService;
    private final DefenseSystem defense;
    private final Radar radar;
    private final TargetFactory factory;

    private final ScheduledExecutorService loopExecutor;
    private final ExecutorService threatPool;

    private final ConcurrentHashMap<String, Target> targets = new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<DroneSwarm> swarms = new CopyOnWriteArrayList<>();
    private final ConcurrentLinkedQueue<Runnable> commands = new ConcurrentLinkedQueue<>();
    private final AtomicReference<SimulationSnapshot> snapshot;
    private final AtomicReference<SimulationState> state = new AtomicReference<>(SimulationState.READY);
    private final AtomicInteger idSequence = new AtomicInteger();
    private final AtomicInteger swarmSequence = new AtomicInteger();
    private final AtomicInteger spawnedCount = new AtomicInteger();
    private final AtomicInteger neutralizedCount = new AtomicInteger();
    private final AtomicInteger escapedCount = new AtomicInteger();
    private final AtomicInteger leftAreaCount = new AtomicInteger();
    private volatile double speedMultiplier = 1.0;

    // ---- Yalnızca simülasyon thread'inde erişilen alanlar ----
    private final PriorityQueue<ScheduledAction> pendingActions = new PriorityQueue<>(
            Comparator.comparingDouble(ScheduledAction::time).thenComparingLong(ScheduledAction::sequence));
    private final List<ActiveEffect> effects = new ArrayList<>();
    private final Set<String> assessedIds = new HashSet<>();
    private final ScenarioContext scenarioContext = new EngineScenarioContext();
    private Random random;
    private Scenario currentScenario;
    private List<Target> lastPriorities = List.of();
    private double simulationTime;
    private long tickCount;
    private long actionSequence;
    private boolean quietSpawns;
    private double lastTickMillis;
    private double stepAccumulator;

    public SimulationEngine(SimulationConfig config, ThreatConfig threatConfig,
                            DefenseConfig defenseConfig, EventBus eventBus) {
        this.config = Objects.requireNonNull(config, "config");
        this.eventBus = Objects.requireNonNull(eventBus, "eventBus");
        this.threatService = new ThreatAssessmentService(threatConfig);
        this.priorityService = new PriorityService();
        this.defense = new DefenseSystem(defenseConfig, eventBus);
        this.radar = new Radar(0, 0, config.defaultRadarRange(), config.sweepPeriod());
        this.factory = new TargetFactory(config.trailLength());
        this.random = new Random(config.defaultSeed());
        this.snapshot = new AtomicReference<>(SimulationSnapshot.empty(config.defaultRadarRange()));

        this.loopExecutor = Executors.newSingleThreadScheduledExecutor(namedThreads("simulation-loop"));
        this.threatPool = Executors.newFixedThreadPool(config.threatPoolSize(), namedThreads("threat-worker"));
        long periodNanos = TimeUnit.SECONDS.toNanos(1) / config.tickRateHz();
        loopExecutor.scheduleAtFixedRate(this::tick, 0, periodNanos, TimeUnit.NANOSECONDS);
    }

    private static ThreadFactory namedThreads(String prefix) {
        AtomicInteger counter = new AtomicInteger();
        return runnable -> {
            Thread thread = new Thread(runnable, prefix + "-" + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }

    // =================================================================== public API (thread-safe)

    /** READY → RUNNING. */
    public void start() {
        commands.add(() -> {
            if (state.compareAndSet(SimulationState.READY, SimulationState.RUNNING)) {
                publish(EventType.SIMULATION_STARTED, null, "Simülasyon başladı — "
                        + (currentScenario == null ? "manuel mod" : currentScenario.name()));
            }
        });
    }

    /** RUNNING → PAUSED. */
    public void pause() {
        commands.add(() -> {
            if (state.compareAndSet(SimulationState.RUNNING, SimulationState.PAUSED)) {
                publish(EventType.SIMULATION_PAUSED, null, "Simülasyon duraklatıldı");
            }
        });
    }

    /** PAUSED → RUNNING. */
    public void resume() {
        commands.add(() -> {
            if (state.compareAndSet(SimulationState.PAUSED, SimulationState.RUNNING)) {
                publish(EventType.SIMULATION_RESUMED, null, "Simülasyon devam ediyor");
            }
        });
    }

    /** Dünyayı temizler; yüklü bir senaryo varsa aynı seed ile yeniden kurar. Durum READY olur. */
    public void reset() {
        commands.add(() -> {
            resetWorld();
            publish(EventType.SIMULATION_RESET, null, "Simülasyon sıfırlandı");
            if (currentScenario != null) {
                populateScenario(currentScenario);
            }
        });
    }

    /** Senaryoyu yükler (önce dünyayı sıfırlar). Durum READY olur. */
    public void loadScenario(Scenario scenario) {
        Objects.requireNonNull(scenario, "scenario");
        commands.add(() -> {
            currentScenario = scenario;
            resetWorld();
            populateScenario(scenario);
        });
    }

    /**
     * Rastgele bir yönden, menzil dışında merkeze doğru ilerleyen bir hedef ekler.
     *
     * @param baseSpeed temel hız (m/s)
     */
    public void spawnTarget(TargetType type, double baseSpeed) {
        Objects.requireNonNull(type, "type");
        if (baseSpeed <= 0) {
            throw new IllegalArgumentException("speed must be positive");
        }
        commands.add(() -> {
            double bearing = random.nextDouble() * 360.0;
            double offset = (random.nextDouble() * 2 - 1) * MANUAL_HEADING_OFFSET;
            spawnInternal(type, radar.getRange() * SPAWN_RANGE_FACTOR, bearing, offset, baseSpeed);
            state.compareAndSet(SimulationState.FINISHED, SimulationState.RUNNING);
        });
    }

    /** Simülasyon hız çarpanı (örn. 0.5, 1, 2, 4). */
    public void setSpeed(double multiplier) {
        if (multiplier <= 0) {
            throw new IllegalArgumentException("multiplier must be positive");
        }
        speedMultiplier = multiplier;
        commands.add(() -> publish(EventType.SETTINGS_CHANGED, null,
                "Simülasyon hızı %sx olarak ayarlandı".formatted(formatMultiplier(multiplier))));
    }

    public void setRadarRange(double range) {
        double clamped = Math.max(config.minRadarRange(), Math.min(config.maxRadarRange(), range));
        commands.add(() -> {
            if (Math.abs(radar.getRange() - clamped) >= 1) {
                radar.setRange(clamped);
                publish(EventType.SETTINGS_CHANGED, null, "Radar menzili %.0f m olarak ayarlandı".formatted(clamped));
            }
        });
    }

    public void setRadarActive(boolean active) {
        commands.add(() -> {
            if (radar.isActive() != active) {
                radar.setActive(active);
                publish(EventType.SETTINGS_CHANGED, null, active ? "Radar AÇIK" : "Radar KAPALI");
            }
        });
    }

    /** UI'ın her karede okuduğu en son snapshot. */
    public SimulationSnapshot getSnapshot() {
        return snapshot.get();
    }

    public SimulationState getState() {
        return state.get();
    }

    public double getSpeed() {
        return speedMultiplier;
    }

    public EventBus getEventBus() {
        return eventBus;
    }

    public SimulationConfig getConfig() {
        return config;
    }

    /** Tüm executor'ları düzgünce kapatır. */
    public void shutdown() {
        loopExecutor.shutdown();
        awaitQuietly(loopExecutor);
        threatPool.shutdown();
        awaitQuietly(threatPool);
    }

    private static void awaitQuietly(ExecutorService executor) {
        try {
            if (!executor.awaitTermination(1, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    // =================================================================== simülasyon döngüsü

    private void tick() {
        long start = System.nanoTime();
        try {
            Runnable command;
            while ((command = commands.poll()) != null) {
                command.run();
            }
            if (state.get() == SimulationState.RUNNING) {
                // Sabit adımlı (fixed timestep) ilerleme: hız çarpanı, tick başına kaç adet
                // sabit dt adımı atılacağını belirler (2x → 2 adım, 0.5x → iki tick'te bir adım).
                // Böylece aynı seed her hızda birebir aynı sonucu üretir.
                stepAccumulator += speedMultiplier;
                while (stepAccumulator >= 1.0 && state.get() == SimulationState.RUNNING) {
                    stepAccumulator -= 1.0;
                    step(config.tickSeconds());
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        } catch (RuntimeException e) {
            // Döngünün sessizce ölmemesi için yakala ve devam et.
            System.err.println("Simulation tick failed: " + e);
            e.printStackTrace();
        }
        lastTickMillis = (System.nanoTime() - start) / 1_000_000.0;
        snapshot.set(buildSnapshot());
    }

    private void step(double dt) throws InterruptedException {
        simulationTime += dt;
        tickCount++;

        runDueActions();
        radar.update(dt);

        for (DroneSwarm swarm : swarms) {
            if (swarm.update(dt)) {
                publish(EventType.SWARM_MANEUVER, null,
                        "%s iki gruba ayrılıyor, iki yandan yaklaşma manevrası (%d üye)".formatted(
                                swarm.getId(), swarm.size()));
            }
        }
        swarms.removeIf(DroneSwarm::isEmpty);

        for (Target target : targets.values()) {
            if (target.isActive()) {
                target.update(dt);
            }
        }

        updateDetection();
        checkBoundaries();
        assessThreats();

        List<Target> priorities = priorityService.prioritize(targets.values());
        defense.update(dt, simulationTime, priorities, random).ifPresent(this::onEngagement);
        lastPriorities = priorities;

        removeInactiveTargets();
        ageEffects(dt);
        checkFinished();
    }

    private void runDueActions() {
        while (!pendingActions.isEmpty() && pendingActions.peek().time() <= simulationTime) {
            pendingActions.poll().action().run();
        }
    }

    private void updateDetection() {
        for (Target target : targets.values()) {
            if (!target.isActive()) {
                continue;
            }
            boolean inRange = radar.isInRange(target);
            if (!target.isDetected()) {
                if (inRange && radar.wasSwept(radar.bearingTo(target))) {
                    target.markDetected();
                    addEffect(EffectType.PING, target.getX(), target.getY(), PING_DURATION);
                    publish(EventType.TARGET_DETECTED, target.getId(),
                            "%s algılandı — %s, kerteriz %03.0f°, %.0f m".formatted(
                                    target.getId(), target.getType().displayName(),
                                    radar.bearingTo(target), radar.distanceTo(target)));
                }
            } else if (!inRange) {
                target.markLost();
                assessedIds.remove(target.getId());
                publish(EventType.TARGET_LOST, target.getId(),
                        radar.isActive()
                                ? "%s kaybedildi — radar kapsama alanından çıktı".formatted(target.getId())
                                : "%s kaybedildi — radar kapalı".formatted(target.getId()));
            }
        }
    }

    private void checkBoundaries() {
        double escapeRadius = defense.getConfig().escapeRadius();
        double areaLimit = radar.getRange() * config.areaLimitFactor();
        for (Target target : targets.values()) {
            if (!target.isActive()) {
                continue;
            }
            double distance = radar.distanceTo(target);
            if (distance <= escapeRadius) {
                target.escape();
                escapedCount.incrementAndGet();
                addEffect(EffectType.ESCAPED, target.getX(), target.getY(), ESCAPED_DURATION);
                publish(EventType.TARGET_ESCAPED, target.getId(),
                        "%s SIZDI — %.0f m savunma çemberini geçti".formatted(target.getId(), escapeRadius));
            } else if (distance > areaLimit && movingAway(target)) {
                target.leaveArea();
                leftAreaCount.incrementAndGet();
                publish(EventType.TARGET_LEFT_AREA, target.getId(),
                        "%s simülasyon alanından çıktı".formatted(target.getId()));
            }
        }
    }

    private static boolean movingAway(Target target) {
        return target.getX() * target.getVelocityX() + target.getY() * target.getVelocityY() > 0;
    }

    private void assessThreats() throws InterruptedException {
        List<Target> detected = new ArrayList<>();
        for (Target target : targets.values()) {
            if (target.isActive() && target.isDetected()) {
                detected.add(target);
            }
        }
        List<ThreatResult> results = threatService.assessAll(detected, radar, threatPool);
        for (int i = 0; i < detected.size(); i++) {
            Target target = detected.get(i);
            ThreatLevel previous = target.getThreatLevel();
            boolean firstAssessment = assessedIds.add(target.getId());
            ThreatResult raw = results.get(i);
            ThreatResult result = firstAssessment
                    ? raw
                    : raw.withLevel(threatService.getConfig().levelFor(raw.score(), previous));
            target.applyThreat(result);
            // Log'u sade tutmak için yalnızca ilk değerlendirme ve seviye yükselişleri yayınlanır.
            if (firstAssessment || result.level().compareTo(previous) > 0) {
                publish(EventType.THREAT_LEVEL_CHANGED, target.getId(),
                        "%s tehdit seviyesi: %s (%d)".formatted(target.getId(), result.level().label(), result.roundedScore()));
            }
        }
    }

    private void onEngagement(EngagementOutcome outcome) {
        if (outcome.success()) {
            neutralizedCount.incrementAndGet();
            addEffect(EffectType.NEUTRALIZED, outcome.x(), outcome.y(), NEUTRALIZED_DURATION);
        } else {
            addEffect(EffectType.MISS, outcome.x(), outcome.y(), MISS_DURATION);
        }
    }

    private void removeInactiveTargets() {
        Iterator<Target> it = targets.values().iterator();
        while (it.hasNext()) {
            Target target = it.next();
            if (!target.isActive()) {
                assessedIds.remove(target.getId());
                it.remove();
            }
        }
    }

    private void checkFinished() {
        if (spawnedCount.get() > 0 && targets.isEmpty() && pendingActions.isEmpty()
                && state.compareAndSet(SimulationState.RUNNING, SimulationState.FINISHED)) {
            int seconds = (int) simulationTime;
            publish(EventType.SIMULATION_FINISHED, null,
                    "SİMÜLASYON BİTTİ — toplam %d | etkisiz %d | sızan %d | alandan çıkan %d | süre %02d:%02d"
                            .formatted(spawnedCount.get(), neutralizedCount.get(), escapedCount.get(),
                                    leftAreaCount.get(), seconds / 60, seconds % 60));
        }
    }

    // =================================================================== efektler

    private void addEffect(EffectType type, double x, double y, double duration) {
        effects.add(new ActiveEffect(type, x, y, duration));
    }

    private void ageEffects(double dt) {
        Iterator<ActiveEffect> it = effects.iterator();
        while (it.hasNext()) {
            ActiveEffect effect = it.next();
            effect.age += dt;
            if (effect.age >= effect.duration) {
                it.remove();
            }
        }
    }

    // =================================================================== dünya yönetimi

    private void resetWorld() {
        targets.clear();
        swarms.clear();
        pendingActions.clear();
        effects.clear();
        assessedIds.clear();
        lastPriorities = List.of();
        defense.reset();
        radar.resetSweep();
        simulationTime = 0;
        stepAccumulator = 0;
        tickCount = 0;
        actionSequence = 0;
        idSequence.set(0);
        swarmSequence.set(0);
        spawnedCount.set(0);
        neutralizedCount.set(0);
        escapedCount.set(0);
        leftAreaCount.set(0);
        random = new Random(currentScenario != null ? currentScenario.seed() : config.defaultSeed());
        state.set(SimulationState.READY);
    }

    private void populateScenario(Scenario scenario) {
        quietSpawns = true;
        try {
            scenario.populate(scenarioContext);
        } finally {
            quietSpawns = false;
        }
        publish(EventType.SCENARIO_LOADED, null, "Senaryo yüklendi: %s — %s (seed %d, %d hedef hazır)"
                .formatted(scenario.name(), scenario.description(), scenario.seed(), targets.size()));
    }

    private Target spawnInternal(TargetType type, double distance, double bearingDeg,
                                 double headingOffsetDeg, double baseSpeed) {
        double x = distance * Math.sin(Math.toRadians(bearingDeg));
        double y = distance * Math.cos(Math.toRadians(bearingDeg));
        double heading = Geometry.bearingOf(-x, -y) + headingOffsetDeg;
        Target target = factory.create(type, nextId(type), x, y, heading, baseSpeed, random);
        registerTarget(target);
        return target;
    }

    private void registerTarget(Target target) {
        targets.put(target.getId(), target);
        spawnedCount.incrementAndGet();
        if (!quietSpawns) {
            publish(EventType.TARGET_SPAWNED, target.getId(), "%s eklendi — %s, %.0f km/h, kerteriz %03.0f°"
                    .formatted(target.getId(), target.getType().displayName(),
                            Geometry.toKmh(target.getSpeed()), target.bearingFromOrigin()));
        }
    }

    private String nextId(TargetType type) {
        return "%s-%02d".formatted(type.idPrefix(), idSequence.incrementAndGet());
    }

    private void publish(EventType type, String targetId, String message) {
        eventBus.publish(SimulationEvent.of(simulationTime, type, targetId, message));
    }

    private static String formatMultiplier(double multiplier) {
        return multiplier == Math.rint(multiplier)
                ? String.valueOf((long) multiplier)
                : String.valueOf(multiplier);
    }

    // =================================================================== snapshot

    private SimulationSnapshot buildSnapshot() {
        List<TargetView> views = new ArrayList<>(targets.size());
        int detected = 0;
        int high = 0;
        int medium = 0;
        int low = 0;
        for (Target t : targets.values()) {
            int n = t.getTrailCount();
            double[] trailX = new double[n];
            double[] trailY = new double[n];
            int copied = t.copyTrail(trailX, trailY);
            double distance = radar.distanceTo(t);
            views.add(new TargetView(t.getId(), t.getType(), t.getStatus(), t.getX(), t.getY(),
                    t.getVelocityX(), t.getVelocityY(), t.getSpeed(), t.getHeading(), distance,
                    t.isDetected(), t.getThreat(), t.getSwarmId(), trailX, trailY, copied));
            if (t.isDetected()) {
                detected++;
                switch (t.getThreatLevel()) {
                    case HIGH -> high++;
                    case MEDIUM -> medium++;
                    case LOW -> low++;
                }
            }
        }

        List<PriorityEntry> priorities = new ArrayList<>(config.priorityListSize());
        int rank = 1;
        for (Target t : lastPriorities) {
            if (rank > config.priorityListSize()) {
                break;
            }
            if (!t.isActive() || !t.isDetected()) {
                continue;
            }
            priorities.add(new PriorityEntry(rank++, t.getId(), t.getType(), t.getThreatScore(),
                    t.getThreatLevel(), radar.distanceTo(t), t.getSpeed(), t.getTimeToImpact()));
        }

        List<Effect> effectViews = new ArrayList<>(effects.size());
        for (ActiveEffect e : effects) {
            effectViews.add(new Effect(e.type, e.x, e.y, Math.min(1.0, e.age / e.duration)));
        }

        Target defenseTarget = defense.getCurrentTarget();
        DefenseView defenseView = new DefenseView(defense.getState(),
                defenseTarget == null ? null : defenseTarget.getId(),
                defense.getTrackingTime(), defense.getStateTime());

        Stats stats = new Stats(targets.size(), detected, high, medium, low,
                neutralizedCount.get(), escapedCount.get(), leftAreaCount.get(),
                spawnedCount.get(), pendingActions.size());

        return new SimulationSnapshot(tickCount, simulationTime, state.get(),
                currentScenario == null ? "Manuel" : currentScenario.name(),
                speedMultiplier, lastTickMillis,
                new RadarView(radar.getRange(), radar.getSweepAngle(), radar.isActive()),
                defenseView, stats, views, priorities, effectViews);
    }

    // =================================================================== iç tipler

    private record ScheduledAction(double time, long sequence, Runnable action) {
    }

    private static final class ActiveEffect {
        private final EffectType type;
        private final double x;
        private final double y;
        private final double duration;
        private double age;

        private ActiveEffect(EffectType type, double x, double y, double duration) {
            this.type = type;
            this.x = x;
            this.y = y;
            this.duration = duration;
        }
    }

    /** Senaryoların motorla etkileşimi; yalnızca simülasyon thread'inde çağrılır. */
    private final class EngineScenarioContext implements ScenarioContext {

        @Override
        public Random random() {
            return random;
        }

        @Override
        public double radarRange() {
            return radar.getRange();
        }

        @Override
        public Target spawn(TargetType type, double distance, double bearingDeg,
                            double headingOffsetDeg, double baseSpeed) {
            return spawnInternal(type, distance, bearingDeg, headingOffsetDeg, baseSpeed);
        }

        @Override
        public DroneSwarm spawnSwarm(List<TargetType> composition, double distance, double bearingDeg,
                                     double baseSpeed, double spread, SwarmConfig swarmConfig) {
            double cx = distance * Math.sin(Math.toRadians(bearingDeg));
            double cy = distance * Math.cos(Math.toRadians(bearingDeg));
            double heading = Geometry.bearingOf(-cx, -cy);
            List<Target> members = new ArrayList<>(composition.size());
            for (TargetType type : composition) {
                double r = spread * Math.sqrt(random.nextDouble());
                double a = random.nextDouble() * 2 * Math.PI;
                double x = cx + r * Math.cos(a);
                double y = cy + r * Math.sin(a);
                members.add(factory.create(type, nextId(type), x, y, heading, baseSpeed, random));
            }
            String swarmId = "SÜRÜ-" + swarmSequence.incrementAndGet();
            DroneSwarm swarm = new DroneSwarm(swarmId, members, swarmConfig, 0, 0);
            for (Target member : members) {
                registerTarget(member);
            }
            swarms.add(swarm);
            if (!quietSpawns) {
                publish(EventType.TARGET_SPAWNED, null,
                        "%s %d üyeyle eklendi".formatted(swarmId, members.size()));
            }
            return swarm;
        }

        @Override
        public void schedule(double delaySeconds, Runnable action) {
            pendingActions.add(new ScheduledAction(simulationTime + Math.max(0, delaySeconds),
                    actionSequence++, action));
        }
    }
}
