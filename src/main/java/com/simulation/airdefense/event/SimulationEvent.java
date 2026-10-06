package com.simulation.airdefense.event;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

/**
 * Değiştirilemez simülasyon olayı.
 *
 * @param timestamp      olayın oluştuğu duvar saati
 * @param simulationTime simülasyon zamanı (s)
 * @param type           olay türü
 * @param targetId       ilgili hedef (yoksa {@code null})
 * @param message        okunabilir mesaj
 */
public record SimulationEvent(LocalTime timestamp,
                              double simulationTime,
                              EventType type,
                              String targetId,
                              String message) {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");

    public SimulationEvent {
        Objects.requireNonNull(timestamp, "timestamp");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(message, "message");
    }

    public static SimulationEvent of(double simulationTime, EventType type, String targetId, String message) {
        return new SimulationEvent(LocalTime.now(), simulationTime, type, targetId, message);
    }

    /** Log satırı biçimi: {@code [14:32:01] TARGET-04 detected}. */
    public String format() {
        return "[" + TIME_FORMAT.format(timestamp) + "] " + message;
    }
}
