package com.virditech.ac7000.capture;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class CaptureScheduleTest {
    @Test public void defaultScheduleCollectsExactlyOneHundredSamples() {
        assertEquals(100, CaptureSchedule.TARGET_COUNT);
        assertEquals(9, CaptureSchedule.DEFAULT_STEPS.length);
        int[] sectors = {1, 4, 7, 2, 5, 8, 3, 6, 9};
        for (int i = 0; i < sectors.length; i++) {
            assertEquals(sectors[i], CaptureSchedule.DEFAULT_STEPS[i].sector);
            assertEquals(i == 4 ? 20 : 10, CaptureSchedule.DEFAULT_STEPS[i].targetCount);
        }
        assertEquals("CENTER", CaptureSchedule.DEFAULT_STEPS[4].name);
    }

    @Test public void currentStepClampsOutOfRangeIndexes() {
        assertEquals("LEFT TOP", CaptureSchedule.currentStep(-1).name);
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
        assertFalse(CaptureSchedule.shouldCheckQuality("curved_picture"));
        assertFalse(CaptureSchedule.shouldCheckQuality("curved_print"));
        assertFalse(CaptureSchedule.shouldCheckQuality("curved_mask"));
        assertFalse(CaptureSchedule.shouldCheckQuality("curved_pmask"));
    }

    @Test public void liveQualityModesCheckQualityBehavior() {
        assertTrue(CaptureSchedule.shouldCheckQuality("live", CaptureStorage.QUALITY_MEDIUM));
        assertTrue(CaptureSchedule.shouldCheckQuality("live", CaptureStorage.QUALITY_HIGH));
        assertFalse(CaptureSchedule.shouldCheckQuality("live", CaptureStorage.QUALITY_OFF));
    }
}
