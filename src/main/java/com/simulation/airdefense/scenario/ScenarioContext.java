package com.simulation.airdefense.scenario;

import com.simulation.airdefense.model.Target;
import com.simulation.airdefense.model.TargetType;
import com.simulation.airdefense.swarm.DroneSwarm;
import com.simulation.airdefense.swarm.SwarmConfig;

import java.util.List;
import java.util.Random;

/** Senaryoların simülasyona hedef eklemek için kullandığı arayüz. */
public interface ScenarioContext {

    /** Senaryonun seed'iyle oluşturulmuş rastgele kaynak. */
    Random random();

    /** Güncel radar menzili (m). */
    double radarRange();

    /**
     * Merkezden {@code distance} uzaklıkta, {@code bearingDeg} kerterizinde bir hedef oluşturur.
     * Hedef merkeze doğru, {@code headingOffsetDeg} kadar sapmayla ilerler.
     *
     * @param baseSpeed temel hız (m/s); FastDrone kendi çarpanını uygular
     */
    Target spawn(TargetType type, double distance, double bearingDeg, double headingOffsetDeg, double baseSpeed);

    /**
     * Belirtilen merkez kerteriz/mesafe etrafında bir sürü oluşturur.
     *
     * @param composition üyelerin türleri
     * @param spread      üyelerin dağıldığı yarıçap (m)
     */
    DroneSwarm spawnSwarm(List<TargetType> composition, double distance, double bearingDeg,
                          double baseSpeed, double spread, SwarmConfig config);

    /** {@code delaySeconds} simülasyon saniyesi sonra çalışacak bir eylem planlar. */
    void schedule(double delaySeconds, Runnable action);
}
