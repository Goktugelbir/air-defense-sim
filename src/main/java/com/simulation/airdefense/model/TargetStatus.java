package com.simulation.airdefense.model;

/**
 * Hedefin yaşam döngüsü durumu.
 *
 * <pre>
 * UNDETECTED → DETECTED → TRACKING → ENGAGED → NEUTRALIZED
 *                  ↘ LOST (menzil dışına çıktı, tekrar algılanabilir)
 *                  ↘ ESCAPED (merkeze sızdı, son durum)
 * </pre>
 */
public enum TargetStatus {
    UNDETECTED("ALGILANMADI"),
    DETECTED("ALGILANDI"),
    TRACKING("TAKİPTE"),
    ENGAGED("ANGAJE"),
    NEUTRALIZED("ETKİSİZ"),
    LOST("KAYIP"),
    ESCAPED("SIZDI");

    private final String label;

    TargetStatus(String label) {
        this.label = label;
    }

    /** Arayüzde gösterilen Türkçe ad. */
    public String label() {
        return label;
    }

    /** Hedefin artık simülasyona katılmadığı son durumlar. */
    public boolean isTerminal() {
        return this == NEUTRALIZED || this == ESCAPED;
    }
}
