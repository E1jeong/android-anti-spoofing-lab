package com.unionbiometrics.vision;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** One model slot and one anti-spoofing session. */
public final class AntiSpoofingEngine implements AutoCloseable {
    private final Classifier classifier;
    private final SessionController session;
    private final boolean requiresRgb;
    private final ModelInfo info;
    private final ExecutorService sessionExecutor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Object queueLock = new Object();
    private final Object inferenceLock = new Object();
    private Bitmap queuedRgbCopy;
    private Bitmap queuedIrCopy;
    private AntiSpoofingCallback queuedCallback;
    private long queuedGeneration;
    private Long sessionStartNanos;
    private long queuedSubmittedNanos;
    private volatile long generation;
    private long nextSubmissionId;
    private long latestQueuedSubmissionId;
    private boolean processingFrames;
    private boolean finalResultScheduled;
    private volatile boolean closed;

    private AntiSpoofingEngine(Classifier classifier, int modelCount, int settleFrames, int sampleCount) {
        this.classifier = classifier;
        session = new SessionController(settleFrames, sampleCount);
        requiresRgb = classifier.inputTensorCount() == Constants.DUAL_INPUT_COUNT;
        info = new ModelInfo(modelCount, classifier.inputTensorCount(), classifier.cropMarginRatio());
    }

    /** Returns the loaded model's immutable information without requiring an inference result. */
    public ModelInfo getModelInfo() {
        return info;
    }

