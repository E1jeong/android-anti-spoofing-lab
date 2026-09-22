package com.unionbiometrics.vision.internal.inference;

import com.unionbiometrics.vision.api.ClassLabels;
import com.unionbiometrics.vision.api.ProbabilityResult;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class FrameResultTest {
    @Test
    public void exposesResultWithoutExposingMutableProbabilities() {
        FrameResult result = FrameResult.success(
                new ProbabilityResult(probabilitiesAt(0)),
                2L, 3L);
        float[] probabilities = result.result().probabilities();
        probabilities[0] = 0f;

        assertTrue(result.successful());
        assertEquals(2L, result.preprocessMs());
        assertEquals(3L, result.inferenceMs());
        assertEquals(1f, result.result().probability(0), 0.0001f);
        assertArrayEquals(probabilitiesAt(0), result.result().probabilities(), 0.0001f);
    }

    @Test
    public void exposesExplicitErrorWithoutClassification() {
        FrameResult result = FrameResult.error("broken frame");

        assertFalse(result.successful());
        assertEquals("broken frame", result.errorMessage());
        assertNull(result.result());
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsSuccessfulResultWithoutOutput() {
        FrameResult.success(null, 0L, 0L);
    }

    private static float[] probabilitiesAt(int index) {
        float[] probabilities = new float[ClassLabels.count()];
        probabilities[index] = 1f;
        return probabilities;
    }
}
