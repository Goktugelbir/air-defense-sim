package com.simulation.airdefense.config;

import com.simulation.airdefense.model.Target;

/**
 * Genel simülasyon parametreleri.
 *
 * @param tickRateHz          simülasyon döngüsünün frekansı
 * @param defaultRadarRange   varsayılan radar menzili (m)
 * @param minRadarRange       arayüzde seçilebilecek minimum menzil (m)
 * @param maxRadarRange       arayüzde seçilebilecek maksimum menzil (m)
 * @param ringSpacing         menzil halkaları arası mesafe (m)
 * @param sweepPeriod         radar taramasının tam tur süresi (s)
 * @param threatPoolSize      paralel tehdit hesaplaması için thread sayısı
 * @param trailLength         hedef başına tutulan iz noktası sayısı
 * @param areaLimitFactor     hedefin alandan çıkmış sayılması için menzil çarpanı
 * @param priorityListSize    sağ panelde gösterilen öncelik listesi uzunluğu
 * @param maxLogLines         log panelinde tutulacak maksimum satır
 * @param defaultSeed         senaryolar için varsayılan seed
 */
public record SimulationConfig(int tickRateHz,
                               double defaultRadarRange,
                               double minRadarRange,
                               double maxRadarRange,
                               double ringSpacing,
                               double sweepPeriod,
                               int threatPoolSize,
                               int trailLength,
                               double areaLimitFactor,
                               int priorityListSize,
                               int maxLogLines,
                               long defaultSeed) {

    public SimulationConfig {
        if (tickRateHz <= 0 || threatPoolSize <= 0 || trailLength <= 0) {
            throw new IllegalArgumentException("Rates and sizes must be positive");
        }
        if (minRadarRange <= 0 || maxRadarRange < minRadarRange
                || defaultRadarRange < minRadarRange || defaultRadarRange > maxRadarRange) {
            throw new IllegalArgumentException("Invalid radar range configuration");
        }
    }

    public static SimulationConfig defaults() {
        int cores = Runtime.getRuntime().availableProcessors();
        return new SimulationConfig(
                60,
                5000.0, 1000.0, 10000.0, 1000.0,
                4.0,
                Math.max(2, Math.min(8, cores - 1)),
                Target.DEFAULT_TRAIL_LENGTH,
                1.6,
                5,
                500,
                42L);
    }

    /** Sabit tick süresi (s). */
    public double tickSeconds() {
        return 1.0 / tickRateHz;
    }
}
