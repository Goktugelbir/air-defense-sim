package com.simulation.airdefense.scenario;

import com.simulation.airdefense.model.TargetType;
import com.simulation.airdefense.swarm.SwarmConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Aynı anda 50+ hedef: tüm yönlerden bireysel hedefler ve iki küçük sürü.
 * Performans ve önceliklendirme davranışını göstermek için kullanılır.
 */
public final class HighDensityScenario implements Scenario {

    private static final int INDIVIDUAL_TARGETS = 36;
    private static final int SWARM_SIZE = 10;
    private static final int SWARM_COUNT = 2;
    private static final double MIN_RANGE_FACTOR = 1.03;
    private static final double MAX_RANGE_FACTOR = 1.22;
    private static final double MAX_HEADING_OFFSET = 6.0;
    private static final double SWARM_SPEED = 45.0;
    private static final double SWARM_SPREAD = 350.0;

    private final long seed;

    public HighDensityScenario(long seed) {
        this.seed = seed;
    }

    @Override
    public String name() {
        return "Yoğun Saldırı";
    }

    @Override
    public String description() {
        return "%d hedef aynı anda, tüm türler karışık".formatted(INDIVIDUAL_TARGETS + SWARM_SIZE * SWARM_COUNT);
    }

    @Override
    public long seed() {
        return seed;
    }

    @Override
    public void populate(ScenarioContext context) {
        Random random = context.random();
        TargetType[] types = TargetType.values();
        for (int i = 0; i < INDIVIDUAL_TARGETS; i++) {
            TargetType type = types[i % types.length];
            double distance = context.radarRange()
                    * (MIN_RANGE_FACTOR + random.nextDouble() * (MAX_RANGE_FACTOR - MIN_RANGE_FACTOR));
            double bearing = (360.0 / INDIVIDUAL_TARGETS) * i + random.nextDouble() * 8.0;
            double offset = (random.nextDouble() * 2 - 1) * MAX_HEADING_OFFSET;
            context.spawn(type, distance, bearing, offset, type.defaultBaseSpeed());
        }
        double firstBearing = random.nextDouble() * 360.0;
        for (int s = 0; s < SWARM_COUNT; s++) {
            List<TargetType> composition = new ArrayList<>();
            for (int i = 0; i < SWARM_SIZE; i++) {
                composition.add(i % 4 == 3 ? TargetType.DECOY : TargetType.DRONE);
            }
            double bearing = firstBearing + s * 180.0;
            context.spawnSwarm(composition, context.radarRange() * MAX_RANGE_FACTOR, bearing,
                    SWARM_SPEED, SWARM_SPREAD, SwarmConfig.defaults());
        }
    }
}
