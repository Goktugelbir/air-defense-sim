package com.simulation.airdefense;

import com.simulation.airdefense.model.Drone;
import com.simulation.airdefense.model.Target;
import com.simulation.airdefense.radar.Radar;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RadarTest {

    private static final double RANGE = 5000;
    private static final double SWEEP_PERIOD = 4.0;

    private Radar radar;

    @BeforeEach
    void setUp() {
        radar = new Radar(0, 0, RANGE, SWEEP_PERIOD);
    }

    private static Target droneAt(double x, double y) {
        return new Drone("T", x, y, 180, 50);
    }

    @Test
    void targetInsideRangeIsInRange() {
        assertTrue(radar.isInRange(droneAt(1000, 2000)));
    }

    @Test
    void targetExactlyOnRangeBoundaryIsInRange() {
        assertTrue(radar.isInRange(droneAt(0, RANGE)));
    }

    @Test
    void targetOutsideRangeIsNotInRange() {
        assertFalse(radar.isInRange(droneAt(4000, 3500)));
    }

    @Test
    void inactiveRadarDetectsNothing() {
        radar.setActive(false);
        assertFalse(radar.isInRange(droneAt(100, 100)));
    }

    @Test
    void changingRangeAffectsDetection() {
        Target t = droneAt(0, 3000);
        radar.setRange(2000);
        assertFalse(radar.isInRange(t));
        radar.setRange(4000);
        assertTrue(radar.isInRange(t));
    }

    @Test
    void sweepAdvancesByFullCircleOverPeriod() {
        radar.update(1.0);
        assertEquals(90.0, radar.getSweepAngle(), 1e-9);
        radar.update(3.0);
        assertEquals(0.0, radar.getSweepAngle(), 1e-9);
    }

    @Test
    void targetIsDetectedOnlyWhenSweepPassesIt() {
        // Doğu yönünde (kerteriz 90°) bir hedef
        Target east = droneAt(1000, 0);
        radar.update(0.5); // 0° → 45°
        assertFalse(radar.canDetect(east), "Sweep henüz 90°'ye ulaşmadı");
        radar.update(0.6); // 45° → 99°
        assertTrue(radar.canDetect(east), "Sweep 90°'yi geçti");
        radar.update(0.5); // 99° → 144°
        assertFalse(radar.canDetect(east), "Sweep hedefi geride bıraktı");
    }

    @Test
    void sweepWrapsAroundNorth() {
        Target northWest = droneAt(-100, 1000); // kerteriz ≈ 354°
        radar.setSweepAngle(350);
        radar.update(0.1); // 350° → 359°
        assertTrue(radar.canDetect(northWest));
        Target northEast = droneAt(100, 1000); // kerteriz ≈ 6°
        radar.update(0.1); // 359° → 8°
        assertTrue(radar.canDetect(northEast));
    }

    @Test
    void targetOutOfRangeIsNotDetectedEvenWhenSwept() {
        Target farEast = droneAt(6000, 0);
        radar.update(1.1);
        assertFalse(radar.canDetect(farEast));
    }

    @Test
    void bearingFollowsCompassConvention() {
        assertEquals(0.0, radar.bearingTo(droneAt(0, 100)), 1e-9);
        assertEquals(90.0, radar.bearingTo(droneAt(100, 0)), 1e-9);
        assertEquals(180.0, radar.bearingTo(droneAt(0, -100)), 1e-9);
        assertEquals(270.0, radar.bearingTo(droneAt(-100, 0)), 1e-9);
    }
}
