package com.simulation.airdefense.event;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Olayları bir {@link BlockingQueue} üzerinden ayrı bir thread'de tüketen logger.
 * <p>
 * Simülasyon thread'i yalnızca kuyruğa ekler (bloklamaz). Tüketici thread olayları
 * toplar ve en fazla {@code flushIntervalMillis} aralıklarla toplu (batch) olarak
 * {@code batchSink}'e iletir. Arayüz bu batch'i tek bir {@code Platform.runLater} ile işler.
 */
public class EventLogger implements EventListener {

    private static final long DEFAULT_FLUSH_INTERVAL_MILLIS = 100;
    private static final int MAX_BATCH_SIZE = 256;

    private final BlockingQueue<SimulationEvent> queue = new LinkedBlockingQueue<>();
    private final Consumer<List<SimulationEvent>> batchSink;
    private final long flushIntervalMillis;
    private final Thread worker;
    private volatile boolean running = true;

    public EventLogger(Consumer<List<SimulationEvent>> batchSink) {
        this(batchSink, DEFAULT_FLUSH_INTERVAL_MILLIS);
    }

    public EventLogger(Consumer<List<SimulationEvent>> batchSink, long flushIntervalMillis) {
        this.batchSink = Objects.requireNonNull(batchSink, "batchSink");
        this.flushIntervalMillis = flushIntervalMillis;
        this.worker = Thread.ofPlatform().name("event-logger").daemon(true).unstarted(this::consumeLoop);
    }

    /** Tüketici thread'i başlatır. */
    public void start() {
        worker.start();
    }

    @Override
    public void onEvent(SimulationEvent event) {
        if (running) {
            queue.offer(event);
        }
    }

    private void consumeLoop() {
        List<SimulationEvent> buffer = new ArrayList<>();
        long lastFlush = System.nanoTime();
        while (running || !queue.isEmpty()) {
            try {
                SimulationEvent first = queue.poll(flushIntervalMillis, TimeUnit.MILLISECONDS);
                if (first != null) {
                    buffer.add(first);
                    queue.drainTo(buffer, MAX_BATCH_SIZE);
                }
                long now = System.nanoTime();
                boolean due = TimeUnit.NANOSECONDS.toMillis(now - lastFlush) >= flushIntervalMillis;
                if (!buffer.isEmpty() && (due || buffer.size() >= MAX_BATCH_SIZE)) {
                    flush(buffer);
                    lastFlush = now;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        queue.drainTo(buffer);
        if (!buffer.isEmpty()) {
            flush(buffer);
        }
    }

    private void flush(List<SimulationEvent> buffer) {
        List<SimulationEvent> batch = List.copyOf(buffer);
        buffer.clear();
        try {
            batchSink.accept(batch);
        } catch (RuntimeException e) {
            System.err.println("Log sink failed: " + e);
        }
    }

    /** Thread'i durdurur ve bitmesini kısa süre bekler. */
    public void shutdown() {
        running = false;
        worker.interrupt();
        try {
            worker.join(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
