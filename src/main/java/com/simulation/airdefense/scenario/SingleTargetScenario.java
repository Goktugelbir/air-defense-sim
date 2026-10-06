package com.simulation.airdefense.scenario;

import com.simulation.airdefense.model.TargetType;

/** Tek yönden gelen tek bir normal İHA. Algoritmaları adım adım izlemek için idealdir. */
public final class SingleTargetScenario implements Scenario {

    private static final double SPAWN_RANGE_FACTOR = 1.12;
    private static final double SPEED = 70.0;

    private final long seed;

    public SingleTargetScenario(long seed) {
        this.seed = seed;
    }

    @Override
    public String name() {
        return "Tek Hedef";
    }

    @Override
    public String description() {
        return "1 normal İHA, tek yönden düz yaklaşma";
    }

    @Override
    public long seed() {
        return seed;
    }

    @Override
    public void populate(ScenarioContext context) {
        double bearing = context.random().nextDouble() * 360.0;
        context.spawn(TargetType.DRONE, context.radarRange() * SPAWN_RANGE_FACTOR, bearing, 0.0, SPEED);
    }
}
