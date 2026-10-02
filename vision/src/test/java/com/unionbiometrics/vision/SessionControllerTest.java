package com.unionbiometrics.vision;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class SessionControllerTest {
    @Test
    public void discardsConfiguredFramesBeforeCollectingAndDeciding() {
        SessionController controller = new SessionController(10, 3);

        for (int i = 0; i < 10; i++) {
            assertEquals(SessionResult.Status.PENDING, controller.beforeSample().status());
            assertEquals(9 - i, controller.settleRemaining());
            assertEquals(0, controller.acceptedSamples());
        }
        assertNull(controller.beforeSample());
        SessionResult firstSample = controller.add(classificationAt(0), 11L);
        assertEquals(SessionResult.Status.PENDING, firstSample.status());
        assertEquals(1, controller.acceptedSamples());
        assertEquals(Long.valueOf(11L), firstSample.inferenceMs());
        assertEquals(SessionResult.Status.PENDING,
                controller.add(classificationAt(0), 12L).status());
        SessionResult decision = controller.add(classificationAt(0), 13L);
        assertEquals(SessionResult.Status.LIVE, decision.status());
        assertEquals(3, controller.acceptedSamples());
        assertEquals(Long.valueOf(13L), decision.inferenceMs());
        assertEquals(SessionResult.Status.LIVE, controller.beforeSample().status());
    }

    @Test
    public void failureClearsSamplesAndRestartsSession() {
        SessionController controller = new SessionController(0, 3);
        assertNull(controller.beforeSample());
        controller.add(classificationAt(0), 1L);

        assertEquals(SessionResult.Status.ERROR, controller.fail("bad frame").status());
        assertEquals(0, controller.acceptedSamples());
        assertNull(controller.beforeSample());
        SessionResult restarted = controller.add(classificationAt(0), 1L);
        assertEquals(SessionResult.Status.PENDING, restarted.status());
    }

    @Test
    public void closeRejectsLaterSessions() {
        SessionController controller = new SessionController(10, 3);

        controller.beforeSample();
        controller.close();

        assertEquals(SessionResult.Status.ERROR, controller.beforeSample().status());
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsNegativeSettleCount() {
        new SessionController(-1, 3);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsZeroSampleCount() {
        new SessionController(10, 0);
    }

    private static ProbabilityResult classificationAt(int index) {
        float[] probabilities = new float[ClassLabels.count()];
        probabilities[index] = 1f;
        return new ProbabilityResult(probabilities);
    }
}
