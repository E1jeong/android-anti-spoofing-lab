package com.unionbiometrics.vision.api;

import com.unionbiometrics.vision.VisionSdk;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class VisionClassificationTest {
    @Test
    public void computesTopIndexFromTwelveClassProbabilities() {
        float[] probabilities = new float[VisionSdk.labels().length];
        probabilities[11] = 0.9f;

        VisionClassification result = new VisionClassification(probabilities, 2L, 3L);

        assertEquals(11, result.topIndex());
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsNonContractProbabilityCount() {
        new VisionClassification(new float[]{1f}, 2L, 3L);
    }
}
