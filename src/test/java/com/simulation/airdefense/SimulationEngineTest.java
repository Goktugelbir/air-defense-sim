package com.simulation.airdefense;

import com.simulation.airdefense.config.DefenseConfig;
import com.simulation.airdefense.config.SimulationConfig;
import com.simulation.airdefense.config.ThreatConfig;
import com.simulation.airdefense.event.EventBus;
import com.simulation.airdefense.scenario.RandomAttackScenario;
import com.simulation.airdefense.scenario.Scenario;
import com.simulation.airdefense.scenario.SingleTargetScenario;
import com.simulation.airdefense.simulation.SimulationEngine;
import com.simulation.airdefense.simulation.SimulationSnapshot;
import com.simulation.airdefense.simulation.SimulationState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Motorun uçtan uca davranışı: senaryonun bitmesi ve seed ile tekrar edilebilirlik. */
class SimulationEngineTest {

    /** Senaryoyu verilen hızda sonuna kadar çalıştırır ve son snapshot'ı döndürür. */
    private static SimulationSnapshot runToCompletion(Scenario scenario, double speed) throws InterruptedException {
        SimulationEngine engine = new SimulationEngine(SimulationConfig.defaults(), ThreatConfig.defaults(),
                DefenseConfig.defaults(), new EventBus());
        try {
            engine.setSpeed(speed);
            engine.loadScenario(scenario);
            engine.start();
            while (engine.getSnapshot().state() != SimulationState.FINISHED) {
                Thread.sleep(20);
            }
            return engine.getSnapshot();
        } finally {
            engine.shutdown();
        }
    }

    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    void singleTargetScenarioFinishesWithEveryTargetAccountedFor() throws InterruptedException {
        SimulationSnapshot end = runToCompletion(new SingleTargetScenario(42), 32);
        SimulationSnapshot.Stats s = end.stats();
        assertEquals(1, s.spawned());
        assertEquals(0, s.active());
        assertEquals(s.spawned(), s.neutralized() + s.escaped() + s.leftArea());
    }

    @Test
    @Timeout(value = 120, unit = TimeUnit.SECONDS)
    void sameSeedGivesSameOutcomeRegardlessOfSpeed() throws InterruptedException {
        SimulationSnapshot a = runToCompletion(new RandomAttackScenario(7), 16);
        SimulationSnapshot b = runToCompletion(new RandomAttackScenario(7), 32);
        assertEquals(a.stats(), b.stats());
        assertEquals(a.tick(), b.tick());
        assertEquals(a.simulationTime(), b.simulationTime(), 1e-9);
    }
}
