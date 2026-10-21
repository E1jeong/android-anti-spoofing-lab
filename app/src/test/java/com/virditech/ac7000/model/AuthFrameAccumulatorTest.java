package com.virditech.ac7000.model;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public final class AuthFrameAccumulatorTest {
    @Test public void rejectsInvalidConfigurationAndFrame() {
        assertInvalid(() -> new AuthFrameAccumulator(0, 0.85f));
        assertInvalid(() -> new AuthFrameAccumulator(5, 1.1f));
        AuthFrameAccumulator accumulator = new AuthFrameAccumulator(5, 0.85f);
        assertInvalid(() -> accumulator.add(new float[]{1f}, 0L, 0L));
    }

    @Test public void emitsOnlyAfterTargetFramesAndSelectsTopSpoof() {
        AuthFrameAccumulator accumulator = new AuthFrameAccumulator(3, 0.85f);
        assertNull(accumulator.add(new float[]{0.8f, 0.1f, 0.1f}, 1_000_000L, 2_000_000L));
        assertNull(accumulator.add(new float[]{0.9f, 0.0f, 0.1f}, 2_000_000L, 3_000_000L));
        AuthFrameAccumulator.Verdict verdict = accumulator.add(
                new float[]{0.85f, 0.05f, 0.10f}, 3_000_000L, 6_000_000L);

        assertTrue(verdict.live);
        assertEquals(0.85f, verdict.averageLiveScore, 0.0001f);
        assertEquals(2, verdict.topSpoofIndex);
        assertEquals(5L, verdict.elapsedMs);
    }

    @Test public void resetDropsPartialVerdict() {
        AuthFrameAccumulator accumulator = new AuthFrameAccumulator(2, 0.85f);
        accumulator.add(new float[]{1f, 0f}, 1L, 1L);
        accumulator.reset();
        assertNull(accumulator.add(new float[]{0f, 1f}, 2L, 2L));
        AuthFrameAccumulator.Verdict verdict = accumulator.add(new float[]{0f, 1f}, 3L, 3L);
        assertFalse(verdict.live);
    }

    @Test public void sumsIndoorAndOutdoorLiveWithoutTreatingOutdoorLiveAsSpoof() {
        AuthFrameAccumulator accumulator = new AuthFrameAccumulator(1, 0.85f);
        float[] probabilities = new float[24];
        probabilities[0] = 0.40f;
        probabilities[12] = 0.50f;
        probabilities[13] = 0.10f;
        AuthFrameAccumulator.Verdict verdict = accumulator.add(probabilities, 0L, 1L);
        assertTrue(verdict.live);
        assertEquals(0.90f, verdict.averageLiveScore, 0.0001f);
        assertEquals(13, verdict.topSpoofIndex);
    }

    private static void assertInvalid(Runnable action) {
        try {
            action.run();
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // Expected.
        }
    }
}
