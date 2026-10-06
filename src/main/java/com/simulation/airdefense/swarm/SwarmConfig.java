package com.simulation.airdefense.swarm;

/**
 * Boids sürü algoritmasının parametreleri.
 *
 * @param neighborRadius      komşu sayılma yarıçapı (m)
 * @param separationRadius    ayrışma kuvvetinin devreye girdiği yarıçap (m)
 * @param separationWeight    Separation ağırlığı
 * @param alignmentWeight     Alignment ağırlığı
 * @param cohesionWeight      Cohesion ağırlığı
 * @param goalWeight          Goal seeking ağırlığı
 * @param maxSteering         maksimum yönlendirme ivmesi (m/s²)
 * @param maneuverInterval    toplu manevralar arası süre (s)
 * @param maneuverDuration    bir manevranın maksimum süresi (s)
 * @param flankOffset         ikiye bölünmede her grubun yana açılma mesafesi (m)
 * @param flankAdvance        ikiye bölünmede ara noktanın merkeze doğru ilerleme mesafesi (m)
 * @param minManeuverDistance sürü merkeze bu mesafeden yakınsa manevra yapılmaz (m)
 * @param waypointRadius      ara noktaya ulaşıldı sayılma yarıçapı (m)
 */
public record SwarmConfig(double neighborRadius,
                          double separationRadius,
                          double separationWeight,
                          double alignmentWeight,
                          double cohesionWeight,
                          double goalWeight,
                          double maxSteering,
                          double maneuverInterval,
                          double maneuverDuration,
                          double flankOffset,
                          double flankAdvance,
                          double minManeuverDistance,
                          double waypointRadius) {

    public SwarmConfig {
        if (neighborRadius <= 0 || separationRadius <= 0 || maxSteering <= 0) {
            throw new IllegalArgumentException("Radii and steering must be positive");
        }
        if (separationWeight < 0 || alignmentWeight < 0 || cohesionWeight < 0 || goalWeight < 0) {
            throw new IllegalArgumentException("Weights must be non-negative");
        }
    }

    public static SwarmConfig defaults() {
        return new SwarmConfig(
                450.0, 140.0,
                1.8, 0.8, 0.7, 1.0,
                30.0,
                16.0, 12.0,
                1800.0, 1000.0,
                2600.0, 300.0);
    }
}
