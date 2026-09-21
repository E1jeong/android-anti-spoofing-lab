package com.unionbiometrics.vision.internal.session;

import com.unionbiometrics.vision.api.ClassLabels;
import com.unionbiometrics.vision.api.ProbabilityResult;
import com.unionbiometrics.vision.api.AntiSpoofingOptions;
import com.unionbiometrics.vision.api.AntiSpoofingResult;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class SessionControllerTest {
    @Test
    public void runsSettleCollectAndTerminalSequence() {
        FakeClock clock = new FakeClock();
        List<Boolean> illumination = new ArrayList<>();
        SessionController controller = new SessionController(
                new AntiSpoofingOptions(400L, 3, 150_000_000L), illumination::add, clock::now);

        assertEquals(AntiSpoofingResult.Status.SETTLING, controller.start().status());
        clock.nowMs = 399L;
        assertEquals(AntiSpoofingResult.Status.SETTLING, controller.beforeSample().status());
        clock.nowMs = 400L;
        assertNull(controller.beforeSample());
        assertEquals(AntiSpoofingResult.Status.COLLECTING, controller.add(classificationAt(0), 2L, 3L).status());
        assertEquals(AntiSpoofingResult.Status.COLLECTING, controller.add(classificationAt(0), 2L, 3L).status());
        assertEquals(AntiSpoofingResult.Status.LIVE, controller.add(classificationAt(0), 2L, 3L).status());
        assertEquals(AntiSpoofingResult.Status.LIVE, controller.beforeSample().status());
        assertEquals(List.of(true), illumination);
    }

    @Test
    public void resetAndCloseAlwaysRequestIlluminationOff() {
        FakeClock clock = new FakeClock();
        List<Boolean> illumination = new ArrayList<>();
        SessionController controller = new SessionController(
                AntiSpoofingOptions.defaults(), illumination::add, clock::now);

        controller.reset();
        controller.close();

        assertEquals(List.of(false, false), illumination);
    }

    @Test
    public void failedIlluminationEnableRequestsCleanup() {
        List<Boolean> illumination = new ArrayList<>();
        SessionController controller = new SessionController(
                AntiSpoofingOptions.defaults(), enabled -> {
                    illumination.add(enabled);
                    if (enabled) throw new IllegalStateException("driver failure");
                }, () -> 0L);

        AntiSpoofingResult result = controller.start();

        assertEquals(AntiSpoofingResult.Status.ERROR, result.status());
        assertEquals("IR illumination failed: driver failure", result.errorMessage());
        assertEquals(List.of(true, false), illumination);
    }

    @Test
    public void failureClearsSamplesAndRequestsIlluminationOff() {
        FakeClock clock = new FakeClock();
        List<Boolean> illumination = new ArrayList<>();
        SessionController controller = new SessionController(
                new AntiSpoofingOptions(0L, 3, 150_000_000L), illumination::add, clock::now);
        controller.start();
        controller.add(classificationAt(0), 2L, 3L);

        assertEquals(AntiSpoofingResult.Status.ERROR, controller.fail("bad frame").status());
        assertEquals(AntiSpoofingResult.Status.SETTLING, controller.beforeSample().status());
        assertNull(controller.beforeSample());
        AntiSpoofingResult restarted = controller.add(classificationAt(0), 2L, 3L);
        assertEquals(AntiSpoofingResult.Status.COLLECTING, restarted.status());
        assertEquals(1, restarted.sampleCount());
        assertEquals(List.of(true, false, true), illumination);
    }

    private static ProbabilityResult classificationAt(int index) {
        float[] probabilities = new float[ClassLabels.count()];
        probabilities[index] = 1f;
        return new ProbabilityResult(probabilities);
    }

    private static final class FakeClock {
        long nowMs;

        long now() {
            return nowMs;
        }
    }
}
