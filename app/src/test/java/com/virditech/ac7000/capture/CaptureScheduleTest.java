package com.virditech.ac7000.capture;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class CaptureScheduleTest {
    @Test public void defaultScheduleCollectsExactlyOneHundredSamples() {
        assertEquals(100, CaptureSchedule.TARGET_COUNT);
        assertEquals(9, CaptureSchedule.DEFAULT_STEPS.length);
        assertEquals(5, CaptureSchedule.DEFAULT_STEPS[0].sector);
        assertEquals("CENTER", CaptureSchedule.DEFAULT_STEPS[0].name);
        assertEquals(20, CaptureSchedule.DEFAULT_STEPS[0].targetCount);
    }

    @Test public void currentStepClampsOutOfRangeIndexes() {
        assertEquals("CENTER", CaptureSchedule.currentStep(-1).name);
        assertEquals("RIGHT BOTTOM", CaptureSchedule.currentStep(99).name);
    }

    @Test public void countdownRoundsUpRemainingMilliseconds() {
        assertEquals(3, CaptureSchedule.countdownSeconds(3000L, 1L));
        assertEquals(1, CaptureSchedule.countdownSeconds(3000L, 2999L));
        assertEquals(0, CaptureSchedule.countdownSeconds(3000L, 3000L));
        assertEquals(0, CaptureSchedule.countdownSeconds(3000L, 4000L));
    }

    @Test public void onlyLiveClassUsesQualityCheck() {
        assertTrue(CaptureSchedule.shouldCheckQuality("live"));
        assertFalse(CaptureSchedule.shouldCheckQuality("display"));
        assertFalse(CaptureSchedule.shouldCheckQuality("picture"));
        assertFalse(CaptureSchedule.shouldCheckQuality("print"));
        assertFalse(CaptureSchedule.shouldCheckQuality("mask"));
        assertFalse(CaptureSchedule.shouldCheckQuality("pmask"));
    }
}
