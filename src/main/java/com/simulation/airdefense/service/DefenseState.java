package com.simulation.airdefense.service;

/**
 * Soyut savunma sisteminin durumları (State pattern).
 *
 * <pre>
 * IDLE → SELECTING → TRACKING → ENGAGING → COOLDOWN → IDLE / TRACKING
 * </pre>
 */
public enum DefenseState {
    IDLE("BEKLEMEDE"),
    SELECTING("HEDEF SEÇİYOR"),
    TRACKING("TAKİP"),
    ENGAGING("ANGAJMAN"),
    COOLDOWN("SOĞUMA");

    private final String label;

    DefenseState(String label) {
        this.label = label;
    }

    /** Arayüzde gösterilen Türkçe ad. */
    public String label() {
        return label;
    }
}
