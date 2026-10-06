package com.simulation.airdefense.model;

/**
 * Bir hedef için tehdit değerlendirmesinin sonucu.
 *
 * @param score              toplam skor (0–100)
 * @param level              skordan türetilen seviye
 * @param distanceScore      merkeze yakınlık bileşeni (0–1)
 * @param speedScore         hız bileşeni (0–1)
 * @param directionScore     yönün merkeze bakma derecesi (0–1)
 * @param timeToImpactScore  merkeze varış süresi bileşeni (0–1)
 * @param typeScore          tür bileşeni (0–1)
 * @param timeToImpact       mevcut hızla merkeze varış süresi (s), yaklaşmıyorsa {@link Double#POSITIVE_INFINITY}
 */
public record ThreatResult(double score,
                           ThreatLevel level,
                           double distanceScore,
                           double speedScore,
                           double directionScore,
                           double timeToImpactScore,
                           double typeScore,
                           double timeToImpact) {

    /** Henüz değerlendirilmemiş hedefler için başlangıç sonucu. */
    public static final ThreatResult NONE =
            new ThreatResult(0, ThreatLevel.LOW, 0, 0, 0, 0, 0, Double.POSITIVE_INFINITY);

    public ThreatResult {
        if (score < 0 || score > 100 || Double.isNaN(score)) {
            throw new IllegalArgumentException("score must be within [0, 100]: " + score);
        }
        if (level == null) {
            throw new IllegalArgumentException("level must not be null");
        }
    }

    /** Aynı skor ve bileşenlerle, farklı seviyeye sahip bir kopya. */
    public ThreatResult withLevel(ThreatLevel newLevel) {
        return newLevel == level ? this : new ThreatResult(score, newLevel, distanceScore, speedScore,
                directionScore, timeToImpactScore, typeScore, timeToImpact);
    }

    /** Arayüzde gösterilen tam sayı skor. */
    public int roundedScore() {
        return (int) Math.round(score);
    }
}
