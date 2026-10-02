package com.unionbiometrics.vision;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertNull;

public class SessionControllerTest {
    @Test
    public void discardsConfiguredFramesBeforeCollectingAndDeciding() {
        SessionController controller = new SessionController(10, 3);

        for (int i = 0; i < 10; i++) {
            AntiSpoofingResult waiting = controller.prepare();
            assertEquals(AntiSpoofingResult.Status.RUNNING, waiting.status());
            assertEquals(9 - i, waiting.settleRemaining());
            assertEquals(0, waiting.acceptedSamples());
        }
        assertNull(controller.prepare());
        AntiSpoofingResult firstSample = controller.add(classificationAt(0), 11L);
        assertEquals(AntiSpoofingResult.Status.RUNNING, firstSample.status());
        assertEquals(1, firstSample.acceptedSamples());
        assertEquals(Long.valueOf(11L), firstSample.inferenceMs());
        assertEquals(AntiSpoofingResult.Status.RUNNING,
                controller.add(classificationAt(0), 12L).status());
        AntiSpoofingResult decision = controller.add(classificationAt(0), 13L);
        assertEquals(AntiSpoofingResult.Status.LIVE, decision.status());
        assertEquals(3, decision.acceptedSamples());
        assertEquals(Long.valueOf(13L), decision.inferenceMs());
        assertEquals(AntiSpoofingResult.Status.LIVE, controller.prepare().status());
    }

    @Test
    public void failureClearsSamplesAndRestartsSession() {
        SessionController controller = new SessionController(0, 3);
        assertNull(controller.prepare());
        controller.add(classificationAt(0), 1L);

        AntiSpoofingResult error = controller.fail("bad frame");
        assertEquals(AntiSpoofingResult.Status.ERROR, error.status());
        assertEquals(0, error.acceptedSamples());
        assertNull(controller.prepare());
        AntiSpoofingResult restarted = controller.add(classificationAt(0), 1L);
        assertEquals(AntiSpoofingResult.Status.RUNNING, restarted.status());
    }

    @Test
    public void closeRejectsLaterSessions() {
        SessionController controller = new SessionController(10, 3);

        controller.prepare();
        controller.close();

        assertEquals(AntiSpoofingResult.Status.ERROR, controller.prepare().status());
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsNegativeSettleCount() {
        new SessionController(-1, 3);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsZeroSampleCount() {
        new SessionController(10, 0);
    }

    @Test
    public void averagesSamplesAndAcceptsDentalClass() {
        SessionController controller = new SessionController(0, 3);
        assertNull(controller.prepare());
        controller.add(classificationAt(10), 1L);
        controller.add(classificationAt(10), 2L);
        AntiSpoofingResult decision = controller.add(classificationAt(10), 3L);
        assertEquals(AntiSpoofingResult.Status.LIVE, decision.status());
        assertEquals(1f, decision.probability(10), 0f);
        assertEquals("DENTAL_WHITE", AntiSpoofingResult.classLabels()[10]);
    }

    @Test
    public void resetDropsPreviousSamples() {
        SessionController controller = new SessionController(0, 3);
        assertNull(controller.prepare());
        controller.add(classificationAt(0), 1L);
        controller.reset();
        assertNull(controller.prepare());
        AntiSpoofingResult result = controller.add(classificationAt(1), 2L);
        assertArrayEquals(classificationAt(1), result.probabilities(), 0f);
    }

    @Test
    public void averagesDifferentSamples() {
        SessionController controller = new SessionController(0, 3);
        assertNull(controller.prepare());
        controller.add(classificationAt(0), 1L);
        controller.add(classificationAt(1), 2L);
        AntiSpoofingResult decision = controller.add(classificationAt(0), 3L);
        assertEquals(AntiSpoofingResult.Status.LIVE, decision.status());
        assertEquals(2f / 3f, decision.probability(0), 0.0001f);
        assertEquals(1f / 3f, decision.probability(1), 0.0001f);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsNonContractProbabilityCount() {
        AntiSpoofingResult.sessionDecision(new float[]{1f}, 1L, 0, 1);
    }

    private static float[] classificationAt(int index) {
        float[] probabilities = new float[VisionConstants.CLASS_COUNT];
        probabilities[index] = 1f;
        return probabilities;
    }
}
