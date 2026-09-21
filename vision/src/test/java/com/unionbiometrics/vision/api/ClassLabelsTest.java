package com.unionbiometrics.vision.api;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ClassLabelsTest {
    @Test
    public void labelsReturnsDefensiveCopyOfTwelveClassContract() {
        String[] labels = ClassLabels.values();
        labels[0] = "changed";

        assertEquals(12, ClassLabels.count());
        assertEquals("LIVE", ClassLabels.label(0));
        assertEquals("DENTAL_BLACK", ClassLabels.label(11));
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
