package com.simulation.airdefense.scenario;

import com.simulation.airdefense.model.TargetType;
import com.simulation.airdefense.swarm.SwarmConfig;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Tek yönden yaklaşan ~20 üyeli Boids sürüsü; içinde birkaç Decoy bulunur. */
public final class SwarmScenario implements Scenario {

    private static final int DRONES = 16;
    private static final int DECOYS = 4;
    private static final double SPAWN_RANGE_FACTOR = 1.15;
    private static final double SPEED = 45.0;
    private static final double SPREAD = 450.0;

    private final long seed;

    public SwarmScenario(long seed) {
        this.seed = seed;
    }

    @Override
    public String name() {
        return "Sürü Saldırısı";
    }

    @Override
    public String description() {
        return "%d üyeli sürü (%d yem), tek yönden".formatted(DRONES + DECOYS, DECOYS);
    }

    @Override
    public long seed() {
        return seed;
    }

    @Override
    public void populate(ScenarioContext context) {
        List<TargetType> composition = new ArrayList<>();
        for (int i = 0; i < DRONES; i++) {
            composition.add(TargetType.DRONE);
        }
        for (int i = 0; i < DECOYS; i++) {
            composition.add(TargetType.DECOY);
        }
        Collections.shuffle(composition, context.random());
        double bearing = context.random().nextDouble() * 360.0;
        context.spawnSwarm(composition, context.radarRange() * SPAWN_RANGE_FACTOR, bearing,
                SPEED, SPREAD, SwarmConfig.defaults());
    }
}
