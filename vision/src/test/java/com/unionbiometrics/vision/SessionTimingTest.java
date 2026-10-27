package com.unionbiometrics.vision;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class SessionTimingTest {
    @Test
    public void sessionDurationKeepsDecisionAndLastInferenceTime() {
        SessionController controller = new SessionController(1, 2);
        assertEquals(VisionLivenessResult.Status.RUNNING, controller.prepare().getStatus());

        assertNull(controller.prepare());
        assertEquals(VisionLivenessResult.Status.RUNNING, controller.add(live(), 4L).getStatus());

        assertNull(controller.prepare());
        VisionLivenessResult decision = controller.add(live(), 5L);
        VisionLivenessResult delivered = new VisionLivenessResult(decision.getStatus(),
                decision.getProbabilities(), decision.getTopIndex(), decision.getInferenceMs(),
                decision.getErrorMessage(), 300L);

        assertEquals(Long.valueOf(300L), delivered.getSessionDurationMs());
        assertEquals(Long.valueOf(5L), delivered.getInferenceMs());
        assertEquals(VisionLivenessResult.Status.LIVE, delivered.getStatus());
        assertArrayEquals(live(), delivered.getProbabilities(), 0f);
        assertNull(decision.getSessionDurationMs());
    }

    @Test
    public void failedSessionKeepsDurationAndErrorDetails() {
        VisionLivenessResult error = new VisionLivenessResult(VisionLivenessResult.Status.ERROR,
                null, -1, null, "bad frame", 50L);
        assertEquals(VisionLivenessResult.Status.ERROR, error.getStatus());
        assertEquals("bad frame", error.getErrorMessage());
        assertNull(error.getProbabilities());
        assertEquals(Long.valueOf(50L), error.getSessionDurationMs());
    }

    @Test
    public void rawFrameAndRawFrameErrorHaveNoSessionDuration() {
        assertNull(VisionLivenessResult.frameResult(live(), 4L).getSessionDurationMs());
        assertNull(VisionLivenessResult.frameError("bad crop").getSessionDurationMs());
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsNegativeSessionDuration() {
        new VisionLivenessResult(VisionLivenessResult.Status.ERROR, null, -1, null, "bad frame", -1L);
    }

    private static float[] live() {
        float[] probabilities = new float[Constants.CLASS_COUNT];
        probabilities[0] = 1f;
        return probabilities;
    }
}
