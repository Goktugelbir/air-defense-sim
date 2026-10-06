package com.simulation.airdefense.radar;

import com.simulation.airdefense.model.Geometry;
import com.simulation.airdefense.model.Target;

/**
 * Dönen tarama çizgisine (sweep) sahip radar.
 * <p>
 * Algılama kuralı: bir hedef ancak menzil içindeyse VE son tick'te sweep çizgisi
 * hedefin kerterizinden geçtiyse algılanır. Böylece görsel tarama ile algılama mantığı tutarlıdır.
 * <p>
 * Simülasyon thread'inde güncellenir; thread-safe değildir.
 */
public class Radar {

    private final double centerX;
    private final double centerY;
    private final double sweepPeriod;
    private double range;
    private boolean active = true;
    private double sweepAngle;
    private double previousSweepAngle;
    private double sweptSpan;

    /**
     * @param centerX     merkez x (m)
     * @param centerY     merkez y (m)
     * @param range       menzil (m)
     * @param sweepPeriod tam tur süresi (s)
     */
    public Radar(double centerX, double centerY, double range, double sweepPeriod) {
        if (range <= 0 || sweepPeriod <= 0) {
            throw new IllegalArgumentException("range and sweepPeriod must be positive");
        }
        this.centerX = centerX;
        this.centerY = centerY;
        this.range = range;
        this.sweepPeriod = sweepPeriod;
    }

    /** Sweep açısını {@code dt} kadar ilerletir. Radar kapalıysa tarama durur. */
    public void update(double dt) {
        previousSweepAngle = sweepAngle;
        if (!active || dt <= 0) {
            sweptSpan = 0;
            return;
        }
        sweptSpan = 360.0 * dt / sweepPeriod;
        sweepAngle = Geometry.normalizeDegrees(sweepAngle + sweptSpan);
    }

    /** Hedef aktif radarın menzili içinde mi? */
    public boolean isInRange(Target target) {
        return active && target.distanceTo(centerX, centerY) <= range;
    }

    /** Verilen kerteriz son tick'te taranan yay içinde mi? */
    public boolean wasSwept(double bearingDeg) {
        if (sweptSpan <= 0) {
            return false;
        }
        if (sweptSpan >= 360.0) {
            return true;
        }
        return Geometry.normalizeDegrees(bearingDeg - previousSweepAngle) <= sweptSpan;
    }

    /** Hedef bu tick'te algılanabilir mi (menzilde ve sweep üzerinden geçti)? */
    public boolean canDetect(Target target) {
        return isInRange(target) && wasSwept(bearingTo(target));
    }

    /** Merkezden hedefe kerteriz. */
    public double bearingTo(Target target) {
        return Geometry.bearingOf(target.getX() - centerX, target.getY() - centerY);
    }

    public double distanceTo(Target target) {
        return target.distanceTo(centerX, centerY);
    }

    /** Sweep'i başlangıç açısına döndürür. */
    public void resetSweep() {
        sweepAngle = 0;
        previousSweepAngle = 0;
        sweptSpan = 0;
    }

    /** Test ve senaryolar için sweep açısını doğrudan ayarlar. */
    public void setSweepAngle(double degrees) {
        this.sweepAngle = Geometry.normalizeDegrees(degrees);
        this.previousSweepAngle = this.sweepAngle;
        this.sweptSpan = 0;
    }

    public double getCenterX() {
        return centerX;
    }

    public double getCenterY() {
        return centerY;
    }

    public double getRange() {
        return range;
    }

    public void setRange(double range) {
        if (range <= 0) {
            throw new IllegalArgumentException("range must be positive");
        }
        this.range = range;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public double getSweepAngle() {
        return sweepAngle;
    }

    public double getSweepPeriod() {
        return sweepPeriod;
    }
}
