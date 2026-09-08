package com.virditech.ac7000.camera;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

public final class FrameSynchronizerTest {
    @Test public void matchesAtBothInclusiveDeltaBoundaries() {
        List<Frame> disposed = new ArrayList<>();
        FrameSynchronizer<Frame> frames = new FrameSynchronizer<>(150L,
                frame -> frame.timestamp, disposed::add);
        Frame older = new Frame(850L);
        frames.offerSecondary(older);
        assertSame(older, frames.takeFor(1_000L));

        Frame newer = new Frame(1_150L);
        frames.offerSecondary(newer);
        assertSame(newer, frames.takeFor(1_000L));
        assertEquals(0, disposed.size());
    }

    @Test public void disposesStaleSecondaryButRetainsFutureSecondary() {
        List<Frame> disposed = new ArrayList<>();
        FrameSynchronizer<Frame> frames = new FrameSynchronizer<>(150L,
                frame -> frame.timestamp, disposed::add);
        Frame stale = new Frame(800L);
        frames.offerSecondary(stale);
        assertNull(frames.takeFor(1_000L));
        assertSame(stale, disposed.get(0));

        Frame future = new Frame(1_300L);
        frames.offerSecondary(future);
        assertNull(frames.takeFor(1_000L));
        assertSame(future, frames.takeFor(1_200L));
    }

    @Test public void replacementAndClearDisposeOwnershipExactlyOnce() {
        List<Frame> disposed = new ArrayList<>();
        FrameSynchronizer<Frame> frames = new FrameSynchronizer<>(150L,
                frame -> frame.timestamp, disposed::add);
        Frame first = new Frame(1L);
        Frame second = new Frame(2L);
        frames.offerSecondary(first);
        frames.offerSecondary(second);
        frames.clear();
        assertEquals(java.util.Arrays.asList(first, second), disposed);
    }

    private static final class Frame {
        final long timestamp;

        Frame(long timestamp) {
            this.timestamp = timestamp;
        }
    }
}
