package com.simulation.airdefense.model;

/** Tehdit skorundan türetilen seviye. Eşikler {@code ThreatConfig} içinde tutulur. */
public enum ThreatLevel {
    LOW("DÜŞÜK"),
    MEDIUM("ORTA"),
    HIGH("YÜKSEK");

    private final String label;

    ThreatLevel(String label) {
        this.label = label;
    }

    /** Arayüzde gösterilen Türkçe ad. */
    public String label() {
        return label;
    }
}
