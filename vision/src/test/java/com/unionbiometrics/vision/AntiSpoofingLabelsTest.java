package com.unionbiometrics.vision;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertArrayEquals;

public class AntiSpoofingLabelsTest {
    @Test
    public void labelsReturnsDefensiveCopyOfTwelveClassContract() {
        String[] labels = AntiSpoofingResult.classLabels();
        assertArrayEquals(new String[]{
                "LIVE", "PRINT", "PICTURE", "MASK", "DISPLAY", "PMASK",
                "CURVED_PRINT", "CURVED_MASK", "CURVED_PICTURE", "CURVED_PMASK",
                "DENTAL_WHITE", "DENTAL_BLACK"
        }, labels);
        labels[0] = "changed";

        assertEquals(12, VisionConstants.CLASS_COUNT);
        assertEquals("LIVE", AntiSpoofingResult.classLabels()[0]);
        assertEquals("DENTAL_BLACK", AntiSpoofingResult.classLabels()[11]);
    }

    @Test
    public void displayLabelShortensCurvedClassNames() {
        assertEquals("C PRINT", AntiSpoofingResult.classDisplayLabel(6));
        assertEquals("C PMASK", AntiSpoofingResult.classDisplayLabel(9));
        assertEquals("DENTAL_WHITE", AntiSpoofingResult.classDisplayLabel(10));
    }

    @Test
    public void dentalMaskClassesAreAccepted() {
        assertEquals(true, AntiSpoofingResult.isAcceptedClass(0));
        assertEquals(true, AntiSpoofingResult.isAcceptedClass(10));
        assertEquals(true, AntiSpoofingResult.isAcceptedClass(11));
        assertEquals(false, AntiSpoofingResult.isAcceptedClass(1));
    }
}
