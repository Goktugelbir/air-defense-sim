package com.simulation.airdefense.swarm;

import com.simulation.airdefense.model.Target;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Basitleştirilmiş Boids algoritmasıyla hareket eden İHA sürüsü.
 * <p>
 * Her üye için dört kuvvet birleştirilir:
 * <ul>
 *   <li><b>Separation</b>: çok yakın komşulardan uzaklaş</li>
 *   <li><b>Alignment</b>: komşuların ortalama yönüne hizalan</li>
 *   <li><b>Cohesion</b>: komşuların ağırlık merkezine yönel</li>
 *   <li><b>Goal seeking</b>: hedef bölgeye (radar merkezi veya manevra ara noktası) yönel</li>
 * </ul>
 * Sürü belirli aralıklarla toplu manevra yapar: ikiye bölünür, iki grup farklı yanlardan
 * ara noktalara gider, ardından merkeze yeniden yönelir.
 * <p>
 * Amaç algoritmik davranışı göstermektir; gerçek bir askeri algoritma değildir.
 * Simülasyon thread'inde çalışır; thread-safe değildir.
 */
public class DroneSwarm {

    /** Sürünün toplu davranış modu. */
    public enum Mode {
        CONVERGE,
        SPLIT
    }

    private final String id;
    private final SwarmConfig config;
    private final List<Target> members;
    private final double goalX;
    private final double goalY;
    private final Map<Target, Integer> groups = new IdentityHashMap<>();
    private final double[] waypointX = new double[2];
    private final double[] waypointY = new double[2];

    private Mode mode = Mode.CONVERGE;
    private double maneuverTimer;
    private double modeTime;

    /**
     * @param id      sürü kimliği
     * @param members sürü üyeleri (her biri bu sürüye atanır)
     * @param config  Boids parametreleri
     * @param goalX   hedef bölge x (m)
     * @param goalY   hedef bölge y (m)
     */
    public DroneSwarm(String id, List<? extends Target> members, SwarmConfig config, double goalX, double goalY) {
        this.id = Objects.requireNonNull(id, "id");
        this.config = Objects.requireNonNull(config, "config");
        this.members = new ArrayList<>(members);
        this.goalX = goalX;
        this.goalY = goalY;
        this.maneuverTimer = config.maneuverInterval();
        for (Target member : this.members) {
            member.assignToSwarm(id);
        }
    }

    /**
     * Tüm üyelerin hız vektörlerini Boids kurallarına göre günceller. Konumları değiştirmez;
     * entegrasyonu her hedefin kendi {@code update} metodu yapar.
     *
     * @return bu adımda yeni bir toplu manevra başladıysa {@code true}
     */
    public boolean update(double dt) {
        members.removeIf(t -> !t.isActive());
        groups.keySet().removeIf(t -> !t.isActive());
        if (members.isEmpty() || dt <= 0) {
            return false;
        }

        boolean maneuverStarted = updateMode(dt);

        int n = members.size();
        double neighborSq = config.neighborRadius() * config.neighborRadius();
        double separationRadius = config.separationRadius();
        double[] newVx = new double[n];
        double[] newVy = new double[n];

        for (int i = 0; i < n; i++) {
            Target self = members.get(i);
            int group = groupOf(self);
            double px = self.getX();
            double py = self.getY();

            double sepX = 0, sepY = 0;
            double aliX = 0, aliY = 0;
            double cohX = 0, cohY = 0;
            int neighbors = 0;

            for (int j = 0; j < n; j++) {
                if (i == j) {
                    continue;
                }
                Target other = members.get(j);
                if (groupOf(other) != group) {
                    continue;
                }
                double dx = px - other.getX();
                double dy = py - other.getY();
                double distSq = dx * dx + dy * dy;
                if (distSq > neighborSq) {
                    continue;
                }
                neighbors++;
                aliX += other.getVelocityX();
                aliY += other.getVelocityY();
                cohX += other.getX();
                cohY += other.getY();
                double dist = Math.sqrt(distSq);
                if (dist > 1e-6 && dist < separationRadius) {
                    double strength = (separationRadius - dist) / separationRadius;
                    sepX += dx / dist * strength;
                    sepY += dy / dist * strength;
                }
            }

            double steerX = 0, steerY = 0;
            if (neighbors > 0) {
                double[] sep = normalize(sepX, sepY);
                double[] ali = normalize(aliX / neighbors, aliY / neighbors);
                double[] coh = normalize(cohX / neighbors - px, cohY / neighbors - py);
                steerX += config.separationWeight() * sep[0]
                        + config.alignmentWeight() * ali[0]
                        + config.cohesionWeight() * coh[0];
                steerY += config.separationWeight() * sep[1]
                        + config.alignmentWeight() * ali[1]
                        + config.cohesionWeight() * coh[1];
            }

            double gx = goalXFor(group);
            double gy = goalYFor(group);
            double[] goal = normalize(gx - px, gy - py);
            steerX += config.goalWeight() * goal[0];
            steerY += config.goalWeight() * goal[1];

            double[] steer = normalize(steerX, steerY);
            double vx = self.getVelocityX() + steer[0] * config.maxSteering() * dt;
            double vy = self.getVelocityY() + steer[1] * config.maxSteering() * dt;
            double[] dir = normalize(vx, vy);
            double speed = self.getCruiseSpeed();
            newVx[i] = dir[0] * speed;
            newVy[i] = dir[1] * speed;
        }

        for (int i = 0; i < n; i++) {
            members.get(i).setVelocity(newVx[i], newVy[i]);
        }
        return maneuverStarted;
    }

