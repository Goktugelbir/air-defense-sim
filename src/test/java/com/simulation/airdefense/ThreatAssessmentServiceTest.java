package com.simulation.airdefense;

import com.simulation.airdefense.config.ThreatConfig;
import com.simulation.airdefense.model.Decoy;
import com.simulation.airdefense.model.Drone;
import com.simulation.airdefense.model.FastDrone;
import com.simulation.airdefense.model.Geometry;
import com.simulation.airdefense.model.Target;
import com.simulation.airdefense.model.ThreatLevel;
import com.simulation.airdefense.model.ThreatResult;
import com.simulation.airdefense.radar.Radar;
import com.simulation.airdefense.service.ThreatAssessmentService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ThreatAssessmentServiceTest {

    private final ThreatConfig config = ThreatConfig.defaults();
    private final ThreatAssessmentService service = new ThreatAssessmentService(config);
    private final Radar radar = new Radar(0, 0, 5000, 4);

    /** Merkeze doğru uçan, verilen kerterizde ve mesafedeki drone. */
    private static Drone droneTowardsCenter(double distance, double bearing, double speed) {
        double x = distance * Math.sin(Math.toRadians(bearing));
        double y = distance * Math.cos(Math.toRadians(bearing));
        return new Drone("D", x, y, Geometry.bearingOf(-x, -y), speed);
    }

    @Test
    void scoreStaysWithinZeroToHundredForRandomTargets() {
        Random random = new Random(7);
        for (int i = 0; i < 5_000; i++) {
            double x = (random.nextDouble() * 2 - 1) * 12_000;
            double y = (random.nextDouble() * 2 - 1) * 12_000;
            double heading = random.nextDouble() * 360;
            double speed = 1 + random.nextDouble() * 400;
            Target t = switch (i % 3) {
                case 0 -> new Drone("D", x, y, heading, speed);
                case 1 -> new FastDrone("F", x, y, heading, speed);
                default -> new Decoy("X", x, y, heading, speed);
            };
            ThreatResult r = service.calculateThreat(t, radar);
            assertTrue(r.score() >= 0 && r.score() <= 100, "score out of range: " + r.score());
            for (double c : new double[]{r.distanceScore(), r.speedScore(), r.directionScore(),
                    r.timeToImpactScore(), r.typeScore()}) {
                assertTrue(c >= 0 && c <= 1, "component out of range: " + c);
            }
        }
    }

    @Test
    void maximumThreatIsCappedAtHundred() {
        ThreatConfig extreme = new ThreatConfig(0.2, 0.2, 0.2, 0.2, 0.2, 1, 1, 31, 71, 1, 1, 1, 1);
        ThreatResult r = new ThreatAssessmentService(extreme).calculateThreat(droneTowardsCenter(1, 0, 500), radar);
        assertTrue(r.score() <= 100);
        assertEquals(ThreatLevel.HIGH, r.level());
    }

    @ParameterizedTest(name = "score {0} → {1}")
    @CsvSource({
            "0, LOW", "15, LOW", "30, LOW", "30.4, LOW",
            "30.5, MEDIUM", "31, MEDIUM", "50, MEDIUM", "70, MEDIUM", "70.4, MEDIUM",
            "70.5, HIGH", "71, HIGH", "85, HIGH", "100, HIGH"
    })
    void levelThresholdsMatchSpecification(double score, ThreatLevel expected) {
        assertEquals(expected, config.levelFor(score));
    }

    @Test
    void hysteresisKeepsPreviousLevelNearThreshold() {
        assertEquals(ThreatLevel.LOW, config.levelFor(31.5, ThreatLevel.LOW));
        assertEquals(ThreatLevel.MEDIUM, config.levelFor(34.5, ThreatLevel.LOW));
        assertEquals(ThreatLevel.HIGH, config.levelFor(69.0, ThreatLevel.HIGH));
        assertEquals(ThreatLevel.MEDIUM, config.levelFor(66.0, ThreatLevel.HIGH));
        assertEquals(ThreatLevel.HIGH, config.levelFor(90.0, ThreatLevel.LOW));
    }

    @Test
    void decoyScoresLowerThanDroneUnderSameConditions() {
        double x = 1500, y = 1200;
        double heading = Geometry.bearingOf(-x, -y);
        Drone drone = new Drone("D", x, y, heading, 50);
        Decoy decoy = new Decoy("X", x, y, heading, 50);
        double droneScore = service.calculateThreat(drone, radar).score();
        double decoyScore = service.calculateThreat(decoy, radar).score();
        assertTrue(decoyScore < droneScore, "decoy " + decoyScore + " should be < drone " + droneScore);
    }

    @Test
    void closerTargetIsMoreThreatening() {
        double near = service.calculateThreat(droneTowardsCenter(1000, 45, 50), radar).score();
        double far = service.calculateThreat(droneTowardsCenter(4500, 45, 50), radar).score();
        assertTrue(near > far);
    }

    @Test
    void targetMovingAwayHasZeroDirectionAndNoImpactTime() {
        Drone away = new Drone("D", 0, 2000, 0, 50); // kuzeyde, kuzeye uçuyor
        ThreatResult r = service.calculateThreat(away, radar);
        assertEquals(0.0, r.directionScore(), 1e-9);
        assertEquals(0.0, r.timeToImpactScore(), 1e-9);
        assertTrue(Double.isInfinite(r.timeToImpact()));
    }

    @Test
    void timeToImpactMatchesDistanceOverClosingSpeed() {
        ThreatResult r = service.calculateThreat(droneTowardsCenter(3000, 120, 50), radar);
        assertEquals(60.0, r.timeToImpact(), 1e-6);
        assertEquals(1.0, r.directionScore(), 1e-9);
    }

    @Test
    void scoreEqualsWeightedSumOfComponents() {
        ThreatResult r = service.calculateThreat(droneTowardsCenter(2500, 200, 60), radar);
        double expected = 100 * (config.distanceWeight() * r.distanceScore()
                + config.speedWeight() * r.speedScore()
                + config.directionWeight() * r.directionScore()
                + config.timeToImpactWeight() * r.timeToImpactScore()
                + config.typeWeight() * r.typeScore());
        assertEquals(expected, r.score(), 1e-9);
    }

    @Test
    void weightsMustSumToOne() {
        assertThrows(IllegalArgumentException.class,
                () -> new ThreatConfig(0.5, 0.5, 0.5, 0, 0, 150, 120, 31, 71, 0.6, 0.85, 0.8, 0.1));
    }

    @Test
    void parallelAssessmentMatchesSequential() throws InterruptedException {
        List<Target> targets = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            targets.add(droneTowardsCenter(500 + i * 40, i * 3.6, 30 + i));
        }
        ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            List<ThreatResult> parallel = service.assessAll(targets, radar, pool);
            assertEquals(targets.size(), parallel.size());
            for (int i = 0; i < targets.size(); i++) {
                assertEquals(service.calculateThreat(targets.get(i), radar), parallel.get(i));
            }
        } finally {
            pool.shutdownNow();
        }
    }
}
