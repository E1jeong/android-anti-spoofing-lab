package com.unionbiometrics.vision.internal.inference;

import android.graphics.Rect;

import com.unionbiometrics.vision.api.AntiSpoofingEngine;
import com.unionbiometrics.vision.api.AntiSpoofingFrame;
import com.unionbiometrics.vision.api.FaceCrop;
import com.unionbiometrics.vision.api.ProbabilityResult;
import com.unionbiometrics.vision.internal.classification.ClassificationResult;
import com.unionbiometrics.vision.internal.classification.SlotClassifier;
import com.unionbiometrics.vision.internal.classification.SlotClassificationResult;

import java.util.Objects;

/** Internal raw single-frame inference shared by product sessions and the Lab app. */
public final class FrameClassifier {
    public interface EngineAccess {
        FrameResult inferFrame(AntiSpoofingFrame frame);
    }

    private final SlotClassifier slotClassifier;
    private final long maxPairDeltaNs;

    public FrameClassifier(SlotClassifier slotClassifier, long maxPairDeltaNs) {
        this.slotClassifier = Objects.requireNonNull(slotClassifier, "slotClassifier");
        this.maxPairDeltaNs = maxPairDeltaNs;
    }

    public static FrameResult infer(AntiSpoofingEngine engine, AntiSpoofingFrame frame) {
        Objects.requireNonNull(engine, "engine");
        if (!(engine instanceof EngineAccess)) {
            return FrameResult.error("Unsupported anti-spoofing engine implementation");
        }
        return ((EngineAccess) engine).inferFrame(frame);
    }

    public FrameResult infer(AntiSpoofingFrame frame) {
        if (frame == null) return FrameResult.error("Vision frame must not be null");
        if (Math.abs(frame.rgbTimestampNs() - frame.irTimestampNs()) > maxPairDeltaNs) {
            return FrameResult.error(
                    "RGB/IR frame delta exceeds " + maxPairDeltaNs + " ns");
        }
        try {
            Rect rgbCrop = expandFaceBox(
                    frame.rgbFaceBox(), frame.rgb().getWidth(), frame.rgb().getHeight());
            Rect irCrop = expandFaceBox(
                    frame.irFaceBox(), frame.ir().getWidth(), frame.ir().getHeight());
            SlotClassificationResult source = slotClassifier.classify(
                    frame.rgb(), rgbCrop, frame.ir(), irCrop);
            return FrameResult.success(
                    toPublic(source.result), source.preprocessMs, source.inferenceMs);
        } catch (RuntimeException e) {
            return FrameResult.error("Vision inference failed: " + e.getMessage());
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
