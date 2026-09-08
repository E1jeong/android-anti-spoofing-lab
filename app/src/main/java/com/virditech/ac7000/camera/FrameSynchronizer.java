package com.virditech.ac7000.camera;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.ToLongFunction;

/** Retains the latest secondary frame and matches it to a primary timestamp. */
public final class FrameSynchronizer<T> {
    private final long maxDelta;
    private final ToLongFunction<T> timestampReader;
    private final Consumer<T> disposer;
    private T latestSecondary;

    public FrameSynchronizer(long maxDelta, ToLongFunction<T> timestampReader,
                             Consumer<T> disposer) {
        if (maxDelta < 0L) throw new IllegalArgumentException("maxDelta must not be negative");
        this.maxDelta = maxDelta;
        this.timestampReader = Objects.requireNonNull(timestampReader, "timestampReader");
        this.disposer = Objects.requireNonNull(disposer, "disposer");
    }

    public synchronized void offerSecondary(T frame) {
        Objects.requireNonNull(frame, "frame");
        T replaced = latestSecondary;
        latestSecondary = frame;
        if (replaced != null) disposer.accept(replaced);
    }

    public synchronized T takeFor(long primaryTimestamp) {
        if (latestSecondary == null) return null;
        long delta = primaryTimestamp - timestampReader.applyAsLong(latestSecondary);
        if (Math.abs(delta) <= maxDelta) {
            T matched = latestSecondary;
            latestSecondary = null;
            return matched;
        }
        if (delta > maxDelta) {
            disposer.accept(latestSecondary);
            latestSecondary = null;
        }
        return null;
    }

    public synchronized void clear() {
        T discarded = latestSecondary;
        latestSecondary = null;
        if (discarded != null) disposer.accept(discarded);
    }
}
