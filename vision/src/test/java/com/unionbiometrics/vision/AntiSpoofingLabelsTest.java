package com.unionbiometrics.vision;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertThrows;

public class AntiSpoofingLabelsTest {
    @Test
    public void modelInfoProvidesImmutableClassOrderBeforeLoading() {
        List<String> labels = new ModelInfo(1).classLabels;
        assertArrayEquals(new String[]{
                "LIVE", "PRINT", "PICTURE", "MASK", "DISPLAY", "PMASK",
                "CURVED_PRINT", "CURVED_MASK", "CURVED_PICTURE", "CURVED_PMASK",
                "DENTAL_WHITE", "DENTAL_BLACK"
        }, labels.toArray(new String[0]));
        assertThrows(UnsupportedOperationException.class, () -> labels.set(0, "changed"));

        assertEquals(12, Constants.CLASS_COUNT);
        assertEquals("LIVE", new ModelInfo(1).classLabels.get(0));
        assertEquals("DENTAL_BLACK", new ModelInfo(1).classLabels.get(11));
    }

    @Test
    public void dentalMaskClassesAreAccepted() {
        assertEquals(true, AntiSpoofingResult.isLiveClass(0));
        assertEquals(true, AntiSpoofingResult.isLiveClass(10));
        assertEquals(true, AntiSpoofingResult.isLiveClass(11));
        assertEquals(false, AntiSpoofingResult.isLiveClass(1));
    }
}
