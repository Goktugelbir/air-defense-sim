package com.simulation.airdefense;

import com.simulation.airdefense.model.Drone;
import com.simulation.airdefense.model.Target;
import com.simulation.airdefense.model.ThreatLevel;
import com.simulation.airdefense.model.ThreatResult;
import com.simulation.airdefense.service.PriorityService;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PriorityServiceTest {

    private final PriorityService service = new PriorityService();

    /** Algılanmış, verilen skor ve varış süresine sahip hedef. */
    private static Target target(String id, double x, double y, double score, double timeToImpact) {
        Drone d = new Drone(id, x, y, 180, 50);
        d.markDetected();
        ThreatLevel level = score >= 71 ? ThreatLevel.HIGH : score >= 31 ? ThreatLevel.MEDIUM : ThreatLevel.LOW;
        d.applyThreat(new ThreatResult(score, level, 0, 0, 0, 0, 0, timeToImpact));
        return d;
    }

    private static List<String> ids(List<Target> targets) {
        return targets.stream().map(Target::getId).toList();
    }

    @Test
    void sortsByThreatScoreDescending() {
        List<Target> input = List.of(
                target("A", 0, 3000, 40, 60),
                target("B", 0, 3000, 94, 60),
                target("C", 0, 3000, 79, 60),
                target("D", 0, 3000, 87, 60));
        assertEquals(List.of("B", "D", "C", "A"), ids(service.prioritize(input)));
    }

    @Test
    void tieOnScoreIsBrokenByTimeToImpactAscending() {
        List<Target> input = List.of(
                target("SLOW", 0, 1000, 80, 40),
                target("FAST", 0, 1000, 80, 12),
                target("MID", 0, 1000, 80, 25));
        assertEquals(List.of("FAST", "MID", "SLOW"), ids(service.prioritize(input)));
    }

    @Test
    void tieOnScoreAndTimeIsBrokenByDistanceAscending() {
        List<Target> input = List.of(
                target("FAR", 0, 4000, 65, 30),
                target("NEAR", 0, 900, 65, 30),
                target("MID", 2000, 0, 65, 30));
        assertEquals(List.of("NEAR", "MID", "FAR"), ids(service.prioritize(input)));
    }

    @Test
    void infiniteTimeToImpactComesLastAmongEqualScores() {
        List<Target> input = List.of(
                target("AWAY", 0, 1000, 50, Double.POSITIVE_INFINITY),
                target("CLOSING", 0, 3000, 50, 90));
        assertEquals(List.of("CLOSING", "AWAY"), ids(service.prioritize(input)));
    }

    @Test
    void undetectedAndInactiveTargetsAreExcluded() {
        Target detected = target("OK", 0, 1000, 50, 20);
        Target undetected = new Drone("HIDDEN", 0, 1000, 180, 50);
        Target neutralized = target("GONE", 0, 1000, 99, 5);
        neutralized.neutralize();
        Target escaped = target("ESC", 0, 50, 99, 1);
        escaped.escape();
        List<Target> result = service.prioritize(List.of(detected, undetected, neutralized, escaped));
        assertEquals(List.of("OK"), ids(result));
    }

    @Test
    void lostTargetsAreExcluded() {
        Target lost = target("LOST", 0, 6000, 90, 50);
        lost.markLost();
        assertTrue(service.prioritize(List.of(lost)).isEmpty());
    }

    @Test
    void resultIsUnmodifiable() {
        List<Target> result = service.prioritize(new ArrayList<>(List.of(target("A", 0, 1000, 50, 20))));
        assertThrows(UnsupportedOperationException.class, () -> result.add(target("B", 0, 1000, 10, 20)));
    }

    @Test
    void emptyInputGivesEmptyList() {
        assertTrue(service.prioritize(List.of()).isEmpty());
    }
}
