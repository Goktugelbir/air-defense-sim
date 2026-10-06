package com.simulation.airdefense.event;

/** Simülasyon olaylarını dinleyen gözlemci (Observer pattern). */
@FunctionalInterface
public interface EventListener {

    /**
     * Olay yayınlandığında çağrılır. Yayınlayan thread'de (genellikle simülasyon thread'i)
     * çalışır; bu yüzden uygulamalar hızlı dönmeli ve ağır işleri başka thread'e devretmelidir.
     */
    void onEvent(SimulationEvent event);
}
