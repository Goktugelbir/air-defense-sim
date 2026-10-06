package com.simulation.airdefense.config;

import com.simulation.airdefense.model.TargetType;

/**
 * Soyut savunma sistemi durum makinesinin parametreleri.
 *
 * @param selectionDuration     SELECTING durumunda geçen süre (s)
 * @param minTrackingTime       ENGAGING'e geçmeden önce gereken minimum takip süresi (s)
 * @param engagementRange       ENGAGING'e geçiş için gereken maksimum mesafe (m)
 * @param engagementDuration    bir angajman denemesinin süresi (s)
 * @param cooldownDuration      her denemeden sonraki bekleme süresi (s)
 * @param baseSuccessRate       temel başarı olasılığı (0–1)
 * @param fastSuccessRate       hızlı hedeflere karşı başarı olasılığı
 * @param maneuveringSuccessRate manevralı hedeflere karşı başarı olasılığı
 * @param decoySuccessRate      yemlere karşı başarı olasılığı
 * @param retargetMargin        takip sırasında hedef değiştirmek için gereken skor farkı
 * @param escapeRadius          bu mesafeden (m) daha yakına gelen hedef sızmış sayılır
 */
public record DefenseConfig(double selectionDuration,
                            double minTrackingTime,
                            double engagementRange,
                            double engagementDuration,
                            double cooldownDuration,
                            double baseSuccessRate,
                            double fastSuccessRate,
                            double maneuveringSuccessRate,
                            double decoySuccessRate,
                            double retargetMargin,
                            double escapeRadius) {

    public DefenseConfig {
        requireProbability(baseSuccessRate, "baseSuccessRate");
        requireProbability(fastSuccessRate, "fastSuccessRate");
        requireProbability(maneuveringSuccessRate, "maneuveringSuccessRate");
        requireProbability(decoySuccessRate, "decoySuccessRate");
        if (selectionDuration < 0 || minTrackingTime < 0 || engagementDuration < 0 || cooldownDuration < 0) {
            throw new IllegalArgumentException("Durations must be non-negative");
        }
        if (engagementRange <= 0 || escapeRadius < 0) {
            throw new IllegalArgumentException("Invalid ranges");
        }
    }

    private static void requireProbability(double p, String name) {
        if (p < 0 || p > 1) {
            throw new IllegalArgumentException(name + " must be within [0, 1]: " + p);
        }
    }

    public static DefenseConfig defaults() {
        return new DefenseConfig(
                0.3, 2.0, 2500.0, 0.6, 1.2,
                0.70, 0.45, 0.50, 0.70,
                15.0, 100.0);
    }

    /** Hedef türüne göre başarı olasılığı. */
    public double successRate(TargetType type) {
        return switch (type) {
            case DRONE -> baseSuccessRate;
            case FAST_DRONE -> fastSuccessRate;
            case MANEUVERING_DRONE -> maneuveringSuccessRate;
            case DECOY -> decoySuccessRate;
        };
    }
}
