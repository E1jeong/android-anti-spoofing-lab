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
    @Test public void sumsIndoorAndOutdoorLive() {
        float[] probabilities = new float[24];
        probabilities[0] = 0.35f;
        probabilities[12] = 0.46f;
        assertTrue(AttackLiveCaptureGate.shouldSave(probabilities));
        probabilities[12] = 0.44f;
        assertFalse(AttackLiveCaptureGate.shouldSave(probabilities));
    }
}
