package com.unionbiometrics.vision.api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import java.util.Collections;

import org.junit.Test;

public final class EngineLoadResultTest {
    @Test
    public void engineOwnsItsMetadata() {
        EngineInfo info = new EngineInfo("MODEL 1", "NNAPI", 0.1f);
        AntiSpoofingEngine engine = new FakeEngine(info);

        EngineLoadResult result = new EngineLoadResult(
                Collections.singletonList(engine),
                Collections.emptyList());

        assertSame(engine, result.engines().get(0));
        assertSame(info, result.engines().get(0).info());
        assertEquals("NNAPI", result.engines().get(0).info().backend());
    }

    private static final class FakeEngine implements AntiSpoofingEngine {
        private final EngineInfo info;

        FakeEngine(EngineInfo info) {
            this.info = info;
        }

        @Override public EngineInfo info() {
            return info;
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
