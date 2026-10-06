package com.simulation.airdefense.service;

import com.simulation.airdefense.config.ThreatConfig;
import com.simulation.airdefense.model.Geometry;
import com.simulation.airdefense.model.Target;
import com.simulation.airdefense.model.ThreatResult;
import com.simulation.airdefense.radar.Radar;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

/**
 * Hedefler için tehdit skoru hesaplar.
 *
 * <pre>
 * Threat Score = 100 × Σ (ağırlık_i × bileşen_i)
 * </pre>
 * Bileşenler 0–1 aralığına normalize edilir:
 * <ul>
 *   <li><b>distance</b>: 1 − mesafe / menzil</li>
 *   <li><b>speed</b>: hız / referans maksimum hız</li>
 *   <li><b>direction</b>: hız vektörü ile merkeze olan vektörün kosinüs benzerliği (negatifse 0)</li>
 *   <li><b>timeToImpact</b>: 1 − varış süresi / ufuk süresi</li>
 *   <li><b>type</b>: türe göre sabit puan</li>
 * </ul>
 * Servis durumsuzdur (stateless) ve thread-safe'tir.
 */
public class ThreatAssessmentService {

    private final ThreatConfig config;

    public ThreatAssessmentService(ThreatConfig config) {
        this.config = Objects.requireNonNull(config, "config");
    }

    public ThreatConfig getConfig() {
        return config;
    }

    /** Tek bir hedefin tehdit değerlendirmesi. Hedefi değiştirmez. */
    public ThreatResult calculateThreat(Target target, Radar radar) {
        double dx = radar.getCenterX() - target.getX();
        double dy = radar.getCenterY() - target.getY();
        double distance = Math.hypot(dx, dy);
        double speed = target.getSpeed();

        double distanceScore = Geometry.clamp01(1.0 - distance / radar.getRange());
        double speedScore = Geometry.clamp01(speed / config.referenceMaxSpeed());

        double cosine;
        if (distance < 1e-9) {
            cosine = 1.0;
        } else if (speed < 1e-9) {
            cosine = 0.0;
        } else {
            cosine = (target.getVelocityX() * dx + target.getVelocityY() * dy) / (speed * distance);
        }
        double directionScore = Geometry.clamp01(cosine);

        double closingSpeed = speed * cosine;
        double timeToImpact;
        if (distance < 1e-9) {
            timeToImpact = 0.0;
        } else if (closingSpeed > 1e-9) {
            timeToImpact = distance / closingSpeed;
        } else {
            timeToImpact = Double.POSITIVE_INFINITY;
        }
        double timeToImpactScore = Double.isInfinite(timeToImpact)
                ? 0.0
                : Geometry.clamp01(1.0 - timeToImpact / config.timeToImpactHorizon());

        double typeScore = Geometry.clamp01(config.typeScore(target.getType()));

        double weighted = config.distanceWeight() * distanceScore
                + config.speedWeight() * speedScore
                + config.directionWeight() * directionScore
                + config.timeToImpactWeight() * timeToImpactScore
                + config.typeWeight() * typeScore;
        double score = Math.max(0.0, Math.min(100.0, 100.0 * weighted));

        return new ThreatResult(score, config.levelFor(score),
                distanceScore, speedScore, directionScore, timeToImpactScore, typeScore, timeToImpact);
    }

    /**
     * Hedefleri verilen thread havuzunda paralel değerlendirir ({@code invokeAll}).
     * Çağıran thread tüm sonuçlar gelene kadar bekler; bu sürede hedefler değiştirilmemelidir.
     *
     * @return girdiyle aynı sırada sonuç listesi
     */
    public List<ThreatResult> assessAll(List<Target> targets, Radar radar, ExecutorService executor)
            throws InterruptedException {
        if (targets.isEmpty()) {
            return List.of();
        }
        List<Callable<ThreatResult>> tasks = new ArrayList<>(targets.size());
        for (Target target : targets) {
            tasks.add(() -> calculateThreat(target, radar));
        }
        List<Future<ThreatResult>> futures = executor.invokeAll(tasks);
        List<ThreatResult> results = new ArrayList<>(futures.size());
        for (Future<ThreatResult> future : futures) {
            try {
                results.add(future.get());
            } catch (ExecutionException e) {
                throw new IllegalStateException("Threat calculation failed", e.getCause());
            }
        }
        return results;
    }
}
