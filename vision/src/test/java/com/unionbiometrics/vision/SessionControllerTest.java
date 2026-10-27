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
            VisionLivenessResult waiting = controller.prepare();
            assertEquals(VisionLivenessResult.Status.RUNNING, waiting.getStatus());
            assertNull(waiting.getProbabilities());
            assertNull(waiting.getInferenceMs());
        }
        assertNull(controller.prepare());
        VisionLivenessResult firstSample = controller.add(classificationAt(0), 11L);
        assertEquals(VisionLivenessResult.Status.RUNNING, firstSample.getStatus());
        assertEquals(Long.valueOf(11L), firstSample.getInferenceMs());
        assertEquals(VisionLivenessResult.Status.RUNNING,
                controller.add(classificationAt(0), 12L).getStatus());
        VisionLivenessResult decision = controller.add(classificationAt(0), 13L);
        assertEquals(VisionLivenessResult.Status.LIVE, decision.getStatus());
        assertEquals(Long.valueOf(13L), decision.getInferenceMs());
        assertEquals(VisionLivenessResult.Status.LIVE, controller.prepare().getStatus());
    }

    @Test
    public void failureClearsSamplesAndRestartsSession() {
        SessionController controller = new SessionController(0, 3);
        assertNull(controller.prepare());
        controller.add(classificationAt(0), 1L);

        VisionLivenessResult error = controller.fail("bad frame");
        assertEquals(VisionLivenessResult.Status.ERROR, error.getStatus());
        assertNull(error.getProbabilities());
        assertNull(controller.prepare());
        VisionLivenessResult restarted = controller.add(classificationAt(0), 1L);
        assertEquals(VisionLivenessResult.Status.RUNNING, restarted.getStatus());
        assertEquals(VisionLivenessResult.Status.RUNNING,
                controller.add(classificationAt(0), 2L).getStatus());
        assertEquals(VisionLivenessResult.Status.LIVE,
                controller.add(classificationAt(0), 3L).getStatus());
    }

    @Test
    public void closeRejectsLaterSessions() {
        SessionController controller = new SessionController(10, 3);

        controller.prepare();
        controller.close();

        assertEquals(VisionLivenessResult.Status.ERROR, controller.prepare().getStatus());
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
        VisionLivenessResult decision = controller.add(classificationAt(10), 3L);
        assertEquals(VisionLivenessResult.Status.LIVE, decision.getStatus());
        assertEquals(1f, decision.getProbabilities()[10], 0f);
        assertEquals("DENTAL_WHITE", new VisionModelInfo(1, 1, 0.1f).classLabels.get(10));
    }

    @Test
    public void resetDropsPreviousSamples() {
        SessionController controller = new SessionController(0, 3);
        assertNull(controller.prepare());
        controller.add(classificationAt(0), 1L);
        controller.reset();
        assertNull(controller.prepare());
        VisionLivenessResult result = controller.add(classificationAt(1), 2L);
        assertArrayEquals(classificationAt(1), result.getProbabilities(), 0f);
    }

    @Test
    public void averagesDifferentSamples() {
        SessionController controller = new SessionController(0, 3);
        assertNull(controller.prepare());
        controller.add(classificationAt(0), 1L);
        controller.add(classificationAt(1), 2L);
        VisionLivenessResult decision = controller.add(classificationAt(0), 3L);
        assertEquals(VisionLivenessResult.Status.LIVE, decision.getStatus());
        assertEquals(2f / 3f, decision.getProbabilities()[0], 0.0001f);
        assertEquals(1f / 3f, decision.getProbabilities()[1], 0.0001f);
    }

    @Test
    public void rejectsLiveClassesBelowMinimumScoreAndKeepsClassification() {
        for (int liveIndex : new int[]{0, 10, 11}) {
            SessionController controller = new SessionController(0, 1);
            assertNull(controller.prepare());
            float[] probabilities = probabilitiesWithTop(liveIndex, 0.3999f);
            VisionLivenessResult decision = controller.add(probabilities, 3L);

            assertEquals(VisionLivenessResult.Status.SPOOF, decision.getStatus());
            assertEquals(liveIndex, decision.getTopIndex());
            assertEquals(0.3999f, decision.getScore(), 0f);
            assertArrayEquals(probabilities, decision.getProbabilities(), 0f);
            assertEquals(VisionLivenessResult.Status.SPOOF, controller.prepare().getStatus());
        }
    }

    @Test
    public void acceptsLiveClassesAtOrAboveMinimumScore() {
        for (int liveIndex : new int[]{0, 10, 11}) {
            for (float score : new float[]{0.4f, 0.4001f}) {
                SessionController controller = new SessionController(0, 1);
                assertNull(controller.prepare());
                VisionLivenessResult decision = controller.add(probabilitiesWithTop(liveIndex, score), 3L);

                assertEquals(VisionLivenessResult.Status.LIVE, decision.getStatus());
            }
        }
    }

    @Test
    public void appliesMinimumScoreToSessionAverage() {
        SessionController controller = new SessionController(0, 2);
        assertNull(controller.prepare());
        assertEquals(VisionLivenessResult.Status.RUNNING,
                controller.add(probabilitiesWithTop(0, 0.6f), 1L).getStatus());
        VisionLivenessResult decision = controller.add(probabilitiesWithTop(0, 0.1f), 2L);

        assertEquals(VisionLivenessResult.Status.SPOOF, decision.getStatus());
        assertEquals(0, decision.getTopIndex());
        assertEquals(0.35f, decision.getScore(), 0.0001f);

        controller.reset();
        assertNull(controller.prepare());
        controller.add(probabilitiesWithTop(0, 0.2f), 1L);
        decision = controller.add(probabilitiesWithTop(0, 0.6f), 2L);
        assertEquals(VisionLivenessResult.Status.LIVE, decision.getStatus());
        assertEquals(0.4f, decision.getScore(), 0f);
    }

    @Test
    public void rejectsSpoofClassAboveMinimumScore() {
        SessionController controller = new SessionController(0, 1);
        assertNull(controller.prepare());
        VisionLivenessResult decision = controller.add(probabilitiesWithTop(1, 0.9f), 1L);

        assertEquals(VisionLivenessResult.Status.SPOOF, decision.getStatus());
    }

    @Test
    public void rawFrameDoesNotApplySessionMinimumScore() {
        VisionLivenessResult frame = VisionLivenessResult.frameResult(
                probabilitiesWithTop(0, 0.3999f), 2L);

        assertEquals(VisionLivenessResult.Status.LIVE, frame.getStatus());
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsNonContractProbabilityCount() {
        VisionLivenessResult.sessionComplete(new float[]{1f}, 1L);
    }

    private static float[] classificationAt(int index) {
        float[] probabilities = new float[Constants.CLASS_COUNT];
        probabilities[index] = 1f;
        return probabilities;
    }

    private static float[] probabilitiesWithTop(int index, float score) {
        float[] probabilities = new float[Constants.CLASS_COUNT];
        for (int i = 0; i < probabilities.length; i++) {
            probabilities[i] = i == index ? score : (1f - score) / (probabilities.length - 1);
        }
        return probabilities;
    }
}
