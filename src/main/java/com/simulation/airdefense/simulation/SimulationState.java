package com.simulation.airdefense.simulation;

/** Simülasyonun genel durumu. */
public enum SimulationState {
    READY("HAZIR"),
    RUNNING("ÇALIŞIYOR"),
    PAUSED("DURAKLATILDI"),
    FINISHED("BİTTİ");

    private final String label;

    SimulationState(String label) {
        this.label = label;
    }

    /** Arayüzde gösterilen Türkçe ad. */
    public String label() {
        return label;
    }
}
