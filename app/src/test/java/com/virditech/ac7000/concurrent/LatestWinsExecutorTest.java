package com.virditech.ac7000.concurrent;

import org.junit.Test;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class LatestWinsExecutorTest {
    @Test public void keepsOnlyNewestWaitingTaskAndDisposesEveryTask() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        CountDownLatch firstStarted = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch idle = new CountDownLatch(1);
        List<Integer> processed = new CopyOnWriteArrayList<>();
        List<Integer> disposed = new CopyOnWriteArrayList<>();
        LatestWinsExecutor<Integer> latest = new LatestWinsExecutor<>(executor, value -> {
            processed.add(value);
            if (value == 1) {
                firstStarted.countDown();
                await(releaseFirst);
            }
        }, disposed::add, (value, error) -> {}, idle::countDown);

        latest.offer(1);
        assertTrue(firstStarted.await(1, TimeUnit.SECONDS));
        latest.offer(2);
        latest.offer(3);
        releaseFirst.countDown();

        assertTrue(idle.await(1, TimeUnit.SECONDS));
        assertEquals(java.util.Arrays.asList(1, 3), processed);
        assertEquals(3, disposed.size());
        assertEquals(1, Collections.frequency(disposed, 1));
        assertEquals(1, Collections.frequency(disposed, 2));
        assertEquals(1, Collections.frequency(disposed, 3));
        executor.shutdownNow();
    }

    @Test public void clearDisposesPendingTaskExactlyOnce() {
        List<Runnable> submitted = new CopyOnWriteArrayList<>();
        List<Integer> disposed = new CopyOnWriteArrayList<>();
        LatestWinsExecutor<Integer> latest = new LatestWinsExecutor<>(submitted::add,
                value -> {}, disposed::add, (value, error) -> {}, () -> {});

        latest.offer(1);
        latest.clear();

        assertEquals(Collections.singletonList(1), disposed);
        submitted.get(0).run();
        assertEquals(Collections.singletonList(1), disposed);
    }

    @Test public void rejectedWorkerDisposesTaskExactlyOnce() {
        List<Integer> disposed = new CopyOnWriteArrayList<>();
        LatestWinsExecutor<Integer> latest = new LatestWinsExecutor<>(command -> {
            throw new java.util.concurrent.RejectedExecutionException("expected");
        }, value -> {}, disposed::add, (value, error) -> {}, () -> {});

        latest.offer(1);

        assertEquals(Collections.singletonList(1), disposed);
    }

    @Test public void processingFailureDoesNotPreventNextTask() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        CountDownLatch failed = new CountDownLatch(1);
        CountDownLatch secondProcessed = new CountDownLatch(1);
        LatestWinsExecutor<Integer> latest = new LatestWinsExecutor<>(executor, value -> {
            if (value == 1) throw new IllegalStateException("expected");
            secondProcessed.countDown();
        }, value -> {}, (value, error) -> failed.countDown(), () -> {});

        latest.offer(1);
        assertTrue(failed.await(1, TimeUnit.SECONDS));
        latest.offer(2);
        assertTrue(secondProcessed.await(1, TimeUnit.SECONDS));
        executor.shutdownNow();
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
