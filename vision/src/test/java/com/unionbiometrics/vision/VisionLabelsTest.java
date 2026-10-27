package com.unionbiometrics.vision;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertThrows;

public class VisionLabelsTest {
    @Test
    public void modelInfoProvidesImmutableClassOrderBeforeLoading() {
        List<String> labels = new VisionModelInfo(1).classLabels;
        assertArrayEquals(new String[]{
                "LIVE", "PRINT", "PICTURE", "MASK", "DISPLAY", "PMASK",
                "CURVED_PRINT", "CURVED_MASK", "CURVED_PICTURE", "CURVED_PMASK",
                "DENTAL_WHITE", "DENTAL_BLACK"
        }, labels.toArray(new String[0]));
        assertThrows(UnsupportedOperationException.class, () -> labels.set(0, "changed"));

        assertEquals(12, Constants.CLASS_COUNT);
        assertEquals("LIVE", new VisionModelInfo(1).classLabels.get(0));
        assertEquals("DENTAL_BLACK", new VisionModelInfo(1).classLabels.get(11));
    }

    @Test
    public void dentalMaskClassesAreAccepted() {
        assertEquals(true, VisionLivenessResult.isLiveClass(0));
        assertEquals(true, VisionLivenessResult.isLiveClass(10));
        assertEquals(true, VisionLivenessResult.isLiveClass(11));
        assertEquals(false, VisionLivenessResult.isLiveClass(1));
    }
}
