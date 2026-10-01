package com.unionbiometrics.vision;

import org.junit.Test;

import com.unionbiometrics.vision.internal.ResultFixture;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public final class FacadeContractTest {
    @Test(expected = IllegalArgumentException.class)
    public void optionsRejectNegativeSlot() {
        new AntiSpoofingEngine.Options(-1, 10, 3);
    }

    @Test(expected = IllegalArgumentException.class)
    public void optionsRejectZeroSamples() {
        new AntiSpoofingEngine.Options(0, 10, 0);
    }

    @Test
    public void defaultOptionsPreserveSessionCounts() {
        AntiSpoofingEngine.Options options = AntiSpoofingEngine.Options.defaults();
        assertEquals(0, options.slotIndex());
        assertEquals(10, options.irSettleFrameCount());
        assertEquals(3, options.sampleCount());
        assertEquals(AntiSpoofingEngine.Options.Mode.SYNC, options.mode());
        assertEquals(AntiSpoofingEngine.Options.Mode.LIVE,
                options.live(null).withSlotIndex(1).mode());
    }

    @Test(expected = IllegalArgumentException.class)
    public void irFrameRejectsMissingBitmap() {
        AntiSpoofingEngine.Frame.ir(null, null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void dualFrameRejectsMissingRgb() {
        AntiSpoofingEngine.Frame.dual(null, null, null, null, 1L, 1L);
    }

    @Test
    public void errorIsDistinctFromSpoof() {
        AntiSpoofingResult result = AntiSpoofingResult.fromInternal(
                com.unionbiometrics.vision.internal.SessionResult.error("bad slot"), 0, 0);
        assertEquals(AntiSpoofingResult.Status.ERROR, result.status());
        assertEquals("bad slot", result.errorMessage());
        assertEquals(0f, result.score(), 0f);
        assertNull(result.probabilities());
    }

    @Test
    public void attackAndProbabilitiesAreDefensive() {
        float[] probabilities = new float[12];
        probabilities[1] = 0.8f;
        AntiSpoofingResult result = AntiSpoofingResult.fromInternal(
                ResultFixture.decision(probabilities), 0, 3);
        probabilities[1] = 0f;
        assertEquals(AntiSpoofingResult.Status.SPOOF, result.status());
        assertEquals(AntiSpoofingResult.Attack.PRINT, result.attack());
        assertEquals(0.8f, result.score(), 0f);
        result.probabilities()[1] = 0f;
        assertEquals(0.8f, result.score(), 0f);
    }

    @Test
    public void acceptedDentalClassHasNoAttack() {
        float[] probabilities = new float[12];
        probabilities[10] = 1f;
        AntiSpoofingResult result = AntiSpoofingResult.fromInternal(
                ResultFixture.decision(probabilities), 0, 3);
        assertEquals(AntiSpoofingResult.Status.LIVE, result.status());
        assertEquals(AntiSpoofingResult.Attack.NONE, result.attack());
        assertEquals("DENTAL_WHITE", result.displayLabel());
    }
}
