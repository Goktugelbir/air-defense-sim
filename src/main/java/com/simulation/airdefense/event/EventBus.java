package com.simulation.airdefense.event;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Basit, thread-safe olay yolu (Observer pattern).
 * Dinleyici listesi {@link CopyOnWriteArrayList} olduğundan yayın sırasında abonelik değişebilir.
 */
public class EventBus {

    private final List<EventListener> listeners = new CopyOnWriteArrayList<>();

    public void subscribe(EventListener listener) {
        listeners.add(Objects.requireNonNull(listener, "listener"));
    }

    public void unsubscribe(EventListener listener) {
        listeners.remove(listener);
    }

    /** Olayı tüm dinleyicilere iletir. Bir dinleyicinin hatası diğerlerini etkilemez. */
    public void publish(SimulationEvent event) {
        for (EventListener listener : listeners) {
            try {
                listener.onEvent(event);
            } catch (RuntimeException e) {
                System.err.println("Event listener failed: " + e);
            }
        }
    }

    public int listenerCount() {
        return listeners.size();
    }
}
