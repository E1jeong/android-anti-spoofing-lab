package com.virditech.ac7000;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.TextureView;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.virditech.ac7000.calibration.Calibration;
import com.virditech.ac7000.camera.DualCameraController;
import com.virditech.ac7000.camera.FrameData;
import com.virditech.ac7000.camera.FramePair;
import com.virditech.ac7000.face.FaceDetector;
import com.virditech.ac7000.device.HardwareControls;
import com.virditech.ac7000.device.AppWatchdog;
import com.virditech.ac7000.model.AntiSpoofingClassifier;
import com.virditech.ac7000.model.ClassificationResult;
import com.virditech.ac7000.model.FaceCrop;
import com.virditech.ac7000.ui.OverlayView;

import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public final class MainActivity extends Activity {
    private static final int CAMERA_PERMISSION_REQUEST = 10;
    private static final long MAX_PAIR_DELTA_NS = 50_000_000L;

    private final ExecutorService trackingExecutor = Executors.newSingleThreadExecutor();
    private final ExecutorService inferenceExecutor = Executors.newSingleThreadExecutor();
    private final AtomicReference<TrackingFrame> pendingTracking = new AtomicReference<>();
    private final AtomicReference<InferenceTask> pendingInference = new AtomicReference<>();
    private final AtomicBoolean trackingWorkerRunning = new AtomicBoolean();
    private final AtomicBoolean inferenceWorkerRunning = new AtomicBoolean();
    private final Object irLock = new Object();
    private FrameData latestIr;
    private TextureView rgbView;
    private TextureView irView;
    private OverlayView overlay;
    private TextView performance;
    private TextView status;
    private DualCameraController cameras;
    private FaceDetector faceDetector;
    private AntiSpoofingClassifier classifier;
    private Calibration calibration;
    private final AppWatchdog appWatchdog = new AppWatchdog();
    private boolean showIr;
    private boolean resumed;
    private int trackingFrames;
    private int inferenceFrames;
    private long trackingWindowStartNs;
    private long inferenceWindowStartNs;
    private volatile long rgbConversionMs;
    private volatile long irConversionMs;
    private volatile long detectionMs;
    private volatile long inferenceMs;
    private volatile float trackingFps;
    private volatile float inferenceFps;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
        WindowManager.LayoutParams windowAttributes = getWindow().getAttributes();
        windowAttributes.screenBrightness = 1f;
        getWindow().setAttributes(windowAttributes);
        appWatchdog.start();
        buildUi();
        initializeEngines();
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_REQUEST);
        }
    }

    private void buildUi() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        rgbView = new TextureView(this);
        irView = new TextureView(this);
        irView.setAlpha(0f);
        overlay = new OverlayView(this);
        root.addView(rgbView, match());
        root.addView(irView, match());
        root.addView(overlay, match());

        performance = label(22f);
        FrameLayout.LayoutParams perfParams = wrap(Gravity.TOP | Gravity.END, 16, 16);
        root.addView(performance, perfParams);

        status = label(20f);
        status.setText("Initializing...");
        FrameLayout.LayoutParams statusParams = wrap(Gravity.BOTTOM | Gravity.START, 16, 16);
        root.addView(status, statusParams);

        Button switchButton = new Button(this);
        switchButton.setText("SHOW IR");
        switchButton.setOnClickListener(v -> {
            showIr = !showIr;
            rgbView.setAlpha(showIr ? 0f : 1f);
            irView.setAlpha(showIr ? 1f : 0f);
            overlay.setShowIr(showIr);
            switchButton.setText(showIr ? "SHOW RGB" : "SHOW IR");
        });
        root.addView(switchButton, wrap(Gravity.BOTTOM | Gravity.END, 16, 16));
        setContentView(root);
    }

    private void initializeEngines() {
        trackingExecutor.execute(() -> {
            StringBuilder messages = new StringBuilder();
            try { calibration = Calibration.load(); }
            catch (Exception e) { messages.append("CALIBRATION REQUIRED: ").append(e.getMessage()); }
            try { faceDetector = new FaceDetector(getApplicationContext()); }
            catch (Exception e) { append(messages, e.getMessage()); }
            try { classifier = new AntiSpoofingClassifier(getApplicationContext()); }
            catch (Exception e) { append(messages, "MODEL REQUIRED: " + e.getMessage()); }
            String message = messages.length() == 0 ? "Ready" : messages.toString();
            runOnUiThread(() -> {
                if (cameras != null) cameras.setIrFramesEnabled(classifier != null);
                status.setText(message);
            });
        });
    }

    private void startCameras() {
        if (!resumed || cameras != null || checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) return;
        HardwareControls.setLcdBrightness(90);
        HardwareControls.setIrLed(true);
        cameras = new DualCameraController(this, rgbView, irView, new DualCameraController.Listener() {
            @Override public void onRgb(FrameData frame) { submitTracking(frame); }
            @Override public void onIr(FrameData frame) { offerIr(frame); }
            @Override public void onError(String message) { runOnUiThread(() -> status.setText(message)); }
        });
        cameras.setIrFramesEnabled(classifier != null);
        cameras.start();
    }

    @Override protected void onResume() {
        super.onResume();
        resumed = true;
        HardwareControls.setLcdBrightness(90);
        startCameras();
    }

    @Override protected void onPause() {
        resumed = false;
        if (cameras != null) {
            cameras.stop();
            cameras = null;
        }
        HardwareControls.setIrLed(false);
        clearPendingWork();
        overlay.clearResult();
        super.onPause();
    }

    private void offerIr(FrameData frame) {
        synchronized (irLock) {
            if (latestIr != null) latestIr.recycle();
            latestIr = frame;
        }
    }

    private void submitTracking(FrameData rgb) {
        FrameData ir = null;
        synchronized (irLock) {
            if (latestIr != null) {
                long delta = rgb.timestampNs - latestIr.timestampNs;
                if (Math.abs(delta) <= MAX_PAIR_DELTA_NS) {
                    ir = latestIr;
                    latestIr = null;
                } else if (delta > MAX_PAIR_DELTA_NS) {
                    latestIr.recycle();
                    latestIr = null;
                }
            }
        }
        TrackingFrame replaced = pendingTracking.getAndSet(new TrackingFrame(rgb, ir));
        if (replaced != null) replaced.recycle();
        if (trackingWorkerRunning.compareAndSet(false, true)) trackingExecutor.execute(this::drainTracking);
    }

    private void drainTracking() {
        try {
            TrackingFrame frame;
            while ((frame = pendingTracking.getAndSet(null)) != null) {
                try { processTracking(frame); }
                catch (Exception e) { runOnUiThread(() -> status.setText("Tracking failed")); }
                finally { frame.recycle(); }
            }
        } finally {
            trackingWorkerRunning.set(false);
            if (pendingTracking.get() != null && trackingWorkerRunning.compareAndSet(false, true)) {
                trackingExecutor.execute(this::drainTracking);
            }
        }
    }

    private void processTracking(TrackingFrame frame) {
        if (faceDetector == null || calibration == null) return;
        long start = SystemClock.elapsedRealtimeNanos();
        Rect detected = faceDetector.detectLargest(frame.rgb.bitmap);
        detectionMs = (SystemClock.elapsedRealtimeNanos() - start) / 1_000_000L;
        rgbConversionMs = frame.rgb.conversionMs;
        if (frame.ir != null) irConversionMs = frame.ir.conversionMs;
        updateTrackingFps();
        if (detected == null) {
            runOnUiThread(() -> {
                if (!resumed) return;
                overlay.clearResult();
                performance.setText(formatPerformance());
            });
            return;
        }
        int irWidth = frame.ir == null ? frame.rgb.bitmap.getWidth() : frame.ir.bitmap.getWidth();
        int irHeight = frame.ir == null ? frame.rgb.bitmap.getHeight() : frame.ir.bitmap.getHeight();
        Rect irDetected = calibration.rgbToIr(detected, irWidth, irHeight);
        runOnUiThread(() -> {
            if (!resumed) return;
            overlay.showFace(detected, irDetected);
            performance.setText(formatPerformance());
        });
        if (classifier == null || frame.ir == null) return;
        Rect rgbCrop = FaceCrop.expand(detected, classifier.cropMarginRatio(), frame.rgb.bitmap.getWidth(), frame.rgb.bitmap.getHeight());
        Rect irCrop = FaceCrop.expand(irDetected, classifier.cropMarginRatio(), frame.ir.bitmap.getWidth(), frame.ir.bitmap.getHeight());
        submitInference(new InferenceTask(frame.detachPair(), rgbCrop, irCrop));
    }

    private void submitInference(InferenceTask task) {
        InferenceTask replaced = pendingInference.getAndSet(task);
        if (replaced != null) replaced.recycle();
        if (inferenceWorkerRunning.compareAndSet(false, true)) inferenceExecutor.execute(this::drainInference);
    }

    private void drainInference() {
        try {
            InferenceTask task;
            while ((task = pendingInference.getAndSet(null)) != null) {
                try { processInference(task); }
                catch (Exception e) { runOnUiThread(() -> status.setText("Inference failed")); }
                finally { task.recycle(); }
            }
        } finally {
            inferenceWorkerRunning.set(false);
            if (pendingInference.get() != null && inferenceWorkerRunning.compareAndSet(false, true)) {
                inferenceExecutor.execute(this::drainInference);
            }
        }
    }

    private void processInference(InferenceTask task) {
        ClassificationResult result = classifier.classify(task.pair.rgb.bitmap, task.rgbCrop,
                task.pair.ir.bitmap, task.irCrop);
        inferenceMs = result.inferenceMs;
        updateInferenceFps();
        runOnUiThread(() -> {
            if (!resumed) return;
            overlay.showResult(result);
            performance.setText(formatPerformance());
        });
    }

    private void updateTrackingFps() {
        long now = SystemClock.elapsedRealtimeNanos();
        if (trackingWindowStartNs == 0L) trackingWindowStartNs = now;
        trackingFrames++;
        long elapsed = now - trackingWindowStartNs;
        if (elapsed >= 1_000_000_000L) {
            trackingFps = trackingFrames * 1_000_000_000f / elapsed;
            trackingFrames = 0;
            trackingWindowStartNs = now;
        }
    }

    private void updateInferenceFps() {
        long now = SystemClock.elapsedRealtimeNanos();
        if (inferenceWindowStartNs == 0L) inferenceWindowStartNs = now;
        inferenceFrames++;
        long elapsed = now - inferenceWindowStartNs;
        if (elapsed >= 1_000_000_000L) {
            inferenceFps = inferenceFrames * 1_000_000_000f / elapsed;
            inferenceFrames = 0;
            inferenceWindowStartNs = now;
        }
    }

    private String formatPerformance() {
        return String.format(Locale.US, "Convert RGB/IR %d/%d ms\nDetect %d ms  %.1f FPS\nInference %d ms  %.1f FPS",
                rgbConversionMs, irConversionMs, detectionMs, trackingFps, inferenceMs, inferenceFps);
    }

    private void clearPendingWork() {
        TrackingFrame tracking = pendingTracking.getAndSet(null);
        if (tracking != null) tracking.recycle();
        InferenceTask inference = pendingInference.getAndSet(null);
        if (inference != null) inference.recycle();
        synchronized (irLock) {
            if (latestIr != null) latestIr.recycle();
            latestIr = null;
        }
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_PERMISSION_REQUEST && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) startCameras();
        else status.setText("CAMERA permission denied");
    }

    @Override protected void onDestroy() {
        if (cameras != null) cameras.stop();
        HardwareControls.setIrLed(false);
        clearPendingWork();
        trackingExecutor.shutdownNow();
        inferenceExecutor.shutdownNow();
        if (classifier != null) classifier.close();
        if (faceDetector != null) faceDetector.close();
        appWatchdog.close();
        super.onDestroy();
    }

    private static final class TrackingFrame {
        private FrameData rgb;
        private FrameData ir;

        TrackingFrame(FrameData rgb, FrameData ir) {
            this.rgb = rgb;
            this.ir = ir;
        }

        FramePair detachPair() {
            FramePair pair = new FramePair(rgb, ir);
            rgb = null;
            ir = null;
            return pair;
        }

        void recycle() {
            if (rgb != null) rgb.recycle();
            if (ir != null) ir.recycle();
            rgb = null;
            ir = null;
        }
    }

    private static final class InferenceTask {
        final FramePair pair;
        final Rect rgbCrop;
        final Rect irCrop;

        InferenceTask(FramePair pair, Rect rgbCrop, Rect irCrop) {
            this.pair = pair;
            this.rgbCrop = rgbCrop;
            this.irCrop = irCrop;
        }

        void recycle() {
            pair.recycle();
        }
    }

    private TextView label(float size) {
        TextView view = new TextView(this);
        view.setTextColor(Color.WHITE);
        view.setTextSize(size);
        view.setShadowLayer(5f, 1f, 1f, Color.BLACK);
        return view;
    }

    private static FrameLayout.LayoutParams match() {
        return new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT);
    }

    private static FrameLayout.LayoutParams wrap(int gravity, int horizontalMargin, int verticalMargin) {
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, gravity);
        params.setMargins(horizontalMargin, verticalMargin, horizontalMargin, verticalMargin);
        return params;
    }

    private static void append(StringBuilder builder, String message) {
        if (builder.length() > 0) builder.append('\n');
        builder.append(message);
    }
}
