package com.unionbiometrics.vision.internal;

import androidx.annotation.RestrictTo;
import android.graphics.Rect;

@RestrictTo(RestrictTo.Scope.LIBRARY)
public final class AntiSpoofingEngineImpl implements AutoCloseable {
    private final SlotClassifier slotClassifier;
    private final SessionController session;

    public AntiSpoofingEngineImpl(SlotClassifier slotClassifier, int settleFrames, int samples) {
        this.slotClassifier = slotClassifier;
        session = new SessionController(settleFrames, samples);
    }

    public String label() { return slotClassifier.label(); }
    public String backend() { return slotClassifier.inferenceBackend(); }
    public float cropMarginRatio() { return slotClassifier.cropMarginRatio(); }

    public int settleRemaining() {
        return session.settleRemaining();
    }

    public int acceptedSamples() {
        return session.acceptedSamples();
    }

    synchronized FrameResult inferFrame(FrameInput frame) {
        if (session.isClosed()) return FrameResult.error("Vision engine is closed");
        return classify(frame);
    }

    public synchronized SessionResult process(FrameInput frame) {
        if (session.isClosed()) {
            return SessionResult.error("Vision engine is closed");
        }
        if (frame == null) return fail("Vision frame must not be null");
        SessionResult sessionState = session.beforeSample();
        if (sessionState != null) return sessionState;
        FrameResult inference = classify(frame);
        if (!inference.successful()) return fail(inference.errorMessage());
        try {
            return session.add(inference.result(), inference.inferenceMs());
        } catch (RuntimeException e) {
            return fail("Vision inference failed: " + e.getMessage());
        }
    }

    public synchronized void reset() {
        session.reset();
    }

    @Override
    public synchronized void close() {
        if (session.isClosed()) return;
        session.close();
        slotClassifier.close();
    }

    private FrameResult classify(FrameInput frame) {
        if (frame == null) return FrameResult.error("Vision frame must not be null");
        try {
            Rect rgbCrop = frame.expanded() ? frame.rgbFaceBox() : FaceCrop.expand(
                    frame.rgbFaceBox(), cropMarginRatio(), frame.rgb().getWidth(), frame.rgb().getHeight());
            Rect irCrop = frame.expanded() ? frame.irFaceBox() : FaceCrop.expand(
                    frame.irFaceBox(), cropMarginRatio(), frame.ir().getWidth(), frame.ir().getHeight());
            return slotClassifier.classify(frame.rgb(), rgbCrop, frame.ir(), irCrop);
        } catch (RuntimeException e) {
            return FrameResult.error("Vision inference failed: " + e.getMessage());
        }
    }

    private SessionResult fail(String message) {
        return session.fail(message);
    }
}
