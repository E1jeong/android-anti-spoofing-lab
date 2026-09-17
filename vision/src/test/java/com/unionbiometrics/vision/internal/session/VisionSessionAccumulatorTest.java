package com.unionbiometrics.vision.internal.session;

import com.unionbiometrics.vision.VisionSdk;
import com.unionbiometrics.vision.api.VisionResult;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public class VisionSessionAccumulatorTest {
    @Test
    public void averagesThreeProbabilityVectorsBeforeLiveDecision() {
        VisionSessionAccumulator accumulator = new VisionSessionAccumulator(3);

        assertEquals(VisionResult.Status.COLLECTING, accumulator.add(result(0.7f, 0.3f), 2L, 3L).status());
        assertEquals(VisionResult.Status.COLLECTING, accumulator.add(result(0.8f, 0.2f), 2L, 3L).status());
        VisionResult decision = accumulator.add(result(0.9f, 0.1f), 2L, 3L);

        assertEquals(VisionResult.Status.LIVE, decision.status());
        assertEquals(3, decision.sampleCount());
        assertEquals(0, decision.topIndex());
        assertEquals(0.8f, decision.probabilities()[0], 0.0001f);
    }

    @Test
    public void dentalClassIsAcceptedAsLive() {
        VisionSessionAccumulator accumulator = new VisionSessionAccumulator(3);

        accumulator.add(resultAt(10), 2L, 3L);
        accumulator.add(resultAt(10), 2L, 3L);
        VisionResult decision = accumulator.add(resultAt(10), 2L, 3L);

        assertEquals(VisionResult.Status.LIVE, decision.status());
        assertEquals("DENTAL_WHITE", decision.topLabel());
    }

    @Test
    public void resetDropsPreviousSamples() {
        VisionSessionAccumulator accumulator = new VisionSessionAccumulator(3);
        accumulator.add(result(0.9f, 0.1f), 2L, 3L);
        accumulator.reset();

        VisionResult result = accumulator.add(result(0.1f, 0.9f), 2L, 3L);

        assertEquals(1, result.sampleCount());
        assertArrayEquals(result(0.1f, 0.9f), result.probabilities(), 0.0001f);
    }

    private static float[] result(float live, float print) {
        float[] probabilities = new float[VisionSdk.labels().length];
        probabilities[0] = live;
        probabilities[1] = print;
        return probabilities;
    }

    private static float[] resultAt(int index) {
        float[] probabilities = new float[VisionSdk.labels().length];
        probabilities[index] = 1f;
        return probabilities;
    }
}
