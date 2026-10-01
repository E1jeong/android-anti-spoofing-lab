package com.unionbiometrics.vision;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.unionbiometrics.vision.internal.FrameInput;
import com.unionbiometrics.vision.internal.FaceCrop;
import com.unionbiometrics.vision.internal.SlotClassifier;
import com.unionbiometrics.vision.internal.AntiSpoofingEngineImpl;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Product anti-spoofing entry point. One instance owns one model slot and session. */
public final class AntiSpoofingEngine implements AutoCloseable {
    public static final class AntiSpoofingException extends Exception {
        public AntiSpoofingException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public static final class Options {
        public enum Mode { SYNC, LIVE }

        private final int slotIndex;
        private final int irSettleFrameCount;
        private final int sampleCount;
        private final Mode mode;
        private final Executor callbackExecutor;

        public Options(int slotIndex, int irSettleFrameCount, int sampleCount) {
            this(slotIndex, irSettleFrameCount, sampleCount, Mode.SYNC, null);
        }

        private Options(int slotIndex, int irSettleFrameCount, int sampleCount,
                        Mode mode, Executor callbackExecutor) {
            if (slotIndex < 0) throw new IllegalArgumentException("slotIndex must be >= 0");
            if (irSettleFrameCount < 0) {
                throw new IllegalArgumentException("irSettleFrameCount must be >= 0");
            }
            if (sampleCount <= 0) throw new IllegalArgumentException("sampleCount must be > 0");
            if (mode == null) throw new IllegalArgumentException("mode must not be null");
            this.slotIndex = slotIndex;
            this.irSettleFrameCount = irSettleFrameCount;
            this.sampleCount = sampleCount;
            this.mode = mode;
            this.callbackExecutor = callbackExecutor;
        }

        public static Options defaults() { return new Options(0, 10, 3); }
        public Options withSlotIndex(int index) {
            return new Options(index, irSettleFrameCount, sampleCount, mode, callbackExecutor);
        }
        /** Uses asynchronous inference; null dispatches callbacks on the main looper. */
        public Options live(Executor executor) {
            return new Options(slotIndex, irSettleFrameCount, sampleCount, Mode.LIVE, executor);
        }
        public int slotIndex() { return slotIndex; }
        public int irSettleFrameCount() { return irSettleFrameCount; }
        public int sampleCount() { return sampleCount; }
        public Mode mode() { return mode; }
    }

    public static final class Frame {
        private final Bitmap rgb;
        private final Rect rgbFace;
        private final Bitmap ir;
        private final Rect irFace;
        private final long rgbTimestampNs;
        private final long irTimestampNs;

        private Frame(Bitmap rgb, Rect rgbFace, Bitmap ir, Rect irFace,
                      long rgbTimestampNs, long irTimestampNs) {
            if (ir == null || irFace == null || irFace.isEmpty()) {
                throw new IllegalArgumentException("IR bitmap and nonempty face box are required");
            }
            if ((rgb == null) != (rgbFace == null) || (rgbFace != null && rgbFace.isEmpty())) {
                throw new IllegalArgumentException("RGB bitmap and nonempty face box must be paired");
            }
            if (rgbTimestampNs < 0 || irTimestampNs < 0) {
                throw new IllegalArgumentException("Timestamps must be >= 0");
            }
            validateBox(ir, irFace, "IR");
            if (rgb != null) validateBox(rgb, rgbFace, "RGB");
            this.rgb = rgb;
            this.rgbFace = rgbFace == null ? null : new Rect(rgbFace);
            this.ir = ir;
            this.irFace = new Rect(irFace);
            this.rgbTimestampNs = rgbTimestampNs;
            this.irTimestampNs = irTimestampNs;
        }

        private static void validateBox(Bitmap bitmap, Rect box, String name) {
            if (bitmap.isRecycled() || box.left < 0 || box.top < 0
                    || box.right > bitmap.getWidth() || box.bottom > bitmap.getHeight()) {
                throw new IllegalArgumentException(name + " frame or face box is invalid");
            }
        }

