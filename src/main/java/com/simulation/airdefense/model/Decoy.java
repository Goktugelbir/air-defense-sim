package com.simulation.airdefense.model;

/**
 * Radarda gerçek bir hedef gibi görünen yem (decoy). Hafif dalgalanarak merkeze ilerler;
 * tür bileşeni düşük olduğu için tehdit skoru düşük kalır.
 */
public final class Decoy extends Target {

    /** Dalgalanma genliği (derece). */
    public static final double WEAVE_AMPLITUDE_DEG = 12.0;
    /** Dalgalanma periyodu (s). */
    public static final double WEAVE_PERIOD = 6.0;
    /** Saniyedeki maksimum dönüş (derece). */
    public static final double TURN_RATE_DEG = 30.0;

    private double phase;

    public Decoy(String id, double x, double y, double headingDeg, double speed) {
        this(id, x, y, headingDeg, speed, DEFAULT_TRAIL_LENGTH);
    }

    public Decoy(String id, double x, double y, double headingDeg, double speed, int trailLength) {
        super(id, TargetType.DECOY, x, y, headingDeg, speed, trailLength);
    }

    @Override
    public void update(double dt) {
        if (!isSwarmControlled()) {
            phase += dt;
            double weave = WEAVE_AMPLITUDE_DEG * Math.sin(2 * Math.PI * phase / WEAVE_PERIOD);
            turnTowards(headingToOrigin() + weave, TURN_RATE_DEG * dt);
        }
        integrate(dt);
    }
}
