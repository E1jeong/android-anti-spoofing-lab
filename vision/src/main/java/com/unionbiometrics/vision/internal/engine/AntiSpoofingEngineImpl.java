package com.unionbiometrics.vision.internal.engine;

import android.graphics.Rect;
import android.os.SystemClock;

import androidx.annotation.RestrictTo;

import com.unionbiometrics.vision.api.AntiSpoofingEngine;
import com.unionbiometrics.vision.api.ProbabilityResult;
import com.unionbiometrics.vision.api.AntiSpoofingFrame;
import com.unionbiometrics.vision.api.InferenceResult;
import com.unionbiometrics.vision.api.AntiSpoofingOptions;
import com.unionbiometrics.vision.api.AntiSpoofingResult;
import com.unionbiometrics.vision.api.IrLedController;
import com.unionbiometrics.vision.api.FaceCrop;
import com.unionbiometrics.vision.api.EngineInfo;
import com.unionbiometrics.vision.internal.model.ClassificationResult;
import com.unionbiometrics.vision.internal.model.ModelSlotClassifier;
import com.unionbiometrics.vision.internal.model.SlotClassificationResult;
import com.unionbiometrics.vision.internal.session.SessionController;

@RestrictTo(RestrictTo.Scope.LIBRARY)
public final class AntiSpoofingEngineImpl implements AntiSpoofingEngine {
    private final ModelSlotClassifier slotClassifier;
    private final AntiSpoofingOptions options;
    private final SessionController session;
    private final EngineInfo info;

    public AntiSpoofingEngineImpl(ModelSlotClassifier slotClassifier, IrLedController irLedController,
                                  AntiSpoofingOptions options) {
        this.slotClassifier = slotClassifier;
        this.options = options;
        info = new EngineInfo(
                slotClassifier.label(), slotClassifier.inferenceBackend(),
                slotClassifier.cropMarginRatio());
        session = new SessionController(options, irLedController::setEnabled,
                SystemClock::elapsedRealtime);
    }

    @Override
    public EngineInfo info() {
        return info;
    }

    private Rect expandFaceBox(Rect faceBox, int imageWidth, int imageHeight) {
        return FaceCrop.expand(faceBox, slotClassifier.cropMarginRatio(), imageWidth, imageHeight);
    }

    @Override
    public synchronized InferenceResult infer(AntiSpoofingFrame frame) {
        if (session.isClosed()) return InferenceResult.error("Vision engine is closed");
        if (frame == null) return InferenceResult.error("Vision frame must not be null");
        if (Math.abs(frame.rgbTimestampNs() - frame.irTimestampNs()) > options.maxPairDeltaNs()) {
            return InferenceResult.error(
                    "RGB/IR frame delta exceeds " + options.maxPairDeltaNs() + " ns");
        }
        try {
            SlotClassificationResult source = classifyFrame(frame);
            return InferenceResult.success(
                    toPublic(source.result), source.preprocessMs, source.inferenceMs);
        } catch (RuntimeException e) {
            return InferenceResult.error("Vision inference failed: " + e.getMessage());
        }
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
        InferenceResult inference = infer(frame);
        if (!inference.successful()) return fail(inference.errorMessage());
        try {
            return session.add(
                    inference.result(), inference.preprocessMs(), inference.inferenceMs());
        } catch (RuntimeException e) {
            return fail("Vision inference failed: " + e.getMessage());
        }
    }

    private SlotClassificationResult classifyFrame(AntiSpoofingFrame frame) {
        Rect rgbFace = frame.rgbFaceBox();
        Rect irFace = frame.irFaceBox();
        Rect rgbCrop = expandFaceBox(rgbFace, frame.rgb().getWidth(), frame.rgb().getHeight());
        Rect irCrop = expandFaceBox(irFace, frame.ir().getWidth(), frame.ir().getHeight());
        return slotClassifier.classify(frame.rgb(), rgbCrop, frame.ir(), irCrop);
    }

    private static ProbabilityResult toPublic(ClassificationResult result) {
        if (result == null) return null;
        return new ProbabilityResult(result.probabilities);
    }

    @Override
    public synchronized void reset() {
        session.reset();
    }

    @Override
    public synchronized void close() {
        if (session.isClosed()) return;
        session.close();
        slotClassifier.close();
    }

    private AntiSpoofingResult fail(String message) {
        return session.fail(message);
    }
}
