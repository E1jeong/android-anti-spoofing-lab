package com.unionbiometrics.vision.api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;

import java.util.Collections;

import org.junit.Test;

public final class EngineLoadResultTest {
    @Test
    public void keepsEngineAndInfoInMatchingOrder() {
        AntiSpoofingEngine engine = new FakeEngine();
        EngineInfo info = new EngineInfo("MODEL 1", "Ready", 0.1f);

        EngineLoadResult result = new EngineLoadResult(
                Collections.singletonList(engine),
                Collections.singletonList(info),
                Collections.emptyList());

        assertSame(engine, result.engines().get(0));
        assertSame(info, result.engineInfos().get(0));
        assertEquals("MODEL 1", result.engineInfos().get(0).label());
    }

    @Test
    public void rejectsMismatchedEngineAndInfoCounts() {
        try {
            new EngineLoadResult(
                    Collections.singletonList(new FakeEngine()),
                    Collections.emptyList(),
                    Collections.emptyList());
            fail("Expected mismatched metadata to be rejected");
        } catch (IllegalArgumentException expected) {
            assertEquals("engines and engineInfos must have the same size", expected.getMessage());
        }
    }

    private static final class FakeEngine implements AntiSpoofingEngine {
        @Override public InferenceResult infer(AntiSpoofingFrame frame) {
            return null;
        }

        @Override public AntiSpoofingResult startSession() {
            return null;
        }

        @Override public AntiSpoofingResult process(AntiSpoofingFrame frame) {
            return null;
        }

        @Override public void reset() {}

        @Override public void close() {}
    }
}
