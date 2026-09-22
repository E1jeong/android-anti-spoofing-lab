package com.unionbiometrics.vision.internal.model;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class ClassifierTest {
    @Test
    public void irLuminanceUsesEveryColorChannel() {
        int nonGrayPixel = 0xFF0A64C8;

        assertEquals(88, Classifier.irLuminance(nonGrayPixel));
        assertEquals(54, Classifier.irLuminance(0xFFFF0000));
        assertEquals(182, Classifier.irLuminance(0xFF00FF00));
        assertEquals(18, Classifier.irLuminance(0xFF0000FF));
    }

    @Test
    public void irLuminancePreservesGrayPixel() {
        assertEquals(137, Classifier.irLuminance(0xFF898989));
    }
}
