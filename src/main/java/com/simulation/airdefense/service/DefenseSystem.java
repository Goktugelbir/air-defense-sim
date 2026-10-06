package com.simulation.airdefense.service;

import com.simulation.airdefense.config.DefenseConfig;
import com.simulation.airdefense.event.EventBus;
import com.simulation.airdefense.event.EventType;
import com.simulation.airdefense.event.SimulationEvent;
import com.simulation.airdefense.model.Target;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.random.RandomGenerator;

/**
 * Eğitim amaçlı, soyut savunma sistemi. Gerçek bir silah veya güdüm modeli içermez;
 * yalnızca durum yönetimi ve algoritmik karar verme sürecini modeller (State pattern).
 *
 * <pre>
 * IDLE ──(öncelikli hedef var)──▶ SELECTING ──(seçim süresi)──▶ TRACKING
 * TRACKING ──(takip ≥ min süre ve mesafe &lt; angajman menzili)──▶ ENGAGING
 * ENGAGING ──(deneme süresi, olasılıksal sonuç)──▶ COOLDOWN
 * COOLDOWN ──(hedef hâlâ geçerli)──▶ TRACKING   |   (değilse) ──▶ IDLE
 * </pre>
 * Simülasyon thread'inde çalışır; thread-safe değildir.
 */
public class DefenseSystem {

    private final DefenseConfig config;
    private final EventBus eventBus;

    private DefenseState state = DefenseState.IDLE;
    private Target currentTarget;
    private double stateTime;
    private double trackingTime;
    private int engagementCount;
    private int neutralizedCount;

    public DefenseSystem(DefenseConfig config, EventBus eventBus) {
        this.config = Objects.requireNonNull(config, "config");
        this.eventBus = Objects.requireNonNull(eventBus, "eventBus");
    }

    /**
     * Durum makinesini bir adım ilerletir.
     *
     * @param dt         zaman adımı (s)
     * @param simTime    simülasyon zamanı (log için)
     * @param priorities öncelik sırasına dizilmiş hedefler
     * @param random     angajman sonucu için rastgele kaynak
     * @return bu adımda bir angajman sonuçlandıysa sonucu
     */
    public Optional<EngagementOutcome> update(double dt, double simTime,
                                              List<Target> priorities, RandomGenerator random) {
        stateTime += dt;
        return switch (state) {
            case IDLE -> {
                if (!priorities.isEmpty()) {
                    transition(DefenseState.SELECTING);
                }
                yield Optional.empty();
            }
            case SELECTING -> {
                if (stateTime >= config.selectionDuration()) {
                    selectTop(simTime, priorities);
                }
                yield Optional.empty();
            }
            case TRACKING -> {
                handleTracking(dt, simTime, priorities);
                yield Optional.empty();
            }
            case ENGAGING -> handleEngaging(simTime, random);
            case COOLDOWN -> {
                if (stateTime >= config.cooldownDuration()) {
                    if (isValid(currentTarget)) {
                        currentTarget.markTracking();
                        transition(DefenseState.TRACKING);
                    } else {
                        currentTarget = null;
                        transition(DefenseState.IDLE);
                    }
                }
                yield Optional.empty();
            }
        };
    }

    private void selectTop(double simTime, List<Target> priorities) {
        if (priorities.isEmpty()) {
            transition(DefenseState.IDLE);
            return;
        }
        beginTracking(simTime, priorities.getFirst());
    }

    private void beginTracking(double simTime, Target target) {
        currentTarget = target;
        trackingTime = 0;
        target.markTracking();
        transition(DefenseState.TRACKING);
        publish(simTime, EventType.PRIORITY_SELECTED, target,
                "%s öncelikli hedef olarak seçildi (skor %d)".formatted(
                        target.getId(), target.getThreat().roundedScore()));
    }

    private void handleTracking(double dt, double simTime, List<Target> priorities) {
        if (!isValid(currentTarget)) {
            release();
            transition(priorities.isEmpty() ? DefenseState.IDLE : DefenseState.SELECTING);
            return;
        }
        if (!priorities.isEmpty()) {
            Target top = priorities.getFirst();
            if (top != currentTarget
                    && top.getThreatScore() > currentTarget.getThreatScore() + config.retargetMargin()) {
                release();
                beginTracking(simTime, top);
                return;
            }
        }
        trackingTime += dt;
        if (trackingTime >= config.minTrackingTime() && distance(currentTarget) < config.engagementRange()) {
            currentTarget.markEngaged();
            engagementCount++;
            transition(DefenseState.ENGAGING);
            publish(simTime, EventType.ENGAGEMENT_STARTED, currentTarget,
                    "%s angajman denemesi #%d, mesafe %.0f m".formatted(
                            currentTarget.getId(), engagementCount, distance(currentTarget)));
        }
    }

    private Optional<EngagementOutcome> handleEngaging(double simTime, RandomGenerator random) {
        if (stateTime < config.engagementDuration()) {
            return Optional.empty();
        }
        Target target = currentTarget;
        if (!isValid(target)) {
            currentTarget = null;
            transition(DefenseState.COOLDOWN);
            return Optional.empty();
        }
        double probability = config.successRate(target.getType());
        boolean success = random.nextDouble() < probability;
        EngagementOutcome outcome = new EngagementOutcome(target, success, probability, target.getX(), target.getY());
        if (success) {
            target.neutralize();
            neutralizedCount++;
            currentTarget = null;
            publish(simTime, EventType.TARGET_NEUTRALIZED, target,
                    "%s etkisiz hale getirildi (p=%%%.0f)".formatted(target.getId(), probability * 100));
        } else {
            target.markTracking();
            publish(simTime, EventType.ENGAGEMENT_FAILED, target,
                    "%s angajman başarısız (p=%%%.0f), soğumadan sonra tekrar denenecek".formatted(
                            target.getId(), probability * 100));
        }
        transition(DefenseState.COOLDOWN);
        return Optional.of(outcome);
    }

    private boolean isValid(Target target) {
        return target != null && target.isActive() && target.isDetected() && !target.getStatus().isTerminal();
    }

    private double distance(Target target) {
        return Math.hypot(target.getX(), target.getY());
    }

    private void release() {
        if (currentTarget != null) {
            currentTarget.releaseTracking();
        }
        currentTarget = null;
        trackingTime = 0;
    }

    private void transition(DefenseState next) {
        state = next;
        stateTime = 0;
    }

    private void publish(double simTime, EventType type, Target target, String message) {
        eventBus.publish(SimulationEvent.of(simTime, type, target.getId(), message));
    }

    /** Sistemi başlangıç durumuna döndürür. */
    public void reset() {
        release();
        state = DefenseState.IDLE;
        stateTime = 0;
        engagementCount = 0;
        neutralizedCount = 0;
    }

    /** Radar kapandığında veya hedef kaybolduğunda takibi bırakır. */
    public void abort() {
        release();
        transition(DefenseState.IDLE);
    }

    public DefenseState getState() {
        return state;
    }

    public Target getCurrentTarget() {
        return currentTarget;
    }

    public double getTrackingTime() {
        return trackingTime;
    }

    public double getStateTime() {
        return stateTime;
    }

    public int getEngagementCount() {
        return engagementCount;
    }

    public int getNeutralizedCount() {
        return neutralizedCount;
    }

    public DefenseConfig getConfig() {
        return config;
    }
}
