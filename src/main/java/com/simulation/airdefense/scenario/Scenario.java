package com.simulation.airdefense.scenario;

/**
 * Bir saldırı senaryosu (Strategy pattern). Her senaryo başlangıç koşullarını
 * {@link ScenarioContext} üzerinden kurar. Tüm rastgelelik bağlamın seed'li
 * {@code Random} nesnesinden gelir; böylece aynı seed ile senaryo tekrar edilebilir.
 */
public interface Scenario {

    /** Arayüzde gösterilen ad. */
    String name();

    /** Kısa açıklama. */
    String description();

    /** Tekrar edilebilirlik için kullanılan seed. */
    long seed();

    /** Hedefleri ve zamanlanmış olayları oluşturur. */
    void populate(ScenarioContext context);
}
