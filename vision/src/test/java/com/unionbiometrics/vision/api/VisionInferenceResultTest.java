package com.unionbiometrics.vision.api;

import com.unionbiometrics.vision.VisionSdk;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class VisionInferenceResultTest {
    @Test
    public void convertsPairedInternalResultWithoutExposingMutableProbabilities() {
        VisionInferenceResult result = VisionInferenceResult.success(
                null,
                new VisionClassification(probabilitiesAt(0), 2L, 3L),
                new VisionClassification(probabilitiesAt(1), 5L, 7L),
                7L, 10L);
        float[] probabilities = result.rgbResult().probabilities();
        probabilities[0] = 0f;

        assertTrue(result.successful());
        assertTrue(result.hasPairedResults());
        assertEquals(7L, result.preprocessMs());
        assertEquals(10L, result.inferenceMs());
        assertEquals(1f, result.rgbResult().probability(0), 0.0001f);
        assertArrayEquals(probabilitiesAt(0), result.rgbResult().probabilities(), 0.0001f);
    }

    @Test
    public void exposesExplicitErrorWithoutClassification() {
        VisionInferenceResult result = VisionInferenceResult.error("broken frame");

        assertFalse(result.successful());
        assertEquals("broken frame", result.errorMessage());
        assertNull(result.primaryResult());
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsSuccessfulResultWithoutOutput() {
        VisionInferenceResult.success(null, null, null, 0L, 0L);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsIncompletePairedResult() {
        VisionInferenceResult.success(null,
                new VisionClassification(probabilitiesAt(0), 2L, 3L),
                null, 2L, 3L);
    }

    private static float[] probabilitiesAt(int index) {
        float[] probabilities = new float[VisionSdk.labels().length];
        probabilities[index] = 1f;
        return probabilities;
    }
}
