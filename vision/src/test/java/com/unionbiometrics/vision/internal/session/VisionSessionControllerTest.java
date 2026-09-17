package com.unionbiometrics.vision.internal.session;

import com.unionbiometrics.vision.VisionSdk;
import com.unionbiometrics.vision.api.VisionClassification;
import com.unionbiometrics.vision.api.VisionOptions;
import com.unionbiometrics.vision.api.VisionResult;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class VisionSessionControllerTest {
    @Test
    public void runsSettleCollectAndTerminalSequence() {
        FakeClock clock = new FakeClock();
        List<Boolean> illumination = new ArrayList<>();
        VisionSessionController controller = new VisionSessionController(
                new VisionOptions(400L, 3, 150_000_000L), illumination::add, clock::now);

        assertEquals(VisionResult.Status.SETTLING, controller.start().status());
        clock.nowMs = 399L;
        assertEquals(VisionResult.Status.SETTLING, controller.beforeSample().status());
        clock.nowMs = 400L;
        assertNull(controller.beforeSample());
        assertEquals(VisionResult.Status.COLLECTING, controller.add(classificationAt(0)).status());
        assertEquals(VisionResult.Status.COLLECTING, controller.add(classificationAt(0)).status());
        assertEquals(VisionResult.Status.LIVE, controller.add(classificationAt(0)).status());
        assertEquals(VisionResult.Status.LIVE, controller.beforeSample().status());
        assertEquals(List.of(true), illumination);
    }

    @Test
    public void resetAndCloseAlwaysRequestIlluminationOff() {
        FakeClock clock = new FakeClock();
        List<Boolean> illumination = new ArrayList<>();
        VisionSessionController controller = new VisionSessionController(
                VisionOptions.defaults(), illumination::add, clock::now);

        controller.reset();
        controller.close();

        assertEquals(List.of(false, false), illumination);
    }

    @Test
    public void failedIlluminationEnableRequestsCleanup() {
        List<Boolean> illumination = new ArrayList<>();
        VisionSessionController controller = new VisionSessionController(
                VisionOptions.defaults(), enabled -> {
                    illumination.add(enabled);
                    if (enabled) throw new IllegalStateException("driver failure");
                }, () -> 0L);

        VisionResult result = controller.start();

        assertEquals(VisionResult.Status.ERROR, result.status());
        assertEquals(List.of(true, false), illumination);
    }

    @Test
    public void failureClearsSamplesAndRequestsIlluminationOff() {
        FakeClock clock = new FakeClock();
        List<Boolean> illumination = new ArrayList<>();
        VisionSessionController controller = new VisionSessionController(
                new VisionOptions(0L, 3, 150_000_000L), illumination::add, clock::now);
        controller.start();
        controller.add(classificationAt(0));

        assertEquals(VisionResult.Status.ERROR, controller.fail("bad frame").status());
        assertEquals(VisionResult.Status.SETTLING, controller.beforeSample().status());
        assertNull(controller.beforeSample());
        VisionResult restarted = controller.add(classificationAt(0));
        assertEquals(VisionResult.Status.COLLECTING, restarted.status());
        assertEquals(1, restarted.sampleCount());
        assertEquals(List.of(true, false, true), illumination);
    }

    private static VisionClassification classificationAt(int index) {
        float[] probabilities = new float[VisionSdk.labels().length];
        probabilities[index] = 1f;
        return new VisionClassification(probabilities, 2L, 3L);
    }

    private static final class FakeClock {
        long nowMs;

        long now() {
            return nowMs;
        }
    }
}
