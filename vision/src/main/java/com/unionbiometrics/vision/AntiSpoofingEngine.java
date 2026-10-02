package com.unionbiometrics.vision;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.unionbiometrics.vision.internal.FaceCrop;
import com.unionbiometrics.vision.internal.FrameInput;
import com.unionbiometrics.vision.internal.FrameResult;
import com.unionbiometrics.vision.internal.SessionController;
import com.unionbiometrics.vision.internal.SessionResult;
import com.unionbiometrics.vision.internal.SlotClassifier;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** One model slot and one anti-spoofing session. */
public final class AntiSpoofingEngine implements AutoCloseable {
    private final SlotClassifier slot;
    private final SessionController session;
    private final boolean requiresRgb;
    public final String label;
    public final String backend;
    public final float cropMarginRatio;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Object lock = new Object();
    private FrameInput pendingFrame;
    private Bitmap pendingRgbCopy;
    private Bitmap pendingIrCopy;
    private AntiSpoofingCallback pendingCallback;
    private long pendingGeneration;
    private volatile long generation;
    private long nextSubmissionId;
    private long latestQueuedSubmissionId;
    private boolean draining;
    private boolean terminalDelivered;
    private volatile boolean closed;

    private AntiSpoofingEngine(SlotClassifier slot, int settleFrames, int sampleCount) {
        this.slot = slot;
        session = new SessionController(settleFrames, sampleCount);
        requiresRgb = slot.isDualInput();
        label = slot.label();
        backend = slot.inferenceBackend();
        cropMarginRatio = slot.cropMarginRatio();
    }

    /** Returns one raw frame result without changing the callback session. Call off the main thread. */
    public AntiSpoofingResult process(Bitmap rgb, Rect rgbFace, Bitmap ir, Rect irFace) {
        if (ir == null || irFace == null) {
            throw new IllegalArgumentException("IR bitmap and face box are required");
        }
        if ((rgb == null) != (rgbFace == null)) {
            throw new IllegalArgumentException("RGB bitmap and face box must be paired");
        }
        if (requiresRgb && rgb == null) {
            throw new IllegalArgumentException("RGB and IR frame required by this slot");
        }
        if (ir.isRecycled() || (rgb != null && rgb.isRecycled())) {
            throw new IllegalArgumentException("Frame bitmap is recycled");
        }
        synchronized (session) {
            if (closed) throw new IllegalStateException("Vision engine is closed");
            return AntiSpoofingResult.fromFrame(slot.classify(new FrameInput(
                    rgb == null ? ir : rgb, rgbFace == null ? irFace : rgbFace, ir, irFace)));
        }
    }

