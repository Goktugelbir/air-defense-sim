package com.simulation.airdefense.model;

/**
 * Koordinat sistemi yardımcıları.
 * <p>
 * Dünya koordinatları metre cinsindendir: +x doğu, +y kuzey. Yön (heading/bearing)
 * derece cinsindendir; 0° kuzey, saat yönünde artar.
 */
public final class Geometry {

    private Geometry() {
    }

    /** Açıyı [0, 360) aralığına normalize eder. */
    public static double normalizeDegrees(double degrees) {
        double d = degrees % 360.0;
        return d < 0 ? d + 360.0 : d;
    }

    /** İki açı arasındaki işaretli en kısa fark (-180, 180]. */
    public static double angleDifference(double fromDeg, double toDeg) {
        double diff = normalizeDegrees(toDeg - fromDeg);
        return diff > 180.0 ? diff - 360.0 : diff;
    }

    /** Orijinden (x, y) noktasına olan kerteriz (0° kuzey, saat yönü). */
    public static double bearingOf(double x, double y) {
        return normalizeDegrees(Math.toDegrees(Math.atan2(x, y)));
    }

    /** Verilen yön ve hızın x (doğu) bileşeni. */
    public static double velocityX(double headingDeg, double speed) {
        return speed * Math.sin(Math.toRadians(headingDeg));
    }

    /** Verilen yön ve hızın y (kuzey) bileşeni. */
    public static double velocityY(double headingDeg, double speed) {
        return speed * Math.cos(Math.toRadians(headingDeg));
    }

    public static double clamp01(double value) {
        return value < 0 ? 0 : (value > 1 ? 1 : value);
    }

    /** m/s → km/h dönüşümü. */
    public static double toKmh(double metersPerSecond) {
        return metersPerSecond * 3.6;
    }

    /** km/h → m/s dönüşümü. */
    public static double toMetersPerSecond(double kmh) {
        return kmh / 3.6;
    }
}
