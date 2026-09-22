package com.unionbiometrics.vision.internal.engine;

import android.os.SystemClock;

import androidx.annotation.RestrictTo;

import com.unionbiometrics.vision.api.AntiSpoofingEngine;
import com.unionbiometrics.vision.api.AntiSpoofingFrame;
import com.unionbiometrics.vision.api.AntiSpoofingOptions;
import com.unionbiometrics.vision.api.AntiSpoofingResult;
import com.unionbiometrics.vision.api.IrLedController;
import com.unionbiometrics.vision.api.EngineInfo;
import com.unionbiometrics.vision.internal.inference.FrameInference;
import com.unionbiometrics.vision.internal.inference.InferenceResult;
import com.unionbiometrics.vision.internal.model.ModelSlotClassifier;
import com.unionbiometrics.vision.internal.session.SessionController;

@RestrictTo(RestrictTo.Scope.LIBRARY)
public final class AntiSpoofingEngineImpl
        implements AntiSpoofingEngine, FrameInference.EngineAccess {
    private final FrameInference frameInference;
    private final AntiSpoofingOptions options;
    private final SessionController session;
    private final EngineInfo info;

    public AntiSpoofingEngineImpl(ModelSlotClassifier slotClassifier, IrLedController irLedController,
                                  AntiSpoofingOptions options) {
        this.options = options;
        info = new EngineInfo(
                slotClassifier.label(), slotClassifier.inferenceBackend(),
                slotClassifier.cropMarginRatio());
        frameInference = new FrameInference(slotClassifier, options.maxPairDeltaNs());
        session = new SessionController(options, irLedController::setEnabled,
                SystemClock::elapsedRealtime);
    }

    @Override
    public EngineInfo info() {
        return info;
    }

    @RestrictTo(RestrictTo.Scope.LIBRARY)
    @Override
    public synchronized InferenceResult inferFrame(AntiSpoofingFrame frame) {
        if (session.isClosed()) return InferenceResult.error("Vision engine is closed");
        return frameInference.infer(frame);
    }

    @Override
    public synchronized AntiSpoofingResult startSession() {
        return session.start();
    }

    @Override
    public synchronized AntiSpoofingResult process(AntiSpoofingFrame frame) {
        if (session.isClosed()) {
            return AntiSpoofingResult.error(options.sampleCount(), "Vision engine is closed");
        }
        if (frame == null) return fail("Vision frame must not be null");
        AntiSpoofingResult sessionState = session.beforeSample();
        if (sessionState != null) return sessionState;
        InferenceResult inference = frameInference.infer(frame);
        if (!inference.successful()) return fail(inference.errorMessage());
        try {
            return session.add(
                    inference.result(), inference.preprocessMs(), inference.inferenceMs());
        } catch (RuntimeException e) {
            return fail("Vision inference failed: " + e.getMessage());
        }
    }

    @Override
    public synchronized void reset() {
        session.reset();
    }

    @Override
    public synchronized void close() {
        if (session.isClosed()) return;
        session.close();
        frameInference.close();
    }

    private AntiSpoofingResult fail(String message) {
        return session.fail(message);
    }
}
