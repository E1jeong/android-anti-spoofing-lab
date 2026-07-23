package com.virditech.ac7000.capture;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class AttackLiveCaptureGateTest {
    @Test public void savesOnlyAtOrAboveEightyPercentLive() {
        assertFalse(AttackLiveCaptureGate.shouldSave(new float[]{0.799f}));
        assertTrue(AttackLiveCaptureGate.shouldSave(new float[]{0.80f}));
        assertTrue(AttackLiveCaptureGate.shouldSave(new float[]{0.801f}));
    }

    @Test public void rejectsMissingLiveProbability() {
        assertFalse(AttackLiveCaptureGate.shouldSave(null));
        assertFalse(AttackLiveCaptureGate.shouldSave(new float[0]));
    }
}
