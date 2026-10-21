package com.unionbiometrics.vision;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertArrayEquals;

public class AntiSpoofingLabelsTest {
    @Test
    public void labelsReturnsDefensiveCopyOfTwentyFourClassContract() {
        String[] labels = AntiSpoofingResult.classLabels();
        assertArrayEquals(new String[]{
                "LIVE", "PRINT", "PICTURE", "MASK", "DISPLAY", "PMASK",
                "CURVED_PRINT", "CURVED_MASK", "CURVED_PICTURE", "CURVED_PMASK",
                "DENTAL_WHITE", "DENTAL_BLACK",
                "LIVE_OUTDOOR", "PRINT_OUTDOOR", "PICTURE_OUTDOOR", "MASK_OUTDOOR",
                "DISPLAY_OUTDOOR", "PMASK_OUTDOOR", "CURVED_PRINT_OUTDOOR", "CURVED_MASK_OUTDOOR",
                "CURVED_PICTURE_OUTDOOR", "CURVED_PMASK_OUTDOOR",
                "DENTAL_WHITE_OUTDOOR", "DENTAL_BLACK_OUTDOOR"
        }, labels);
        labels[0] = "changed";

        assertEquals(24, VisionConstants.CLASS_COUNT);
        assertEquals("LIVE", AntiSpoofingResult.classLabels()[0]);
        assertEquals("DENTAL_BLACK", AntiSpoofingResult.classLabels()[11]);
    }

    @Test
    public void displayLabelShortensCurvedClassNames() {
        assertEquals("C PRINT", AntiSpoofingResult.classDisplayLabel(6));
        assertEquals("C PMASK", AntiSpoofingResult.classDisplayLabel(9));
        assertEquals("DENTAL_WHITE", AntiSpoofingResult.classDisplayLabel(10));
        assertEquals("C PRINT OUT", AntiSpoofingResult.classDisplayLabel(18));
        assertEquals("LIVE OUT", AntiSpoofingResult.classDisplayLabel(12));
    }

    @Test
    public void dentalMaskClassesAreAccepted() {
        assertEquals(true, AntiSpoofingResult.isAcceptedClass(0));
        assertEquals(true, AntiSpoofingResult.isAcceptedClass(10));
        assertEquals(true, AntiSpoofingResult.isAcceptedClass(11));
        assertEquals(false, AntiSpoofingResult.isAcceptedClass(1));
        assertEquals(true, AntiSpoofingResult.isAcceptedClass(12));
        assertEquals(true, AntiSpoofingResult.isAcceptedClass(22));
        assertEquals(true, AntiSpoofingResult.isAcceptedClass(23));
        assertEquals(false, AntiSpoofingResult.isAcceptedClass(13));
        assertEquals(false, AntiSpoofingResult.isAcceptedClass(24));
    }
    @Test
    public void frameDecisionsCoverBothEnvironments() {
        for (int index = 0; index < 24; index++) {
            float[] probabilities = new float[24];
            probabilities[index] = 1f;
            boolean accepted = index == 0 || index == 10 || index == 11
                    || index == 12 || index == 22 || index == 23;
            assertEquals(accepted, AntiSpoofingResult.frame(probabilities, 0L, 0L).isAccepted());
        }
    }

    @Test
    public void mergesEnvironmentProbabilitiesForDisplay() {
        float[] probabilities = new float[24];
        probabilities[0] = 0.2f;
        probabilities[12] = 0.1f;
        probabilities[1] = 0.2f;
        probabilities[13] = 0.5f;
        AntiSpoofingResult result = AntiSpoofingResult.frame(probabilities, 0L, 0L);

        assertEquals(0.3f, result.mergedProbability(0), 0.0001f);
        assertEquals(0.7f, result.mergedProbability(1), 0.0001f);
        assertEquals(1, result.mergedTopIndex());
        assertEquals(13, result.topIndex());
        assertEquals(24, result.probabilities().length);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsLegacyTwelveProbabilityVector() {
        AntiSpoofingResult.frame(new float[12], 0L, 0L);
    }
}