    /** Synchronously infers one frame without advancing the session. Call off the main thread. */
    public AntiSpoofingResult inferFrame(Bitmap rgb, Rect rgbFace, Bitmap ir, Rect irFace) {
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
        synchronized (inferenceLock) {
            if (closed) throw new IllegalStateException("Vision engine is closed");
            return runInference(
                    rgb == null ? ir : rgb,
                    new Rect(rgbFace == null ? irFace : rgbFace),
                    ir, new Rect(irFace), false);
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
            int modelCount = ModelLoader.modelCount(application == null ? context : application);
            Classifier loaded = ModelLoader.loadModel(
                    application == null ? context : application, slotIndex);
            boolean created = false;
            try {
                AntiSpoofingEngine engine = new AntiSpoofingEngine(
                        loaded, modelCount, settleFrames, sampleCount);
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

    /** Reads only the registered count before engine creation, without loading model slots. */
    public static ModelInfo getModelInfo(Context context) {
        if (context == null) throw new IllegalArgumentException("context must not be null");
        Context application = context.getApplicationContext();
        try {
            return new ModelInfo(ModelLoader.modelCount(application == null ? context : application));
        } catch (Exception e) {
            throw new IllegalStateException("Model information failed to load: " + e.getMessage(), e);
        }
    }

    /** Expands a face box for host previews or capture. */
    public static Rect expandFace(Rect face, float marginRatio, int width, int height) {
        int marginX = Math.round(face.width() * marginRatio);
        int marginY = Math.round(face.height() * marginRatio);
        Rect expanded = new Rect(
                Math.max(0, face.left - marginX),
                Math.max(0, face.top - marginY),
                Math.min(width, face.right + marginX),
                Math.min(height, face.bottom + marginY));
        if (expanded.width() <= 0 || expanded.height() <= 0) {
            throw new IllegalArgumentException("Face crop is empty");
        }
        return expanded;
    }

    /** Submits a frame for async session inference, copying crops before returning. Delivers results on the main looper. */
    public void inferSession(Bitmap rgb, Rect rgbFace, Bitmap ir, Rect irFace,
                             AntiSpoofingCallback callback) {
        long submittedNanos = SystemClock.elapsedRealtimeNanos();
        if (callback == null) throw new IllegalArgumentException("callback must not be null");
        if (ir == null || irFace == null || irFace.isEmpty()) {
            throw new IllegalArgumentException("IR bitmap and nonempty face box are required");
        }
        boolean copyRgb = shouldCopyRgb(requiresRgb, rgb, rgbFace,
                rgbFace != null && rgbFace.isEmpty());
        validateBox(ir, irFace, "IR");
        if (rgb != null) validateBox(rgb, rgbFace, "RGB");

        final long submittedGeneration;
        final long submissionId;
        synchronized (queueLock) {
            if (closed) throw new IllegalStateException("Vision engine is closed");
            submittedGeneration = generation;
            submissionId = ++nextSubmissionId;
        }
        float margin = info.cropMarginRatio;
        Bitmap irCopy = copyExpandedCrop(ir, irFace, margin);
        Bitmap rgbCopy = null;
        try {
            if (copyRgb) rgbCopy = copyExpandedCrop(rgb, rgbFace, margin);
        } catch (RuntimeException e) {
            irCopy.recycle();
            throw e;
        }
        synchronized (queueLock) {
            if (closed || generation != submittedGeneration
                    || submissionId < latestQueuedSubmissionId) {
                if (rgbCopy != null) rgbCopy.recycle();
                irCopy.recycle();
                return;
            }
            latestQueuedSubmissionId = submissionId;
            if (sessionStartNanos == null) sessionStartNanos = submittedNanos;
            queuedSubmittedNanos = submittedNanos;
            recycleQueuedFrame();
            queuedRgbCopy = rgbCopy;
            queuedIrCopy = irCopy;
            queuedCallback = callback;
            queuedGeneration = submittedGeneration;
            if (!processingFrames) {
                processingFrames = true;
                sessionExecutor.execute(this::processPendingFrames);
            }
        }
    }

    private void processPendingFrames() {
        while (true) {
            Bitmap rgbCopy;
            Bitmap irCopy;
            AntiSpoofingCallback callback;
            long frameGeneration;
            long sessionStartedNanos;
            synchronized (queueLock) {
                if (closed || queuedIrCopy == null) {
                    processingFrames = false;
                    return;
                }
                rgbCopy = queuedRgbCopy;
                irCopy = queuedIrCopy;
                callback = queuedCallback;
                frameGeneration = queuedGeneration;
                sessionStartedNanos = sessionStartNanos;
                queuedRgbCopy = null;
                queuedIrCopy = null;
                queuedCallback = null;
            }
            AntiSpoofingResult result;
            boolean notifyResult;
            try {
                synchronized (inferenceLock) {
                    if (closed || generation != frameGeneration) continue;
                    result = processSessionFrame(rgbCopy, irCopy);
                    notifyResult = result.getStatus() == AntiSpoofingResult.Status.ERROR
                            || (result.getStatus() != AntiSpoofingResult.Status.RUNNING
                            && !finalResultScheduled);
                    if (result.getStatus() == AntiSpoofingResult.Status.LIVE
                            || result.getStatus() == AntiSpoofingResult.Status.SPOOF) {
                        finalResultScheduled = true;
                    }
                }
            } catch (RuntimeException e) {
                result = AntiSpoofingResult.sessionError("Vision inference failed: " + e.getMessage());
                notifyResult = true;
            } finally {
                if (rgbCopy != null) rgbCopy.recycle();
                irCopy.recycle();
            }
            if (!notifyResult) continue;
            if (result.getStatus() == AntiSpoofingResult.Status.ERROR) {
                synchronized (queueLock) {
                    if (generation == frameGeneration) {
                        sessionStartNanos = queuedIrCopy != null ? Long.valueOf(queuedSubmittedNanos) : null;
                    }
                }
            }
            AntiSpoofingResult delivered = result;
            mainHandler.post(() -> {
                synchronized (queueLock) {
                    if (closed || generation != frameGeneration) return;
                }
                try {
                    long durationMs = (SystemClock.elapsedRealtimeNanos() - sessionStartedNanos) / 1_000_000L;
                    callback.onResult(new AntiSpoofingResult(delivered.getStatus(),
                            delivered.getProbabilities(), delivered.getTopIndex(),
                            delivered.getInferenceMs(), delivered.getErrorMessage(), durationMs));
                } catch (RuntimeException e) {
                    Log.e("AntiSpoofingEngine", "Callback failed", e);
                }
            });
        }
    }

    private AntiSpoofingResult processSessionFrame(Bitmap rgb, Bitmap ir) {
        AntiSpoofingResult state = session.prepare();
        if (state != null) return state;
        try {
            Bitmap inputRgb = rgb == null ? ir : rgb;
            AntiSpoofingResult frameResult = runInference(inputRgb,
                    new Rect(0, 0, inputRgb.getWidth(), inputRgb.getHeight()),
                    ir, new Rect(0, 0, ir.getWidth(), ir.getHeight()), true);
            if (frameResult.getStatus() == AntiSpoofingResult.Status.ERROR) {
                return session.fail(frameResult.getErrorMessage());
            }
            return session.add(frameResult.getProbabilities(), frameResult.getInferenceMs());
        } catch (RuntimeException e) {
            return session.fail("Vision inference failed: " + e.getMessage());
        }
    }

    private AntiSpoofingResult runInference(Bitmap rgb, Rect rgbFace, Bitmap ir, Rect irFace,
                                           boolean isExpanded) {
        try {
            Rect rgbCrop = isExpanded ? rgbFace : expandFace(
                    rgbFace, info.cropMarginRatio, rgb.getWidth(), rgb.getHeight());
            Rect irCrop = isExpanded ? irFace : expandFace(
                    irFace, info.cropMarginRatio, ir.getWidth(), ir.getHeight());
            return classifier.classify(rgb, rgbCrop, ir, irCrop);
        } catch (RuntimeException e) {
            return AntiSpoofingResult.frameError("Vision inference failed: " + e.getMessage());
        }
    }

    static boolean shouldCopyRgb(boolean requiresRgb, Object rgb, Object rgbFace,
                                 boolean rgbFaceEmpty) {
        if ((rgb == null) != (rgbFace == null) || (rgbFace != null && rgbFaceEmpty)) {
            throw new IllegalArgumentException("RGB bitmap and nonempty face box must be paired");
        }
        if (requiresRgb && rgb == null) {
            throw new IllegalArgumentException("RGB and IR frame required by this slot");
        }
        return requiresRgb && rgb != null;
    }

    private static void validateBox(Bitmap bitmap, Rect box, String name) {
        if (bitmap.isRecycled() || box.left < 0 || box.top < 0
                || box.right > bitmap.getWidth() || box.bottom > bitmap.getHeight()) {
            throw new IllegalArgumentException(name + " frame or face box is invalid");
        }
    }

    private static Bitmap copyExpandedCrop(Bitmap source, Rect face, float margin) {
        Rect crop = expandFace(face, margin, source.getWidth(), source.getHeight());
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

    private void recycleQueuedFrame() {
        if (queuedRgbCopy != null) queuedRgbCopy.recycle();
        if (queuedIrCopy != null) queuedIrCopy.recycle();
        queuedRgbCopy = null;
        queuedIrCopy = null;
        queuedCallback = null;
    }

    public void reset() {
        synchronized (queueLock) {
            if (closed) return;
            generation++;
            recycleQueuedFrame();
            sessionStartNanos = null;
            synchronized (inferenceLock) {
                session.reset();
                finalResultScheduled = false;
            }
        }
    }

    @Override public void close() {
        synchronized (queueLock) {
            if (closed) return;
            closed = true;
            generation++;
            recycleQueuedFrame();
            sessionStartNanos = null;
            sessionExecutor.shutdown();
            synchronized (inferenceLock) {
                session.close();
                classifier.close();
            }
        }
    }
}
