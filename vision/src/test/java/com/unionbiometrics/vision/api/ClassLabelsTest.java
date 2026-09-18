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
}
