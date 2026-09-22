package com.unionbiometrics.vision.internal.inference;

import android.graphics.Rect;

import com.unionbiometrics.vision.api.AntiSpoofingEngine;
import com.unionbiometrics.vision.api.AntiSpoofingFrame;
import com.unionbiometrics.vision.api.FaceCrop;
import com.unionbiometrics.vision.api.ProbabilityResult;
import com.unionbiometrics.vision.internal.model.ClassificationResult;
import com.unionbiometrics.vision.internal.model.ModelSlotClassifier;
import com.unionbiometrics.vision.internal.model.SlotClassificationResult;

import java.util.Objects;

/** Internal raw single-frame inference shared by product sessions and the Lab app. */
public final class FrameInference {
    public interface EngineAccess {
        InferenceResult inferFrame(AntiSpoofingFrame frame);
    }

    private final ModelSlotClassifier slotClassifier;
    private final long maxPairDeltaNs;

    public FrameInference(ModelSlotClassifier slotClassifier, long maxPairDeltaNs) {
        this.slotClassifier = Objects.requireNonNull(slotClassifier, "slotClassifier");
        this.maxPairDeltaNs = maxPairDeltaNs;
    }

    public static InferenceResult infer(AntiSpoofingEngine engine, AntiSpoofingFrame frame) {
        Objects.requireNonNull(engine, "engine");
        if (!(engine instanceof EngineAccess)) {
            return InferenceResult.error("Unsupported anti-spoofing engine implementation");
        }
        return ((EngineAccess) engine).inferFrame(frame);
    }

    public InferenceResult infer(AntiSpoofingFrame frame) {
        if (frame == null) return InferenceResult.error("Vision frame must not be null");
        if (Math.abs(frame.rgbTimestampNs() - frame.irTimestampNs()) > maxPairDeltaNs) {
            return InferenceResult.error(
                    "RGB/IR frame delta exceeds " + maxPairDeltaNs + " ns");
        }
        try {
            Rect rgbCrop = expandFaceBox(
                    frame.rgbFaceBox(), frame.rgb().getWidth(), frame.rgb().getHeight());
            Rect irCrop = expandFaceBox(
                    frame.irFaceBox(), frame.ir().getWidth(), frame.ir().getHeight());
            SlotClassificationResult source = slotClassifier.classify(
                    frame.rgb(), rgbCrop, frame.ir(), irCrop);
            return InferenceResult.success(
                    toPublic(source.result), source.preprocessMs, source.inferenceMs);
        } catch (RuntimeException e) {
            return InferenceResult.error("Vision inference failed: " + e.getMessage());
        }
    }

    public void close() {
        slotClassifier.close();
    }

    private Rect expandFaceBox(Rect faceBox, int imageWidth, int imageHeight) {
        return FaceCrop.expand(
                faceBox, slotClassifier.cropMarginRatio(), imageWidth, imageHeight);
    }

    private static ProbabilityResult toPublic(ClassificationResult result) {
        if (result == null) return null;
        return new ProbabilityResult(result.probabilities);
    }
}
