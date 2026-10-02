package com.unionbiometrics.vision;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

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
        AntiSpoofingResult result = AntiSpoofingResult.fromInternal(
                SessionResult.error("bad slot"), 0, 0);
        assertEquals(AntiSpoofingResult.Status.ERROR, result.status());
        assertEquals("bad slot", result.errorMessage());
        assertEquals(0f, result.score(), 0f);
        assertNull(result.probabilities());
    }

    @Test
    public void probabilitiesAreDefensive() {
        float[] probabilities = new float[12];
        probabilities[1] = 0.8f;
        AntiSpoofingResult result = AntiSpoofingResult.fromInternal(
                ResultFixture.decision(probabilities), 0, 3);
        probabilities[1] = 0f;
        assertEquals(AntiSpoofingResult.Status.SPOOF, result.status());
        assertEquals(0.8f, result.score(), 0f);
        result.probabilities()[1] = 0f;
        assertEquals(0.8f, result.score(), 0f);
    }

    @Test
    public void acceptedDentalClassKeepsDisplayLabel() {
        float[] probabilities = new float[12];
        probabilities[10] = 1f;
        AntiSpoofingResult result = AntiSpoofingResult.fromInternal(
                ResultFixture.decision(probabilities), 0, 3);
        assertEquals(AntiSpoofingResult.Status.LIVE, result.status());
        assertEquals("DENTAL_WHITE", result.displayLabel());
    }

    @Test
    public void rawFrameKeepsProbabilitiesAndTiming() {
        float[] probabilities = new float[12];
        probabilities[0] = 0.9f;
        AntiSpoofingResult result = AntiSpoofingResult.fromFrame(
                ResultFixture.frame(probabilities));
        assertEquals(AntiSpoofingResult.Status.LIVE, result.status());
        assertEquals(0.9f, result.probability(0), 0f);
        assertEquals(2L, result.preprocessMs());
        assertEquals(Long.valueOf(4L), result.inferenceMs());
    }

    @Test
    public void rawFrameErrorHasNoProbability() {
        AntiSpoofingResult result = AntiSpoofingResult.fromFrame(
                ResultFixture.frameError("invalid crop"));
        assertEquals(AntiSpoofingResult.Status.ERROR, result.status());
        assertEquals("invalid crop", result.errorMessage());
        assertNull(result.probabilities());
    }
}
