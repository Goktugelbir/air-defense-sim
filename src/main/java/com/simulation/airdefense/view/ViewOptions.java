package com.simulation.airdefense.view;

/** Radar çiziminde açılıp kapatılabilen katmanlar. Yalnızca JavaFX thread'inde kullanılır. */
public final class ViewOptions {

    private boolean showTrails = true;
    private boolean showPrediction = true;
    private boolean showLabels = true;

    public boolean showTrails() {
        return showTrails;
    }

    public void setShowTrails(boolean showTrails) {
        this.showTrails = showTrails;
    }

    public boolean showPrediction() {
        return showPrediction;
    }

    public void setShowPrediction(boolean showPrediction) {
        this.showPrediction = showPrediction;
    }

    public boolean showLabels() {
        return showLabels;
    }

    public void setShowLabels(boolean showLabels) {
        this.showLabels = showLabels;
    }
}
