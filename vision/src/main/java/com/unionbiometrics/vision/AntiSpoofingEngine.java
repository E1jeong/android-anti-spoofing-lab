package com.unionbiometrics.vision;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** One model slot and one anti-spoofing session. */
public final class AntiSpoofingEngine implements AutoCloseable {
    private final Classifier classifier;
    private final SessionController session;
    private final boolean requiresRgb;
    public final String label;
    public final String backend;
    public final float cropMarginRatio;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Object lock = new Object();
    private Bitmap queuedRgbCopy;
    private Bitmap queuedIrCopy;
    private AntiSpoofingCallback queuedCallback;
    private long queuedGeneration;
    private volatile long generation;
    private long nextSubmissionId;
    private long latestQueuedSubmissionId;
    private boolean draining;
    private boolean terminalDelivered;
    private volatile boolean closed;

    private AntiSpoofingEngine(ModelLoader.LoadedModel model, int settleFrames, int sampleCount) {
        classifier = model.classifier;
        session = new SessionController(settleFrames, sampleCount);
        requiresRgb = classifier.inputTensorCount() == 2;
        label = model.label;
        backend = classifier.inferenceBackend();
        cropMarginRatio = classifier.cropMarginRatio();
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
            return classify(
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
            ModelLoader.LoadedModel loaded = ModelLoader.loadSelected(
                    application == null ? context : application, slotIndex);
            boolean created = false;
            try {
                AntiSpoofingEngine engine = new AntiSpoofingEngine(
                        loaded, settleFrames, sampleCount);
                created = true;
                return engine;
            } finally {
                if (!created) {
                    try { loaded.classifier.close(); } catch (RuntimeException ignored) {}
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
            return ModelLoader.slotCount(context);
        } catch (Exception e) {
            throw new IllegalStateException("Model manifest failed to load: " + e.getMessage(), e);
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
            recycleQueuedFrame();
            queuedRgbCopy = rgbCopy;
            queuedIrCopy = irCopy;
            queuedCallback = callback;
            queuedGeneration = submittedGeneration;
            if (!draining) {
                draining = true;
                worker.execute(this::drain);
            }
        }
    }

    private void drain() {
        while (true) {
            Bitmap rgbCopy;
            Bitmap irCopy;
            AntiSpoofingCallback callback;
            long frameGeneration;
            synchronized (lock) {
                if (closed || queuedIrCopy == null) {
                    draining = false;
                    return;
                }
                rgbCopy = queuedRgbCopy;
                irCopy = queuedIrCopy;
                callback = queuedCallback;
                frameGeneration = queuedGeneration;
                queuedRgbCopy = null;
                queuedIrCopy = null;
                queuedCallback = null;
            }
            AntiSpoofingResult result;
            boolean notifyResult;
            try {
                synchronized (session) {
                    if (closed || generation != frameGeneration) continue;
                    result = processSession(rgbCopy, irCopy);
                    notifyResult = result.status() == AntiSpoofingResult.Status.ERROR
                            || (result.status() != AntiSpoofingResult.Status.RUNNING
                            && !terminalDelivered);
                    if (result.status() == AntiSpoofingResult.Status.LIVE
                            || result.status() == AntiSpoofingResult.Status.SPOOF) {
                        terminalDelivered = true;
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

    private AntiSpoofingResult processSession(Bitmap rgb, Bitmap ir) {
        AntiSpoofingResult state = session.prepare();
        if (state != null) return state;
        try {
            Bitmap inputRgb = rgb == null ? ir : rgb;
            AntiSpoofingResult inference = classify(inputRgb,
                    new Rect(0, 0, inputRgb.getWidth(), inputRgb.getHeight()),
                    ir, new Rect(0, 0, ir.getWidth(), ir.getHeight()), true);
            if (inference.status() == AntiSpoofingResult.Status.ERROR) {
                return session.fail(inference.errorMessage());
            }
            return session.add(inference.probabilities(), inference.inferenceMs());
        } catch (RuntimeException e) {
            return session.fail("Vision inference failed: " + e.getMessage());
        }
    }

    private AntiSpoofingResult classify(Bitmap rgb, Rect rgbFace, Bitmap ir, Rect irFace,
                                       boolean expanded) {
        try {
            Rect rgbCrop = expanded ? rgbFace : expandFace(
                    rgbFace, cropMarginRatio, rgb.getWidth(), rgb.getHeight());
            Rect irCrop = expanded ? irFace : expandFace(
                    irFace, cropMarginRatio, ir.getWidth(), ir.getHeight());
            return classifier.classify(rgb, rgbCrop, ir, irCrop);
        } catch (RuntimeException e) {
            return AntiSpoofingResult.frameError("Vision inference failed: " + e.getMessage());
        }
    }

    private static void validateBox(Bitmap bitmap, Rect box, String name) {
        if (bitmap.isRecycled() || box.left < 0 || box.top < 0
                || box.right > bitmap.getWidth() || box.bottom > bitmap.getHeight()) {
            throw new IllegalArgumentException(name + " frame or face box is invalid");
        }
    }

    private static Bitmap copyCrop(Bitmap source, Rect face, float margin) {
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
        synchronized (lock) {
            if (closed) return;
            generation++;
            recycleQueuedFrame();
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
            recycleQueuedFrame();
            worker.shutdown();
            synchronized (session) {
                session.close();
                classifier.close();
            }
        }
    }
}
