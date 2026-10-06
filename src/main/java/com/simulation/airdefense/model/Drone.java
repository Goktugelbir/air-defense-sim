package com.simulation.airdefense.model;

/** Sabit rotada düz ilerleyen standart İHA. */
public final class Drone extends Target {

    public Drone(String id, double x, double y, double headingDeg, double speed) {
        this(id, x, y, headingDeg, speed, DEFAULT_TRAIL_LENGTH);
    }

    public Drone(String id, double x, double y, double headingDeg, double speed, int trailLength) {
        super(id, TargetType.DRONE, x, y, headingDeg, speed, trailLength);
    }

    @Override
    public void update(double dt) {
        // Düz hareket: hız vektörü değişmez (sürüdeyse sürü belirler).
        integrate(dt);
    }
}
