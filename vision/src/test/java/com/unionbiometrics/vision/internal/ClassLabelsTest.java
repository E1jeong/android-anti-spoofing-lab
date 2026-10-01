package com.unionbiometrics.vision.internal;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertArrayEquals;

public class ClassLabelsTest {
    @Test
    public void labelsReturnsDefensiveCopyOfTwelveClassContract() {
        String[] labels = ClassLabels.values();
        assertArrayEquals(new String[]{
                "LIVE", "PRINT", "PICTURE", "MASK", "DISPLAY", "PMASK",
                "CURVED_PRINT", "CURVED_MASK", "CURVED_PICTURE", "CURVED_PMASK",
                "DENTAL_WHITE", "DENTAL_BLACK"
        }, labels);
        labels[0] = "changed";

        assertEquals(12, ClassLabels.count());
        assertEquals("LIVE", ClassLabels.values()[0]);
        assertEquals("DENTAL_BLACK", ClassLabels.values()[11]);
    }

    @Test
    public void displayLabelShortensCurvedClassNames() {
        assertEquals("C PRINT", ClassLabels.displayLabel(6));
        assertEquals("C PMASK", ClassLabels.displayLabel(9));
        assertEquals("DENTAL_WHITE", ClassLabels.displayLabel(10));
    }

    @Test
    public void dentalMaskClassesAreAccepted() {
        assertEquals(true, ClassLabels.isAcceptedClass(0));
        assertEquals(true, ClassLabels.isAcceptedClass(10));
        assertEquals(true, ClassLabels.isAcceptedClass(11));
        assertEquals(false, ClassLabels.isAcceptedClass(1));
    }
}
