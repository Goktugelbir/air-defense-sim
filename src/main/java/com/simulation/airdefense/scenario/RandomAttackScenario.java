package com.simulation.airdefense.scenario;

import com.simulation.airdefense.model.TargetType;

import java.util.Random;

/** Farklı yönlerden, zamana yayılmış şekilde gelen 15–25 karışık hedef. */
public final class RandomAttackScenario implements Scenario {

    private static final int MIN_TARGETS = 15;
    private static final int MAX_TARGETS = 25;
    private static final double MIN_INTERVAL = 1.0;
    private static final double MAX_INTERVAL = 4.0;
    private static final double MIN_RANGE_FACTOR = 1.05;
    private static final double MAX_RANGE_FACTOR = 1.20;
    private static final double MAX_HEADING_OFFSET = 8.0;
    private static final double SPEED_JITTER = 0.2;

    private final long seed;

    public RandomAttackScenario(long seed) {
        this.seed = seed;
    }

    @Override
    public String name() {
        return "Rastgele Saldırı";
    }

    @Override
    public String description() {
        return "%d–%d karışık hedef, farklı yönlerden zamanla".formatted(MIN_TARGETS, MAX_TARGETS);
    }

    @Override
    public long seed() {
        return seed;
    }

    @Override
    public void populate(ScenarioContext context) {
        Random random = context.random();
        int count = MIN_TARGETS + random.nextInt(MAX_TARGETS - MIN_TARGETS + 1);
        double time = 0;
        for (int i = 0; i < count; i++) {
            TargetType type = pickType(random);
            double distance = context.radarRange()
                    * (MIN_RANGE_FACTOR + random.nextDouble() * (MAX_RANGE_FACTOR - MIN_RANGE_FACTOR));
            double bearing = random.nextDouble() * 360.0;
            double offset = (random.nextDouble() * 2 - 1) * MAX_HEADING_OFFSET;
            double speed = type.defaultBaseSpeed() * (1 + (random.nextDouble() * 2 - 1) * SPEED_JITTER);
            if (i == 0) {
                context.spawn(type, distance, bearing, offset, speed);
            } else {
                time += MIN_INTERVAL + random.nextDouble() * (MAX_INTERVAL - MIN_INTERVAL);
                context.schedule(time, () -> context.spawn(type, distance, bearing, offset, speed));
            }
        }
    }

    private static TargetType pickType(Random random) {
        double r = random.nextDouble();
        if (r < 0.40) {
            return TargetType.DRONE;
        }
        if (r < 0.60) {
            return TargetType.FAST_DRONE;
        }
        if (r < 0.85) {
            return TargetType.MANEUVERING_DRONE;
        }
        return TargetType.DECOY;
    }
}
