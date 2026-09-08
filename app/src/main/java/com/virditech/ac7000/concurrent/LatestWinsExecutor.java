package com.virditech.ac7000.concurrent;

import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/** Executes one task at a time and retains only the newest task waiting behind it. */
public final class LatestWinsExecutor<T> {
    private final Executor executor;
    private final Consumer<T> processor;
    private final Consumer<T> disposer;
    private final BiConsumer<T, RuntimeException> errorHandler;
    private final Runnable idleCallback;
    private final AtomicReference<T> pending = new AtomicReference<>();
    private final AtomicBoolean workerRunning = new AtomicBoolean();

    public LatestWinsExecutor(Executor executor, Consumer<T> processor, Consumer<T> disposer,
                              BiConsumer<T, RuntimeException> errorHandler,
                              Runnable idleCallback) {
        this.executor = Objects.requireNonNull(executor, "executor");
        this.processor = Objects.requireNonNull(processor, "processor");
        this.disposer = Objects.requireNonNull(disposer, "disposer");
        this.errorHandler = Objects.requireNonNull(errorHandler, "errorHandler");
        this.idleCallback = Objects.requireNonNull(idleCallback, "idleCallback");
    }

    public void offer(T task) {
        Objects.requireNonNull(task, "task");
        T replaced = pending.getAndSet(task);
        if (replaced != null) disposer.accept(replaced);
        scheduleWorker();
    }

    public void clear() {
        T discarded = pending.getAndSet(null);
        if (discarded != null) disposer.accept(discarded);
    }

    private void scheduleWorker() {
        if (!workerRunning.compareAndSet(false, true)) return;
        try {
            executor.execute(this::drain);
        } catch (RejectedExecutionException e) {
            workerRunning.set(false);
            T rejected = pending.getAndSet(null);
            if (rejected != null) disposer.accept(rejected);
        }
    }

    private void drain() {
        try {
            T task;
            while ((task = pending.getAndSet(null)) != null) {
                try {
                    processor.accept(task);
                } catch (RuntimeException e) {
                    errorHandler.accept(task, e);
                } finally {
                    disposer.accept(task);
                }
            }
        } finally {
            workerRunning.set(false);
            if (pending.get() != null) scheduleWorker();
            idleCallback.run();
        }
    }
}
