package com.virditech.ac7000.recognition;

import org.junit.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class RecognitionWorkCoordinatorTest {
    @Test
    public void preparationExceptionReleasesWorker() {
        RecognitionWorkCoordinator coordinator = new RecognitionWorkCoordinator();
        assertEquals(0L, coordinator.acquireWorkerGeneration());
        try {
            coordinator.prepareOwnedWork(() -> {
                throw new IllegalStateException("alignment failed");
            });
            fail("Expected alignment failure");
        } catch (IllegalStateException expected) {
            // Expected.
        }
        assertEquals(0L, coordinator.acquireWorkerGeneration());
    }

    @Test
    public void nullPreparationReleasesWorker() {
        RecognitionWorkCoordinator coordinator = new RecognitionWorkCoordinator();
        assertEquals(0L, coordinator.acquireWorkerGeneration());
        coordinator.prepareOwnedWork(() -> null);
        assertEquals(0L, coordinator.acquireWorkerGeneration());
    }

    @Test
    public void acquisitionCapturesGenerationAndInvalidationPreventsCommit() {
        RecognitionWorkCoordinator coordinator = new RecognitionWorkCoordinator();
        long acceptedGeneration = coordinator.acquireWorkerGeneration();
        coordinator.invalidate();
        AtomicBoolean mutated = new AtomicBoolean();

        assertFalse(coordinator.commitIfCurrent(acceptedGeneration, () -> true,
                () -> mutated.set(true)));
        assertFalse(mutated.get());
    }

    @Test
    public void currentEnrollmentCommitRuns() {
        RecognitionWorkCoordinator coordinator = new RecognitionWorkCoordinator();
        long acceptedGeneration = coordinator.acquireWorkerGeneration();
        AtomicBoolean mutated = new AtomicBoolean();

        assertTrue(coordinator.commitIfCurrent(acceptedGeneration, () -> true,
                () -> mutated.set(true)));
        assertTrue(mutated.get());
    }
}