    private boolean updateMode(double dt) {
        modeTime += dt;
        if (mode == Mode.SPLIT) {
            if (modeTime >= config.maneuverDuration() || allGroupsReachedWaypoint()) {
                mode = Mode.CONVERGE;
                modeTime = 0;
                groups.clear();
            }
            return false;
        }
        maneuverTimer -= dt;
        if (maneuverTimer > 0) {
            return false;
        }
        maneuverTimer = config.maneuverInterval();
        double[] centroid = centroid();
        double cx = centroid[0];
        double cy = centroid[1];
        double distanceToGoal = Math.hypot(goalX - cx, goalY - cy);
        if (members.size() < 4 || distanceToGoal < config.minManeuverDistance()) {
            return false;
        }
        startSplit(cx, cy, distanceToGoal);
        return true;
    }

    private void startSplit(double cx, double cy, double distanceToGoal) {
        double ux = (goalX - cx) / distanceToGoal;
        double uy = (goalY - cy) / distanceToGoal;
        double perpX = uy;
        double perpY = -ux;
        double advance = Math.min(config.flankAdvance(), distanceToGoal * 0.5);
        for (int g = 0; g < 2; g++) {
            double sign = g == 0 ? 1 : -1;
            waypointX[g] = cx + ux * advance + perpX * sign * config.flankOffset();
            waypointY[g] = cy + uy * advance + perpY * sign * config.flankOffset();
        }
        // Üyeleri merkez çizgisinin hangi tarafında olduklarına göre gruplara ayır;
        // grupların dengeli olması için sıralayıp ortadan böl.
        List<Target> sorted = new ArrayList<>(members);
        sorted.sort((a, b) -> Double.compare(
                (b.getX() - cx) * perpX + (b.getY() - cy) * perpY,
                (a.getX() - cx) * perpX + (a.getY() - cy) * perpY));
        groups.clear();
        int half = sorted.size() / 2;
        for (int i = 0; i < sorted.size(); i++) {
            groups.put(sorted.get(i), i < half ? 0 : 1);
        }
        mode = Mode.SPLIT;
        modeTime = 0;
    }

    private boolean allGroupsReachedWaypoint() {
        double radiusSq = config.waypointRadius() * config.waypointRadius();
        for (int g = 0; g < 2; g++) {
            double sx = 0, sy = 0;
            int count = 0;
            for (Map.Entry<Target, Integer> e : groups.entrySet()) {
                if (e.getValue() == g) {
                    sx += e.getKey().getX();
                    sy += e.getKey().getY();
                    count++;
                }
            }
            if (count == 0) {
                continue;
            }
            double dx = sx / count - waypointX[g];
            double dy = sy / count - waypointY[g];
            if (dx * dx + dy * dy > radiusSq) {
                return false;
            }
        }
        return true;
    }

    private int groupOf(Target target) {
        if (mode != Mode.SPLIT) {
            return 0;
        }
        Integer g = groups.get(target);
        return g == null ? 0 : g;
    }

    private double goalXFor(int group) {
        return mode == Mode.SPLIT ? waypointX[group] : goalX;
    }

    private double goalYFor(int group) {
        return mode == Mode.SPLIT ? waypointY[group] : goalY;
    }

    /** Aktif üyelerin ağırlık merkezi. */
    public double[] centroid() {
        double sx = 0, sy = 0;
        for (Target t : members) {
            sx += t.getX();
            sy += t.getY();
        }
        int n = Math.max(1, members.size());
        return new double[]{sx / n, sy / n};
    }

    private static double[] normalize(double x, double y) {
        double len = Math.hypot(x, y);
        if (len < 1e-9) {
            return new double[]{0, 0};
        }
        return new double[]{x / len, y / len};
    }

    public String getId() {
        return id;
    }

    public Mode getMode() {
        return mode;
    }

    public int size() {
        return members.size();
    }

    public boolean isEmpty() {
        return members.isEmpty();
    }

    /** Üyelerin değiştirilemez görünümü. */
    public List<Target> getMembers() {
        return List.copyOf(members);
    }

    public SwarmConfig getConfig() {
        return config;
    }
}
