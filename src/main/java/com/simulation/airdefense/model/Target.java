package com.simulation.airdefense.model;

import java.util.Objects;

/**
 * Radar üzerinde hareket eden tüm hedeflerin soyut temel sınıfı.
 * <p>
 * Hareket delta-time tabanlıdır: alt sınıflar {@link #update(double)} içinde kendi
 * yönlendirme davranışlarını uygular ve ardından {@link #integrate(double)} ile konumu ilerletir.
 * Bir sürüye bağlı hedeflerde ({@link #isSwarmControlled()}) hız vektörünü sürü belirler,
 * hedef yalnızca entegrasyon yapar.
 * <p>
 * Bu sınıf thread-safe değildir; tüm değişiklikler simülasyon thread'inde yapılır.
 * Paralel tehdit hesaplaması sırasında hedefler yalnızca okunur.
 */
public abstract sealed class Target permits Drone, FastDrone, ManeuveringDrone, Decoy {

    /** İz noktaları arasındaki simülasyon süresi (s). */
    public static final double TRAIL_SAMPLE_INTERVAL = 0.25;
    /** Varsayılan iz uzunluğu (nokta sayısı). */
    public static final int DEFAULT_TRAIL_LENGTH = 16;

    private final String id;
    private final TargetType type;
    private final double cruiseSpeed;

    protected double x;
    protected double y;
    protected double velocityX;
    protected double velocityY;

    private ThreatResult threat = ThreatResult.NONE;
    private TargetStatus status = TargetStatus.UNDETECTED;
    private boolean detected;
    private boolean active = true;
    private String swarmId;

    private final double[] trailX;
    private final double[] trailY;
    private int trailHead;
    private int trailCount;
    private double trailTimer;

    /**
     * @param id          benzersiz kimlik
     * @param type        hedef türü
     * @param x           başlangıç x (m)
     * @param y           başlangıç y (m)
     * @param headingDeg  başlangıç yönü (derece, 0° kuzey)
     * @param cruiseSpeed seyir hızı (m/s)
     * @param trailLength tutulacak iz noktası sayısı
     */
    protected Target(String id, TargetType type, double x, double y,
                     double headingDeg, double cruiseSpeed, int trailLength) {
        this.id = Objects.requireNonNull(id, "id");
        this.type = Objects.requireNonNull(type, "type");
        if (cruiseSpeed <= 0) {
            throw new IllegalArgumentException("cruiseSpeed must be positive: " + cruiseSpeed);
        }
        this.cruiseSpeed = cruiseSpeed;
        this.x = x;
        this.y = y;
        this.velocityX = Geometry.velocityX(headingDeg, cruiseSpeed);
        this.velocityY = Geometry.velocityY(headingDeg, cruiseSpeed);
        this.trailX = new double[Math.max(1, trailLength)];
        this.trailY = new double[Math.max(1, trailLength)];
        pushTrail();
    }

    /**
     * Hedefi {@code dt} saniye ilerletir. Her alt sınıf kendi hareket modelini uygular.
     *
     * @param dt simülasyon zaman adımı (s), hız çarpanı uygulanmış
     */
    public abstract void update(double dt);

    /** Hız vektörünü konuma uygular ve iz kaydını günceller. */
    protected final void integrate(double dt) {
        x += velocityX * dt;
        y += velocityY * dt;
        trailTimer += dt;
        if (trailTimer >= TRAIL_SAMPLE_INTERVAL) {
            trailTimer -= TRAIL_SAMPLE_INTERVAL;
            pushTrail();
        }
    }

    /**
     * Mevcut yönü, en fazla {@code maxTurnDeg} derece döndürerek hedef yöne yaklaştırır.
     * Hız büyüklüğü korunur.
     */
    protected final void turnTowards(double desiredHeadingDeg, double maxTurnDeg) {
        double current = getHeading();
        double diff = Geometry.angleDifference(current, desiredHeadingDeg);
        double step = Math.max(-maxTurnDeg, Math.min(maxTurnDeg, diff));
        setHeadingAndSpeed(current + step, getSpeed());
    }

    private void pushTrail() {
        trailX[trailHead] = x;
        trailY[trailHead] = y;
        trailHead = (trailHead + 1) % trailX.length;
        if (trailCount < trailX.length) {
            trailCount++;
        }
    }

