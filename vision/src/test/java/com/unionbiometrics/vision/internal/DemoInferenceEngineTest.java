package com.unionbiometrics.vision.internal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

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
}
