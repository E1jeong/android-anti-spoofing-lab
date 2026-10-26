package com.unionbiometrics.vision;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public final class FacadeContractTest {
    @Test(expected = IllegalArgumentException.class)
    public void createRejectsNegativeSlot() {
        AntiSpoofingEngine.create(null, -1, 10, 3);
    }

    @Test(expected = IllegalArgumentException.class)
    public void createRejectsZeroSamples() {
        AntiSpoofingEngine.create(null, 0, 10, 0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void createRejectsNegativeSettleFrames() {
        AntiSpoofingEngine.create(null, 0, -1, 3);
    }

    @Test(expected = IllegalArgumentException.class)
    public void createRejectsMissingContext() {
        AntiSpoofingEngine.create(null, 0, 10, 3);
    }

    @Test
    public void errorIsDistinctFromSpoof() {
        AntiSpoofingResult result = AntiSpoofingResult.sessionError("bad slot");
        assertEquals(AntiSpoofingResult.Status.ERROR, result.getStatus());
        assertEquals("bad slot", result.getErrorMessage());
        assertEquals(0f, result.getScore(), 0f);
        assertNull(result.getProbabilities());
    }

    @Test
    public void runningKeepsCollectedSample() {
        float[] probabilities = new float[12];
        probabilities[0] = 0.8f;
        AntiSpoofingResult result = AntiSpoofingResult.sessionRunning(probabilities, 4L);

        assertEquals(AntiSpoofingResult.Status.RUNNING, result.getStatus());
        assertEquals(0.8f, result.getProbability(0), 0f);
        assertEquals(Long.valueOf(4L), result.getInferenceMs());
    }

    @Test
    public void probabilitiesAreDefensive() {
        float[] probabilities = new float[12];
        probabilities[1] = 0.8f;
        AntiSpoofingResult result = AntiSpoofingResult.sessionComplete(probabilities, 4L);
        probabilities[1] = 0f;
        assertEquals(AntiSpoofingResult.Status.SPOOF, result.getStatus());
        assertEquals(0.8f, result.getScore(), 0f);
        result.getProbabilities()[1] = 0f;
        assertEquals(0.8f, result.getScore(), 0f);
    }

    @Test
    public void acceptedDentalClassKeepsClassLabel() {
        float[] probabilities = new float[12];
        probabilities[10] = 1f;
        AntiSpoofingResult result = AntiSpoofingResult.sessionComplete(probabilities, 4L);
        assertEquals(AntiSpoofingResult.Status.LIVE, result.getStatus());
        assertEquals("DENTAL_WHITE", AntiSpoofingResult.getClassLabels()[result.getTopIndex()]);
    }

    @Test
    public void rawFrameKeepsProbabilitiesAndTiming() {
        float[] probabilities = new float[12];
        probabilities[0] = 0.9f;
        AntiSpoofingResult result = AntiSpoofingResult.frameResult(probabilities, 4L);
        probabilities[0] = 0f;
        assertEquals(AntiSpoofingResult.Status.LIVE, result.getStatus());
        assertEquals(0.9f, result.getProbability(0), 0f);
        assertEquals(Long.valueOf(4L), result.getInferenceMs());
        result.getProbabilities()[0] = 0f;
        assertEquals(0.9f, result.getProbability(0), 0f);
    }

    @Test
    public void rawFrameErrorHasNoProbability() {
        AntiSpoofingResult result = AntiSpoofingResult.frameError("invalid crop");
        assertEquals(AntiSpoofingResult.Status.ERROR, result.getStatus());
        assertEquals("invalid crop", result.getErrorMessage());
        assertNull(result.getProbabilities());
        assertEquals(Long.valueOf(0L), result.getInferenceMs());
    }

    @Test(expected = IllegalArgumentException.class)
    public void rawFrameRejectsMissingProbabilities() {
        AntiSpoofingResult.frameResult(null, 0L);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rawFrameRejectsWrongProbabilityCount() {
        AntiSpoofingResult.frameResult(new float[]{1f}, 0L);
    }

    @Test(expected = IllegalArgumentException.class)
    public void sessionRejectsWrongProbabilityCount() {
        AntiSpoofingResult.sessionComplete(new float[]{1f}, 0L);
    }

    @Test
    public void irOnlySlotAcceptsNullOrPairedRgbWithoutCopying() {
        assertFalse(AntiSpoofingEngine.shouldCopyRgb(false, null, null, false));
        assertFalse(AntiSpoofingEngine.shouldCopyRgb(false, new Object(), new Object(), false));
    }

    @Test(expected = IllegalArgumentException.class)
    public void irOnlySlotRejectsUnpairedRgbInputs() {
        AntiSpoofingEngine.shouldCopyRgb(false, new Object(), null, false);
    }

    @Test(expected = IllegalArgumentException.class)
    public void irOnlySlotRejectsNullRgbWithEmptyFaceBox() {
        AntiSpoofingEngine.shouldCopyRgb(false, null, new Object(), true);
    }

    @Test(expected = IllegalArgumentException.class)
    public void irOnlySlotRejectsEmptyRgbFaceBox() {
        AntiSpoofingEngine.shouldCopyRgb(false, new Object(), new Object(), true);
    }

    @Test
    public void dualSlotCopiesPairedRgbInputs() {
        assertTrue(AntiSpoofingEngine.shouldCopyRgb(true, new Object(), new Object(), false));
    }

    @Test(expected = IllegalArgumentException.class)
    public void dualSlotRejectsMissingRgbInputs() {
        AntiSpoofingEngine.shouldCopyRgb(true, null, null, false);
    }

    @Test(expected = IllegalArgumentException.class)
    public void dualSlotRejectsUnpairedRgbInputs() {
        AntiSpoofingEngine.shouldCopyRgb(true, null, new Object(), false);
    }
}