        /** Borrows an IR bitmap and its unexpanded camera-coordinate face box. */
        public static Frame ir(Bitmap ir, Rect irFace) {
            return new Frame(null, null, ir, irFace, 0, 0);
        }
        /** Borrows paired RGB/IR bitmaps; frame pairing remains the host's responsibility. */
        public static Frame dual(Bitmap rgb, Rect rgbFace, Bitmap ir, Rect irFace,
                                 long rgbTimestampNs, long irTimestampNs) {
            if (rgb == null || rgbFace == null) {
                throw new IllegalArgumentException("RGB bitmap and face box are required");
            }
            return new Frame(rgb, rgbFace, ir, irFace, rgbTimestampNs, irTimestampNs);
        }
        public long rgbTimestampNs() { return rgbTimestampNs; }
        public long irTimestampNs() { return irTimestampNs; }
    }

    private final AntiSpoofingEngineImpl delegate;
    private final boolean dualInput;
    private final Options options;
    private final Executor callbackExecutor;
    private final ExecutorService worker;
    private final Object queueLock = new Object();
    private Snapshot pending;
    private boolean draining;
    private boolean closed;
    private long generation;
    private long nextSubmissionId;
    private long latestQueuedSubmissionId;

    private AntiSpoofingEngine(SlotClassifier slot, Options options) {
        this.delegate = new AntiSpoofingEngineImpl(slot,
                options.irSettleFrameCount, options.sampleCount);
        this.dualInput = slot.isDualInput();
        this.options = options;
        this.callbackExecutor = options.callbackExecutor != null
                ? options.callbackExecutor
                : command -> new Handler(Looper.getMainLooper()).post(command);
        this.worker = options.mode == Options.Mode.LIVE
                ? Executors.newSingleThreadExecutor() : null;
    }

    /** Loads and warms manifest slot 0. Call from a background thread. */
    public static AntiSpoofingEngine create(Context context) throws AntiSpoofingException {
        return create(context, Options.defaults());
    }

    /** Loads and warms the selected zero-based manifest slot. Call from a background thread. */
    public static AntiSpoofingEngine create(Context context, Options options)
            throws AntiSpoofingException {
        if (context == null || options == null) {
            throw new IllegalArgumentException("context and options must not be null");
        }
        Context application = context.getApplicationContext();
        try {
            return new AntiSpoofingEngine(SlotClassifier.loadSelected(
                    application == null ? context : application, options.slotIndex), options);
        } catch (Exception e) {
            throw new AntiSpoofingException("Model slot " + options.slotIndex
                    + " failed to load: " + e.getMessage(), e);
        }
    }

    public String label() { return delegate.label(); }
    public String backend() { return delegate.backend(); }
    public float cropMarginRatio() { return delegate.cropMarginRatio(); }
    public boolean requiresRgb() { return dualInput; }

    /** Processes one borrowed frame on the caller's thread in SYNC mode. */
    public AntiSpoofingResult process(Frame frame) {
        if (options.mode != Options.Mode.SYNC) {
            throw new IllegalStateException("process requires SYNC mode");
        }
        validate(frame);
        synchronized (queueLock) {
            if (closed) throw new IllegalStateException("Vision engine is closed");
        }
        return runFrame(borrowed(frame));
    }

    /** Copies expanded crops before returning. Superseded or reset frames receive no callback. */
    public void submit(Frame frame, AntiSpoofingCallback callback) {
        if (options.mode != Options.Mode.LIVE) {
            throw new IllegalStateException("submit requires LIVE mode");
        }
        if (callback == null) throw new IllegalArgumentException("callback must not be null");
        validate(frame);
        final long submittedGeneration;
        final long submissionId;
        synchronized (queueLock) {
            if (closed) throw new IllegalStateException("Vision engine is closed");
            submittedGeneration = generation;
            submissionId = ++nextSubmissionId;
        }
        Snapshot snapshot = snapshot(frame, callback);
        synchronized (queueLock) {
            if (closed) {
                snapshot.recycle();
                throw new IllegalStateException("Vision engine is closed");
            }
            if (generation != submittedGeneration || submissionId < latestQueuedSubmissionId) {
                snapshot.recycle();
                return;
            }
            latestQueuedSubmissionId = submissionId;
            snapshot.generation = submittedGeneration;
            if (pending != null) pending.recycle();
            pending = snapshot;
            if (!draining) {
                draining = true;
                worker.execute(this::drain);
            }
        }
    }