    /**
     * İz noktalarını eskiden yeniye doğru verilen dizilere kopyalar.
     *
     * @return kopyalanan nokta sayısı
     */
    public int copyTrail(double[] outX, double[] outY) {
        int n = Math.min(trailCount, Math.min(outX.length, outY.length));
        int start = (trailHead - trailCount + trailX.length) % trailX.length;
        int skip = trailCount - n;
        for (int i = 0; i < n; i++) {
            int idx = (start + skip + i) % trailX.length;
            outX[i] = trailX[idx];
            outY[i] = trailY[idx];
        }
        return n;
    }

    public int getTrailCount() {
        return trailCount;
    }

    // ---------------------------------------------------------------- kinematik

    public void setVelocity(double vx, double vy) {
        this.velocityX = vx;
        this.velocityY = vy;
    }

    public void setHeadingAndSpeed(double headingDeg, double speed) {
        this.velocityX = Geometry.velocityX(headingDeg, speed);
        this.velocityY = Geometry.velocityY(headingDeg, speed);
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public double getVelocityX() {
        return velocityX;
    }

    public double getVelocityY() {
        return velocityY;
    }

    /** Anlık hız büyüklüğü (m/s). */
    public double getSpeed() {
        return Math.hypot(velocityX, velocityY);
    }

    /** Anlık yön (derece, 0° kuzey, saat yönü). */
    public double getHeading() {
        return Geometry.normalizeDegrees(Math.toDegrees(Math.atan2(velocityX, velocityY)));
    }

    /** Hedefin tasarlanmış seyir hızı (m/s). */
    public double getCruiseSpeed() {
        return cruiseSpeed;
    }

    /** Verilen noktaya mesafe (m). */
    public double distanceTo(double px, double py) {
        return Math.hypot(x - px, y - py);
    }

    /** Merkezden (0, 0) bakıldığında hedefin kerterizi. */
    public double bearingFromOrigin() {
        return Geometry.bearingOf(x, y);
    }

    /** Hedeften merkeze (0, 0) doğru yön. */
    protected double headingToOrigin() {
        return Geometry.bearingOf(-x, -y);
    }

    // ---------------------------------------------------------------- durum geçişleri

    /** Radar tarafından ilk kez (veya kaybedildikten sonra yeniden) algılandı. */
    public void markDetected() {
        detected = true;
        if (!status.isTerminal()) {
            status = TargetStatus.DETECTED;
        }
    }

    /** Radar teması kaybedildi. */
    public void markLost() {
        detected = false;
        if (!status.isTerminal()) {
            status = TargetStatus.LOST;
        }
    }

    public void markTracking() {
        if (!status.isTerminal()) {
            status = TargetStatus.TRACKING;
        }
    }

    public void markEngaged() {
        if (!status.isTerminal()) {
            status = TargetStatus.ENGAGED;
        }
    }

    /** Savunma sistemi takibi bıraktığında hedef algılanmış duruma döner. */
    public void releaseTracking() {
        if (status == TargetStatus.TRACKING || status == TargetStatus.ENGAGED) {
            status = detected ? TargetStatus.DETECTED : TargetStatus.LOST;
        }
    }

    public void neutralize() {
        status = TargetStatus.NEUTRALIZED;
        active = false;
    }

    public void escape() {
        status = TargetStatus.ESCAPED;
        active = false;
    }

    /** Hedef simülasyon alanından tamamen çıktı. */
    public void leaveArea() {
        detected = false;
        status = TargetStatus.LOST;
        active = false;
    }

    // ---------------------------------------------------------------- tehdit

    public void applyThreat(ThreatResult result) {
        this.threat = Objects.requireNonNull(result, "result");
    }

    public ThreatResult getThreat() {
        return threat;
    }

    public double getThreatScore() {
        return threat.score();
    }

    public ThreatLevel getThreatLevel() {
        return threat.level();
    }

    public double getTimeToImpact() {
        return threat.timeToImpact();
    }

    // ---------------------------------------------------------------- erişimciler

    public String getId() {
        return id;
    }

    public TargetType getType() {
        return type;
    }

    public TargetStatus getStatus() {
        return status;
    }

    public boolean isDetected() {
        return detected;
    }

    public boolean isActive() {
        return active;
    }

    public String getSwarmId() {
        return swarmId;
    }

    public boolean isSwarmControlled() {
        return swarmId != null;
    }

    public void assignToSwarm(String swarmId) {
        this.swarmId = swarmId;
    }

    @Override
    public String toString() {
        return "%s[%s, x=%.0f, y=%.0f, v=%.1f, status=%s]".formatted(
                getClass().getSimpleName(), id, x, y, getSpeed(), status);
    }
}
