package com.virditech.ac7000;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.Gravity;
import android.view.TextureView;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

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
    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();
    private final AtomicReference<TrackingFrame> pendingTracking = new AtomicReference<>();
    private final AtomicReference<InferenceTask> pendingInference = new AtomicReference<>();
    private final AtomicBoolean trackingWorkerRunning = new AtomicBoolean();
    private final AtomicBoolean inferenceWorkerRunning = new AtomicBoolean();
    private final AtomicBoolean calibrationRequested = new AtomicBoolean();
    private final Object irLock = new Object();
    private FrameData latestIr;
    private TextureView rgbView;
    private TextureView irView;
    private OverlayView overlay;
    private ProgressBar loadingSpinner;
    private TextView performance;
    private TextView status;
    private TextView resultsLabel;
    private TextView calibrationInstruction;
    private Button switchButton;
    private Button startCollectionButton;
    private TextView collectionProgress;
    private LinearLayout controlsLayout;
    private Button calibrationConfirm;
    private Button calibrationCancel;
    private View calibrationHotspot;
    private DualCameraController cameras;
    private FaceDetector faceDetector;
    private AntiSpoofingClassifier classifier;
    private Calibration calibration;
    private final AppWatchdog appWatchdog = new AppWatchdog();
    private volatile boolean isCollecting;
    private volatile int collectionCount;
    private File currentCollectionDir;
    private boolean showIr;
    private boolean showIrBeforeCalibration;
    private volatile boolean calibrationMode;
    private boolean resumed;
    private int calibrationTapCount;
    private long lastCalibrationTapMs;
    private long lastFaceDetectedMs;
    private String normalStatusMessage = "Initializing...";
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
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED ||
            checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE}, CAMERA_PERMISSION_REQUEST);
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

        loadingSpinner = new ProgressBar(this);
        loadingSpinner.setIndeterminate(true);
        root.addView(loadingSpinner, wrap(Gravity.CENTER, 0, 0));

        performance = label(22f);
        performance.setText(String.format(Locale.US, "Convert RGB/IR %d/%d ms\nDetect %d ms  %.1f FPS\nInference %d ms  %.1f FPS", 0, 0, 0, 0.0f, 0, 0.0f));
        FrameLayout.LayoutParams perfParams = wrap(Gravity.BOTTOM | Gravity.START, 16, 16);
        root.addView(performance, perfParams);

        resultsLabel = label(24f);
        resultsLabel.setTextColor(Color.WHITE);
        resultsLabel.setShadowLayer(5f, 1f, 1f, Color.BLACK);
        FrameLayout.LayoutParams resultsParams = wrap(Gravity.TOP | Gravity.START, 16, 16);
        root.addView(resultsLabel, resultsParams);
        resetResultsLabelToZero();

        status = label(20f);
        status.setText("Initializing...");
        FrameLayout.LayoutParams statusParams = wrap(Gravity.TOP | Gravity.END, 16, 16);
        root.addView(status, statusParams);

        controlsLayout = new LinearLayout(this);
        controlsLayout.setOrientation(LinearLayout.VERTICAL);
        controlsLayout.setGravity(Gravity.END | Gravity.BOTTOM);

        collectionProgress = label(18f);
        collectionProgress.setText("");
        collectionProgress.setVisibility(View.GONE);
        controlsLayout.addView(collectionProgress);

        int buttonWidth = getResources().getDisplayMetrics().widthPixels / 3;

        startCollectionButton = new Button(this);
        startCollectionButton.setText("START CAPTURE");
        startCollectionButton.setEnabled(false);
        startCollectionButton.setOnClickListener(v -> startDataCollection());
        controlsLayout.addView(startCollectionButton, new LinearLayout.LayoutParams(buttonWidth, LinearLayout.LayoutParams.WRAP_CONTENT));

        switchButton = new Button(this);
        switchButton.setText("SHOW IR");
        switchButton.setEnabled(false);
        switchButton.setOnClickListener(v -> {
            setIrVisible(!showIr);
        });
        controlsLayout.addView(switchButton, new LinearLayout.LayoutParams(buttonWidth, LinearLayout.LayoutParams.WRAP_CONTENT));

        root.addView(controlsLayout, wrap(Gravity.BOTTOM | Gravity.END, 16, 16));

        calibrationInstruction = label(24f);
        calibrationInstruction.setText("Fit one face inside the guide, then press CONFIRM");
        calibrationInstruction.setGravity(Gravity.CENTER);
        calibrationInstruction.setVisibility(View.GONE);
        root.addView(calibrationInstruction, wrap(Gravity.TOP | Gravity.CENTER_HORIZONTAL, 16, 24));

        calibrationConfirm = new Button(this);
        calibrationConfirm.setText("CONFIRM");
        calibrationConfirm.setVisibility(View.GONE);
        calibrationConfirm.setOnClickListener(v -> {
            calibrationRequested.set(true);
            calibrationInstruction.setText("Hold still while RGB and IR faces are measured...");
        });
        FrameLayout.LayoutParams confirmParams = wrap(Gravity.BOTTOM | Gravity.END, 16, 16);
        confirmParams.width = buttonWidth;
        root.addView(calibrationConfirm, confirmParams);

        calibrationCancel = new Button(this);
        calibrationCancel.setText("CANCEL");
        calibrationCancel.setVisibility(View.GONE);
        calibrationCancel.setOnClickListener(v -> exitCalibrationMode());
        FrameLayout.LayoutParams cancelParams = wrap(Gravity.BOTTOM | Gravity.START, 16, 16);
        cancelParams.width = buttonWidth;
        root.addView(calibrationCancel, cancelParams);

        calibrationHotspot = new View(this);
        calibrationHotspot.setOnClickListener(v -> recordCalibrationTap());
        root.addView(calibrationHotspot, new FrameLayout.LayoutParams(dp(180), dp(180), Gravity.TOP | Gravity.START));
        setContentView(root);
    }

    private void setIrVisible(boolean visible) {
        showIr = visible;
        rgbView.setAlpha(showIr ? 0f : 1f);
        irView.setAlpha(showIr ? 1f : 0f);
        overlay.setShowIr(showIr);
        overlay.setTranslationX(showIr ? irView.getTranslationX() : 0f);
        overlay.setTranslationY(showIr ? irView.getTranslationY() : 0f);
        switchButton.setText(showIr ? "SHOW RGB" : "SHOW IR");
    }

    private void recordCalibrationTap() {
        long now = SystemClock.elapsedRealtime();
        if (now - lastCalibrationTapMs > 2_000L) calibrationTapCount = 0;
        lastCalibrationTapMs = now;
        if (++calibrationTapCount >= 5) {
            calibrationTapCount = 0;
            enterCalibrationMode();
        }
    }

    private void enterCalibrationMode() {
        if (calibrationMode) return;
        calibrationMode = true;
        calibrationRequested.set(false);
        showIrBeforeCalibration = showIr;
        setIrVisible(true);
        overlay.clearResult();
        overlay.setCalibrationMode(true);
        performance.setVisibility(View.GONE);
        status.setVisibility(View.GONE);
        resultsLabel.setVisibility(View.GONE);
        controlsLayout.setVisibility(View.GONE);
        calibrationHotspot.setVisibility(View.GONE);
        calibrationInstruction.setText("Fit one face inside the guide, then press CONFIRM");
        calibrationInstruction.setVisibility(View.VISIBLE);
        calibrationConfirm.setVisibility(View.VISIBLE);
        calibrationCancel.setVisibility(View.VISIBLE);
        if (cameras != null) cameras.setIrFramesEnabled(true);
    }

    private void exitCalibrationMode() {
        if (!calibrationMode) return;
        calibrationMode = false;
        calibrationRequested.set(false);
        overlay.setCalibrationMode(false);
        overlay.clearResult();
        setIrVisible(showIrBeforeCalibration);
        calibrationInstruction.setVisibility(View.GONE);
        calibrationConfirm.setVisibility(View.GONE);
        calibrationCancel.setVisibility(View.GONE);
        performance.setVisibility(View.VISIBLE);
        status.setText(normalStatusMessage);
        status.setVisibility(View.VISIBLE);
        resetResultsLabelToZero();
        resultsLabel.setVisibility(View.VISIBLE);
        controlsLayout.setVisibility(View.VISIBLE);
        calibrationHotspot.setVisibility(View.VISIBLE);
        if (cameras != null) cameras.setIrFramesEnabled(true);
    }

    private void initializeEngines() {
        trackingExecutor.execute(() -> {
            StringBuilder messages = new StringBuilder();
            try { calibration = Calibration.load(); }
            catch (Exception e) {
                calibration = Calibration.identity();
                messages.append("CALIBRATION NOT SET");
            }
            try { faceDetector = new FaceDetector(getApplicationContext()); }
            catch (Exception e) { append(messages, e.getMessage()); }
            try { classifier = new AntiSpoofingClassifier(getApplicationContext()); }
            catch (Exception e) { append(messages, "MODEL REQUIRED: " + e.getMessage()); }
            String message = messages.length() == 0 ? "Ready" : messages.toString();
            normalStatusMessage = message;
            runOnUiThread(() -> {
                if (cameras != null) cameras.setIrFramesEnabled(true);
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
        cameras.setIrFramesEnabled(true);
        cameras.start();
    }

    @Override protected void onResume() {
        super.onResume();
        resumed = true;
        HardwareControls.setLcdBrightness(90);
        startCameras();
    }

    @Override protected void onPause() {
        if (calibrationMode) exitCalibrationMode();
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
        boolean captureCalibration = calibrationMode && calibrationRequested.getAndSet(false);
        long start = SystemClock.elapsedRealtimeNanos();
        Rect detected = captureCalibration
                ? faceDetector.detectSingle(frame.rgb.bitmap)
                : faceDetector.detectLargest(frame.rgb.bitmap);
        detectionMs = (SystemClock.elapsedRealtimeNanos() - start) / 1_000_000L;
        rgbConversionMs = frame.rgb.conversionMs;
        if (frame.ir != null) irConversionMs = frame.ir.conversionMs;
        updateTrackingFps();
        if (detected == null) {
            runOnUiThread(() -> {
                if (!resumed) return;
                overlay.clearResult();
                if (captureCalibration) {
                    calibrationInstruction.setText("Exactly one RGB face is required. Try again.");
                } else {
                    performance.setText(formatPerformance());
                    if (SystemClock.elapsedRealtime() - lastFaceDetectedMs > 10_000L) {
                        resetResultsLabelToZero();
                    }
                }
            });
            return;
        } else {
            lastFaceDetectedMs = SystemClock.elapsedRealtime();
        }
        int irWidth = frame.ir == null ? frame.rgb.bitmap.getWidth() : frame.ir.bitmap.getWidth();
        int irHeight = frame.ir == null ? frame.rgb.bitmap.getHeight() : frame.ir.bitmap.getHeight();
        Rect irDetected = calibration.rgbToIr(detected, irWidth, irHeight);

        if (captureCalibration) {
            if (frame.ir == null) {
                calibrationRequested.set(true);
                runOnUiThread(() -> calibrationInstruction.setText("Waiting for a synchronized IR frame. Hold still..."));
                return;
            }
            Rect detectedIr = faceDetector.detectSingle(frame.ir.bitmap);
            if (detectedIr == null) {
                runOnUiThread(() -> calibrationInstruction.setText("Exactly one IR face is required. Try again."));
                return;
            }
            if (!calibrationMode) return;
            Calibration measured = Calibration.fromFaces(detected, detectedIr, irWidth);
            try {
                measured.save();
                calibration = measured;
                normalStatusMessage = "Calibration saved";
                runOnUiThread(() -> {
                    if (!resumed) return;
                    exitCalibrationMode();
                    status.setText("Calibration saved");
                });
            } catch (Exception e) {
                runOnUiThread(() -> calibrationInstruction.setText("Unable to save calibration. Try again."));
            }
            return;
        }

        runOnUiThread(() -> {
            if (!resumed) return;
            overlay.showFace(detected, irDetected);
            performance.setText(formatPerformance());
            if (calibration != null && detected != null) {
                float sx = irView.getWidth() / 432f;
                float sy = irView.getHeight() / 768f;
                float scale = detected.width() / calibration.getReferenceFaceWidth();
                float tx = calibration.getHorizontal() * scale * sx;
                float ty = calibration.getVertical() * scale * sy;
                irView.setTranslationX(tx);
                irView.setTranslationY(ty);
                overlay.setTranslationX(showIr ? tx : 0f);
                overlay.setTranslationY(showIr ? ty : 0f);
            } else {
                irView.setTranslationX(0f);
                irView.setTranslationY(0f);
                overlay.setTranslationX(0f);
                overlay.setTranslationY(0f);
            }
        });
        if (calibrationMode) return;

        if (isCollecting && frame.ir != null) {
            final int currentCount = collectionCount + 1;
            if (currentCount <= 100) {
                collectionCount = currentCount;
                float margin = classifier != null ? classifier.cropMarginRatio() : 0.15f;
                Rect rgbR = FaceCrop.expand(detected, margin, frame.rgb.bitmap.getWidth(), frame.rgb.bitmap.getHeight());
                int rL = Math.max(0, rgbR.left);
                int rT = Math.max(0, rgbR.top);
                int rW = Math.min(frame.rgb.bitmap.getWidth() - rL, rgbR.width());
                int rH = Math.min(frame.rgb.bitmap.getHeight() - rT, rgbR.height());
                final Bitmap rgbFace = rW > 0 && rH > 0 ? Bitmap.createBitmap(frame.rgb.bitmap, rL, rT, rW, rH) : null;
                
                Rect irR = FaceCrop.expand(irDetected, margin, frame.ir.bitmap.getWidth(), frame.ir.bitmap.getHeight());
                int iL = Math.max(0, irR.left);
                int iT = Math.max(0, irR.top);
                int iW = Math.min(frame.ir.bitmap.getWidth() - iL, irR.width());
                int iH = Math.min(frame.ir.bitmap.getHeight() - iT, irR.height());
                final Bitmap irFace = iW > 0 && iH > 0 ? Bitmap.createBitmap(frame.ir.bitmap, iL, iT, iW, iH) : null;
                
                final File dir = currentCollectionDir;
                ioExecutor.execute(() -> {
                    if (rgbFace != null) saveBitmap(rgbFace, new File(dir, String.format(Locale.US, "rgb_%03d.jpg", currentCount)));
                    if (irFace != null) saveBitmap(irFace, new File(dir, String.format(Locale.US, "ir_%03d.jpg", currentCount)));
                    if (rgbFace != null) rgbFace.recycle();
                    if (irFace != null) irFace.recycle();
                    runOnUiThread(() -> {
                        collectionProgress.setText("Captured: " + currentCount + "/100");
                        if (currentCount == 100) {
                            isCollecting = false;
                            startCollectionButton.setEnabled(true);
                            switchButton.setEnabled(true);
                            startCollectionButton.setText("START CAPTURE");
                            collectionProgress.setVisibility(View.GONE);
                        }
                    });
                });
            } else {
                isCollecting = false;
            }
        }

        if (isCollecting || classifier == null || frame.ir == null) return;
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
            
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < ClassificationResult.LABELS.length; i++) {
                if (i > 0) sb.append("\n");
                sb.append(String.format(Locale.US, "%s %.1f%%", ClassificationResult.LABELS[i], result.probabilities[i] * 100f));
            }
            resultsLabel.setText(sb.toString());
        });
    }

    private void startDataCollection() {
        if (!Environment.isExternalStorageManager()) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
            intent.setData(Uri.parse("package:" + getPackageName()));
            startActivity(intent);
            status.setText("Please grant All Files Access and try again.");
            return;
        }
        if (isCollecting) return;
        startCollectionButton.setEnabled(false);
        switchButton.setEnabled(false);
        startCollectionButton.setText("COLLECTING...");
        collectionProgress.setVisibility(View.VISIBLE);
        collectionProgress.setText("Captured: 0/100");
        
        ioExecutor.execute(() -> {
            File baseDir = new File("/sdcard/Pictures");
            if (!baseDir.exists() && !baseDir.mkdirs()) {
                runOnUiThread(() -> {
                    status.setText("Failed to create base directory");
                    startCollectionButton.setEnabled(true);
                    switchButton.setEnabled(true);
                    startCollectionButton.setText("START CAPTURE");
                });
                return;
            }
            int n = 1;
            File targetDir;
            while (true) {
                targetDir = new File(baseDir, "train_" + n);
                if (!targetDir.exists()) {
                    if (!targetDir.mkdirs()) {
                        runOnUiThread(() -> {
                            status.setText("Failed to create target directory");
                            startCollectionButton.setEnabled(true);
                            switchButton.setEnabled(true);
                            startCollectionButton.setText("START CAPTURE");
                        });
                        return;
                    }
                    break;
                }
                n++;
            }
            currentCollectionDir = targetDir;
            collectionCount = 0;
            isCollecting = true;
        });
    }

    private void saveBitmap(Bitmap bitmap, File file) {
        try (FileOutputStream out = new FileOutputStream(file)) {
            bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out);
        } catch (IOException e) {
            runOnUiThread(() -> status.setText("Save failed: " + e.getMessage()));
            e.printStackTrace();
        }
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
        if (loadingSpinner.getVisibility() == View.VISIBLE) {
            loadingSpinner.setVisibility(View.GONE);
            if (!isCollecting) {
                startCollectionButton.setEnabled(true);
                switchButton.setEnabled(true);
            }
        }
        return String.format(Locale.US, "Convert RGB/IR %d/%d ms\nDetect %d ms  %.1f FPS\nInference %d ms  %.1f FPS",
                rgbConversionMs, irConversionMs, detectionMs, trackingFps, inferenceMs, inferenceFps);
    }

    private void resetResultsLabelToZero() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < ClassificationResult.LABELS.length; i++) {
            if (i > 0) sb.append("\n");
            sb.append(String.format(Locale.US, "%s 0.0%%", ClassificationResult.LABELS[i]));
        }
        resultsLabel.setText(sb.toString());
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
        ioExecutor.shutdownNow();
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

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static void append(StringBuilder builder, String message) {
        if (builder.length() > 0) builder.append('\n');
        builder.append(message);
    }
}
