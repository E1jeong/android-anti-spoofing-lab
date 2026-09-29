package com.unionbiometrics.vision.internal.engine;

import com.unionbiometrics.vision.api.AntiSpoofingEngine;
import com.unionbiometrics.vision.api.AntiSpoofingFrame;
import com.unionbiometrics.vision.internal.inference.FrameResult;

import java.util.Objects;

/** Lab-only raw-frame entry. Product hosts call AntiSpoofingEngine.process(). */
public final class DemoInferenceEngine {
    private DemoInferenceEngine() {}

    public static FrameResult infer(AntiSpoofingEngine engine, AntiSpoofingFrame frame) {
        Objects.requireNonNull(engine, "engine");
        if (!(engine instanceof AntiSpoofingEngineImpl)) {
            return FrameResult.error("Unsupported anti-spoofing engine implementation");
        }
        return ((AntiSpoofingEngineImpl) engine).inferFrame(frame);
    }
}
