package com.virditech.ac7000.performance;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class LatencyWindowTest {
    @Test public void reportsNearestRankPercentiles() {
        LatencyWindow window = new LatencyWindow(20);
        for (int value = 1; value <= 20; value++) window.add(value);

        LatencyWindow.Snapshot snapshot = window.snapshot();

        assertEquals(20L, snapshot.count);
        assertEquals(10L, snapshot.p50Ms);
        assertEquals(19L, snapshot.p95Ms);
        assertTrue(snapshot.hasSamples());
    }

    @Test public void keepsOnlyNewestValuesButPreservesTotalCount() {
        LatencyWindow window = new LatencyWindow(3);
        window.add(100L);
        window.add(10L);
        window.add(20L);
        window.add(30L);
        LatencyWindow.Snapshot snapshot = window.snapshot();

        assertEquals(4L, snapshot.count);
        assertEquals(20L, snapshot.p50Ms);
        assertEquals(30L, snapshot.p95Ms);
    }

    @Test public void emptyWindowHasNoSamples() {
        LatencyWindow.Snapshot snapshot = new LatencyWindow(1).snapshot();

        assertFalse(snapshot.hasSamples());
        assertEquals(0L, snapshot.count);
    }
}
