package com.virditech.ac7000.recognition;

import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/** Coordinates recognition worker ownership and invalidation-sensitive commits. */
public final class RecognitionWorkCoordinator {
    private boolean workerRunning;
    private long invalidationGeneration;

    public synchronized long acquireWorkerGeneration() {
        if (workerRunning) return -1L;
        workerRunning = true;
        return invalidationGeneration;
    }

    public synchronized void releaseWorker() {
        workerRunning = false;
    }

    public <T> T prepareOwnedWork(Supplier<T> preparation) {
        T prepared = null;
        try {
            prepared = preparation.get();
            return prepared;
        } finally {
            if (prepared == null) releaseWorker();
        }
    }

    public synchronized void invalidate() {
        invalidationGeneration++;
    }

    public synchronized boolean isCurrent(long expectedGeneration) {
        return expectedGeneration == invalidationGeneration;
    }

    public synchronized boolean commitIfCurrent(long expectedGeneration,
                                                BooleanSupplier additionalCheck,
                                                Runnable commit) {
        if (expectedGeneration != invalidationGeneration || !additionalCheck.getAsBoolean()) {
            return false;
        }
        commit.run();
        return true;
    }

    public synchronized void runExclusive(Runnable action) {
        action.run();
    }
}
