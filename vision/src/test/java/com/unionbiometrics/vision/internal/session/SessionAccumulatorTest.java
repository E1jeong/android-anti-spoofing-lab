package com.unionbiometrics.vision.internal.session;

import com.unionbiometrics.vision.api.ClassLabels;
import com.unionbiometrics.vision.api.AntiSpoofingResult;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public class SessionAccumulatorTest {
    @Test
    public void averagesThreeProbabilityVectorsBeforeLiveDecision() {
        SessionAccumulator accumulator = new SessionAccumulator(3);

        assertEquals(AntiSpoofingResult.Status.PENDING, accumulator.add(result(0.7f, 0.3f), 11L).status());
        assertEquals(AntiSpoofingResult.Status.PENDING, accumulator.add(result(0.8f, 0.2f), 12L).status());
        AntiSpoofingResult decision = accumulator.add(result(0.9f, 0.1f), 13L);

        assertEquals(AntiSpoofingResult.Status.LIVE, decision.status());
        assertEquals(0, decision.result().topIndex());
        assertEquals(0.8f, decision.result().probability(0), 0.0001f);
        assertEquals(Long.valueOf(13L), decision.inferenceMs());
    }

    @Test
    public void dentalClassIsAcceptedAsLive() {
        SessionAccumulator accumulator = new SessionAccumulator(3);

        accumulator.add(resultAt(10), 1L);
        accumulator.add(resultAt(10), 1L);
        AntiSpoofingResult decision = accumulator.add(resultAt(10), 1L);

        assertEquals(AntiSpoofingResult.Status.LIVE, decision.status());
        assertEquals("DENTAL_WHITE", decision.result().topLabel());
    }

    @Test
    public void resetDropsPreviousSamples() {
        SessionAccumulator accumulator = new SessionAccumulator(3);
        accumulator.add(result(0.9f, 0.1f), 1L);
        accumulator.reset();

        AntiSpoofingResult result = accumulator.add(result(0.1f, 0.9f), 1L);

        assertArrayEquals(result(0.1f, 0.9f), result.result().probabilities(), 0.0001f);
    }

    private static float[] result(float live, float print) {
        float[] probabilities = new float[ClassLabels.count()];
        probabilities[0] = live;
        probabilities[1] = print;
        return probabilities;
    }

    private static float[] resultAt(int index) {
        float[] probabilities = new float[ClassLabels.count()];
        probabilities[index] = 1f;
        return probabilities;
    }
}
