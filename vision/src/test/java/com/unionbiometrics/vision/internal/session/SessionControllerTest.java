package com.unionbiometrics.vision.internal.session;

import com.unionbiometrics.vision.api.ClassLabels;
import com.unionbiometrics.vision.api.ProbabilityResult;
import com.unionbiometrics.vision.api.AntiSpoofingOptions;
import com.unionbiometrics.vision.api.AntiSpoofingResult;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class SessionControllerTest {
    @Test
    public void discardsConfiguredFramesBeforeCollectingAndDeciding() {
        SessionController controller = new SessionController(
                new AntiSpoofingOptions(10, 3, 150_000_000L));

        for (int i = 0; i < 10; i++) {
            assertEquals(AntiSpoofingResult.Status.PENDING, controller.beforeSample().status());
        }
        assertNull(controller.beforeSample());
        AntiSpoofingResult firstSample = controller.add(classificationAt(0), 11L);
        assertEquals(AntiSpoofingResult.Status.PENDING, firstSample.status());
        assertEquals(Long.valueOf(11L), firstSample.inferenceMs());
        assertEquals(AntiSpoofingResult.Status.PENDING,
                controller.add(classificationAt(0), 12L).status());
        AntiSpoofingResult decision = controller.add(classificationAt(0), 13L);
        assertEquals(AntiSpoofingResult.Status.LIVE, decision.status());
        assertEquals(Long.valueOf(13L), decision.inferenceMs());
        assertEquals(AntiSpoofingResult.Status.LIVE, controller.beforeSample().status());
    }

    @Test
    public void failureClearsSamplesAndRestartsSession() {
        SessionController controller = new SessionController(
                new AntiSpoofingOptions(0, 3, 150_000_000L));
        assertNull(controller.beforeSample());
        controller.add(classificationAt(0), 1L);

        assertEquals(AntiSpoofingResult.Status.ERROR, controller.fail("bad frame").status());
        assertNull(controller.beforeSample());
        AntiSpoofingResult restarted = controller.add(classificationAt(0), 1L);
        assertEquals(AntiSpoofingResult.Status.PENDING, restarted.status());
    }

    @Test
    public void closeRejectsLaterSessions() {
        SessionController controller = new SessionController(AntiSpoofingOptions.defaults());

        controller.beforeSample();
        controller.close();

        assertEquals(AntiSpoofingResult.Status.ERROR, controller.beforeSample().status());
    }

    private static ProbabilityResult classificationAt(int index) {
        float[] probabilities = new float[ClassLabels.count()];
        probabilities[index] = 1f;
        return new ProbabilityResult(probabilities);
    }
}
