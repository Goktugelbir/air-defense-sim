package com.simulation.airdefense.view;

/**
 * Dünya koordinatlarını (metre, +y kuzey) ekran piksellerine (+y aşağı) dönüştürür ve tersini yapar.
 * Radar merkezi her zaman tuvalin ortasına yerleşir.
 */
public final class CoordinateMapper {

    private double centerX;
    private double centerY;
    private double scale = 1.0;

    /**
     * Ölçeği tuval boyutuna göre günceller.
     *
     * @param width       tuval genişliği (px)
     * @param height      tuval yüksekliği (px)
     * @param worldRadius ekranın kısa kenarının yarısına sığacak dünya yarıçapı (m)
     * @param padding     kenar boşluğu (px)
     */
    public void update(double width, double height, double worldRadius, double padding) {
        centerX = width / 2.0;
        centerY = height / 2.0;
        double available = Math.max(10.0, Math.min(width, height) / 2.0 - padding);
        scale = available / Math.max(1.0, worldRadius);
    }

    public double toScreenX(double worldX) {
        return centerX + worldX * scale;
    }

    public double toScreenY(double worldY) {
        return centerY - worldY * scale;
    }

    public double toWorldX(double screenX) {
        return (screenX - centerX) / scale;
    }

    public double toWorldY(double screenY) {
        return (centerY - screenY) / scale;
    }

    /** Metre cinsinden uzunluğu piksele çevirir. */
    public double toPixels(double meters) {
        return meters * scale;
    }

    public double getCenterX() {
        return centerX;
    }

    public double getCenterY() {
        return centerY;
    }

    /** Piksel başına metre değil, metre başına piksel. */
    public double getScale() {
        return scale;
    }
}
