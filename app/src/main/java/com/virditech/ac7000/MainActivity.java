package com.virditech.ac7000;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Rect;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.SystemClock;
import android.provider.Settings;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.view.TextureView;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.io.File;
import com.cyberlink.faceme.FaceQualityLevel;
import com.virditech.ac7000.calibration.Calibration;
import com.virditech.ac7000.camera.DualCameraController;
import com.virditech.ac7000.camera.FrameData;
import com.virditech.ac7000.camera.FramePair;
import com.virditech.ac7000.capture.CaptureProgressText;
import com.virditech.ac7000.capture.CaptureSchedule;
import com.virditech.ac7000.capture.CaptureStep;
import com.virditech.ac7000.capture.CaptureStorage;
import com.virditech.ac7000.concurrent.GenerationGuard;
import com.virditech.ac7000.face.FaceDetector;
import com.virditech.ac7000.device.HardwareControls;
import com.virditech.ac7000.device.IrCameraExposureController;
import com.virditech.ac7000.device.AppWatchdog;
import com.virditech.ac7000.device.UbimDaemonClient;
import com.virditech.ac7000.model.ClassificationResult;
import com.virditech.ac7000.model.FaceCrop;
import com.virditech.ac7000.model.ModelSlotClassifier;
import com.virditech.ac7000.model.SlotClassificationResult;
import com.virditech.ac7000.performance.LatencyWindow;
import com.virditech.ac7000.ui.MainScreenView;
import com.virditech.ac7000.ui.OverlayView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public final class MainActivity extends Activity {
    private static final String TAG = "MainActivity";
    private static final int CAMERA_PERMISSION_REQUEST = 10;
    private static final long MAX_PAIR_DELTA_NS = 150_000_000L;
    private static final int COLLECTION_TARGET_COUNT = CaptureSchedule.TARGET_COUNT;
    private static final int IR_RESULT_COLOR = Color.rgb(64, 196, 255);
    private static final int COLLECTION_MEDIUM_QUALITY_LEVEL = 1;
    private static final int LATENCY_WINDOW_SIZE = 120;

    private final ExecutorService trackingExecutor = Executors.newSingleThreadExecutor();
    private final ExecutorService inferenceExecutor = Executors.newSingleThreadExecutor();
    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();
    private final ExecutorService modelInitExecutor = Executors.newSingleThreadExecutor();
    private final AtomicReference<TrackingFrame> pendingTracking = new AtomicReference<>();
    private final AtomicReference<InferenceTask> pendingInference = new AtomicReference<>();
    private final AtomicBoolean trackingWorkerRunning = new AtomicBoolean();
    private final AtomicBoolean inferenceWorkerRunning = new AtomicBoolean();
    private final AtomicBoolean calibrationRequested = new AtomicBoolean();
    private final GenerationGuard pipelineGeneration = new GenerationGuard();
    private final Object irLock = new Object();
    private FrameData latestIr;
    private final Object irPreviewLock = new Object();
    private Bitmap latestIrBitmapForCrop;
    private TextureView rgbView;
    private TextureView irView;
    private OverlayView overlay;
    private ProgressBar loadingSpinner;
    private ProgressBar irLoadingSpinner;
    private TextView performance;
    private TextView status;
    private ImageView faceCropView;
    private TextView noFaceLabel;
    private TextView resultsLabel;
    private TextView calibrationInstruction;
    private Button switchButton;
    private Button modelSwitchButton;
    private MainScreenView screen;
    private final Object classifierLock = new Object();
    private final StringBuilder engineErrors = new StringBuilder();
    private final AtomicInteger pendingEngineLoads = new AtomicInteger(2);
    private final ArrayList<ModelSlotClassifier> classifiers = new ArrayList<>();
    private int activeClassifierIndex;
    private volatile boolean enginesShutDown;
    // NNAPI compilation of the NPU model monopolizes the VSI NPU driver, which FaceMe
    // detection also uses, so tracking stalls until every warmup finishes. Keep the
    // loading spinner up until then instead of pretending the camera is usable.
    private volatile boolean enginesWarmedUp;
    private volatile boolean qualityWarmedUp;
    private Button startCollectionButton;
    private ImageButton pauseCollectionButton;
    private ImageButton cancelCollectionButton;
    private FrameLayout highQualityOnlyContainer;
    private CheckBox highQualityOnlyButton;
    private boolean highQualityOnly;
    private TextView collectionProgress;
    private DualCameraController cameras;
    private volatile FaceDetector faceDetector;
    private volatile ModelSlotClassifier classifier;
    private volatile Calibration calibration;
    private final AppWatchdog appWatchdog = AppWatchdog.getInstance();
    private volatile boolean isCollecting;
    private volatile boolean ioBusy;
    private volatile int collectionCount;
    private volatile int collectionSessionId;
    private volatile int collectionStepIndex;
    private volatile int collectionStepCount;
    private volatile boolean collectionPaused;
    private volatile long collectionCountdownEndMs;
    private volatile long collectionPausedCountdownMs;
    private volatile int collectionMinQualityLevel = COLLECTION_MEDIUM_QUALITY_LEVEL;
    private volatile String collectionQualityMode;
    private volatile FaceDetector.FaceQualityCheckResult lastCollectionQuality;
    private File collectionRawRoot;
    private int collectionStartSubjectId;
    private String collectionClassName = "live";
    private volatile boolean showIr;
    private boolean showIrBeforeCalibration;
    private volatile boolean calibrationMode;
    private boolean resumed;
    private int calibrationTapCount;
    private long lastCalibrationTapMs;
    private long lastFaceDetectedMs;
    private volatile String normalStatusMessage = "Initializing...";
    private int trackingFrames;
    private int inferenceFrames;
    private long trackingWindowStartNs;
    private long inferenceWindowStartNs;
    private volatile long rgbConversionMs;
    private volatile long irConversionMs;
    private volatile long detectionMs;
    private volatile long inferenceMs;
    private volatile long rgbInferenceMs = -1L;
    private volatile long irInferenceMs = -1L;
    private final LatencyWindow preprocessLatency = new LatencyWindow(LATENCY_WINDOW_SIZE);
    private final LatencyWindow invokeLatency = new LatencyWindow(LATENCY_WINDOW_SIZE);
    private final LatencyWindow inferenceQueueLatency = new LatencyWindow(LATENCY_WINDOW_SIZE);
    private final LatencyWindow inferenceEndToEndLatency = new LatencyWindow(LATENCY_WINDOW_SIZE);
    private final LatencyWindow captureSaveLatency = new LatencyWindow(LATENCY_WINDOW_SIZE);
    private volatile float trackingFps;
    private volatile float inferenceFps;
    private long lastUiUpdateTimeMs;
    private long lastPreviewUpdateTimeMs;
    private long lastIrCropCopyTimeMs;
    private ToneGenerator captureTone;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
        WindowManager.LayoutParams windowAttributes = getWindow().getAttributes();
        windowAttributes.screenBrightness = 1f;
        getWindow().setAttributes(windowAttributes);
        appWatchdog.start();
        initializeCaptureTone();
        buildUi();
        initializeEngines();
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED ||
            checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE}, CAMERA_PERMISSION_REQUEST);
        }
    }

    private void buildUi() {
        screen = new MainScreenView(this, new MainScreenView.Listener() {
            @Override public void onPauseCollection() { toggleCollectionPaused(); }

            @Override public void onCancelCollection() { cancelDataCollection(); }

            @Override public void onHighQualityOnlyChanged(boolean checked) {
                highQualityOnly = checked;
            }

            @Override public void onStartCollection(String className) {
                int nextNum = getNextSubjectNumber(className);
                startDataCollection(className, nextNum);
            }

            @Override public void onSwitchPreview() { setIrVisible(!showIr); }

            @Override public void onToggleModel() { toggleModel(); }

            @Override public void onCalibrationConfirm() {
                calibrationRequested.set(true);
                calibrationInstruction.setText("Hold still while RGB and IR faces are measured...");
            }

            @Override public void onCalibrationCancel() { exitCalibrationMode(); }

            @Override public void onCalibrationTap() { recordCalibrationTap(); }
        });
        rgbView = screen.rgbView;
        irView = screen.irView;
        overlay = screen.overlay;
        loadingSpinner = screen.loadingSpinner;
        irLoadingSpinner = screen.irLoadingSpinner;
        performance = screen.performance;
        status = screen.status;
        faceCropView = screen.faceCropView;
        noFaceLabel = screen.noFaceLabel;
        resultsLabel = screen.resultsLabel;
        calibrationInstruction = screen.calibrationInstruction;
        switchButton = screen.switchButton;
        modelSwitchButton = screen.modelSwitchButton;
        startCollectionButton = screen.startCollectionButton;
        pauseCollectionButton = screen.pauseCollectionButton;
        cancelCollectionButton = screen.cancelCollectionButton;
        highQualityOnlyContainer = screen.highQualityOnlyContainer;
        highQualityOnlyButton = screen.highQualityOnlyButton;
        collectionProgress = screen.collectionProgress;
        screen.setInitialPerformanceText(String.format(Locale.US, "Convert RGB/IR %d/%d ms\nDetect %d ms  %.1f FPS\nInference %d ms  %.1f FPS\nBackend MODEL", 0, 0, 0, 0.0f, 0, 0.0f));
        resetResultsLabelToZero();
        setContentView(screen.root);
    }
    private void setIrVisible(boolean visible) {
        showIr = visible;
        screen.setIrVisible(showIr);
        if (showIr) {
            synchronized (irPreviewLock) {
                if (latestIrBitmapForCrop != null) {
                    latestIrBitmapForCrop.recycle();
                    latestIrBitmapForCrop = null;
                }
            }
        }
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
        screen.enterCalibrationMode();
        if (cameras != null) cameras.setIrFramesEnabled(true);
    }

    private void exitCalibrationMode() {
        if (!calibrationMode) return;
        calibrationMode = false;
        calibrationRequested.set(false);
        setIrVisible(showIrBeforeCalibration);
        resetResultsLabelToZero();
        screen.exitCalibrationMode(normalStatusMessage);
        if (cameras != null) cameras.setIrFramesEnabled(true);
    }

    private void initializeEngines() {
        ioExecutor.execute(() -> {
            try {
                UbimDaemonClient daemon = new UbimDaemonClient();
                daemon.command("ubim cli.command appops set com.virditech.ac7000 MANAGE_EXTERNAL_STORAGE allow");
            } catch (Exception e) {
                android.util.Log.e("MainActivity", "Failed to auto-grant MANAGE_EXTERNAL_STORAGE", e);
            }
            Calibration.setAppStorageDir(getFilesDir());
            try { calibration = Calibration.load(); }
            catch (Exception e) {
                android.util.Log.e("MainActivity", "Failed to load calibration config", e);
                calibration = Calibration.identity();
                reportEngineError("CALIBRATION NOT SET");
            }
            try {
                FaceDetector detector = new FaceDetector(getApplicationContext());
                synchronized (classifierLock) {
                    if (enginesShutDown) {
                        detector.close();
                        return;
                    }
                    faceDetector = detector;
                }
                if (!detector.isQualityAvailable()) {
                    String message = detector.qualityError();
                    reportEngineError(message.isEmpty() ? "Face quality unavailable" : message);
                } else {
                    qualityWarmedUp = true;
                    android.util.Log.i(TAG, "Face quality warmup completed");
                }
            } catch (Exception e) {
                String message = e.getMessage();
                reportEngineError(message == null ? "Face detector unavailable" : message);
            } finally {
                onEngineLoadFinished();
            }
        });
        modelInitExecutor.execute(this::loadClassifiers);
    }

    private void loadClassifiers() {
        ModelSlotClassifier.LoadResult result = null;
        try {
            result = ModelSlotClassifier.loadAll(getApplicationContext());
        } catch (Exception e) {
            reportEngineError("MODEL LOAD FAILED: " + e.getMessage());
        }
        List<ModelSlotClassifier> loaded = result != null ? result.slots : new ArrayList<>();
        if (result != null) {
            for (String error : result.errors) reportEngineError(error);
        }
        synchronized (classifierLock) {
            if (enginesShutDown) {
                for (ModelSlotClassifier slot : loaded) {
                    try { slot.close(); } catch (Exception ignored) {}
                }
                return;
            }
            classifiers.clear();
            classifiers.addAll(loaded);
            activeClassifierIndex = 0;
            classifier = classifiers.isEmpty() ? null : classifiers.get(0);
        }
        runOnUiThread(() -> {
            if (modelSwitchButton != null) {
                modelSwitchButton.setEnabled(classifiers.size() > 1);
                ModelSlotClassifier active = classifier;
                if (active != null) modelSwitchButton.setText(active.label());
            }
            if (classifier != null && cameras != null) cameras.setIrFramesEnabled(true);
        });
        if (classifier == null) reportEngineError("No model slots loaded");
        updateEngineStatus();
        onEngineLoadFinished();
    }

    private void onEngineLoadFinished() {
        if (pendingEngineLoads.decrementAndGet() != 0) return;
        if (enginesShutDown) return;
        enginesWarmedUp = true;
        runOnUiThread(() -> {
            if (!resumed) return;
            performance.setText(formatPerformance());
        });
    }

    private void reportEngineError(String message) {
        if (message == null || enginesShutDown) return;
        synchronized (engineErrors) {
            append(engineErrors, message);
        }
        updateEngineStatus();
    }

    private void updateEngineStatus() {
        if (enginesShutDown) return;
        String errors;
        synchronized (engineErrors) {
            errors = engineErrors.toString();
        }
        ModelSlotClassifier active = classifier;
        String message = errors.isEmpty()
                ? (active != null ? active.backendStatus() : "Loading model...")
                : errors;
        normalStatusMessage = message;
        runOnUiThread(() -> status.setText(message));
    }

    private void startCameras() {
        if (!resumed || cameras != null || checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) return;
        final int generation = pipelineGeneration.advance();
        HardwareControls.setLcdBrightness(90);
        HardwareControls.setIrLed(true);
        IrCameraExposureController.applyFullAutoExposure();
        cameras = new DualCameraController(this, rgbView, irView, new DualCameraController.Listener() {
            @Override public void onRgb(FrameData frame) { submitTracking(frame, generation); }
            @Override public void onIr(FrameData frame) { offerIr(frame, generation); }
            @Override public void onError(String message) {
                if (isPipelineCurrent(generation)) showTransientStatus(message);
            }
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
        pipelineGeneration.advance();
        if (cameras != null) {
            cameras.stop();
            cameras = null;
        }
        HardwareControls.setIrLed(false);
        clearPendingWork();
        overlay.clearResult();
        synchronized (irPreviewLock) {
            if (latestIrBitmapForCrop != null) {
                latestIrBitmapForCrop.recycle();
                latestIrBitmapForCrop = null;
            }
        }
        super.onPause();
    }

    private boolean isPipelineCurrent(int generation) {
        return resumed && pipelineGeneration.isCurrent(generation);
    }

    private void offerIr(FrameData frame, int generation) {
        if (!isPipelineCurrent(generation)) {
            frame.recycle();
            return;
        }
        if (!showIr) {
            long now = SystemClock.elapsedRealtime();
            if (now - lastIrCropCopyTimeMs >= 66L) {
                lastIrCropCopyTimeMs = now;
                synchronized (irPreviewLock) {
                    if (latestIrBitmapForCrop != null) {
                        latestIrBitmapForCrop.recycle();
                    }
                    Bitmap.Config config = frame.bitmap.getConfig();
                    latestIrBitmapForCrop = frame.bitmap.copy(config == null ? Bitmap.Config.ARGB_8888 : config, false);
                }
            }
        }
        synchronized (irLock) {
            if (latestIr != null) latestIr.recycle();
            latestIr = frame;
        }
    }

    private void submitTracking(FrameData rgb, int generation) {
        if (!isPipelineCurrent(generation)) {
            rgb.recycle();
            return;
        }
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
        TrackingFrame replaced = pendingTracking.getAndSet(new TrackingFrame(rgb, ir, generation,
                SystemClock.elapsedRealtimeNanos()));
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
                    if (isPipelineCurrent(frame.generation)) showTransientStatus("Tracking failed");
                }
                finally { frame.recycle(); }
            }
        } finally {
            trackingWorkerRunning.set(false);
            if (pendingTracking.get() != null && trackingWorkerRunning.compareAndSet(false, true)) {
                trackingExecutor.execute(this::drainTracking);
            }
            if (enginesShutDown) closeFaceDetector();
        }
    }

    private void processTracking(TrackingFrame frame) {
        if (!isPipelineCurrent(frame.generation)) return;
        if (faceDetector == null || calibration == null) return;
        boolean captureCalibration = calibrationMode && calibrationRequested.getAndSet(false);
        long start = SystemClock.elapsedRealtimeNanos();
        Rect detected = captureCalibration
                ? faceDetector.detectSingle(frame.rgb.bitmap)
                : faceDetector.detectLargest(frame.rgb.bitmap);
        if (!isPipelineCurrent(frame.generation)) return;
        detectionMs = (SystemClock.elapsedRealtimeNanos() - start) / 1_000_000L;
        rgbConversionMs = frame.rgb.conversionMs;
        if (frame.ir != null) irConversionMs = frame.ir.conversionMs;
        updateTrackingFps();
        if (detected == null) {
            runOnUiThread(() -> {
                if (!isPipelineCurrent(frame.generation)) return;
                overlay.clearResult();
                clearPreviewFace();
                faceCropView.setScaleX(1f);
                noFaceLabel.setVisibility(View.VISIBLE);
                if (isCollecting) {
                    updateCollectionUi(SystemClock.elapsedRealtime());
                }
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
                runOnUiThread(() -> {
                    if (isPipelineCurrent(frame.generation)) {
                        calibrationInstruction.setText("Waiting for a synchronized IR frame. Hold still...");
                    }
                });
                return;
            }
            Rect detectedIr = faceDetector.detectSingle(frame.ir.bitmap);
            if (detectedIr == null) {
                runOnUiThread(() -> {
                    if (isPipelineCurrent(frame.generation)) {
                        calibrationInstruction.setText("Exactly one IR face is required. Try again.");
                    }
                });
                return;
            }
            if (!calibrationMode) return;
            try {
                Calibration measured = Calibration.fromFaces(detected, detectedIr, irWidth);
                measured.save();
                calibration = measured;
                normalStatusMessage = "Calibration saved";
                runOnUiThread(() -> {
                    if (!isPipelineCurrent(frame.generation)) return;
                    exitCalibrationMode();
                    status.setText("Calibration saved");
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (isPipelineCurrent(frame.generation)) {
                        calibrationInstruction.setText("Unable to save calibration: " + e.getMessage());
                    }
                });
            }
            return;
        }

        Rect rgbCrop = null;
        Rect irCrop = null;
        ModelSlotClassifier activeClassifier = classifier;
        if (activeClassifier != null) {
            float margin = activeClassifier.cropMarginRatio();
            rgbCrop = FaceCrop.expand(detected, margin, frame.rgb.bitmap.getWidth(), frame.rgb.bitmap.getHeight());
            irCrop = FaceCrop.expand(irDetected, margin, irWidth, irHeight);
        }

        Bitmap previewFace = null;
        boolean previewRgb = showIr;
        long previewNow = SystemClock.elapsedRealtime();
        if (previewNow - lastPreviewUpdateTimeMs >= 66L && activeClassifier != null) {
            lastPreviewUpdateTimeMs = previewNow;
            if (previewRgb) {
                previewFace = createCropPreviewBitmap(frame.rgb.bitmap, rgbCrop);
            } else {
                synchronized (irPreviewLock) {
                    if (latestIrBitmapForCrop != null && !latestIrBitmapForCrop.isRecycled()) {
                        previewFace = createCropPreviewBitmap(latestIrBitmapForCrop, irCrop);
                    }
                }
            }
        }
        final Bitmap finalPreviewFace = previewFace;
        final boolean finalPreviewRgb = previewRgb;

        runOnUiThread(() -> {
            if (!isPipelineCurrent(frame.generation)) {
                if (finalPreviewFace != null) finalPreviewFace.recycle();
                return;
            }
            overlay.showFace(detected, irDetected);
            if (isCollecting) {
                updateCollectionUi(SystemClock.elapsedRealtime());
            }
            long now = SystemClock.elapsedRealtime();
            if (now - lastUiUpdateTimeMs >= 150L) {
                performance.setText(formatPerformance());
                lastUiUpdateTimeMs = now;
            }
            noFaceLabel.setVisibility(View.GONE);
            if (finalPreviewFace != null) {
                setPreviewFace(finalPreviewFace, finalPreviewRgb);
            }
        });
        if (calibrationMode) return;

        if (isCollecting && !collectionPaused && frame.ir != null && !ioBusy) {
            final int sessionId = collectionSessionId;
            final String className = collectionClassName;
            final int subjectId = collectionStartSubjectId;
            final String qualityMode = collectionQualityMode;
            long nowMs = SystemClock.elapsedRealtime();
            if (getCollectionCountdownSeconds(nowMs) > 0) return;
            FaceDetector.FaceQualityCheckResult sampleQuality = null;
            if (shouldCheckCollectionQuality(className)) {
                FaceDetector.FaceQualityCheckResult quality =
                        faceDetector.checkFaceQuality(frame.rgb.bitmap, collectionMinQualityLevel);
                lastCollectionQuality = quality;
                if (!quality.passed) {
                    android.util.Log.i(TAG, "Collection quality skipped: " + quality.reason);
                    runOnUiThread(() -> {
                        if (isPipelineCurrent(frame.generation) && isCollecting) {
                            updateCollectionUi(SystemClock.elapsedRealtime());
                        }
                    });
                    return;
                }
                sampleQuality = quality;
            }
            if (!isPipelineCurrent(frame.generation)) return;
            if (collectionPaused) return;
            if (!isActiveCollection(sessionId, className, subjectId)) return;
            ioBusy = true;
            final int currentCount = collectionCount + 1;
            if (currentCount <= COLLECTION_TARGET_COUNT) {
                final String subjectDirName = className + "_" + subjectId;
                ModelSlotClassifier collectionClassifier = classifier;
                float margin = collectionClassifier != null ? collectionClassifier.cropMarginRatio() : 0.10f;
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
                final int minQualityLevel = shouldCheckCollectionQuality(className) ? collectionMinQualityLevel : -1;
                final int actualQualityLevel = sampleQuality != null ? sampleQuality.actualLevel : -1;
                final float qualityScore = sampleQuality != null ? sampleQuality.score : 0f;
                final String metadataJson = CaptureStorage.buildSampleMetadataJson(
                        frame.rgb.bitmap.getWidth(), frame.rgb.bitmap.getHeight(), detected, rgbR,
                        frame.ir.bitmap.getWidth(), frame.ir.bitmap.getHeight(), irDetected, irR, margin,
                        qualityMode, minQualityLevel, actualQualityLevel, qualityScore);

                final File root = collectionRawRoot != null ? collectionRawRoot : resolveRawRoot();
                final File sampleDir = CaptureStorage.sampleDir(root, className, qualityMode,
                        subjectDirName, currentCount);
                final String displayDir = sampleDir.getAbsolutePath();

                if (!isActiveCollection(sessionId, className, subjectId)) {
                    CaptureStorage.recycleBitmaps(fullRGB, cropRGB, fullIR, cropIR);
                    ioBusy = false;
                    return;
                }
                
                ioExecutor.execute(() -> {
                    boolean saved = false;
                    boolean sectorCompleted = false;
                    long saveStartNs = 0L;
                    try {
                        if (!isPipelineCurrent(frame.generation)
                                || !isActiveCollection(sessionId, className, subjectId)) {
                            CaptureStorage.recycleBitmaps(fullRGB, cropRGB, fullIR, cropIR);
                            return;
                        }
                        saveStartNs = SystemClock.elapsedRealtimeNanos();
                        boolean dirReady = sampleDir.isDirectory() || sampleDir.mkdirs();
                        boolean hasAllBitmaps = fullRGB != null && cropRGB != null
                                && fullIR != null && cropIR != null;
                        boolean savedAll = dirReady && hasAllBitmaps;
                        if (!dirReady) {
                            showTransientStatus("Save failed: unable to create " + displayDir);
                            android.util.Log.e(TAG, "Unable to create collection sample folder: " + displayDir);
                        } else if (!hasAllBitmaps) {
                            showTransientStatus("Save failed: incomplete capture frame");
                            android.util.Log.e(TAG, "Incomplete collection sample frame: " + displayDir);
                        }
                        if (fullRGB != null) {
                            if (savedAll) savedAll = saveBitmapAsBmp(fullRGB, new File(sampleDir, "RGB.bmp"));
                            fullRGB.recycle();
                        }
                        if (cropRGB != null) {
                            if (savedAll) savedAll = saveBitmapAsBmp(cropRGB, new File(sampleDir, "cropRGB.bmp"));
                            cropRGB.recycle();
                        }
                        if (fullIR != null) {
                            if (savedAll) savedAll = saveBitmapAsBmp(fullIR, new File(sampleDir, "IR.bmp"));
                            fullIR.recycle();
                        }
                        if (cropIR != null) {
                            if (savedAll) savedAll = saveBitmapAsBmp(cropIR, new File(sampleDir, "cropIR.bmp"));
                            cropIR.recycle();
                        }
                        if (savedAll) savedAll = saveTextFile(metadataJson, new File(sampleDir, "meta.json"));
                        if (savedAll && isPipelineCurrent(frame.generation)
                                && isActiveCollection(sessionId, className, subjectId)) {
                            collectionCount = currentCount;
                            CaptureStep captureStep = currentCollectionStep();
                            collectionStepCount++;
                            if (collectionStepCount >= captureStep.targetCount) {
                                sectorCompleted = true;
                                if (currentCount < COLLECTION_TARGET_COUNT) {
                                    collectionStepIndex = Math.min(collectionStepIndex + 1,
                                            CaptureSchedule.DEFAULT_STEPS.length - 1);
                                    collectionStepCount = 0;
                                    collectionCountdownEndMs = SystemClock.elapsedRealtime()
                                            + CaptureSchedule.STEP_COUNTDOWN_MS;
                                }
                            }
                            saved = true;
                            android.util.Log.i(TAG, "Saved collection sample: " + displayDir);
                        }
                    } finally {
                        if (saveStartNs != 0L) {
                            recordCaptureSaveLatency((SystemClock.elapsedRealtimeNanos() - saveStartNs)
                                    / 1_000_000L);
                        }
                        ioBusy = false;
                    }
                    final boolean savedSample = saved;
                    final boolean completedSector = sectorCompleted;
                    runOnUiThread(() -> {
                        if (!isPipelineCurrent(frame.generation) || !isCollecting) return;
                        updateCollectionUi(SystemClock.elapsedRealtime());
                        if (savedSample && completedSector) {
                            playCollectionFinishedTone();
                        } else if (savedSample) {
                            playCaptureSavedTone();
                        }
                        if (savedSample && currentCount == COLLECTION_TARGET_COUNT) {
                            finishDataCollection();
                        }
                    });
                });
            } else {
                ioBusy = false;
                runOnUiThread(this::finishDataCollection);
            }
        }

        if (isCollecting || rgbCrop == null || irCrop == null || frame.ir == null) return;
        submitInference(new InferenceTask(frame.detachPair(), rgbCrop, irCrop,
                frame.generation, activeClassifier, frame.receivedNs));
    }

    private static Bitmap createCropPreviewBitmap(Bitmap source, Rect crop) {
        if (source == null || crop == null || source.isRecycled()) return null;
        int left = Math.max(0, crop.left);
        int top = Math.max(0, crop.top);
        int width = Math.min(source.getWidth() - left, crop.width());
        int height = Math.min(source.getHeight() - top, crop.height());
        if (width <= 0 || height <= 0) return null;
        return Bitmap.createBitmap(source, left, top, width, height);
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
                    if (isPipelineCurrent(task.generation)) showTransientStatus("Inference failed");
                }
                finally { task.recycle(); }
            }
        } finally {
            inferenceWorkerRunning.set(false);
            if (pendingInference.get() != null && inferenceWorkerRunning.compareAndSet(false, true)) {
                inferenceExecutor.execute(this::drainInference);
            }
            if (enginesShutDown) closeClassifiers();
        }
    }

    private void processInference(InferenceTask task) {
        if (!isPipelineCurrent(task.generation) || task.classifier == null) return;
        long startNs = SystemClock.elapsedRealtimeNanos();
        long queueMs = (startNs - task.enqueuedNs) / 1_000_000L;
        SlotClassificationResult result = task.classifier.classify(task.pair.rgb.bitmap, task.rgbCrop,
                task.pair.ir.bitmap, task.irCrop);
        long endToEndMs = (SystemClock.elapsedRealtimeNanos() - task.receivedNs) / 1_000_000L;
        if (!isPipelineCurrent(task.generation)) return;
        inferenceMs = result.inferenceMs;
        rgbInferenceMs = result.rgbResult != null ? result.rgbResult.inferenceMs : -1L;
        irInferenceMs = result.irResult != null ? result.irResult.inferenceMs : -1L;
        recordInferenceMetrics(result.preprocessMs, result.inferenceMs, queueMs, endToEndMs);
        updateInferenceFps();

        runOnUiThread(() -> {
            if (!isPipelineCurrent(task.generation)) return;
            overlay.showResult(result.primaryResult(), result.irResult);
            resultsLabel.setText(formatClassificationResults(result));
            
            long now = SystemClock.elapsedRealtime();
            if (now - lastUiUpdateTimeMs >= 150L) {
                performance.setText(formatPerformance());
                lastUiUpdateTimeMs = now;
            }
        });
    }

    private void updateCollectionUi(long nowMs) {
        CaptureStep step = currentCollectionStep();
        int countdownSeconds = getCollectionCountdownSeconds(nowMs);
        overlay.setCollectionGuide(step.sector, countdownSeconds);
        collectionProgress.setText(formatCollectionProgress(step));
    }

    private SpannableString formatCollectionProgress(CaptureStep step) {
        String qualityLine = shouldCheckCollectionQuality() ? formatCollectionQualityLine() : null;
        CaptureProgressText progress = CaptureProgressText.format(collectionClassName, step,
                collectionStepCount, collectionCount, COLLECTION_TARGET_COUNT, qualityLine);
        SpannableString text = new SpannableString(progress.text);
        int countColor = Color.rgb(255, 214, 0);
        text.setSpan(new ForegroundColorSpan(countColor), progress.stepCountStart,
                progress.stepCountEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        text.setSpan(new ForegroundColorSpan(countColor), progress.totalCountStart,
                progress.totalCountEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return text;
    }

    private String formatCollectionQualityLine() {
        FaceDetector.FaceQualityCheckResult quality = lastCollectionQuality;
        if (quality == null) {
            return "WAITING 0.000";
        }
        if (quality.actualLevel < 0) {
            return "UNKNOWN 0.000";
        }
        return String.format(Locale.US, "%s %.3f", FaceDetector.levelName(quality.actualLevel), quality.score);
    }

    private boolean shouldCheckCollectionQuality() {
        return shouldCheckCollectionQuality(collectionClassName);
    }

    private boolean shouldCheckCollectionQuality(String className) {
        return CaptureSchedule.shouldCheckQuality(className);
    }

    private boolean isActiveCollection(int sessionId, String className, int subjectId) {
        return isCollecting
                && collectionSessionId == sessionId
                && className.equals(collectionClassName)
                && collectionStartSubjectId == subjectId;
    }

    private void updateHighQualityOnlyButton() {
        if (screen != null) screen.setHighQualityOnly(highQualityOnly);
    }

    private CaptureStep currentCollectionStep() {
        return CaptureSchedule.currentStep(collectionStepIndex);
    }

    private int getCollectionCountdownSeconds(long nowMs) {
        if (collectionPaused) return CaptureSchedule.countdownSeconds(collectionPausedCountdownMs, 0L);
        return CaptureSchedule.countdownSeconds(collectionCountdownEndMs, nowMs);
    }

    private void finishDataCollection() {
        isCollecting = false;
        collectionPaused = false;
        collectionPausedCountdownMs = 0L;
        collectionSessionId++;
        ioBusy = false;
        overlay.setCollecting(false);
        screen.setCollectionPaused(false);
        setCollectionChromeVisible(true);
        startCollectionButton.setEnabled(true);
        switchButton.setEnabled(true);
        if (highQualityOnlyContainer != null) highQualityOnlyContainer.setEnabled(true);
        if (highQualityOnlyButton != null) highQualityOnlyButton.setEnabled(true);
        startCollectionButton.setText("START CAPTURE");
        collectionProgress.setVisibility(View.GONE);
        if (pauseCollectionButton != null) pauseCollectionButton.setVisibility(View.GONE);
        if (cancelCollectionButton != null) cancelCollectionButton.setVisibility(View.GONE);
    }

    private void cancelDataCollection() {
        if (!isCollecting) return;
        final String canceledClassName = collectionClassName;
        final String canceledQualityMode = collectionQualityMode;
        final String canceledSubjectDirName = collectionClassName + "_" + collectionStartSubjectId;
        isCollecting = false;
        collectionPaused = false;
        collectionPausedCountdownMs = 0L;
        collectionSessionId++;
        ioBusy = false;
        overlay.setCollecting(false);
        screen.setCollectionPaused(false);
        setCollectionChromeVisible(true);
        startCollectionButton.setEnabled(false);
        switchButton.setEnabled(true);
        if (highQualityOnlyContainer != null) highQualityOnlyContainer.setEnabled(true);
        if (highQualityOnlyButton != null) highQualityOnlyButton.setEnabled(true);
        startCollectionButton.setText("START CAPTURE");
        collectionProgress.setVisibility(View.GONE);
        if (pauseCollectionButton != null) pauseCollectionButton.setVisibility(View.GONE);
        if (cancelCollectionButton != null) cancelCollectionButton.setVisibility(View.GONE);
        showTransientStatus("Capture canceled");
        ioExecutor.execute(() -> {
            deleteCollectionSubject(canceledClassName, canceledQualityMode, canceledSubjectDirName);
            runOnUiThread(() -> {
                if (!isCollecting) {
                    FaceDetector detector = faceDetector;
                    startCollectionButton.setEnabled(detector != null);
                }
            });
        });
    }

    private void toggleCollectionPaused() {
        if (!isCollecting) return;
        long nowMs = SystemClock.elapsedRealtime();
        if (collectionPaused) {
            collectionCountdownEndMs = nowMs + collectionPausedCountdownMs;
            collectionPausedCountdownMs = 0L;
            collectionPaused = false;
            screen.setCollectionPaused(false);
            updateCollectionUi(nowMs);
            showTransientStatus("Capture resumed");
            return;
        }
        collectionPausedCountdownMs = Math.max(0L, collectionCountdownEndMs - nowMs);
        collectionPaused = true;
        screen.setCollectionPaused(true);
        updateCollectionUi(nowMs);
        showTransientStatus("Capture paused");
    }

    private void setCollectionChromeVisible(boolean visible) {
        screen.setCollectionChromeVisible(visible);
    }

    private File resolveRawRoot() {
        return CaptureStorage.resolveRawRoot();
    }

    private int getNextSubjectNumber(String className) {
        return CaptureStorage.getNextSubjectNumber(resolveRawRoot(), className,
                captureQualityMode(className, highQualityOnly));
    }

    private boolean prepareRawRoot(File rawRoot) {
        return CaptureStorage.prepareRawRoot(rawRoot);
    }

    private void startDataCollection(String className, int subjectNum) {
        if (isCollecting) return;
        FaceDetector detector = faceDetector;
        if ("live".equals(className) && (detector == null || !detector.isQualityAvailable())) {
            String message = detector == null ? "Face detector unavailable" : detector.qualityError();
            showTransientStatus(message.isEmpty() ? "Face quality unavailable" : message);
            startCollectionButton.setText("START CAPTURE");
            return;
        }
        if (!Environment.isExternalStorageManager()) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
            intent.setData(Uri.parse("package:" + getPackageName()));
            startActivity(intent);
            showTransientStatus("Please grant All Files Access and try again.");
            startCollectionButton.setText("START CAPTURE");
            return;
        }
        collectionRawRoot = resolveRawRoot();
        if (!prepareRawRoot(collectionRawRoot)) {
            showTransientStatus("Save failed: unable to write " + collectionRawRoot.getAbsolutePath());
            startCollectionButton.setText("START CAPTURE");
            return;
        }
        android.util.Log.i(TAG, "Collection raw root: " + collectionRawRoot.getAbsolutePath());
        startCollectionButton.setEnabled(false);
        switchButton.setEnabled(false);
        if (highQualityOnlyContainer != null) highQualityOnlyContainer.setEnabled(false);
        if (highQualityOnlyButton != null) highQualityOnlyButton.setEnabled(false);
        startCollectionButton.setText("COLLECTING...");
        collectionProgress.setVisibility(View.VISIBLE);
        if (pauseCollectionButton != null) {
            pauseCollectionButton.setVisibility(View.VISIBLE);
            pauseCollectionButton.setEnabled(true);
        }
        if (cancelCollectionButton != null) cancelCollectionButton.setVisibility(View.VISIBLE);

        collectionClassName = className;
        collectionQualityMode = captureQualityMode(className, highQualityOnly);
        collectionStartSubjectId = subjectNum;
        collectionCount = 0;
        collectionSessionId++;
        collectionStepIndex = 0;
        collectionStepCount = 0;
        collectionPaused = false;
        collectionCountdownEndMs = SystemClock.elapsedRealtime() + CaptureSchedule.STEP_COUNTDOWN_MS;
        collectionPausedCountdownMs = 0L;
        collectionMinQualityLevel = highQualityOnly ? FaceQualityLevel.HIGH : COLLECTION_MEDIUM_QUALITY_LEVEL;
        lastCollectionQuality = null;
        ioBusy = false;
        isCollecting = true;
        overlay.setCollecting(true);
        screen.setCollectionPaused(false);
        updateCollectionUi(SystemClock.elapsedRealtime());
        setCollectionChromeVisible(false);
    }

    private void deleteCollectionSubject(String className, String qualityMode, String subjectDirName) {
        boolean deletedFiles = CaptureStorage.deleteSubject(resolveRawRoot(), className,
                qualityMode, subjectDirName);
        android.util.Log.i(TAG, "Deleted canceled collection subject "
                + className + "/" + (qualityMode == null ? "" : qualityMode + "/")
                + subjectDirName + " files=" + deletedFiles);
    }

    private String captureQualityMode(String className, boolean highQuality) {
        if (!shouldCheckCollectionQuality(className)) return null;
        return highQuality ? CaptureStorage.QUALITY_HIGH : CaptureStorage.QUALITY_MEDIUM;
    }

    private boolean saveBitmapAsBmp(Bitmap bitmap, File file) {
        CaptureStorage.SaveResult result = CaptureStorage.saveBitmapAsBmp(bitmap, file);
        if (!result.saved) showTransientStatus("Save failed: " + result.errorMessage);
        return result.saved;
    }

    private boolean saveTextFile(String text, File file) {
        CaptureStorage.SaveResult result = CaptureStorage.saveTextFile(text, file);
        if (!result.saved) showTransientStatus("Save failed: " + result.errorMessage);
        return result.saved;
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

    private CharSequence formatPerformance() {
        if (enginesWarmedUp && qualityWarmedUp && loadingSpinner.getVisibility() == View.VISIBLE) {
            loadingSpinner.setVisibility(View.GONE);
            irLoadingSpinner.setVisibility(View.GONE);
            if (!isCollecting) {
                FaceDetector detector = faceDetector;
                startCollectionButton.setEnabled(detector != null);
                switchButton.setEnabled(true);
            }
        }
        String backend = classifier != null ? classifier.inferenceBackend() : "MODEL";
        if (rgbInferenceMs >= 0L && irInferenceMs >= 0L) {
            String prefix = String.format(Locale.US,
                    "Convert RGB/IR %d/%d ms\nDetect %d ms  %.1f FPS\nInference RGB %d ms  %.1f FPS\n",
                    rgbConversionMs, irConversionMs, detectionMs, trackingFps, rgbInferenceMs, inferenceFps);
            String irText = String.format(Locale.US, "Inference IR %d ms", irInferenceMs);
            String suffix = String.format(Locale.US, "\nBackend %s", backend);
            SpannableString text = new SpannableString(prefix + irText + suffix);
            text.setSpan(new ForegroundColorSpan(IR_RESULT_COLOR), prefix.length(),
                    prefix.length() + irText.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            return text;
        }
        return String.format(Locale.US, "Convert RGB/IR %d/%d ms\nDetect %d ms  %.1f FPS\nInference %d ms  %.1f FPS\nBackend %s",
                rgbConversionMs, irConversionMs, detectionMs, trackingFps, inferenceMs, inferenceFps, backend);
    }

    private void recordInferenceMetrics(long preprocess, long invoke, long queue, long endToEnd) {
        preprocessLatency.add(preprocess);
        long sampleCount = invokeLatency.add(invoke);
        inferenceQueueLatency.add(queue);
        inferenceEndToEndLatency.add(endToEnd);
        if (sampleCount % 30L == 0L) {
            LatencyWindow.Snapshot preprocessStats = preprocessLatency.snapshot();
            LatencyWindow.Snapshot invokeStats = invokeLatency.snapshot();
            LatencyWindow.Snapshot queueStats = inferenceQueueLatency.snapshot();
            LatencyWindow.Snapshot endToEndStats = inferenceEndToEndLatency.snapshot();
            android.util.Log.i(TAG, String.format(Locale.US,
                    "Latency samples=%d P50/P95 ms preprocess=%d/%d invoke=%d/%d queue=%d/%d endToEnd=%d/%d",
                    invokeStats.count, preprocessStats.p50Ms, preprocessStats.p95Ms,
                    invokeStats.p50Ms, invokeStats.p95Ms, queueStats.p50Ms, queueStats.p95Ms,
                    endToEndStats.p50Ms, endToEndStats.p95Ms));
        }
    }

    private void recordCaptureSaveLatency(long durationMs) {
        long sampleCount = captureSaveLatency.add(durationMs);
        if (sampleCount % 10L == 0L) {
            LatencyWindow.Snapshot stats = captureSaveLatency.snapshot();
            android.util.Log.i(TAG, String.format(Locale.US,
                    "Capture save samples=%d P50/P95 ms=%d/%d",
                    stats.count, stats.p50Ms, stats.p95Ms));
        }
    }

    private CharSequence formatClassificationResults(SlotClassificationResult result) {
        if (result.hasPairedResults()) {
            StringBuilder sb = new StringBuilder();
            appendClassificationResult(sb, null, result.rgbResult);
            sb.append("\n\n");
            int irStart = sb.length();
            appendClassificationResult(sb, null, result.irResult);
            SpannableString text = new SpannableString(sb.toString());
            text.setSpan(new ForegroundColorSpan(IR_RESULT_COLOR), irStart, text.length(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            return text;
        }
        StringBuilder sb = new StringBuilder();
        appendClassificationResult(sb, null, result.result);
        return sb.toString();
    }

    private void appendClassificationResult(StringBuilder sb, String title, ClassificationResult result) {
        if (title != null) sb.append(title).append("\n");
        for (int i = 0; i < ClassificationResult.LABELS.length; i++) {
            if (i > 0) sb.append("\n");
            float probability = result != null ? result.probabilities[i] * 100f : 0f;
            sb.append(String.format(Locale.US, "%s %.1f%%", ClassificationResult.LABELS[i], probability));
        }
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
            if (classifiers.isEmpty()) return;
            activeClassifierIndex = (activeClassifierIndex + 1) % classifiers.size();
            classifier = classifiers.get(activeClassifierIndex);

            final String btnText = classifier.label();
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

    private void initializeCaptureTone() {
        try {
            captureTone = new ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80);
        } catch (RuntimeException e) {
            android.util.Log.w(TAG, "Unable to initialize capture tone", e);
        }
    }

    private void playCaptureSavedTone() {
        ToneGenerator tone = captureTone;
        if (tone != null) tone.startTone(ToneGenerator.TONE_PROP_BEEP, 100);
    }

    private void playCollectionFinishedTone() {
        ToneGenerator tone = captureTone;
        if (tone != null) tone.startTone(ToneGenerator.TONE_PROP_ACK, 350);
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
        synchronized (classifierLock) {
            enginesShutDown = true;
        }
        pipelineGeneration.advance();
        if (cameras != null) cameras.stop();
        HardwareControls.setIrLed(false);
        clearPendingWork();
        trackingExecutor.shutdownNow();
        inferenceExecutor.shutdownNow();
        ioExecutor.shutdownNow();
        modelInitExecutor.shutdownNow();
        boolean inferenceTerminated = awaitExecutorTermination(inferenceExecutor);
        boolean trackingTerminated = awaitExecutorTermination(trackingExecutor);
        awaitExecutorTermination(ioExecutor);
        awaitExecutorTermination(modelInitExecutor);
        if (inferenceTerminated) closeClassifiers();
        if (trackingTerminated) closeFaceDetector();
        if (captureTone != null) {
            captureTone.release();
            captureTone = null;
        }
        clearPreviewFace();
        appWatchdog.close();
        super.onDestroy();
    }

    @Override public void onBackPressed() {
        // Prevent back button navigation
    }

    private static final class TrackingFrame {
        private FrameData rgb;
        private FrameData ir;
        final int generation;
        final long receivedNs;

        TrackingFrame(FrameData rgb, FrameData ir, int generation, long receivedNs) {
            this.rgb = rgb;
            this.ir = ir;
            this.generation = generation;
            this.receivedNs = receivedNs;
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
        final int generation;
        final ModelSlotClassifier classifier;
        final long receivedNs;
        final long enqueuedNs;

        InferenceTask(FramePair pair, Rect rgbCrop, Rect irCrop, int generation,
                      ModelSlotClassifier classifier, long receivedNs) {
            this.pair = pair;
            this.rgbCrop = rgbCrop;
            this.irCrop = irCrop;
            this.generation = generation;
            this.classifier = classifier;
            this.receivedNs = receivedNs;
            this.enqueuedNs = SystemClock.elapsedRealtimeNanos();
        }

        void recycle() {
            pair.recycle();
        }
    }

    private void setPreviewFace(Bitmap bitmap, boolean rgb) {
        screen.setPreviewFace(bitmap);
    }

    private void clearPreviewFace() {
        screen.clearPreviewFace();
    }

    private static void append(StringBuilder builder, String message) {
        if (builder.length() > 0) builder.append('\n');
        builder.append(message);
    }

    private void closeClassifiers() {
        synchronized (classifierLock) {
            for (ModelSlotClassifier slot : classifiers) {
                try { slot.close(); } catch (Exception ignored) {}
            }
            classifier = null;
            classifiers.clear();
        }
    }

    private void closeFaceDetector() {
        FaceDetector detector;
        synchronized (classifierLock) {
            detector = faceDetector;
            faceDetector = null;
        }
        if (detector != null) detector.close();
    }

    private static boolean awaitExecutorTermination(ExecutorService executor) {
        try {
            if (!executor.awaitTermination(2, TimeUnit.SECONDS)) {
                android.util.Log.w("MainActivity", "Executor did not terminate in time");
                return false;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
        return true;
    }
}
