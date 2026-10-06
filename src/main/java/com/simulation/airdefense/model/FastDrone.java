package com.simulation.airdefense.model;

/** Normal İHA'dan {@value #SPEED_FACTOR} kat hızlı, düz ilerleyen hedef. */
public final class FastDrone extends Target {

    /** Temel hıza uygulanan çarpan (2–3 kat aralığında). */
    public static final double SPEED_FACTOR = 2.5;

    /**
     * @param baseSpeed normal bir İHA'nın hızı (m/s); gerçek hız bunun {@value #SPEED_FACTOR} katıdır
     */
    public FastDrone(String id, double x, double y, double headingDeg, double baseSpeed) {
        this(id, x, y, headingDeg, baseSpeed, DEFAULT_TRAIL_LENGTH);
    }

    public FastDrone(String id, double x, double y, double headingDeg, double baseSpeed, int trailLength) {
        super(id, TargetType.FAST_DRONE, x, y, headingDeg, baseSpeed * SPEED_FACTOR, trailLength);
    }

    @Override
    public void update(double dt) {
        integrate(dt);
    }
}
