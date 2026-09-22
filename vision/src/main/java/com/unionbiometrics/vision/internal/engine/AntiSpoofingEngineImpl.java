package com.unionbiometrics.vision.internal.engine;

import android.os.SystemClock;

import androidx.annotation.RestrictTo;

import com.unionbiometrics.vision.api.AntiSpoofingEngine;
import com.unionbiometrics.vision.api.AntiSpoofingFrame;
import com.unionbiometrics.vision.api.AntiSpoofingOptions;
import com.unionbiometrics.vision.api.AntiSpoofingResult;
import com.unionbiometrics.vision.api.IrLedController;
import com.unionbiometrics.vision.api.EngineInfo;
import com.unionbiometrics.vision.internal.inference.FrameClassifier;
import com.unionbiometrics.vision.internal.inference.FrameResult;
import com.unionbiometrics.vision.internal.classification.SlotClassifier;
import com.unionbiometrics.vision.internal.session.SessionController;

@RestrictTo(RestrictTo.Scope.LIBRARY)
public final class AntiSpoofingEngineImpl
        implements AntiSpoofingEngine, FrameClassifier.EngineAccess {
    private final FrameClassifier frameClassifier;
    private final AntiSpoofingOptions options;
    private final SessionController session;
    private final EngineInfo info;

    public AntiSpoofingEngineImpl(SlotClassifier slotClassifier, IrLedController irLedController,
                                  AntiSpoofingOptions options) {
        this.options = options;
        info = new EngineInfo(
                slotClassifier.label(), slotClassifier.inferenceBackend(),
                slotClassifier.cropMarginRatio());
        frameClassifier = new FrameClassifier(slotClassifier, options.maxPairDeltaNs());
        session = new SessionController(options, irLedController::setEnabled,
                SystemClock::elapsedRealtime);
    }

    @Override
    public EngineInfo info() {
        return info;
    }

    @RestrictTo(RestrictTo.Scope.LIBRARY)
    @Override
    public synchronized FrameResult inferFrame(AntiSpoofingFrame frame) {
        if (session.isClosed()) return FrameResult.error("Vision engine is closed");
        return frameClassifier.infer(frame);
    }

    @Override
    public synchronized AntiSpoofingResult startSession() {
        return session.start();
    }

    @Override
    public synchronized AntiSpoofingResult process(AntiSpoofingFrame frame) {
        if (session.isClosed()) {
            return AntiSpoofingResult.error("Vision engine is closed");
        }
        if (frame == null) return fail("Vision frame must not be null");
        AntiSpoofingResult sessionState = session.beforeSample();
        if (sessionState != null) return sessionState;
        FrameResult inference = frameClassifier.infer(frame);
        if (!inference.successful()) return fail(inference.errorMessage());
        try {
            return session.add(inference.result());
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
        frameClassifier.close();
    }

    private AntiSpoofingResult fail(String message) {
        return session.fail(message);
    }
}
