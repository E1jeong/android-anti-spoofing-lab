package com.unionbiometrics.vision.internal.session;

import android.graphics.Rect;
import android.os.SystemClock;

import androidx.annotation.RestrictTo;

import com.unionbiometrics.vision.api.AntiSpoofingEngine;
import com.unionbiometrics.vision.api.VisionClassification;
import com.unionbiometrics.vision.api.VisionFrame;
import com.unionbiometrics.vision.api.VisionInferenceResult;
import com.unionbiometrics.vision.api.VisionOptions;
import com.unionbiometrics.vision.api.VisionResult;
import com.unionbiometrics.vision.api.IrLedController;
import com.unionbiometrics.vision.internal.image.FaceCrop;
import com.unionbiometrics.vision.internal.model.ClassificationResult;
import com.unionbiometrics.vision.internal.model.ModelSlotClassifier;
import com.unionbiometrics.vision.internal.model.SlotClassificationResult;

@RestrictTo(RestrictTo.Scope.LIBRARY)
public final class DefaultAntiSpoofingEngine implements AntiSpoofingEngine {
    private final ModelSlotClassifier classifier;
    private final VisionOptions options;
    private final VisionSessionController session;

    public DefaultAntiSpoofingEngine(ModelSlotClassifier classifier, IrLedController irLedController,
                                     VisionOptions options) {
        this.classifier = classifier;
        this.options = options;
        session = new VisionSessionController(options, irLedController::setEnabled,
                SystemClock::elapsedRealtime);
    }

    @Override
    public String label() {
        return classifier.label();
    }

    @Override
    public String backendStatus() {
        return classifier.backendStatus();
    }

    @Override
    public float cropMarginRatio() {
        return classifier.cropMarginRatio();
    }

    @Override
    public Rect expandFaceBox(Rect faceBox, int imageWidth, int imageHeight) {
        return FaceCrop.expand(faceBox, classifier.cropMarginRatio(), imageWidth, imageHeight);
    }

    @Override
    public synchronized VisionInferenceResult infer(VisionFrame frame) {
        if (session.isClosed()) return VisionInferenceResult.error("Vision engine is closed");
        if (frame == null) return VisionInferenceResult.error("Vision frame must not be null");
        if (Math.abs(frame.rgbTimestampNs() - frame.irTimestampNs()) > options.maxPairDeltaNs()) {
            return VisionInferenceResult.error(
                    "RGB/IR frame delta exceeds " + options.maxPairDeltaNs() + " ns");
        }
        try {
            SlotClassificationResult source = classifyFrame(frame);
            return VisionInferenceResult.success(
                    toPublic(source.result), toPublic(source.rgbResult), toPublic(source.irResult),
                    source.preprocessMs, source.inferenceMs);
        } catch (RuntimeException e) {
            return VisionInferenceResult.error("Vision inference failed: " + e.getMessage());
        }
    }

    @Override
    public synchronized VisionResult startSession() {
        return session.start();
    }

    @Override
    public synchronized VisionResult process(VisionFrame frame) {
        if (session.isClosed()) {
            return VisionResult.error(options.sampleCount(), "Vision engine is closed");
        }
        if (frame == null) return fail("Vision frame must not be null");
        VisionResult sessionState = session.beforeSample();
        if (sessionState != null) return sessionState;
        VisionInferenceResult inference = infer(frame);
        if (!inference.successful()) return fail(inference.errorMessage());
        try {
            return session.add(inference.primaryResult());
        } catch (RuntimeException e) {
            return fail("Vision inference failed: " + e.getMessage());
        }
    }

    private SlotClassificationResult classifyFrame(VisionFrame frame) {
        Rect rgbFace = frame.rgbFaceBox();
        Rect irFace = frame.irFaceBox();
        Rect rgbCrop = expandFaceBox(rgbFace, frame.rgb().getWidth(), frame.rgb().getHeight());
        Rect irCrop = expandFaceBox(irFace, frame.ir().getWidth(), frame.ir().getHeight());
        return classifier.classify(frame.rgb(), rgbCrop, frame.ir(), irCrop);
    }

    private static VisionClassification toPublic(ClassificationResult result) {
        if (result == null) return null;
        return new VisionClassification(result.probabilities, result.preprocessMs, result.inferenceMs);
    }

    @Override
    public synchronized void reset() {
        session.reset();
    }

    @Override
    public synchronized void close() {
        if (session.isClosed()) return;
        session.close();
        classifier.close();
    }

    private VisionResult fail(String message) {
        return session.fail(message);
    }
}