    private void drain() {
        try {
            while (true) {
                Snapshot next;
                synchronized (queueLock) {
                    next = pending;
                    pending = null;
                    if (next == null || closed) {
                        if (next != null) next.recycle();
                        draining = false;
                        return;
                    }
                }
                AntiSpoofingResult result;
                try {
                    result = runFrame(next.frame);
                } catch (RuntimeException e) {
                    result = AntiSpoofingResult.fromInternal(
                            com.unionbiometrics.vision.internal.SessionResult.error(
                                    "Vision inference failed: " + e.getMessage()), 0, 0);
                } finally {
                    next.recycle();
                }
                final long submittedGeneration = next.generation;
                final AntiSpoofingResult delivered = result;
                try {
                    callbackExecutor.execute(() -> {
                        synchronized (queueLock) {
                            if (closed || generation != submittedGeneration) return;
                        }
                        next.callback.onResult(delivered);
                    });
                } catch (RuntimeException e) {
                    Log.e("AntiSpoofingEngine", "Live callback failed", e);
                }
            }
        } catch (RuntimeException e) {
            Log.e("AntiSpoofingEngine", "Live worker failed", e);
            synchronized (queueLock) {
                if (pending != null) pending.recycle();
                pending = null;
                draining = false;
            }
        }
    }

    private AntiSpoofingResult runFrame(FrameInput frame) {
        synchronized (delegate) {
            com.unionbiometrics.vision.internal.SessionResult result = delegate.process(frame);
            return AntiSpoofingResult.fromInternal(
                    result, delegate.settleRemaining(), delegate.acceptedSamples());
        }
    }

    private void validate(Frame frame) {
        if (frame == null) throw new IllegalArgumentException("frame must not be null");
        if (dualInput != (frame.rgb != null)) {
            throw new IllegalArgumentException(dualInput
                    ? "RGB and IR frame required by this slot" : "IR-only frame required by this slot");
        }
    }

    private static FrameInput borrowed(Frame frame) {
        Bitmap rgb = frame.rgb == null ? frame.ir : frame.rgb;
        Rect rgbFace = frame.rgbFace == null ? frame.irFace : frame.rgbFace;
        return new FrameInput(rgb, rgbFace, frame.ir, frame.irFace);
    }

    private Snapshot snapshot(Frame frame, AntiSpoofingCallback callback) {
        float margin = cropMarginRatio();
        Bitmap irCopy = copyCrop(frame.ir, frame.irFace, margin);
        Bitmap rgbCopy = null;
        try {
            if (frame.rgb != null) rgbCopy = copyCrop(frame.rgb, frame.rgbFace, margin);
            Bitmap rgb = rgbCopy == null ? irCopy : rgbCopy;
            FrameInput copied = new FrameInput(
                    rgb, new Rect(0, 0, rgb.getWidth(), rgb.getHeight()),
                    irCopy, new Rect(0, 0, irCopy.getWidth(), irCopy.getHeight()), true);
            return new Snapshot(copied, rgbCopy, irCopy, callback);
        } catch (RuntimeException e) {
            irCopy.recycle();
            if (rgbCopy != null) rgbCopy.recycle();
            throw e;
        }
    }

    private static Bitmap copyCrop(Bitmap source, Rect face, float margin) {
        Rect crop = FaceCrop.expand(face, margin, source.getWidth(), source.getHeight());
        Bitmap copy = Bitmap.createBitmap(crop.width(), crop.height(), Bitmap.Config.ARGB_8888);
        try {
            Canvas canvas = new Canvas(copy);
            canvas.drawBitmap(source, crop,
                    new Rect(0, 0, copy.getWidth(), copy.getHeight()), new Paint());
            return copy;
        } catch (RuntimeException e) {
            copy.recycle();
            throw e;
        }
    }

    public void reset() {
        synchronized (queueLock) {
            if (closed) return;
            generation++;
            if (pending != null) pending.recycle();
            pending = null;
            delegate.reset();
        }
    }

    @Override public void close() {
        synchronized (queueLock) {
            if (closed) return;
            closed = true;
            generation++;
            if (pending != null) pending.recycle();
            pending = null;
            if (worker != null) worker.shutdown();
            delegate.close();
        }
    }

    private static final class Snapshot {
        final FrameInput frame;
        final Bitmap rgbCopy;
        final Bitmap irCopy;
        final AntiSpoofingCallback callback;
        long generation;

        Snapshot(FrameInput frame, Bitmap rgbCopy, Bitmap irCopy,
                 AntiSpoofingCallback callback) {
            this.frame = frame;
            this.rgbCopy = rgbCopy;
            this.irCopy = irCopy;
            this.callback = callback;
        }
        void recycle() {
            if (rgbCopy != null) rgbCopy.recycle();
            irCopy.recycle();
        }
    }
}
