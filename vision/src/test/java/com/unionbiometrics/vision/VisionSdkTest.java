package com.unionbiometrics.vision;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class VisionSdkTest {
    @Test
    public void labelsReturnsDefensiveCopyOfTwelveClassContract() {
        String[] labels = VisionSdk.labels();
        labels[0] = "changed";

        assertEquals(12, VisionSdk.labels().length);
        assertEquals("LIVE", VisionSdk.labels()[0]);
        assertEquals("DENTAL_BLACK", VisionSdk.labels()[11]);
    }
}
