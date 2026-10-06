package com.simulation.airdefense.service;

import com.simulation.airdefense.model.Target;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;

/**
 * Algılanmış hedefleri öncelik sırasına dizer.
 * <ol>
 *   <li>Tehdit skoru (azalan)</li>
 *   <li>Eşitlikte merkeze varış süresi (artan)</li>
 *   <li>Eşitlikte mesafe (artan)</li>
 *   <li>Son olarak ID (deterministik sıra için)</li>
 * </ol>
 */
public class PriorityService {

    /** Öncelik sıralaması için comparator zinciri. */
    public static final Comparator<Target> PRIORITY_ORDER =
            Comparator.comparingDouble(Target::getThreatScore).reversed()
                    .thenComparingDouble(Target::getTimeToImpact)
                    .thenComparingDouble(PriorityService::distanceToCenter)
                    .thenComparing(Target::getId);

    private static double distanceToCenter(Target target) {
        return Math.hypot(target.getX(), target.getY());
    }

    /**
     * Aktif ve algılanmış hedefleri öncelik sırasına göre döndürür.
     *
     * @return değiştirilemez, sıralı liste
     */
    public List<Target> prioritize(Collection<? extends Target> targets) {
        return targets.stream()
                .filter(Target::isActive)
                .filter(Target::isDetected)
                .filter(t -> !t.getStatus().isTerminal())
                .map(t -> (Target) t)
                .sorted(PRIORITY_ORDER)
                .toList();
    }
}
