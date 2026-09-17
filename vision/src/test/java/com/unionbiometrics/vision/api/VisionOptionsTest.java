package com.unionbiometrics.vision.api;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class VisionOptionsTest {
    @Test
    public void defaultsMatchVerifiedProductSequence() {
        VisionOptions options = VisionOptions.defaults();

        assertEquals(400L, options.irSettleMs());
        assertEquals(3, options.sampleCount());
        assertEquals(150_000_000L, options.maxPairDeltaNs());
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsZeroSampleCount() {
        new VisionOptions(400L, 0, 150_000_000L);
    }
}
