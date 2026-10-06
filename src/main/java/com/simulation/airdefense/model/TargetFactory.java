package com.simulation.airdefense.model;

import java.util.Random;

/**
 * Hedef nesnelerini türüne göre üreten fabrika (Factory pattern).
 * Switch pattern matching ile her tür doğru alt sınıfa eşlenir.
 */
public final class TargetFactory {

    private final int trailLength;

    public TargetFactory(int trailLength) {
        this.trailLength = trailLength;
    }

    /**
     * @param type       hedef türü
     * @param id         benzersiz kimlik
     * @param x          başlangıç x (m)
     * @param y          başlangıç y (m)
     * @param headingDeg başlangıç yönü
     * @param baseSpeed  temel hız (m/s); FastDrone kendi çarpanını uygular
     * @param random     manevra davranışı için rastgele kaynak (tekrar edilebilirlik için seed'li)
     */
    public Target create(TargetType type, String id, double x, double y,
                         double headingDeg, double baseSpeed, Random random) {
        return switch (type) {
            case DRONE -> new Drone(id, x, y, headingDeg, baseSpeed, trailLength);
            case FAST_DRONE -> new FastDrone(id, x, y, headingDeg, baseSpeed, trailLength);
            case MANEUVERING_DRONE -> new ManeuveringDrone(id, x, y, headingDeg, baseSpeed,
                    new Random(random.nextLong()), trailLength);
            case DECOY -> new Decoy(id, x, y, headingDeg, baseSpeed, trailLength);
        };
    }
}
