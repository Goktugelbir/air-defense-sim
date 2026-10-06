package com.simulation.airdefense.event;

/** Simülasyon olay türleri. Her tür, log renklendirmesi için bir kategoriye aittir. */
public enum EventType {
    SIMULATION_STARTED(Category.SYSTEM),
    SIMULATION_PAUSED(Category.SYSTEM),
    SIMULATION_RESUMED(Category.SYSTEM),
    SIMULATION_RESET(Category.SYSTEM),
    SIMULATION_FINISHED(Category.SYSTEM),
    SCENARIO_LOADED(Category.SYSTEM),
    SETTINGS_CHANGED(Category.SYSTEM),
    TARGET_SPAWNED(Category.INFO),
    TARGET_DETECTED(Category.DETECTION),
    TARGET_LOST(Category.WARNING),
    THREAT_LEVEL_CHANGED(Category.THREAT),
    PRIORITY_SELECTED(Category.DEFENSE),
    ENGAGEMENT_STARTED(Category.DEFENSE),
    ENGAGEMENT_FAILED(Category.WARNING),
    TARGET_NEUTRALIZED(Category.SUCCESS),
    TARGET_ESCAPED(Category.CRITICAL),
    TARGET_LEFT_AREA(Category.INFO),
    SWARM_MANEUVER(Category.THREAT);

    /** Log panelindeki renk grubu. */
    public enum Category {
        SYSTEM, INFO, DETECTION, THREAT, DEFENSE, SUCCESS, WARNING, CRITICAL
    }

    private final Category category;

    EventType(Category category) {
        this.category = category;
    }

    public Category category() {
        return category;
    }
}
