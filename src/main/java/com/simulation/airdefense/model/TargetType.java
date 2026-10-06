package com.simulation.airdefense.model;

/**
 * Hedef türleri. Her tür, arayüzde kullanılan görünen ad, ID öneki ve
 * senaryolarda kullanılan varsayılan temel hızı (m/s) taşır.
 */
public enum TargetType {
    DRONE("İHA", "İHA", 50.0),
    FAST_DRONE("Hızlı İHA", "HIZ", 50.0),
    MANEUVERING_DRONE("Manevralı İHA", "MNV", 55.0),
    DECOY("Yem (Decoy)", "YEM", 45.0);

    private final String displayName;
    private final String idPrefix;
    private final double defaultBaseSpeed;

    TargetType(String displayName, String idPrefix, double defaultBaseSpeed) {
        this.displayName = displayName;
        this.idPrefix = idPrefix;
        this.defaultBaseSpeed = defaultBaseSpeed;
    }

    public String displayName() {
        return displayName;
    }

    public String idPrefix() {
        return idPrefix;
    }

    /** Senaryolarda kullanılan varsayılan temel hız (m/s). FastDrone bunu kendi çarpanıyla büyütür. */
    public double defaultBaseSpeed() {
        return defaultBaseSpeed;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
