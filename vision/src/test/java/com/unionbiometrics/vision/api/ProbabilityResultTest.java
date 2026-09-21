package com.unionbiometrics.vision.api;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ProbabilityResultTest {
    @Test
    public void computesTopIndexFromTwelveClassProbabilities() {
        float[] probabilities = new float[ClassLabels.count()];
        probabilities[11] = 0.9f;

        ProbabilityResult result = new ProbabilityResult(probabilities);

        assertEquals(11, result.topIndex());
        assertTrue(result.isAccepted());
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsNonContractProbabilityCount() {
        new ProbabilityResult(new float[]{1f});
    }
}
