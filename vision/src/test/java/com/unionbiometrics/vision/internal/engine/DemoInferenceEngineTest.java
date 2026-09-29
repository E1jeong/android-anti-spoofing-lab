package com.unionbiometrics.vision.internal.engine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.fail;

import com.unionbiometrics.vision.api.AntiSpoofingEngine;
import com.unionbiometrics.vision.api.AntiSpoofingFrame;
import com.unionbiometrics.vision.api.AntiSpoofingResult;
import com.unionbiometrics.vision.api.EngineInfo;
import com.unionbiometrics.vision.internal.inference.FrameResult;

import org.junit.Test;

public final class DemoInferenceEngineTest {
    @Test
    public void nullEngineIsRejected() {
        try {
            DemoInferenceEngine.infer(null, null);
            fail("expected NullPointerException");
        } catch (NullPointerException e) {
            assertEquals("engine", e.getMessage());
        }
    }

    @Test
    public void unsupportedEngineReturnsError() {
        FrameResult result = DemoInferenceEngine.infer(new FakeEngine(), null);

        assertFalse(result.successful());
        assertEquals("Unsupported anti-spoofing engine implementation", result.errorMessage());
    }

    private static final class FakeEngine implements AntiSpoofingEngine {
        @Override public EngineInfo info() {
            return null;
        }

        @Override public AntiSpoofingResult process(AntiSpoofingFrame frame) {
            return null;
        }

        @Override public void reset() {}

        @Override public void close() {}
    }
}
