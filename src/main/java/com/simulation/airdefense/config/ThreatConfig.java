package com.simulation.airdefense.config;

import com.simulation.airdefense.model.TargetType;
import com.simulation.airdefense.model.ThreatLevel;

/**
 * Tehdit skoru hesabının tüm parametreleri. Ağırlıkların toplamı 1 olmalıdır.
 *
 * @param distanceWeight      yakınlık bileşeninin ağırlığı
 * @param speedWeight         hız bileşeninin ağırlığı
 * @param directionWeight     yön bileşeninin ağırlığı
 * @param timeToImpactWeight  varış süresi bileşeninin ağırlığı
 * @param typeWeight          tür bileşeninin ağırlığı
 * @param referenceMaxSpeed   speedScore = 1 kabul edilen hız (m/s)
 * @param timeToImpactHorizon bu süreden (s) uzun varış süreleri 0 puan alır
 * @param mediumThreshold     MEDIUM seviyesinin başladığı tam sayı skor (dahil)
 * @param highThreshold       HIGH seviyesinin başladığı tam sayı skor (dahil)
 * @param droneTypeScore      Drone tür puanı (0–1)
 * @param fastDroneTypeScore  FastDrone tür puanı (0–1)
 * @param maneuveringTypeScore ManeuveringDrone tür puanı (0–1)
 * @param decoyTypeScore      Decoy tür puanı (0–1)
 */
public record ThreatConfig(double distanceWeight,
                           double speedWeight,
                           double directionWeight,
                           double timeToImpactWeight,
                           double typeWeight,
                           double referenceMaxSpeed,
                           double timeToImpactHorizon,
                           int mediumThreshold,
                           int highThreshold,
                           double droneTypeScore,
                           double fastDroneTypeScore,
                           double maneuveringTypeScore,
                           double decoyTypeScore) {

    private static final double WEIGHT_TOLERANCE = 1e-6;

    public ThreatConfig {
        double sum = distanceWeight + speedWeight + directionWeight + timeToImpactWeight + typeWeight;
        if (Math.abs(sum - 1.0) > WEIGHT_TOLERANCE) {
            throw new IllegalArgumentException("Threat weights must sum to 1, got " + sum);
        }
        if (referenceMaxSpeed <= 0 || timeToImpactHorizon <= 0) {
            throw new IllegalArgumentException("referenceMaxSpeed and timeToImpactHorizon must be positive");
        }
        if (mediumThreshold <= 0 || highThreshold <= mediumThreshold || highThreshold > 100) {
            throw new IllegalArgumentException("Invalid thresholds: " + mediumThreshold + ", " + highThreshold);
        }
    }

    /** Varsayılan değerler: 0–30 LOW, 31–70 MEDIUM, 71–100 HIGH. */
    public static ThreatConfig defaults() {
        return new ThreatConfig(
                0.30, 0.15, 0.20, 0.20, 0.15,
                150.0, 120.0,
                31, 71,
                0.60, 0.85, 0.80, 0.10);
    }

    /** Hedef türünün 0–1 aralığındaki tür puanı. */
    public double typeScore(TargetType type) {
        return switch (type) {
            case DRONE -> droneTypeScore;
            case FAST_DRONE -> fastDroneTypeScore;
            case MANEUVERING_DRONE -> maneuveringTypeScore;
            case DECOY -> decoyTypeScore;
        };
    }

    /**
     * Seviye değişimi için histerezis payı (puan). Skor eşiğin hemen etrafında salınırken
     * seviyenin sürekli değişip log'u doldurmasını engeller.
     */
    public static final double LEVEL_HYSTERESIS = 3.0;

    /**
     * Önceki seviyeyi dikkate alarak seviye belirler: yeni seviye ancak skor eşiği
     * {@link #LEVEL_HYSTERESIS} kadar geçtiğinde kabul edilir.
     */
    public ThreatLevel levelFor(double score, ThreatLevel previous) {
        ThreatLevel raw = levelFor(score);
        if (previous == null || raw == previous) {
            return raw;
        }
        ThreatLevel shifted = raw.compareTo(previous) > 0
                ? levelFor(score - LEVEL_HYSTERESIS)
                : levelFor(score + LEVEL_HYSTERESIS);
        return shifted == previous ? previous : raw;
    }

    /** 0–100 skoru seviyeye çevirir (tam sayıya yuvarlanmış skor üzerinden). */
    public ThreatLevel levelFor(double score) {
        long rounded = Math.round(score);
        if (rounded >= highThreshold) {
            return ThreatLevel.HIGH;
        }
        if (rounded >= mediumThreshold) {
            return ThreatLevel.MEDIUM;
        }
        return ThreatLevel.LOW;
    }
}
