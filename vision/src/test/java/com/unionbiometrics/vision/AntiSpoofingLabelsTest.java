package com.unionbiometrics.vision;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertArrayEquals;

public class AntiSpoofingLabelsTest {
    @Test
    public void labelsReturnsDefensiveCopyOfTwelveClassContract() {
        String[] labels = AntiSpoofingResult.getClassLabels();
        assertArrayEquals(new String[]{
                "LIVE", "PRINT", "PICTURE", "MASK", "DISPLAY", "PMASK",
                "CURVED_PRINT", "CURVED_MASK", "CURVED_PICTURE", "CURVED_PMASK",
                "DENTAL_WHITE", "DENTAL_BLACK"
        }, labels);
        labels[0] = "changed";

        assertEquals(12, Constants.CLASS_COUNT);
        assertEquals("LIVE", AntiSpoofingResult.getClassLabels()[0]);
        assertEquals("DENTAL_BLACK", AntiSpoofingResult.getClassLabels()[11]);
    }

    @Test
    public void dentalMaskClassesAreAccepted() {
        assertEquals(true, AntiSpoofingResult.isLiveClass(0));
        assertEquals(true, AntiSpoofingResult.isLiveClass(10));
        assertEquals(true, AntiSpoofingResult.isLiveClass(11));
        assertEquals(false, AntiSpoofingResult.isLiveClass(1));
    }
}
