package com.simulation.airdefense.simulation;

import com.simulation.airdefense.model.TargetStatus;
import com.simulation.airdefense.model.TargetType;
import com.simulation.airdefense.model.ThreatLevel;
import com.simulation.airdefense.model.ThreatResult;
import com.simulation.airdefense.service.DefenseState;

import java.util.List;

/**
 * Bir tick sonunda üretilen değiştirilemez simülasyon durumu.
 * <p>
 * Simülasyon thread'i her tick'te yeni bir snapshot üretip {@code AtomicReference} içine koyar;
 * arayüz her karede yalnızca en son snapshot'ı okur. Böylece iki taraf arasında kilit gerekmez.
 */
public record SimulationSnapshot(long tick,
                                 double simulationTime,
                                 SimulationState state,
                                 String scenarioName,
                                 double speedMultiplier,
                                 double tickMillis,
                                 RadarView radar,
                                 DefenseView defense,
                                 Stats stats,
                                 List<TargetView> targets,
                                 List<PriorityEntry> priorities,
                                 List<Effect> effects) {

    public SimulationSnapshot {
        targets = List.copyOf(targets);
        priorities = List.copyOf(priorities);
        effects = List.copyOf(effects);
    }

    /** Başlangıçta, henüz tick yokken kullanılan boş snapshot. */
    public static SimulationSnapshot empty(double radarRange) {
        return new SimulationSnapshot(0, 0, SimulationState.READY, "—", 1.0, 0,
                new RadarView(radarRange, 0, true),
                new DefenseView(DefenseState.IDLE, null, 0, 0),
                new Stats(0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
                List.of(), List.of(), List.of());
    }

    /** ID ile hedef arar; yoksa {@code null}. */
    public TargetView findTarget(String id) {
        if (id == null) {
            return null;
        }
        for (TargetView t : targets) {
            if (t.id().equals(id)) {
                return t;
            }
        }
        return null;
    }

    /** Radarın anlık durumu. */
    public record RadarView(double range, double sweepAngle, boolean active) {
    }

    /** Savunma sisteminin anlık durumu. */
    public record DefenseView(DefenseState state, String targetId, double trackingTime, double stateTime) {
    }

    /** Sayısal istatistikler. */
    public record Stats(int active,
                        int detected,
                        int high,
                        int medium,
                        int low,
                        int neutralized,
                        int escaped,
                        int leftArea,
                        int spawned,
                        int pendingSpawns) {
    }

    /**
     * Bir hedefin çizim ve bilgi paneli için kopyası.
     * {@code trailX}/{@code trailY} eskiden yeniye sıralıdır ve yalnızca bu kayda aittir.
     */
    public record TargetView(String id,
                             TargetType type,
                             TargetStatus status,
                             double x,
                             double y,
                             double velocityX,
                             double velocityY,
                             double speed,
                             double heading,
                             double distance,
                             boolean detected,
                             ThreatResult threat,
                             String swarmId,
                             double[] trailX,
                             double[] trailY,
                             int trailCount) {

        public ThreatLevel level() {
            return threat.level();
        }

        public int score() {
            return threat.roundedScore();
        }
    }

    /** Öncelik listesindeki bir satır. */
    public record PriorityEntry(int rank,
                                String id,
                                TargetType type,
                                double score,
                                ThreatLevel level,
                                double distance,
                                double speed,
                                double timeToImpact) {
    }

    /** Görsel efekt türleri. */
    public enum EffectType {
        /** Radar algılama anında genişleyen halka. */
        PING,
        /** Etkisiz hale getirme anında sönen parlama. */
        NEUTRALIZED,
        /** Başarısız angajman. */
        MISS,
        /** Hedef merkeze sızdı. */
        ESCAPED
    }

    /**
     * Kısa ömürlü görsel efekt.
     *
     * @param progress 0 (yeni) – 1 (bitmek üzere)
     */
    public record Effect(EffectType type, double x, double y, double progress) {
    }
}
