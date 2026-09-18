package com.unionbiometrics.vision.api;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ProbabilityResultTest {
    @Test
    public void computesTopIndexFromTwelveClassProbabilities() {
        float[] probabilities = new float[ClassLabels.count()];
        probabilities[11] = 0.9f;

        ProbabilityResult result = new ProbabilityResult(probabilities, 2L, 3L);

        assertEquals(11, result.topIndex());
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsNonContractProbabilityCount() {
        new ProbabilityResult(new float[]{1f}, 2L, 3L);
    }
}
