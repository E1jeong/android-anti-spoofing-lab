package com.virditech.ac7000.device;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ForegroundEntryDetectorTest {

    @Test
    public void acceptsDeltaAtOrAboveTheThreshold() {
        assertTrue(ForegroundEntryDetector.isChanged(16f));
        assertTrue(ForegroundEntryDetector.isChanged(24f));
    }

    @Test
    public void rejectsDeltaBelowTheThreshold() {
        assertFalse(ForegroundEntryDetector.isChanged(15.9f));
    }
}