    /** Loads and warms a zero-based manifest slot. Call from a background thread. */
    public static AntiSpoofingEngine create(Context context, int slotIndex,
                                            int settleFrames, int sampleCount) {
        if (slotIndex < 0) throw new IllegalArgumentException("slotIndex must be >= 0");
        if (settleFrames < 0) throw new IllegalArgumentException("settleFrames must be >= 0");
        if (sampleCount <= 0) throw new IllegalArgumentException("sampleCount must be > 0");
        if (context == null) throw new IllegalArgumentException("context must not be null");
        Context application = context.getApplicationContext();
        try {
            SlotClassifier loaded = SlotClassifier.loadSelected(
                    application == null ? context : application, slotIndex);
            boolean created = false;
            try {
                AntiSpoofingEngine engine = new AntiSpoofingEngine(
                        loaded, settleFrames, sampleCount);
                created = true;
                return engine;
            } finally {
                if (!created) {
                    try { loaded.close(); } catch (RuntimeException ignored) {}
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException("Model slot " + slotIndex
                    + " failed to load: " + e.getMessage(), e);
        }
    }

    /** Number of model entries in the bundled manifest. */
    public static int slotCount(Context context) {
        if (context == null) throw new IllegalArgumentException("context must not be null");
        try {
            return SlotClassifier.slotCount(context);
        } catch (Exception e) {
            throw new IllegalStateException("Model manifest failed to load: " + e.getMessage(), e);
        }
    }

    /** Expands a face box for host previews or capture. */
    public static Rect expandFace(Rect face, float marginRatio, int width, int height) {
        return FaceCrop.expand(face, marginRatio, width, height);
    }

    /** Copies expanded crops before returning. Delivers results on the main looper. */
    public void infer(Bitmap rgb, Rect rgbFace, Bitmap ir, Rect irFace,
                      AntiSpoofingCallback callback) {
        if (callback == null) throw new IllegalArgumentException("callback must not be null");
        if (ir == null || irFace == null || irFace.isEmpty()) {
            throw new IllegalArgumentException("IR bitmap and nonempty face box are required");
        }
        if ((rgb == null) != (rgbFace == null)
                || (rgbFace != null && rgbFace.isEmpty())) {
            throw new IllegalArgumentException("RGB bitmap and nonempty face box must be paired");
        }
        if (requiresRgb != (rgb != null)) {
            throw new IllegalArgumentException(requiresRgb
                    ? "RGB and IR frame required by this slot"
                    : "IR-only frame required by this slot");
        }
        validateBox(ir, irFace, "IR");
        if (rgb != null) validateBox(rgb, rgbFace, "RGB");

        final long submittedGeneration;
        final long submissionId;
        synchronized (lock) {
            if (closed) throw new IllegalStateException("Vision engine is closed");
            submittedGeneration = generation;
            submissionId = ++nextSubmissionId;
        }
        float margin = cropMarginRatio;
        Bitmap irCopy = copyCrop(ir, irFace, margin);
        Bitmap rgbCopy = null;
        try {
            if (rgb != null) rgbCopy = copyCrop(rgb, rgbFace, margin);
        } catch (RuntimeException e) {
            irCopy.recycle();
            throw e;
        }
        synchronized (lock) {
            if (closed || generation != submittedGeneration
                    || submissionId < latestQueuedSubmissionId) {
                if (rgbCopy != null) rgbCopy.recycle();
                irCopy.recycle();
                return;
            }
            latestQueuedSubmissionId = submissionId;
            recyclePending();
            Bitmap inputRgb = rgbCopy == null ? irCopy : rgbCopy;
            pendingFrame = new FrameInput(
                    inputRgb, new Rect(0, 0, inputRgb.getWidth(), inputRgb.getHeight()),
                    irCopy, new Rect(0, 0, irCopy.getWidth(), irCopy.getHeight()), true);
            pendingRgbCopy = rgbCopy;
            pendingIrCopy = irCopy;
            pendingCallback = callback;
            pendingGeneration = submittedGeneration;
            if (!draining) {
                draining = true;
                worker.execute(this::drain);
            }
        }
    }

    private void drain() {
        while (true) {
            FrameInput frame;
            Bitmap rgbCopy;
            Bitmap irCopy;
            AntiSpoofingCallback callback;
            long frameGeneration;
            synchronized (lock) {
                if (closed || pendingFrame == null) {
                    draining = false;
                    return;
                }
                frame = pendingFrame;
                rgbCopy = pendingRgbCopy;
                irCopy = pendingIrCopy;
                callback = pendingCallback;
                frameGeneration = pendingGeneration;
                pendingFrame = null;
                pendingRgbCopy = null;
                pendingIrCopy = null;
                pendingCallback = null;
            }
            AntiSpoofingResult result;
            boolean notifyResult;
            try {
                synchronized (session) {
                    if (closed || generation != frameGeneration) continue;
                    SessionResult state = process(frame);
                    result = AntiSpoofingResult.fromInternal(
                            state, session.settleRemaining(), session.acceptedSamples());
                    notifyResult = result.status() == AntiSpoofingResult.Status.ERROR
                            || (result.status() != AntiSpoofingResult.Status.PENDING
                            && !terminalDelivered);
                    if (result.status() == AntiSpoofingResult.Status.LIVE
                            || result.status() == AntiSpoofingResult.Status.SPOOF) {
                        terminalDelivered = true;
                    }
                }
            } catch (RuntimeException e) {
                result = AntiSpoofingResult.fromInternal(
                        SessionResult.error("Vision inference failed: " + e.getMessage()), 0, 0);
                notifyResult = true;
            } finally {
                if (rgbCopy != null) rgbCopy.recycle();
                irCopy.recycle();
            }
            if (!notifyResult) continue;
            AntiSpoofingResult delivered = result;
            mainHandler.post(() -> {
                synchronized (lock) {
                    if (closed || generation != frameGeneration) return;
                }
                try {
                    callback.onResult(delivered);
                } catch (RuntimeException e) {
                    Log.e("AntiSpoofingEngine", "Callback failed", e);
                }
            });
        }
    }

    private SessionResult process(FrameInput frame) {
        SessionResult state = session.beforeSample();
        if (state != null) return state;
        try {
            FrameResult inference = slot.classify(frame);
            if (!inference.successful()) return session.fail(inference.errorMessage());
            return session.add(inference.result(), inference.inferenceMs());
        } catch (RuntimeException e) {
            return session.fail("Vision inference failed: " + e.getMessage());
        }
    }

    private static void validateBox(Bitmap bitmap, Rect box, String name) {
        if (bitmap.isRecycled() || box.left < 0 || box.top < 0
                || box.right > bitmap.getWidth() || box.bottom > bitmap.getHeight()) {
            throw new IllegalArgumentException(name + " frame or face box is invalid");
        }
    }

    private static Bitmap copyCrop(Bitmap source, Rect face, float margin) {
        Rect crop = FaceCrop.expand(face, margin, source.getWidth(), source.getHeight());
        Bitmap copy = Bitmap.createBitmap(crop.width(), crop.height(), Bitmap.Config.ARGB_8888);
        try {
            new Canvas(copy).drawBitmap(source, crop,
                    new Rect(0, 0, copy.getWidth(), copy.getHeight()), new Paint());
            return copy;
        } catch (RuntimeException e) {
            copy.recycle();
            throw e;
        }
    }

    private void recyclePending() {
        if (pendingRgbCopy != null) pendingRgbCopy.recycle();
        if (pendingIrCopy != null) pendingIrCopy.recycle();
        pendingFrame = null;
        pendingRgbCopy = null;
        pendingIrCopy = null;
        pendingCallback = null;
    }

    public void reset() {
        synchronized (lock) {
            if (closed) return;
            generation++;
            recyclePending();
            synchronized (session) {
                session.reset();
                terminalDelivered = false;
            }
        }
    }

    @Override public void close() {
        synchronized (lock) {
            if (closed) return;
            closed = true;
            generation++;
            recyclePending();
            worker.shutdown();
            synchronized (session) {
                session.close();
                slot.close();
            }
        }
    }
}
