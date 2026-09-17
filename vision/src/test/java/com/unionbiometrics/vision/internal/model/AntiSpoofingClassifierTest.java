package com.unionbiometrics.vision.internal.model;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class AntiSpoofingClassifierTest {
    @Test
    public void irLuminanceUsesEveryColorChannel() {
        int nonGrayPixel = 0xFF0A64C8;

        assertEquals(88, AntiSpoofingClassifier.irLuminance(nonGrayPixel));
        assertEquals(54, AntiSpoofingClassifier.irLuminance(0xFFFF0000));
        assertEquals(182, AntiSpoofingClassifier.irLuminance(0xFF00FF00));
        assertEquals(18, AntiSpoofingClassifier.irLuminance(0xFF0000FF));
    }

    @Test
    public void irLuminancePreservesGrayPixel() {
        assertEquals(137, AntiSpoofingClassifier.irLuminance(0xFF898989));
    }
}
