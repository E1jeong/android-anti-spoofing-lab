package com.virditech.ac7000.concurrent;

import java.util.concurrent.atomic.AtomicInteger;

public final class GenerationGuard {
    private final AtomicInteger current = new AtomicInteger();

    public int advance() {
        return current.incrementAndGet();
    }

    public boolean isCurrent(int generation) {
        return current.get() == generation;
    }
}
