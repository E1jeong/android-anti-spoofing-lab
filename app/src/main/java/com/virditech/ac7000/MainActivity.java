package com.virditech.ac7000;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Rect;
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
import android.widget.ImageView;
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
import com.virditech.ac7000.device.UbimDaemonClient;
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
    private static final long MAX_PAIR_DELTA_NS = 150_000_000L;

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
    private final Object irPreviewLock = new Object();
    private Bitmap latestIrBitmapForCrop;
    private TextureView rgbView;
    private TextureView irView;
    private ImageView irPreviewView;
    private OverlayView overlay;
    private ProgressBar loadingSpinner;
    private ProgressBar irLoadingSpinner;
    private TextView performance;
    private TextView status;
    private ImageView faceCropView;
    private Bitmap currentPreviewFace;
    private Bitmap currentIrPreviewFrame;
    private TextView noFaceLabel;
    private TextView resultsLabel;
    private TextView calibrationInstruction;
    private Button switchButton;
    private Button modelSwitchButton;
    private boolean isNpuModelSelected = false;
    private final Object classifierLock = new Object();
    private AntiSpoofingClassifier standardClassifier;
    private AntiSpoofingClassifier npuClassifier;
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
    private final AppWatchdog appWatchdog = AppWatchdog.getInstance();
    private volatile boolean isCollecting;
    private volatile boolean ioBusy;
    private volatile int collectionCount;
    private File collectionRawRoot;
    private int collectionStartSubjectId;
    private String collectionClassName = "live";
    private LinearLayout expandableLayout;
    private volatile boolean showIr;
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
    private long lastUiUpdateTimeMs;
    private long lastPreviewUpdateTimeMs;
    private long lastIrPreviewUpdateTimeMs;

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
        irPreviewView = new ImageView(this);
        irPreviewView.setAlpha(0f);
        irPreviewView.setScaleX(-1f);
        irPreviewView.setScaleType(ImageView.ScaleType.FIT_XY);
        overlay = new OverlayView(this);
        root.addView(rgbView, match());
        root.addView(irView, match());
        root.addView(irPreviewView, match());
        root.addView(overlay, match());

        loadingSpinner = new ProgressBar(this);
        loadingSpinner.setIndeterminate(true);
        root.addView(loadingSpinner, wrap(Gravity.CENTER, 0, 0));

        performance = label(22f);
        performance.setText(String.format(Locale.US, "Convert RGB/IR %d/%d ms\nDetect %d ms  %.1f FPS\nInference %d ms  %.1f FPS\nBackend MODEL", 0, 0, 0, 0.0f, 0, 0.0f));
        FrameLayout.LayoutParams perfParams = wrap(Gravity.BOTTOM | Gravity.START, 16, 56);
        root.addView(performance, perfParams);

        int buttonWidth = getResources().getDisplayMetrics().widthPixels / 3;

        resultsLabel = label(32f);
        resultsLabel.setTextColor(Color.WHITE);
        resultsLabel.setShadowLayer(5f, 1f, 1f, Color.BLACK);
        FrameLayout.LayoutParams resultsParams = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.START);
        resultsParams.setMargins(dp(16), dp(16), buttonWidth + dp(16), dp(16));
        root.addView(resultsLabel, resultsParams);
        resetResultsLabelToZero();

        status = label(22f);
        status.setText("Initializing...");
        FrameLayout.LayoutParams statusParams = wrap(Gravity.BOTTOM | Gravity.START, 16, 16);
        root.addView(status, statusParams);

        FrameLayout irCropContainer = new FrameLayout(this);
        FrameLayout.LayoutParams irCropParams = wrap(Gravity.TOP | Gravity.END, 0, 0);
        irCropParams.width = buttonWidth;
        irCropParams.height = buttonWidth;
        root.addView(irCropContainer, irCropParams);

        faceCropView = new ImageView(this);
        faceCropView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        faceCropView.setBackgroundColor(Color.parseColor("#44000000"));
        irCropContainer.addView(faceCropView, match());

        noFaceLabel = label(20f);
        noFaceLabel.setText("NO FACE");
        noFaceLabel.setVisibility(View.GONE);
        irCropContainer.addView(noFaceLabel, wrap(Gravity.CENTER, 0, 0));

        irLoadingSpinner = new ProgressBar(this);
        irLoadingSpinner.setIndeterminate(true);
        irCropContainer.addView(irLoadingSpinner, wrap(Gravity.CENTER, 0, 0));

        controlsLayout = new LinearLayout(this);
        controlsLayout.setOrientation(LinearLayout.VERTICAL);
        controlsLayout.setGravity(Gravity.END | Gravity.BOTTOM);

        collectionProgress = label(24f);
        collectionProgress.setText("");
        collectionProgress.setVisibility(View.GONE);
        controlsLayout.addView(collectionProgress);

        expandableLayout = new LinearLayout(this);
        expandableLayout.setOrientation(LinearLayout.VERTICAL);
        expandableLayout.setGravity(Gravity.END);
        expandableLayout.setVisibility(View.GONE);

        String[] classes = {"live", "display", "picture", "print", "mask"};
        for (String c : classes) {
            Button btn = new Button(this);
            btn.setText(c);
            btn.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#37474F")));
            btn.setTextColor(Color.WHITE);
            btn.setOnClickListener(v -> {
                int nextNum = getNextSubjectNumber(c);
                startDataCollection(c, nextNum);
                expandableLayout.setVisibility(View.GONE);
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(buttonWidth, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = dp(4);
            expandableLayout.addView(btn, lp);
        }
        controlsLayout.addView(expandableLayout);

        startCollectionButton = new Button(this);
        startCollectionButton.setText("START CAPTURE");
        startCollectionButton.setEnabled(false);
        startCollectionButton.setOnClickListener(v -> {
            if (expandableLayout.getVisibility() == View.GONE) {
                expandableLayout.setVisibility(View.VISIBLE);
                startCollectionButton.setText("CANCEL");
            } else {
                expandableLayout.setVisibility(View.GONE);
                startCollectionButton.setText("START CAPTURE");
            }
        });
        controlsLayout.addView(startCollectionButton, new LinearLayout.LayoutParams(buttonWidth, LinearLayout.LayoutParams.WRAP_CONTENT));

        switchButton = new Button(this);
        switchButton.setText("SHOW IR");
        switchButton.setEnabled(false);
        switchButton.setOnClickListener(v -> {
            setIrVisible(!showIr);
        });
        controlsLayout.addView(switchButton, new LinearLayout.LayoutParams(buttonWidth, LinearLayout.LayoutParams.WRAP_CONTENT));

        modelSwitchButton = new Button(this);
        modelSwitchButton.setText("MODEL: Standard");
        modelSwitchButton.setEnabled(false);
        modelSwitchButton.setOnClickListener(v -> toggleModel());
        controlsLayout.addView(modelSwitchButton, new LinearLayout.LayoutParams(buttonWidth, LinearLayout.LayoutParams.WRAP_CONTENT));

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
        irView.setAlpha(0f);
        irPreviewView.setAlpha(showIr ? 1f : 0f);
        overlay.setShowIr(showIr);
        overlay.setTranslationX(showIr ? irPreviewView.getTranslationX() : 0f);
        overlay.setTranslationY(showIr ? irPreviewView.getTranslationY() : 0f);
        if (!showIr) {
            clearIrPreviewFrame();
        } else {
            synchronized (irPreviewLock) {
                if (latestIrBitmapForCrop != null) {
                    latestIrBitmapForCrop.recycle();
                    latestIrBitmapForCrop = null;
                }
            }
        }
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
            try {
                UbimDaemonClient daemon = new UbimDaemonClient();
                daemon.command("ubim cli.command appops set com.virditech.ac7000 MANAGE_EXTERNAL_STORAGE allow");
            } catch (Exception e) {
                android.util.Log.e("MainActivity", "Failed to auto-grant MANAGE_EXTERNAL_STORAGE", e);
            }
            StringBuilder messages = new StringBuilder();
            Calibration.setAppStorageDir(getFilesDir());
            try { calibration = Calibration.load(); }
            catch (Exception e) {
                android.util.Log.e("MainActivity", "Failed to load calibration config", e);
                calibration = Calibration.identity();
                messages.append("CALIBRATION NOT SET");
            }
            try { faceDetector = new FaceDetector(getApplicationContext()); }
            catch (Exception e) { append(messages, e.getMessage()); }
            synchronized (classifierLock) {
                try {
                    standardClassifier = new AntiSpoofingClassifier(getApplicationContext(), "anti_spoofing.tflite", "model_spec.json");
                } catch (Exception e) {
                    append(messages, "STANDARD MODEL FAILED: " + e.getMessage());
                }

                String npuModelName = "anti_spoofing.tflite";
                try {
                    String[] assets = getAssets().list("");
                    if (assets != null) {
                        for (String asset : assets) {
                            if ("anti_spoofing_npu.tflite".equals(asset)) {
                                npuModelName = "anti_spoofing_npu.tflite";
                                break;
                            }
                        }
                    }
                } catch (IOException e) {
                    // ignore
                }

                try {
                    npuClassifier = new AntiSpoofingClassifier(getApplicationContext(), npuModelName, "model_spec_npu.json");
                } catch (Exception e) {
                    append(messages, "NPU MODEL FAILED: " + e.getMessage());
                }

                classifier = standardClassifier;
            }
            String message = messages.length() == 0 ? classifier.backendStatus() : messages.toString();
            normalStatusMessage = message;
            runOnUiThread(() -> {
                if (cameras != null) cameras.setIrFramesEnabled(true);
                if (modelSwitchButton != null) modelSwitchButton.setEnabled(true);
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
            @Override public void onError(String message) { showTransientStatus(message); }
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
        clearIrPreviewFrame();
        synchronized (irPreviewLock) {
            if (latestIrBitmapForCrop != null) {
                latestIrBitmapForCrop.recycle();
                latestIrBitmapForCrop = null;
            }
        }
        super.onPause();
    }

    private void offerIr(FrameData frame) {
        updateIrPreview(frame);
        if (!showIr) {
            synchronized (irPreviewLock) {
                if (latestIrBitmapForCrop != null) {
                    latestIrBitmapForCrop.recycle();
                }
                Bitmap.Config config = frame.bitmap.getConfig();
                latestIrBitmapForCrop = frame.bitmap.copy(config == null ? Bitmap.Config.ARGB_8888 : config, false);
            }
        }
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
                catch (Exception e) {
                    android.util.Log.e("MainActivity", "Tracking failed in drainTracking", e);
                    showTransientStatus("Tracking failed");
                }
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
                clearPreviewFace();
                faceCropView.setScaleX(1f);
                noFaceLabel.setVisibility(View.VISIBLE);
                if (captureCalibration) {
                    calibrationInstruction.setText("Exactly one RGB face is required. Try again.");
                } else {
                    long now = SystemClock.elapsedRealtime();
                    if (now - lastUiUpdateTimeMs >= 150L) {
                        performance.setText(formatPerformance());
                        lastUiUpdateTimeMs = now;
                    }
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
            try {
                Calibration measured = Calibration.fromFaces(detected, detectedIr, irWidth);
                measured.save();
                calibration = measured;
                normalStatusMessage = "Calibration saved";
                runOnUiThread(() -> {
                    if (!resumed) return;
                    exitCalibrationMode();
                    status.setText("Calibration saved");
                });
            } catch (Exception e) {
                runOnUiThread(() -> calibrationInstruction.setText("Unable to save calibration: " + e.getMessage()));
            }
            return;
        }

        Bitmap previewFace = null;
        boolean previewRgb = showIr;
        long previewNow = SystemClock.elapsedRealtime();
        if (previewNow - lastPreviewUpdateTimeMs >= 66L && classifier != null) {
            lastPreviewUpdateTimeMs = previewNow;
            if (previewRgb) {
                Bitmap source = frame.rgb.bitmap;
                Rect face = detected;
                float margin = classifier.cropMarginRatio();
                Rect crop = FaceCrop.expand(face, margin, source.getWidth(), source.getHeight());
                int left = Math.max(0, crop.left);
                int top = Math.max(0, crop.top);
                int width = Math.min(source.getWidth() - left, crop.width());
                int height = Math.min(source.getHeight() - top, crop.height());
                if (width > 0 && height > 0) {
                    previewFace = Bitmap.createBitmap(source, left, top, width, height);
                }
            } else {
                synchronized (irPreviewLock) {
                    if (latestIrBitmapForCrop != null && !latestIrBitmapForCrop.isRecycled()) {
                        Bitmap source = latestIrBitmapForCrop;
                        Rect face = irDetected;
                        float margin = classifier.cropMarginRatio();
                        Rect crop = FaceCrop.expand(face, margin, source.getWidth(), source.getHeight());
                        int left = Math.max(0, crop.left);
                        int top = Math.max(0, crop.top);
                        int width = Math.min(source.getWidth() - left, crop.width());
                        int height = Math.min(source.getHeight() - top, crop.height());
                        if (width > 0 && height > 0) {
                            previewFace = Bitmap.createBitmap(source, left, top, width, height);
                        }
                    }
                }
            }
        }
        final Bitmap finalPreviewFace = previewFace;
        final boolean finalPreviewRgb = previewRgb;

        runOnUiThread(() -> {
            if (!resumed) {
                if (finalPreviewFace != null) finalPreviewFace.recycle();
                return;
            }
            overlay.showFace(detected, irDetected);
            long now = SystemClock.elapsedRealtime();
            if (now - lastUiUpdateTimeMs >= 150L) {
                performance.setText(formatPerformance());
                lastUiUpdateTimeMs = now;
            }
            noFaceLabel.setVisibility(View.GONE);
            if (finalPreviewFace != null) {
                setPreviewFace(finalPreviewFace, finalPreviewRgb);
            }
            if (calibration != null && detected != null) {
                float sx = irPreviewView.getWidth() / 432f;
                float sy = irPreviewView.getHeight() / 768f;
                float scale = detected.width() / calibration.getReferenceFaceWidth();
                float tx = calibration.getHorizontal() * scale * sx;
                float ty = calibration.getVertical() * scale * sy;
                irPreviewView.setTranslationX(tx);
                irPreviewView.setTranslationY(ty);
                overlay.setTranslationX(showIr ? tx : 0f);
                overlay.setTranslationY(showIr ? ty : 0f);
            } else {
                irPreviewView.setTranslationX(0f);
                irPreviewView.setTranslationY(0f);
                overlay.setTranslationX(0f);
                overlay.setTranslationY(0f);
            }
        });
        if (calibrationMode) return;

        if (isCollecting && frame.ir != null && !ioBusy) {
            ioBusy = true;
            final int currentCount = collectionCount + 1;
            if (currentCount <= 100) {
                collectionCount = currentCount;
                float margin = classifier != null ? classifier.cropMarginRatio() : 0.10f;
                Rect rgbR = FaceCrop.expand(detected, margin, frame.rgb.bitmap.getWidth(), frame.rgb.bitmap.getHeight());
                int rL = Math.max(0, rgbR.left);
                int rT = Math.max(0, rgbR.top);
                int rW = Math.min(frame.rgb.bitmap.getWidth() - rL, rgbR.width());
                int rH = Math.min(frame.rgb.bitmap.getHeight() - rT, rgbR.height());
                final Bitmap cropRGB = rW > 0 && rH > 0 ? Bitmap.createBitmap(frame.rgb.bitmap, rL, rT, rW, rH) : null;
                
                Rect irR = FaceCrop.expand(irDetected, margin, frame.ir.bitmap.getWidth(), frame.ir.bitmap.getHeight());
                int iL = Math.max(0, irR.left);
                int iT = Math.max(0, irR.top);
                int iW = Math.min(frame.ir.bitmap.getWidth() - iL, irR.width());
                int iH = Math.min(frame.ir.bitmap.getHeight() - iT, irR.height());
                final Bitmap cropIR = iW > 0 && iH > 0 ? Bitmap.createBitmap(frame.ir.bitmap, iL, iT, iW, iH) : null;
                
                final Bitmap fullRGB = frame.rgb.bitmap.copy(frame.rgb.bitmap.getConfig(), false);
                final Bitmap fullIR = frame.ir.bitmap.copy(frame.ir.bitmap.getConfig(), false);

                final String subjectDirName = collectionClassName + "_" + collectionStartSubjectId;
                final File root = collectionRawRoot != null ? collectionRawRoot : new File(getFilesDir(), "raw");
                final File dir = new File(root, collectionClassName + "/" + subjectDirName + "/" + currentCount);
                
                ioExecutor.execute(() -> {
                    try {
                        if (!dir.exists()) {
                            dir.mkdirs();
                        }
                        if (fullRGB != null) {
                            saveBitmapAsBmp(fullRGB, new File(dir, "RGB.bmp"));
                            fullRGB.recycle();
                        }
                        if (cropRGB != null) {
                            saveBitmapAsBmp(cropRGB, new File(dir, "cropRGB.bmp"));
                            cropRGB.recycle();
                        }
                        if (fullIR != null) {
                            saveBitmapAsBmp(fullIR, new File(dir, "IR.bmp"));
                            fullIR.recycle();
                        }
                        if (cropIR != null) {
                            saveBitmapAsBmp(cropIR, new File(dir, "cropIR.bmp"));
                            cropIR.recycle();
                        }
                    } finally {
                        ioBusy = false;
                    }
                    runOnUiThread(() -> {
                        collectionProgress.setText("Captured: " + currentCount + "/100");
                        if (currentCount == 100) {
                            isCollecting = false;
                            overlay.setCollecting(false);
                            startCollectionButton.setEnabled(true);
                            switchButton.setEnabled(true);
                            startCollectionButton.setText("START CAPTURE");
                            collectionProgress.setVisibility(View.GONE);
                        }
                    });
                });
            } else {
                isCollecting = false;
                ioBusy = false;
                runOnUiThread(() -> overlay.setCollecting(false));
            }
        }

        Rect rgbCrop = null;
        Rect irCrop = null;
        synchronized (classifierLock) {
            AntiSpoofingClassifier activeClassifier = classifier;
            if (activeClassifier != null && frame.ir != null) {
                float margin = activeClassifier.cropMarginRatio();
                rgbCrop = FaceCrop.expand(detected, margin, frame.rgb.bitmap.getWidth(), frame.rgb.bitmap.getHeight());
                irCrop = FaceCrop.expand(irDetected, margin, frame.ir.bitmap.getWidth(), frame.ir.bitmap.getHeight());
            }
        }
        if (isCollecting || rgbCrop == null || irCrop == null || frame.ir == null) return;
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
                catch (Exception e) {
                    android.util.Log.e("MainActivity", "Inference failed in drainInference", e);
                    showTransientStatus("Inference failed");
                }
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
        ClassificationResult result;
        synchronized (classifierLock) {
            AntiSpoofingClassifier activeClassifier = classifier;
            if (activeClassifier == null) return;
            result = activeClassifier.classify(task.pair.rgb.bitmap, task.rgbCrop,
                    task.pair.ir.bitmap, task.irCrop);
        }
        inferenceMs = result.inferenceMs;
        updateInferenceFps();

        runOnUiThread(() -> {
            if (!resumed) return;
            overlay.showResult(result);
            
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < ClassificationResult.LABELS.length; i++) {
                if (i > 0) sb.append("\n");
                sb.append(String.format(Locale.US, "%s %.1f%%", ClassificationResult.LABELS[i], result.probabilities[i] * 100f));
            }
            resultsLabel.setText(sb.toString());
            
            long now = SystemClock.elapsedRealtime();
            if (now - lastUiUpdateTimeMs >= 150L) {
                performance.setText(formatPerformance());
                lastUiUpdateTimeMs = now;
            }
        });
    }

    /**
     * Chooses where captured frames are written. Prefers external storage
     * ({@code /sdcard/Pictures/raw}) so the files are easy to pull off-device, and
     * verifies it with a real probe write. When the app cannot write external storage
     * (e.g. running as the system UID under FUSE) it falls back to app-private internal
     * storage ({@code getFilesDir()/raw}) so capture never silently fails.
     */
    private File resolveRawRoot() {
        File external = new File("/sdcard/Pictures/raw");
        try {
            if (external.isDirectory() || external.mkdirs()) {
                File probe = new File(external, ".wtest");
                try (FileOutputStream out = new FileOutputStream(probe)) {
                    out.write(0);
                }
                probe.delete();
                return external;
            }
        } catch (Exception ignored) {
        }
        return new File(getFilesDir(), "raw");
    }

    private int getNextSubjectNumber(String className) {
        File baseDir = new File(resolveRawRoot(), className);
        if (!baseDir.exists()) {
            return 1;
        }
        int maxNum = 0;
        File[] files = baseDir.listFiles();
        if (files != null) {
            String prefix = className + "_";
            for (File file : files) {
                if (file.isDirectory()) {
                    String name = file.getName();
                    if (name.startsWith(prefix)) {
                        try {
                            int num = Integer.parseInt(name.substring(prefix.length()));
                            if (num > maxNum) {
                                maxNum = num;
                            }
                        } catch (NumberFormatException e) {
                            // ignore
                        }
                    }
                }
            }
        }
        return maxNum + 1;
    }

    private void startDataCollection(String className, int subjectNum) {
        if (isCollecting) return;
        if (!Environment.isExternalStorageManager()) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
            intent.setData(Uri.parse("package:" + getPackageName()));
            startActivity(intent);
            showTransientStatus("Please grant All Files Access and try again.");
            startCollectionButton.setText("START CAPTURE");
            return;
        }
        collectionRawRoot = resolveRawRoot();
        startCollectionButton.setEnabled(false);
        switchButton.setEnabled(false);
        startCollectionButton.setText("COLLECTING...");
        collectionProgress.setVisibility(View.VISIBLE);
        collectionProgress.setText("Captured: 0/100");

        collectionClassName = className;
        collectionStartSubjectId = subjectNum;
        collectionCount = 0;
        ioBusy = false;
        isCollecting = true;
        runOnUiThread(() -> overlay.setCollecting(true));
    }

    private void saveBitmapAsBmp(Bitmap bitmap, File file) {
        try {
            int width = bitmap.getWidth();
            int height = bitmap.getHeight();
            int rowSize = ((width * 24 + 31) / 32) * 4;
            int pixelDataSize = rowSize * height;
            int fileSize = 54 + pixelDataSize;

            byte[] header = new byte[54];
            header[0] = 'B';
            header[1] = 'M';
            header[2] = (byte) (fileSize & 0xFF);
            header[3] = (byte) ((fileSize >> 8) & 0xFF);
            header[4] = (byte) ((fileSize >> 16) & 0xFF);
            header[5] = (byte) ((fileSize >> 24) & 0xFF);
            header[10] = 54;

            header[14] = 40;
            header[18] = (byte) (width & 0xFF);
            header[19] = (byte) ((width >> 8) & 0xFF);
            header[20] = (byte) ((width >> 16) & 0xFF);
            header[21] = (byte) ((width >> 24) & 0xFF);
            header[22] = (byte) (height & 0xFF);
            header[23] = (byte) ((height >> 8) & 0xFF);
            header[24] = (byte) ((height >> 16) & 0xFF);
            header[25] = (byte) ((height >> 24) & 0xFF);
            header[26] = 1;
            header[28] = 24;
            header[34] = (byte) (pixelDataSize & 0xFF);
            header[35] = (byte) ((pixelDataSize >> 8) & 0xFF);
            header[36] = (byte) ((pixelDataSize >> 16) & 0xFF);
            header[37] = (byte) ((pixelDataSize >> 24) & 0xFF);

            try (FileOutputStream out = new FileOutputStream(file)) {
                out.write(header);
                int[] pixels = new int[width * height];
                bitmap.getPixels(pixels, 0, width, 0, 0, width, height);
                byte[] rowBytes = new byte[rowSize];
                for (int y = height - 1; y >= 0; y--) {
                    int offset = y * width;
                    int rowOffset = 0;
                    for (int x = 0; x < width; x++) {
                        int color = pixels[offset + x];
                        rowBytes[rowOffset++] = (byte) (color & 0xFF);
                        rowBytes[rowOffset++] = (byte) ((color >> 8) & 0xFF);
                        rowBytes[rowOffset++] = (byte) ((color >> 16) & 0xFF);
                    }
                    out.write(rowBytes);
                }
            }
        } catch (IOException e) {
            showTransientStatus("Save failed: " + e.getMessage());
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
            irLoadingSpinner.setVisibility(View.GONE);
            if (!isCollecting) {
                startCollectionButton.setEnabled(true);
                switchButton.setEnabled(true);
            }
        }
        String backend = classifier != null ? classifier.inferenceBackend() : "MODEL";
        return String.format(Locale.US, "Convert RGB/IR %d/%d ms\nDetect %d ms  %.1f FPS\nInference %d ms  %.1f FPS\nBackend %s",
                rgbConversionMs, irConversionMs, detectionMs, trackingFps, inferenceMs, inferenceFps, backend);
    }

    private void resetResultsLabelToZero() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < ClassificationResult.LABELS.length; i++) {
            if (i > 0) sb.append("\n");
            sb.append(String.format(Locale.US, "%s 0.0%%", ClassificationResult.LABELS[i]));
        }
        resultsLabel.setText(sb.toString());
    }

    private void toggleModel() {
        synchronized (classifierLock) {
            isNpuModelSelected = !isNpuModelSelected;
            classifier = isNpuModelSelected ? npuClassifier : standardClassifier;

            final String btnText = isNpuModelSelected ? "MODEL: NPU" : "MODEL: Standard";
            final String message = (classifier != null) ? classifier.backendStatus() : "Model not loaded";
            normalStatusMessage = message;

            runOnUiThread(() -> {
                status.setText(message);
                if (modelSwitchButton != null) {
                    modelSwitchButton.setText(btnText);
                }
                performance.setText(formatPerformance());
            });
        }
    }

    private final Runnable restoreStatusRunnable = () -> {
        status.setText(normalStatusMessage);
    };

    private void showTransientStatus(String message) {
        runOnUiThread(() -> {
            status.setText(message);
            status.removeCallbacks(restoreStatusRunnable);
            status.postDelayed(restoreStatusRunnable, 3000L);
        });
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
        else showTransientStatus("CAMERA permission denied");
    }

    @Override protected void onDestroy() {
        if (cameras != null) cameras.stop();
        HardwareControls.setIrLed(false);
        clearPendingWork();
        trackingExecutor.shutdownNow();
        inferenceExecutor.shutdownNow();
        ioExecutor.shutdownNow();
        synchronized (classifierLock) {
            if (standardClassifier != null) {
                try { standardClassifier.close(); } catch (Exception e) {}
            }
            if (npuClassifier != null) {
                try { npuClassifier.close(); } catch (Exception e) {}
            }
            classifier = null;
            standardClassifier = null;
            npuClassifier = null;
        }
        if (faceDetector != null) faceDetector.close();
        clearPreviewFace();
        clearIrPreviewFrame();
        appWatchdog.close();
        super.onDestroy();
    }

    @Override public void onBackPressed() {
        // Prevent back button navigation
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

    private void setPreviewFace(Bitmap bitmap, boolean rgb) {
        Bitmap previous = currentPreviewFace;
        currentPreviewFace = bitmap;
        faceCropView.setScaleX(-1f);
        faceCropView.setImageBitmap(bitmap);
        if (previous != null && previous != bitmap && !previous.isRecycled()) previous.recycle();
    }

    private void clearPreviewFace() {
        faceCropView.setImageDrawable(null);
        if (currentPreviewFace != null && !currentPreviewFace.isRecycled()) currentPreviewFace.recycle();
        currentPreviewFace = null;
    }

    private void updateIrPreview(FrameData frame) {
        if (!showIr) return;
        long now = SystemClock.elapsedRealtime();
        if (now - lastIrPreviewUpdateTimeMs < 150L) return;
        lastIrPreviewUpdateTimeMs = now;
        Bitmap.Config config = frame.bitmap.getConfig();
        Bitmap preview = frame.bitmap.copy(config == null ? Bitmap.Config.ARGB_8888 : config, false);
        runOnUiThread(() -> setIrPreviewFrame(preview));
    }

    private void setIrPreviewFrame(Bitmap bitmap) {
        if (!resumed || !showIr) {
            bitmap.recycle();
            return;
        }
        Bitmap previous = currentIrPreviewFrame;
        currentIrPreviewFrame = bitmap;
        irPreviewView.setImageBitmap(bitmap);
        if (previous != null && previous != bitmap && !previous.isRecycled()) previous.recycle();
    }

    private void clearIrPreviewFrame() {
        irPreviewView.setImageDrawable(null);
        if (currentIrPreviewFrame != null && !currentIrPreviewFrame.isRecycled()) currentIrPreviewFrame.recycle();
        currentIrPreviewFrame = null;
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
