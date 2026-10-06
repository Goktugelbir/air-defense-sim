package com.simulation.airdefense.model;

import java.util.Random;

/**
 * Belirli aralıklarla merkeze göre sağa/sola sapan (zikzak) İHA.
 * Genel olarak merkeze yaklaşır, ancak rotası öngörülemezdir.
 */
public final class ManeuveringDrone extends Target {

    /** Manevralar arasındaki minimum süre (s). */
    public static final double MIN_MANEUVER_INTERVAL = 2.0;
    /** Manevralar arasındaki maksimum süre (s). */
    public static final double MAX_MANEUVER_INTERVAL = 4.5;
    /** Merkez yönünden minimum sapma (derece). */
    public static final double MIN_OFFSET_DEG = 20.0;
    /** Merkez yönünden maksimum sapma (derece). */
    public static final double MAX_OFFSET_DEG = 55.0;
    /** Saniyedeki maksimum dönüş (derece). */
    public static final double TURN_RATE_DEG = 60.0;

    private final Random random;
    private double maneuverTimer;
    private double desiredOffset;
    private int side;

    public ManeuveringDrone(String id, double x, double y, double headingDeg, double speed, Random random) {
        this(id, x, y, headingDeg, speed, random, DEFAULT_TRAIL_LENGTH);
    }

    public ManeuveringDrone(String id, double x, double y, double headingDeg, double speed,
                            Random random, int trailLength) {
        super(id, TargetType.MANEUVERING_DRONE, x, y, headingDeg, speed, trailLength);
        this.random = random;
        this.side = random.nextBoolean() ? 1 : -1;
        scheduleNextManeuver();
    }

    private void scheduleNextManeuver() {
        maneuverTimer = MIN_MANEUVER_INTERVAL
                + random.nextDouble() * (MAX_MANEUVER_INTERVAL - MIN_MANEUVER_INTERVAL);
        side = -side;
        desiredOffset = side * (MIN_OFFSET_DEG + random.nextDouble() * (MAX_OFFSET_DEG - MIN_OFFSET_DEG));
    }

    @Override
    public void update(double dt) {
        if (!isSwarmControlled()) {
            maneuverTimer -= dt;
            if (maneuverTimer <= 0) {
                scheduleNextManeuver();
            }
            turnTowards(headingToOrigin() + desiredOffset, TURN_RATE_DEG * dt);
        }
        integrate(dt);
    }
}
