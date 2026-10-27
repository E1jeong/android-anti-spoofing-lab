package com.unionbiometrics.vision;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public final class FacadeContractTest {
    @Test(expected = IllegalArgumentException.class)
    public void createRejectsNegativeSlot() {
        VisionEngine.create(null, -1, 10, 3);
    }

    @Test(expected = IllegalArgumentException.class)
    public void createRejectsZeroSamples() {
        VisionEngine.create(null, 0, 10, 0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void createRejectsNegativeSettleFrames() {
        VisionEngine.create(null, 0, -1, 3);
    }

    @Test(expected = IllegalArgumentException.class)
    public void createRejectsMissingContext() {
        VisionEngine.create(null, 0, 10, 3);
    }

    @Test
    public void errorIsDistinctFromSpoof() {
        VisionLivenessResult result = VisionLivenessResult.sessionError("bad slot");
        assertEquals(VisionLivenessResult.Status.ERROR, result.getStatus());
        assertEquals("bad slot", result.getErrorMessage());
        assertEquals(0f, result.getScore(), 0f);
        assertNull(result.getProbabilities());
    }

    @Test
    public void runningKeepsCollectedSample() {
        float[] probabilities = new float[12];
        probabilities[0] = 0.8f;
        VisionLivenessResult result = VisionLivenessResult.sessionRunning(probabilities, 4L);

        assertEquals(VisionLivenessResult.Status.RUNNING, result.getStatus());
        assertEquals(0.8f, result.getProbabilities()[0], 0f);
        assertEquals(Long.valueOf(4L), result.getInferenceMs());
    }

    @Test
    public void probabilitiesAreDefensive() {
        float[] probabilities = new float[12];
        probabilities[1] = 0.8f;
        VisionLivenessResult result = VisionLivenessResult.sessionComplete(probabilities, 4L);
        probabilities[1] = 0f;
        assertEquals(VisionLivenessResult.Status.SPOOF, result.getStatus());
        assertEquals(0.8f, result.getScore(), 0f);
        result.getProbabilities()[1] = 0f;
        assertEquals(0.8f, result.getScore(), 0f);
    }

    @Test
    public void acceptedDentalClassKeepsClassLabel() {
        float[] probabilities = new float[12];
        probabilities[10] = 1f;
        VisionLivenessResult result = VisionLivenessResult.sessionComplete(probabilities, 4L);
        assertEquals(VisionLivenessResult.Status.LIVE, result.getStatus());
        assertEquals("DENTAL_WHITE", new VisionModelInfo(1, 1, 0.1f).classLabels.get(result.getTopIndex()));
    }

    @Test
    public void rawFrameKeepsProbabilitiesAndTiming() {
        float[] probabilities = new float[12];
        probabilities[0] = 0.9f;
        VisionLivenessResult result = VisionLivenessResult.frameResult(probabilities, 4L);
        probabilities[0] = 0f;
        assertEquals(VisionLivenessResult.Status.LIVE, result.getStatus());
        assertEquals(0.9f, result.getProbabilities()[0], 0f);
        assertEquals(Long.valueOf(4L), result.getInferenceMs());
        result.getProbabilities()[0] = 0f;
        assertEquals(0.9f, result.getProbabilities()[0], 0f);
    }

    @Test
    public void rawFrameErrorHasNoProbability() {
        VisionLivenessResult result = VisionLivenessResult.frameError("invalid crop");
        assertEquals(VisionLivenessResult.Status.ERROR, result.getStatus());
        assertEquals("invalid crop", result.getErrorMessage());
        assertNull(result.getProbabilities());
        assertEquals(Long.valueOf(0L), result.getInferenceMs());
    }

    @Test(expected = IllegalArgumentException.class)
    public void rawFrameRejectsMissingProbabilities() {
        VisionLivenessResult.frameResult(null, 0L);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rawFrameRejectsWrongProbabilityCount() {
        VisionLivenessResult.frameResult(new float[]{1f}, 0L);
    }

    @Test(expected = IllegalArgumentException.class)
    public void sessionRejectsWrongProbabilityCount() {
        VisionLivenessResult.sessionComplete(new float[]{1f}, 0L);
    }

    @Test
    public void irOnlySlotAcceptsNullOrPairedRgbWithoutCopying() {
        assertFalse(VisionEngine.shouldCopyRgb(false, null, null, false));
        assertFalse(VisionEngine.shouldCopyRgb(false, new Object(), new Object(), false));
    }

    @Test(expected = IllegalArgumentException.class)
    public void irOnlySlotRejectsUnpairedRgbInputs() {
        VisionEngine.shouldCopyRgb(false, new Object(), null, false);
    }

    @Test(expected = IllegalArgumentException.class)
    public void irOnlySlotRejectsNullRgbWithEmptyFaceBox() {
        VisionEngine.shouldCopyRgb(false, null, new Object(), true);
    }

    @Test(expected = IllegalArgumentException.class)
    public void irOnlySlotRejectsEmptyRgbFaceBox() {
        VisionEngine.shouldCopyRgb(false, new Object(), new Object(), true);
    }

    @Test
    public void dualSlotCopiesPairedRgbInputs() {
        assertTrue(VisionEngine.shouldCopyRgb(true, new Object(), new Object(), false));
    }

    @Test(expected = IllegalArgumentException.class)
    public void dualSlotRejectsMissingRgbInputs() {
        VisionEngine.shouldCopyRgb(true, null, null, false);
    }

    @Test(expected = IllegalArgumentException.class)
    public void dualSlotRejectsUnpairedRgbInputs() {
        VisionEngine.shouldCopyRgb(true, null, new Object(), false);
    }
}
