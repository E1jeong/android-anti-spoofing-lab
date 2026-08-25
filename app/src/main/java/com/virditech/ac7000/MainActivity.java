package com.virditech.ac7000;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.PointF;
import android.graphics.Rect;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.view.TextureView;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
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
import com.virditech.ac7000.capture.AttackLiveCaptureGate;
import com.virditech.ac7000.capture.CaptureProgressText;
import com.virditech.ac7000.capture.CaptureSchedule;
import com.virditech.ac7000.capture.CaptureStep;
import com.virditech.ac7000.capture.CaptureStorage;
import com.virditech.ac7000.call.WebRtcCallActivity;
import com.virditech.ac7000.concurrent.GenerationGuard;
import com.virditech.ac7000.api.call.SignalingClient;
import com.virditech.ac7000.face.FaceDetector;
import com.virditech.ac7000.face.FaceDetectionEngine;
import com.virditech.ac7000.face.MediaPipeFaceDetector;
import com.virditech.ac7000.device.HardwareControls;
import com.virditech.ac7000.device.IrCameraExposureController;
import com.virditech.ac7000.device.AppWatchdog;
import com.virditech.ac7000.device.UbimDaemonClient;
import com.virditech.ac7000.model.ClassificationResult;
import com.virditech.ac7000.model.FaceCrop;
import com.virditech.ac7000.model.FaceMotionGate;
import com.virditech.ac7000.model.ModelSlotClassifier;
import com.virditech.ac7000.model.SlotClassificationResult;
import com.virditech.ac7000.performance.LatencyWindow;
import com.virditech.ac7000.recognition.FaceEmbeddingModel;
import com.virditech.ac7000.recognition.FaceModelFingerprint;
import com.virditech.ac7000.recognition.FaceRecognitionActivity;
import com.virditech.ac7000.recognition.FaceRecognitionManager;
import com.virditech.ac7000.recognition.FaceTemplate;
import com.virditech.ac7000.recognition.FaceTemplateRepository;
import com.virditech.ac7000.recognition.RecognitionPolicy;
import com.virditech.ac7000.recognition.RecognitionResult;
import com.virditech.ac7000.recognition.RecognitionWorkCoordinator;
import com.virditech.ac7000.ui.MainScreenView;
import com.virditech.ac7000.ui.OverlayView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public final class MainActivity extends Activity {
    private static final String TAG = "MainActivity";
    private static final String SIGNALING_SERVER_URL = "ws://92.168.70.2:8080/ws";
    private static final String SIGNALING_PEER_ID = "device-test-01";
    private static final int CAMERA_PERMISSION_REQUEST = 10;
    private static final int FACE_MANAGEMENT_REQUEST = 11;
    private static final long MAX_PAIR_DELTA_NS = 150_000_000L;
    private static final int COLLECTION_TARGET_COUNT = CaptureSchedule.TARGET_COUNT;
    private static final int IR_RESULT_COLOR = Color.rgb(64, 196, 255);
    private static final int COLLECTION_MEDIUM_QUALITY_LEVEL = 1;
    private static final int LATENCY_WINDOW_SIZE = 120;
    private static final long MOTION_DIAGNOSTIC_LOG_INTERVAL_MS = 250L;

    private final ExecutorService trackingExecutor = Executors.newSingleThreadExecutor();
    private final ExecutorService inferenceExecutor = Executors.newSingleThreadExecutor();
    private final ExecutorService recognitionExecutor = Executors.newSingleThreadExecutor();
    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();
    private final ExecutorService attackCaptureExecutor = Executors.newSingleThreadExecutor();
    private final ExecutorService modelInitExecutor = Executors.newSingleThreadExecutor();
    private final SignalingClient signalingClient = SignalingClient.getInstance();
    private final SignalingClient.Listener signalingListener = this::handleCallInvite;
    private final AtomicReference<TrackingFrame> pendingTracking = new AtomicReference<>();
    private final AtomicReference<InferenceTask> pendingInference = new AtomicReference<>();
    private final AtomicBoolean trackingWorkerRunning = new AtomicBoolean();
    private final AtomicBoolean inferenceWorkerRunning = new AtomicBoolean();
    private final RecognitionWorkCoordinator recognitionCoordinator = new RecognitionWorkCoordinator();
    private final AtomicBoolean recognitionFaceVisible = new AtomicBoolean();
    private final AtomicLong inferenceMotionGeneration = new AtomicLong();
    private final FaceMotionGate inferenceMotionGate = new FaceMotionGate();
    private final AtomicBoolean calibrationRequested = new AtomicBoolean();
    private final GenerationGuard pipelineGeneration = new GenerationGuard();
    private final Object attackCaptureLock = new Object();
    private final Object irLock = new Object();
    private FrameData latestIr;
    private final Object irPreviewLock = new Object();
    private Bitmap latestIrBitmapForCrop;
    private final Canvas irPreviewCanvas = new Canvas();
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
    private ImageButton stopAttackLiveCaptureButton;
    private FrameLayout highQualityOnlyContainer;
    private Button highQualityOnlyButton;
    private boolean highQualityOnly;
    private TextView collectionProgress;
    private DualCameraController cameras;
    private volatile FaceDetector faceDetector;
    private volatile MediaPipeFaceDetector mediaPipeFaceDetector;
    private volatile FaceDetectionEngine activeFaceDetector;
    private volatile ModelSlotClassifier classifier;
    private volatile Calibration calibration;
    private final AppWatchdog appWatchdog = AppWatchdog.getInstance();
    private volatile boolean isCollecting;
    private volatile boolean isAttackLiveCapturing;
    private volatile boolean ioBusy;
    private volatile boolean attackCaptureSaveBusy;
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
    private File attackCaptureRawRoot;
    private int attackCaptureSubjectId;
    private volatile int attackCaptureCount;
    private volatile boolean showIr;
    private boolean showIrBeforeCalibration;
    private volatile boolean calibrationMode;
    private boolean resumed;
    private int calibrationTapCount;
    private long lastCalibrationTapMs;
    private int settingsTapCount;
    private long lastSettingsTapMs;
    private boolean irCenterAutoExposure = true;
    private static final int AUTH_FRAME_COUNT = 5;
    private static final float AUTH_LIVE_THRESHOLD = 0.85f;
    private volatile FaceRecognitionManager faceRecognitionManager;
    private volatile boolean faceRecognitionMode;
    private final AtomicBoolean enrollRequested = new AtomicBoolean(false);
    private static final int ENROLL_TARGET_FRAME_COUNT = 5;
    private final List<float[]> enrollEmbeddingBuffer = new ArrayList<>();
    private volatile String pendingEnrollmentId;
    private volatile String pendingEnrollmentName;
    private FaceTemplateRepository faceTemplateRepository;
    private volatile String recogModelChecksum;
    private FaceEmbeddingModel.DelegateType recogDelegate = FaceEmbeddingModel.DEFAULT_DELEGATE;
    private String recogModelPath = FaceEmbeddingModel.DEFAULT_MODEL_PATH;
    private volatile boolean authMode;
    private volatile boolean authVerdictShowing;
    private volatile boolean testMenuShowing;
    private final List<float[]> authScoreBuffer = new ArrayList<>();
    private long authStartNs;
    private long lastFaceDetectedMs;
    private volatile String normalStatusMessage = "Initializing...";
    private int trackingFrames;
    private int inferenceFrames;
    private long trackingWindowStartNs;
    private long inferenceWindowStartNs;
    private volatile long detectionMs;
    private volatile long inferenceMs;
    private volatile long rgbInferenceMs = -1L;
    private volatile long irInferenceMs = -1L;
    private volatile long recognitionInferenceMs = -1L;
    private final LatencyWindow preprocessLatency = new LatencyWindow(LATENCY_WINDOW_SIZE);
    private final LatencyWindow invokeLatency = new LatencyWindow(LATENCY_WINDOW_SIZE);
    private final LatencyWindow inferenceQueueLatency = new LatencyWindow(LATENCY_WINDOW_SIZE);
    private final LatencyWindow inferenceEndToEndLatency = new LatencyWindow(LATENCY_WINDOW_SIZE);
    private final LatencyWindow captureSaveLatency = new LatencyWindow(LATENCY_WINDOW_SIZE);
    private volatile float trackingFps;
    private volatile float inferenceFps;
    private long lastUiUpdateTimeMs;
    private long lastMotionDiagnosticLogTimeMs;
    private boolean inferenceBlockedByMotion;
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
        faceTemplateRepository = new FaceTemplateRepository(getApplicationContext());
        buildUi();
        initializeEngines();
        signalingClient.setListener(signalingListener);
        signalingClient.connect(SIGNALING_SERVER_URL, SIGNALING_PEER_ID);
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED ||
            checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE}, CAMERA_PERMISSION_REQUEST);
        }
    }

    private void handleCallInvite(String fromPeerId, String callId) {
        Intent intent = new Intent(this, WebRtcCallActivity.class);
        intent.putExtra(WebRtcCallActivity.EXTRA_REMOTE_PEER_ID, fromPeerId);
        intent.putExtra(WebRtcCallActivity.EXTRA_CALL_ID, callId);
        startActivity(intent);
    }

    private void buildUi() {
        screen = new MainScreenView(this, new MainScreenView.Listener() {
            @Override public void onPauseCollection() { toggleCollectionPaused(); }

            @Override public void onCancelCollection() { cancelDataCollection(); }

            @Override public void onStartAttackLiveCapture() { startAttackLiveCapture(); }

            @Override public void onStopAttackLiveCapture() { stopAttackLiveCapture(); }

            @Override public void onHighQualityOnlyChanged(boolean checked) {
                highQualityOnly = checked;
            }

            @Override public void onStartCollection(String className) {
                int nextNum = getNextSubjectNumber(className);
                startDataCollection(className, nextNum);
            }

            @Override public void onSwitchPreview() { setIrVisible(!showIr); }

            @Override public void onToggleModel() { toggleModel(); }

            @Override public void onToggleIrAutoExposure() { toggleIrAutoExposure(); }

            @Override public void onCalibrationConfirm() {
                calibrationRequested.set(true);
                calibrationInstruction.setText("Hold still while RGB and IR faces are measured...");
            }

            @Override public void onCalibrationCancel() { exitCalibrationMode(); }

            @Override public void onCalibrationTap() { recordCalibrationTap(); }

            @Override public void onSettingsTap() { recordSettingsTap(); }
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
        stopAttackLiveCaptureButton = screen.stopAttackLiveCaptureButton;
        highQualityOnlyContainer = screen.highQualityOnlyContainer;
        highQualityOnlyButton = screen.highQualityOnlyButton;
        collectionProgress = screen.collectionProgress;
        screen.setInitialPerformanceText(String.format(Locale.US, "Detect %d ms  %.1f FPS\nSpoof inference %d ms  %.1f FPS", 0, 0.0f, 0, 0.0f));
        resetResultsLabelToZero();
        setContentView(screen.root);
    }
    private void setIrVisible(boolean visible) {
        showIr = visible;
        screen.setIrVisible(showIr);
        updateIrAeModeLabel();
        if (showIr) {
            synchronized (irPreviewLock) {
                releaseIrPreviewBufferLocked();
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

    private void recordSettingsTap() {
        long now = SystemClock.elapsedRealtime();
        if (now - lastSettingsTapMs > 2_000L) settingsTapCount = 0;
        lastSettingsTapMs = now;
        if (++settingsTapCount >= 5) {
            settingsTapCount = 0;
            showHiddenTestMenu();
        }
    }

    private void showHiddenTestMenu() {
        testMenuShowing = true;
        InferenceTask pending = pendingInference.getAndSet(null);
        if (pending != null) pending.recycle();
        invalidateRecognitionWork();
        synchronized (authScoreBuffer) {
            authScoreBuffer.clear();
            authStartNs = 0L;
        }
        String[] items = {
                "SETTINGS",
                "WEBRTC TEST",
                "AUTH MODE (" + (authMode ? "ON" : "OFF") + ")",
                "DETECTOR: " + (activeFaceDetector != null ? activeFaceDetector.label() : "UNAVAILABLE"),
                "FACE MANAGEMENT"
        };
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("TEST MENU")
                .setItems(items, (d, which) -> {
                    if (which == 0) {
                        startActivity(new Intent(Settings.ACTION_SETTINGS));
                    } else if (which == 1) {
                        startActivity(new Intent(this, WebRtcCallActivity.class));
                    } else if (which == 2) {
                        toggleAuthMode();
                    } else if (which == 3) {
                        toggleFaceDetector();
                    } else if (which == 4) {
                        openFaceManagement();
                    }
                })
                .setNegativeButton("CANCEL", null)
                .create();
        dialog.setOnDismissListener(d -> {
            testMenuShowing = false;
            synchronized (authScoreBuffer) {
                authScoreBuffer.clear();
                authStartNs = 0L;
            }
        });
        dialog.show();
    }

    private void openFaceManagement() {
        FaceRecognitionManager manager = faceRecognitionManager;
        if (manager == null || !manager.isReady() || recogModelChecksum == null) {
            showTransientStatus("Wait for recognition model loading to finish");
            return;
        }
        invalidateRecognitionWork();
        cancelEnrollment();
        Intent intent = new Intent(this, FaceRecognitionActivity.class);
        String modelPath = manager.getModelAssetPath();
        intent.putExtra(FaceRecognitionActivity.EXTRA_MODEL_ASSET_PATH, modelPath);
        intent.putExtra(FaceRecognitionActivity.EXTRA_MODEL_CHECKSUM, recogModelChecksum);
        intent.putExtra(FaceRecognitionActivity.EXTRA_MODEL_LABEL, recognitionModelLabel(modelPath));
        intent.putExtra(FaceRecognitionActivity.EXTRA_RECOGNITION_ENABLED, faceRecognitionMode);
        intent.putExtra(FaceRecognitionActivity.EXTRA_DELEGATE_LABEL, manager.getActiveDelegate());
        startActivityForResult(intent, FACE_MANAGEMENT_REQUEST);
    }

    private void toggleRecognitionFromManagement() {
        faceRecognitionMode = !faceRecognitionMode;
        invalidateRecognitionWork();
        showTransientStatus("FACE RECOGNITION: " + (faceRecognitionMode ? "ON" : "OFF"));
    }

    private void toggleRecognitionDelegateFromManagement() {
        FaceEmbeddingModel.DelegateType requestedDelegate =
                recogDelegate == FaceEmbeddingModel.DelegateType.CPU
                        ? FaceEmbeddingModel.DelegateType.NNAPI
                        : FaceEmbeddingModel.DelegateType.CPU;
        reloadRecognitionModel(recogModelPath, requestedDelegate);
    }

    private void toggleRecognitionModelFromManagement() {
        String requestedModelPath;
        if (recogModelPath.equals(FaceEmbeddingModel.MODEL_NPU_INT8)) {
            requestedModelPath = FaceEmbeddingModel.MODEL_FLOAT16;
        } else if (recogModelPath.equals(FaceEmbeddingModel.MODEL_FLOAT16)) {
            requestedModelPath = FaceEmbeddingModel.MODEL_FLOAT32;
        } else {
            requestedModelPath = FaceEmbeddingModel.MODEL_NPU_INT8;
        }
        reloadRecognitionModel(requestedModelPath, recogDelegate);
    }

    private void reloadRecognitionModel(String requestedModelPath,
                                        FaceEmbeddingModel.DelegateType requestedDelegate) {
        invalidateRecognitionWork();
        cancelEnrollment();
        showTransientStatus("Loading recognition model...");
        modelInitExecutor.execute(() -> {
            FaceRecognitionManager manager = faceRecognitionManager;
            boolean reloaded = manager != null && manager.reloadModel(
                    getApplicationContext(), requestedModelPath, requestedDelegate);
            String checksum = reloaded ? modelChecksum(requestedModelPath) : null;
            runOnUiThread(() -> {
                if (reloaded && checksum != null) {
                    recogModelPath = requestedModelPath;
                    recogDelegate = requestedDelegate;
                    recogModelChecksum = checksum;
                    loadPersistedTemplates(manager, requestedModelPath, checksum);
                }
                showTransientStatus(formatRecognitionReloadStatus(reloaded, manager,
                        requestedModelPath, requestedDelegate));
            });
        });
    }

    private final Handler authHandler = new Handler(Looper.getMainLooper());
    private final Runnable hideAuthResultRunnable = () -> {
        authVerdictShowing = false;
        synchronized (authScoreBuffer) {
            authScoreBuffer.clear();
            authStartNs = 0L;
        }
        screen.hideAuthResult();
    };

    private void toggleAuthMode() {
        if (isCollecting || isAttackLiveCapturing || calibrationMode) {
            showTransientStatus("Cannot toggle Auth Mode during capture/calibration");
            return;
        }
        authMode = !authMode;
        authHandler.removeCallbacks(hideAuthResultRunnable);
        authVerdictShowing = false;
        synchronized (authScoreBuffer) {
            authScoreBuffer.clear();
            authStartNs = 0L;
        }
        screen.setAuthMode(authMode);
        if (!authMode) {
            resetResultsLabelToZero();
        }
        showTransientStatus(authMode ? "AUTH MODE: ON" : "AUTH MODE: OFF");
    }

    private void showAuthVerdict(boolean isLive, float avgLiveScore, String topSpoofLabel, long elapsedMs) {
        authVerdictShowing = true;
        InferenceTask pending = pendingInference.getAndSet(null);
        if (pending != null) pending.recycle();
        if (isLive) {
            playCollectionFinishedTone();
        } else {
            playAuthFailedTone();
        }

        String title = isLive ? "AUTH SUCCESS" : "AUTH FAILED";
        String resultText = isLive ? "LIVE" : "SPOOF (" + topSpoofLabel + ")";
        String message = String.format(Locale.US,
                "%s\nResult: %s\nLive Score: %.1f%%\nFrame Count: %d\nTime: %dms",
                title, resultText, avgLiveScore * 100f, AUTH_FRAME_COUNT, elapsedMs);

        SpannableString spannable = new SpannableString(message);
        int titleColor = isLive ? Color.rgb(0, 230, 118) : Color.rgb(255, 82, 82);
        spannable.setSpan(new ForegroundColorSpan(titleColor), 0, title.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        screen.showAuthResult(spannable);

        authHandler.removeCallbacks(hideAuthResultRunnable);
        authHandler.postDelayed(hideAuthResultRunnable, 3000L);
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
                    activeFaceDetector = detector;
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
            }
            try {
                MediaPipeFaceDetector detector = new MediaPipeFaceDetector(getApplicationContext());
                synchronized (classifierLock) {
                    if (enginesShutDown) {
                        detector.close();
                        return;
                    }
                    mediaPipeFaceDetector = detector;
                    if (activeFaceDetector == null) activeFaceDetector = detector;
                }
            } catch (Exception e) {
                String message = e.getMessage();
                reportEngineError(message == null ? "MediaPipe detector unavailable" : message);
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
        try {
            FaceRecognitionManager recManager = new FaceRecognitionManager(getApplicationContext(), recogModelPath, recogDelegate);
            if (recManager.isReady()) {
                String modelChecksum = modelChecksum(recManager.getModelAssetPath());
                if (modelChecksum == null) throw new IllegalStateException("Model checksum unavailable");
                synchronized (classifierLock) {
                    if (enginesShutDown) {
                        recManager.close();
                    } else {
                        faceRecognitionManager = recManager;
                        recogModelChecksum = modelChecksum;
                        android.util.Log.i(TAG, "FaceRecognitionManager loaded successfully (" + recManager.getActiveDelegate() + ", " + recManager.getModelAssetPath() + ")");
                        loadPersistedTemplates(recManager, recManager.getModelAssetPath(), modelChecksum);
                    }
                }
            } else {
                recManager.close();
            }
        } catch (Exception e) {
            android.util.Log.w(TAG, "FaceRecognitionManager load failed: " + e.getMessage());
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
        applyIrAutoExposure();
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

    private void applyIrAutoExposure() {
        if (irCenterAutoExposure) {
            IrCameraExposureController.applyCenterAutoExposure();
        } else {
            IrCameraExposureController.applyFullAutoExposure();
        }
    }

    private void toggleIrAutoExposure() {
        irCenterAutoExposure = !irCenterAutoExposure;
        applyIrAutoExposure();
        updateIrAeModeLabel();
        showTransientStatus("IR AE: " + (irCenterAutoExposure ? "CENTER" : "FULL"));
    }

    private void updateIrAeModeLabel() {
        screen.setIrAeMode(showIr ? (irCenterAutoExposure ? "CENTER" : "FULL") : null);
    }

    @Override protected void onResume() {
        super.onResume();
        signalingClient.setListener(signalingListener);
        resumed = true;
        HardwareControls.setLcdBrightness(90);
        startCameras();
    }

    @Override protected void onPause() {
        if (calibrationMode) exitCalibrationMode();
        resumed = false;
        authHandler.removeCallbacks(hideAuthResultRunnable);
        authVerdictShowing = false;
        testMenuShowing = false;
        synchronized (authScoreBuffer) {
            authScoreBuffer.clear();
            authStartNs = 0L;
        }
        if (screen != null) screen.hideAuthResult();
        pipelineGeneration.advance();
        if (cameras != null) {
            cameras.stop();
            cameras = null;
        }
        HardwareControls.setIrLed(false);
        clearPendingWork();
        overlay.clearResult();
        if (screen != null) screen.clearCleanModeResult();
        synchronized (irPreviewLock) {
            releaseIrPreviewBufferLocked();
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
                    copyToIrPreviewBufferLocked(frame.bitmap);
                }
            }
        }
        synchronized (irLock) {
            if (latestIr != null) latestIr.recycle();
            latestIr = frame;
        }
    }

    private void copyToIrPreviewBufferLocked(Bitmap source) {
        if (latestIrBitmapForCrop == null || latestIrBitmapForCrop.isRecycled()
                || latestIrBitmapForCrop.getWidth() != source.getWidth()
                || latestIrBitmapForCrop.getHeight() != source.getHeight()) {
            releaseIrPreviewBufferLocked();
            latestIrBitmapForCrop = Bitmap.createBitmap(source.getWidth(), source.getHeight(),
                    Bitmap.Config.ARGB_8888);
            irPreviewCanvas.setBitmap(latestIrBitmapForCrop);
        }
        irPreviewCanvas.drawBitmap(source, 0f, 0f, null);
    }

    private void releaseIrPreviewBufferLocked() {
        irPreviewCanvas.setBitmap(null);
        if (latestIrBitmapForCrop != null && !latestIrBitmapForCrop.isRecycled()) {
            latestIrBitmapForCrop.recycle();
        }
        latestIrBitmapForCrop = null;
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
            if (enginesShutDown) closeFaceDetectors();
        }
    }

    private void processTracking(TrackingFrame frame) {
        if (!isPipelineCurrent(frame.generation)) return;
        FaceDetectionEngine activeDetector = activeFaceDetector;
        if (activeDetector == null || calibration == null) return;
        boolean captureCalibration = calibrationMode && calibrationRequested.getAndSet(false);
        boolean prepareCollectionQuality = !captureCalibration
                && isCollecting && !collectionPaused && frame.ir != null && !ioBusy
                && activeDetector == faceDetector
                && shouldCheckCollectionQuality(collectionClassName)
                && getCollectionCountdownSeconds(SystemClock.elapsedRealtime()) <= 0;
        long start = SystemClock.elapsedRealtimeNanos();
        Rect detected = captureCalibration
                ? activeDetector.detectSingle(frame.rgb.bitmap)
                : prepareCollectionQuality
                        ? faceDetector.detectLargestWithQualityData(frame.rgb.bitmap)
                        : activeDetector.detectLargest(frame.rgb.bitmap);
        PointF[] currentLandmarks = activeDetector.getLastDetectedLandmarks();
        if (!isPipelineCurrent(frame.generation)) return;
        detectionMs = (SystemClock.elapsedRealtimeNanos() - start) / 1_000_000L;
        updateTrackingFps();
        if (detected == null) {
            inferenceMotionGate.reset();
            if (recognitionFaceVisible.getAndSet(false)) invalidateRecognitionWork();
            synchronized (authScoreBuffer) {
                authScoreBuffer.clear();
                authStartNs = 0L;
            }
            runOnUiThread(() -> {
                if (!isPipelineCurrent(frame.generation)) return;
                overlay.clearResult();
                overlay.clearRecognitionResult();
                recognitionInferenceMs = -1L;
                if (screen != null) screen.clearCleanModeResult();
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
            Rect detectedIr = activeDetector.detectSingle(frame.ir.bitmap);
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
                if (!prepareCollectionQuality) return;
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
                Rect irR = FaceCrop.expand(irDetected, margin, frame.ir.bitmap.getWidth(), frame.ir.bitmap.getHeight());
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
                    ioBusy = false;
                    return;
                }

                final FramePair capturePair = frame.detachPair();
                OwnedFrameTask saveTask = new OwnedFrameTask(capturePair, () -> {
                    boolean saved = false;
                    boolean sectorCompleted = false;
                    long saveStartNs = 0L;
                    try {
                        if (!isPipelineCurrent(frame.generation)
                                || !isActiveCollection(sessionId, className, subjectId)) {
                            return;
                        }
                        saveStartNs = SystemClock.elapsedRealtimeNanos();
                        boolean dirReady = sampleDir.isDirectory() || sampleDir.mkdirs();
                        boolean savedAll = dirReady;
                        if (!dirReady) {
                            showTransientStatus("Save failed: unable to create " + displayDir);
                            android.util.Log.e(TAG, "Unable to create collection sample folder: " + displayDir);
                        }
                        if (savedAll) savedAll = saveBitmapAsBmp(capturePair.rgb.bitmap,
                                new File(sampleDir, "RGB.bmp"));
                        if (savedAll) savedAll = saveBitmapRegionAsBmp(capturePair.rgb.bitmap, rgbR,
                                new File(sampleDir, "cropRGB.bmp"));
                        if (savedAll) savedAll = saveBitmapAsBmp(capturePair.ir.bitmap,
                                new File(sampleDir, "IR.bmp"));
                        if (savedAll) savedAll = saveBitmapRegionAsBmp(capturePair.ir.bitmap, irR,
                                new File(sampleDir, "cropIR.bmp"));
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
                try {
                    ioExecutor.execute(saveTask);
                } catch (RejectedExecutionException e) {
                    saveTask.discard();
                    ioBusy = false;
                    android.util.Log.w(TAG, "Capture save rejected during shutdown", e);
                }
            } else {
                ioBusy = false;
                runOnUiThread(this::finishDataCollection);
            }
        }

        if (!isCollecting && !testMenuShowing) {
            scheduleRecognition(frame, detected, currentLandmarks);
        }

        if (isCollecting || authVerdictShowing || testMenuShowing || rgbCrop == null || irCrop == null || frame.ir == null) return;
        if (!authMode) {
            FaceMotionGate.Decision motion = inferenceMotionGate.evaluate(detected.left, detected.top,
                    detected.right, detected.bottom, frame.rgb.bitmap.getWidth(), frame.rgb.bitmap.getHeight(),
                    frame.rgb.timestampNs);
            logMotionDiagnostic(frame, detected, motion);
            if (!motion.allowInference) {
                if (!inferenceBlockedByMotion) blockInferenceForMotion(frame.generation);
                return;
            }
        }
        inferenceBlockedByMotion = false;
        submitInference(new InferenceTask(frame.detachPair(), detected, irDetected, rgbCrop, irCrop, currentLandmarks,
                frame.generation, activeClassifier, frame.receivedNs, inferenceMotionGeneration.get()));
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

    private void logMotionDiagnostic(TrackingFrame frame, Rect rgbFace, FaceMotionGate.Decision motion) {
        long now = SystemClock.elapsedRealtime();
        if (now - lastMotionDiagnosticLogTimeMs < MOTION_DIAGNOSTIC_LOG_INTERVAL_MS) return;
        lastMotionDiagnosticLogTimeMs = now;
        double pairDeltaMs = (frame.rgb.timestampNs - frame.ir.timestampNs) / 1_000_000.0;
        android.util.Log.i(TAG, String.format(Locale.US,
                "Motion gate pairDeltaMs=%+.1f rgbFace=%s speed=%.2f faceWidthsPerSec edge=%b moving=%b stableFrames=%d allow=%b",
                pairDeltaMs, rgbFace, motion.speedFaceWidthsPerSecond, motion.touchesEdge,
                motion.moving, motion.stableFrames, motion.allowInference));
    }

    private void blockInferenceForMotion(int generation) {
        inferenceBlockedByMotion = true;
        synchronized (authScoreBuffer) {
            authScoreBuffer.clear();
            authStartNs = 0L;
        }
        long motionGeneration = inferenceMotionGeneration.incrementAndGet();
        InferenceTask pending = pendingInference.getAndSet(null);
        if (pending != null) pending.recycle();
        runOnUiThread(() -> {
            if (!isPipelineCurrent(generation) || motionGeneration != inferenceMotionGeneration.get()) return;
            overlay.clearClassificationResult();
            if (screen != null) screen.clearCleanModeResult();
            resetResultsLabelToZero();
        });
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
                try {
                    if (!authVerdictShowing && !testMenuShowing) {
                        processInference(task);
                    }
                }
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
            if (enginesShutDown) closeAntiSpoofClassifiers();
        }
    }

    private void processInference(InferenceTask task) {
        if (authVerdictShowing || testMenuShowing || !isPipelineCurrent(task.generation) || task.classifier == null) return;
        long startNs = SystemClock.elapsedRealtimeNanos();
        long queueMs = (startNs - task.enqueuedNs) / 1_000_000L;
        SlotClassificationResult result = task.classifier.classify(task.pair.rgb.bitmap, task.rgbCrop,
                task.pair.ir.bitmap, task.irCrop);
        long endToEndMs = (SystemClock.elapsedRealtimeNanos() - task.receivedNs) / 1_000_000L;
        if (authVerdictShowing || testMenuShowing || !isPipelineCurrent(task.generation)
                || task.motionGeneration != inferenceMotionGeneration.get()) return;
        inferenceMs = result.inferenceMs;
        rgbInferenceMs = result.rgbResult != null ? result.rgbResult.inferenceMs : -1L;
        irInferenceMs = result.irResult != null ? result.irResult.inferenceMs : -1L;
        recordInferenceMetrics(result.preprocessMs, result.inferenceMs, queueMs, endToEndMs);
        updateInferenceFps();

        maybeSaveAttackLiveCapture(task, result);
        runOnUiThread(() -> {
            if (authVerdictShowing || !isPipelineCurrent(task.generation)
                    || task.motionGeneration != inferenceMotionGeneration.get()) return;
            if (authMode) {
                ClassificationResult primary = result.primaryResult();
                if (primary != null && primary.probabilities != null && primary.probabilities.length > 0) {
                    float[] probs = primary.probabilities.clone();
                    synchronized (authScoreBuffer) {
                        if (authScoreBuffer.isEmpty()) {
                            authStartNs = task.receivedNs;
                        }
                        authScoreBuffer.add(probs);
                        if (authScoreBuffer.size() >= AUTH_FRAME_COUNT) {
                            long elapsedMs = (SystemClock.elapsedRealtimeNanos() - authStartNs) / 1_000_000L;
                            int classCount = probs.length;
                            float[] sumProbs = new float[classCount];
                            for (float[] p : authScoreBuffer) {
                                for (int i = 0; i < classCount && i < p.length; i++) {
                                    sumProbs[i] += p[i];
                                }
                            }
                            int frameCount = authScoreBuffer.size();
                            float avgLive = sumProbs[0] / frameCount;
                            boolean isLive = avgLive >= AUTH_LIVE_THRESHOLD;

                            int topSpoofIndex = 1;
                            for (int i = 2; i < classCount; i++) {
                                if (sumProbs[i] > sumProbs[topSpoofIndex]) {
                                    topSpoofIndex = i;
                                }
                            }
                            String topSpoofLabel = (topSpoofIndex < ClassificationResult.LABELS.length)
                                    ? ClassificationResult.displayLabel(topSpoofIndex)
                                    : "UNKNOWN";

                            authScoreBuffer.clear();
                            authStartNs = 0L;
                            showAuthVerdict(isLive, avgLive, topSpoofLabel, elapsedMs);
                        }
                    }
                }
                return;
            }
            overlay.showResult(result.primaryResult(), result.irResult);
            resultsLabel.setText(formatClassificationResults(result));
            if (screen != null) screen.showCleanModeResult(result);
            
            long now = SystemClock.elapsedRealtime();
            if (now - lastUiUpdateTimeMs >= 150L) {
                performance.setText(formatPerformance());
                lastUiUpdateTimeMs = now;
            }
        });
    }

    private static String recognitionModelLabel(String modelPath) {
        if (modelPath == null) return "N/A";
        if (modelPath.contains("int8")) return "INT8";
        if (modelPath.contains("float16")) return "FP16";
        if (modelPath.contains("float32")) return "FP32";
        return modelPath;
    }

    private static String formatRecognitionReloadStatus(boolean reloaded, FaceRecognitionManager manager,
                                                        String requestedModelPath,
                                                        FaceEmbeddingModel.DelegateType requestedDelegate) {
        String requested = recognitionModelLabel(requestedModelPath) + " " + requestedDelegate;
        if (manager == null) return "RECOG RELOAD FAILED: requested " + requested
                + ", manager unavailable";
        String active = recognitionModelLabel(manager.getModelAssetPath()) + " "
                + manager.getRequestedDelegate() + "→" + manager.getActiveDelegate();
        if (reloaded) return "RECOG READY: requested " + requested + ", active " + active;
        String error = manager.getInitError();
        return "RECOG RELOAD FAILED: requested " + requested + ", "
                + (error == null ? "unknown error" : error) + " (active " + active + ")";
    }

    private void loadPersistedTemplates(FaceRecognitionManager manager, String modelAssetPath,
                                        String modelChecksum) {
        if (modelChecksum == null) return;
        ioExecutor.execute(() -> {
            List<FaceTemplate> templates = faceTemplateRepository.loadForModel(modelAssetPath, modelChecksum);
            if (!enginesShutDown && manager == faceRecognitionManager
                    && modelAssetPath.equals(manager.getModelAssetPath())
                    && modelChecksum.equals(recogModelChecksum)) {
                manager.replaceTemplates(templates);
                android.util.Log.i(TAG, "Loaded " + templates.size()
                        + " persisted face templates for " + modelAssetPath);
            }
        });
    }

    private String modelChecksum(String modelAssetPath) {
        try {
            return FaceModelFingerprint.sha256(getApplicationContext(), modelAssetPath);
        } catch (Exception e) {
            android.util.Log.e(TAG, "Failed to calculate face model checksum: " + modelAssetPath, e);
            return null;
        }
    }

    private void scheduleRecognition(TrackingFrame sourceFrame, Rect rgbFace, PointF[] landmarks) {
        FaceRecognitionManager manager = faceRecognitionManager;
        boolean enrollment = enrollRequested.get();
        boolean managerReady = manager != null && manager.isReady();
        int enrolledCount = manager != null ? manager.getEnrolledCount() : 0;
        if (!RecognitionPolicy.shouldSchedule(enrollment, faceRecognitionMode,
                managerReady, enrolledCount)) {
            if (enrollment && !managerReady) {
                final boolean[] cancelled = new boolean[1];
                recognitionCoordinator.runExclusive(() -> {
                    cancelled[0] = enrollRequested.compareAndSet(true, false);
                    if (cancelled[0]) enrollEmbeddingBuffer.clear();
                });
                if (!cancelled[0]) return;
                android.util.Log.e(TAG, "Recognition request error: model not ready");
                runOnUiThread(() -> showTransientStatus("Face recognition model not ready"));
            }
            return;
        }
        recognitionFaceVisible.set(true);
        long invalidationGeneration = recognitionCoordinator.acquireWorkerGeneration();
        if (invalidationGeneration < 0L) return;
        long startNs = SystemClock.elapsedRealtimeNanos();
        String mode = enrollment ? "ENROLL" : "IDENTIFY";
        android.util.Log.i(TAG, "Recognition request accepted: id=" + startNs + " mode=" + mode);
        Bitmap alignedFace;
        try {
            alignedFace = recognitionCoordinator.prepareOwnedWork(() ->
                    FaceRecognitionManager.alignFace(sourceFrame.rgb.bitmap, rgbFace, landmarks));
        } catch (RuntimeException e) {
            android.util.Log.e(TAG, "Recognition request error: id=" + startNs
                    + " mode=" + mode + " alignment failed", e);
            runOnUiThread(() -> showTransientStatus("Face recognition alignment failed"));
            return;
        }
        long alignMs = (SystemClock.elapsedRealtimeNanos() - startNs) / 1_000_000L;
        if (alignedFace == null) {
            android.util.Log.e(TAG, "Recognition request error: id=" + startNs
                    + " mode=" + mode + " alignment failed");
            runOnUiThread(() -> showTransientStatus("Face recognition alignment failed"));
            return;
        }
        if (!recognitionCoordinator.isCurrent(invalidationGeneration)
                || !isPipelineCurrent(sourceFrame.generation)
                || (enrollment ? !enrollRequested.get() : !faceRecognitionMode)) {
            alignedFace.recycle();
            recognitionCoordinator.releaseWorker();
            android.util.Log.i(TAG, "Recognition request cancelled: id=" + startNs
                    + " mode=" + mode + " invalidated before submission");
            return;
        }

        String enrollmentId = enrollment ? pendingEnrollmentId : null;
        String enrollmentName = enrollment ? pendingEnrollmentName : null;
        String modelChecksum = enrollment ? recogModelChecksum : null;
        submitRecognition(new RecognitionTask(alignedFace, manager, enrollment, enrollmentId, enrollmentName,
                modelChecksum,
                sourceFrame.generation, invalidationGeneration, startNs, alignMs));
    }

    private void submitRecognition(RecognitionTask task) {
        RecognitionWork work = new RecognitionWork(task);
        try {
            recognitionExecutor.execute(work);
        } catch (RejectedExecutionException e) {
            work.discard("executor rejected during shutdown", e);
            if (enginesShutDown) closeFaceRecognitionManager();
        }
    }

    private void runRecognition(RecognitionTask task) {
        try {
            if (!isRecognitionTaskCurrent(task)) {
                android.util.Log.i(TAG, "Recognition request cancelled: id=" + task.startedNs
                        + " invalidated before inference");
                return;
            }
            processRecognition(task);
        } catch (Exception e) {
            android.util.Log.e(TAG, "Recognition request error: id=" + task.startedNs, e);
            if (isRecognitionTaskCurrent(task)) {
                runOnUiThread(() -> showTransientStatus("Face recognition failed"));
            }
        } finally {
            task.recycle();
            recognitionCoordinator.releaseWorker();
            if (enginesShutDown) closeFaceRecognitionManager();
        }
    }

    private void processRecognition(RecognitionTask task) {
        long runStartNs = SystemClock.elapsedRealtimeNanos();
        long queueMs = (runStartNs - task.enqueuedNs) / 1_000_000L;
        float[] embedding = task.manager.extractAlignedEmbedding(task.alignedFace);
        long totalMs = (SystemClock.elapsedRealtimeNanos() - task.startedNs) / 1_000_000L;
        long modelMs = task.manager.getLastInferenceMs();
        if (!FaceEmbeddingModel.isValidEmbedding(embedding)) {
            throw new IllegalStateException("Invalid face embedding");
        }
        float embeddingNorm = FaceEmbeddingModel.l2Norm(embedding);
        android.util.Log.i(TAG, String.format(Locale.US,
                "Recognition inference: id=%d delegate=%s dim=%d norm=%.6f Align=%dms Queue=%dms ModelRun=%dms Total=%dms",
                task.startedNs, task.manager.getActiveDelegate(), embedding.length, embeddingNorm,
                task.alignMs, queueMs, modelMs, totalMs));
        if (!isRecognitionTaskCurrent(task)) {
            android.util.Log.i(TAG, "Recognition request cancelled: id=" + task.startedNs
                    + " invalidated after inference");
            return;
        }

        if (task.enrollment) {
            FaceTemplate template = null;
            int collected;
            final FaceTemplate[] templateHolder = new FaceTemplate[1];
            final int[] collectedHolder = new int[1];
            boolean committed = recognitionCoordinator.commitIfCurrent(task.invalidationGeneration,
                    () -> !enginesShutDown && !testMenuShowing
                            && isPipelineCurrent(task.pipelineGeneration) && enrollRequested.get(), () -> {
                        enrollEmbeddingBuffer.add(embedding);
                        collectedHolder[0] = enrollEmbeddingBuffer.size();
                        if (collectedHolder[0] >= ENROLL_TARGET_FRAME_COUNT) {
                            templateHolder[0] = task.manager.enrollFaceAverage(
                                    task.enrollmentId, task.enrollmentName, enrollEmbeddingBuffer);
                            enrollEmbeddingBuffer.clear();
                            enrollRequested.set(false);
                        }
                    });
            if (!committed) {
                android.util.Log.i(TAG, "Recognition request cancelled: id=" + task.startedNs
                        + " enrollment invalidated before commit");
                return;
            }
            template = templateHolder[0];
            collected = collectedHolder[0];
            FaceTemplate enrolled = template;
            int collectedCount = collected;
            boolean enrollmentComplete = collected >= ENROLL_TARGET_FRAME_COUNT;
            android.util.Log.i(TAG, "Recognition request result: id=" + task.startedNs
                    + " mode=ENROLL collected=" + collectedCount + "/" + ENROLL_TARGET_FRAME_COUNT
                    + " complete=" + (enrolled != null));
            runOnUiThread(() -> {
                if (!isRecognitionTaskCurrent(task)) return;
                if (enrolled != null) {
                    playCollectionFinishedTone();
                    showTransientStatus("Enrolled: " + enrolled.getName() + " ("
                            + ENROLL_TARGET_FRAME_COUNT + "-frame avg, Total: "
                            + task.manager.getEnrolledCount() + ")");
                    persistEnrollmentAndReturn(task.manager, task.modelChecksum, enrolled);
                } else if (enrollmentComplete) {
                    showTransientStatus("Enrollment failed: average error");
                } else {
                    showTransientStatus("Enrolling: " + collectedCount + "/"
                            + ENROLL_TARGET_FRAME_COUNT + " frames...");
                }
            });
            return;
        }

        RecognitionResult recognition = task.manager.matchEmbedding(embedding,
                task.manager.getThreshold(), totalMs);
        android.util.Log.i(TAG, String.format(Locale.US,
                "Recognition request result: id=%d mode=IDENTIFY recognized=%b score=%.6f threshold=%.6f",
                task.startedNs, recognition.isRecognized(), recognition.similarityScore(),
                task.manager.getThreshold()));
        runOnUiThread(() -> {
            if (!isRecognitionTaskCurrent(task) || !faceRecognitionMode || authVerdictShowing) return;
            recognitionInferenceMs = modelMs;
            if (recognition.isRecognized()) {
                overlay.showRecognitionResult(String.format(Locale.US, "%s %.1f%%",
                        recognition.matchedTemplate().getName(), recognition.similarityScore() * 100f), true);
            } else {
                overlay.showRecognitionResult(String.format(Locale.US, "UNRECOGNIZED %.1f%%",
                        recognition.similarityScore() * 100f), false);
            }
            performance.setText(formatPerformance());
        });
    }

    private boolean isRecognitionTaskCurrent(RecognitionTask task) {
        return !enginesShutDown
                && !testMenuShowing
                && isPipelineCurrent(task.pipelineGeneration)
                && recognitionCoordinator.isCurrent(task.invalidationGeneration);
    }

    private void invalidateRecognitionWork() {
        recognitionCoordinator.invalidate();
        recognitionFaceVisible.set(false);
        recognitionInferenceMs = -1L;
        runOnUiThread(() -> {
            if (overlay != null) overlay.clearRecognitionResult();
            if (performance != null) performance.setText(formatPerformance());
        });
    }

    private void cancelEnrollment() {
        recognitionCoordinator.runExclusive(() -> {
            enrollRequested.set(false);
            enrollEmbeddingBuffer.clear();
        });
        pendingEnrollmentId = null;
        pendingEnrollmentName = null;
    }

    private void persistEnrollmentAndReturn(FaceRecognitionManager manager, String modelChecksum,
                                            FaceTemplate template) {
        String modelAssetPath = manager.getModelAssetPath();
        ioExecutor.execute(() -> {
            try {
                faceTemplateRepository.save(modelAssetPath, modelChecksum, template);
                runOnUiThread(() -> {
                    if (!isFinishing() && !isDestroyed()) openFaceManagement();
                });
            } catch (RuntimeException e) {
                manager.removeTemplate(template.getId());
                android.util.Log.e(TAG, "Face template save failed", e);
                runOnUiThread(() -> showTransientStatus("Enrollment save failed"));
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
                    FaceDetectionEngine detector = activeFaceDetector;
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
        if (isCollecting || isAttackLiveCapturing) return;
        FaceDetectionEngine activeDetector = activeFaceDetector;
        FaceDetector qualityDetector = faceDetector;
        if (activeDetector == null) {
            showTransientStatus("Face detector unavailable");
            startCollectionButton.setText("START CAPTURE");
            return;
        }
        if ("live".equals(className) && activeDetector != qualityDetector) {
            showTransientStatus("Live quality capture requires FaceMe");
            startCollectionButton.setText("START CAPTURE");
            return;
        }
        if ("live".equals(className) && (qualityDetector == null || !qualityDetector.isQualityAvailable())) {
            String message = qualityDetector == null ? "Face detector unavailable" : qualityDetector.qualityError();
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

    private void startAttackLiveCapture() {
        if (isCollecting || isAttackLiveCapturing) return;
        synchronized (attackCaptureLock) {
            if (attackCaptureSaveBusy) {
                showTransientStatus("Previous attack Live save is still finishing");
                return;
            }
        }
        if (!Environment.isExternalStorageManager()) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
            intent.setData(Uri.parse("package:" + getPackageName()));
            startActivity(intent);
            showTransientStatus("Please grant All Files Access and try again.");
            return;
        }
        attackCaptureRawRoot = resolveRawRoot();
        if (!prepareRawRoot(attackCaptureRawRoot)) {
            showTransientStatus("Save failed: unable to write "
                    + attackCaptureRawRoot.getAbsolutePath());
            return;
        }
        attackCaptureSubjectId = CaptureStorage.getNextSubjectNumber(attackCaptureRawRoot,
                "attack_live");
        attackCaptureCount = 0;
        attackCaptureSaveBusy = false;
        isAttackLiveCapturing = true;
        startCollectionButton.setEnabled(false);
        startCollectionButton.setText("START CAPTURE");
        stopAttackLiveCaptureButton.setVisibility(View.VISIBLE);
        showTransientStatus("Attack Live capture started");
        android.util.Log.i(TAG, "Attack Live capture started: "
                + attackCaptureRawRoot.getAbsolutePath() + "/attack_live/attack_live_"
                + attackCaptureSubjectId);
    }

    private void stopAttackLiveCapture() {
        synchronized (attackCaptureLock) {
            if (!isAttackLiveCapturing) return;
            isAttackLiveCapturing = false;
        }
        stopAttackLiveCaptureButton.setVisibility(View.GONE);
        FaceDetectionEngine detector = activeFaceDetector;
        startCollectionButton.setEnabled(detector != null);
        startCollectionButton.setText("START CAPTURE");
        showTransientStatus("Attack Live capture stopped: " + attackCaptureCount + " saved");
    }

    private void maybeSaveAttackLiveCapture(InferenceTask task, SlotClassificationResult result) {
        ClassificationResult primary = result.primaryResult();
        if (primary == null || !AttackLiveCaptureGate.shouldSave(primary.probabilities)) {
            return;
        }
        FramePair pair;
        synchronized (attackCaptureLock) {
            if (!isAttackLiveCapturing || attackCaptureSaveBusy) return;
            pair = task.detachPair();
            if (pair == null) return;
            attackCaptureSaveBusy = true;
        }
        if (pair == null) return;
        final int sampleIndex = attackCaptureCount + 1;
        final File root = attackCaptureRawRoot != null ? attackCaptureRawRoot : resolveRawRoot();
        final String subjectDirName = "attack_live_" + attackCaptureSubjectId;
        final File sampleDir = CaptureStorage.sampleDir(root, "attack_live", null,
                subjectDirName, sampleIndex);
        final String metadataJson = CaptureStorage.buildSampleMetadataJson(
                pair.rgb.bitmap.getWidth(), pair.rgb.bitmap.getHeight(), task.rgbFace, task.rgbCrop,
                pair.ir.bitmap.getWidth(), pair.ir.bitmap.getHeight(), task.irFace, task.irCrop,
                task.classifier.cropMarginRatio(), "attack_live", -1, -1, 0f);
        OwnedFrameTask saveTask = new OwnedFrameTask(pair, () -> {
            boolean saved = false;
            try {
                boolean dirReady = sampleDir.isDirectory() || sampleDir.mkdirs();
                if (!dirReady) {
                    showTransientStatus("Save failed: unable to create " + sampleDir.getAbsolutePath());
                    return;
                }
                saved = saveBitmapAsBmp(pair.rgb.bitmap, new File(sampleDir, "RGB.bmp"))
                        && saveBitmapRegionAsBmp(pair.rgb.bitmap, task.rgbCrop,
                        new File(sampleDir, "cropRGB.bmp"))
                        && saveBitmapAsBmp(pair.ir.bitmap, new File(sampleDir, "IR.bmp"))
                        && saveBitmapRegionAsBmp(pair.ir.bitmap, task.irCrop,
                        new File(sampleDir, "cropIR.bmp"))
                        && saveTextFile(metadataJson, new File(sampleDir, "meta.json"));
                if (saved) {
                    attackCaptureCount = sampleIndex;
                    android.util.Log.i(TAG, "Saved attack Live sample: " + sampleDir.getAbsolutePath());
                }
            } finally {
                synchronized (attackCaptureLock) {
                    attackCaptureSaveBusy = false;
                }
            }
            if (saved) runOnUiThread(this::playCaptureSavedTone);
        });
        try {
            attackCaptureExecutor.execute(saveTask);
        } catch (RejectedExecutionException e) {
            saveTask.discard();
            synchronized (attackCaptureLock) {
                attackCaptureSaveBusy = false;
            }
            android.util.Log.w(TAG, "Attack Live save rejected during shutdown", e);
        }
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

    private boolean saveBitmapRegionAsBmp(Bitmap bitmap, Rect region, File file) {
        CaptureStorage.SaveResult result = CaptureStorage.saveBitmapRegionAsBmp(bitmap, region, file);
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
                FaceDetectionEngine detector = activeFaceDetector;
                startCollectionButton.setEnabled(detector != null);
                switchButton.setEnabled(true);
            }
        }
        String recognitionText = faceRecognitionMode && recognitionInferenceMs >= 0L
                ? String.format(Locale.US, "\nRecog inference %d ms", recognitionInferenceMs)
                : "";
        if (rgbInferenceMs >= 0L && irInferenceMs >= 0L) {
            String prefix = String.format(Locale.US,
                    "Detect %d ms  %.1f FPS\nSpoof RGB %d ms  %.1f FPS\n",
                    detectionMs, trackingFps, rgbInferenceMs, inferenceFps);
            String irText = String.format(Locale.US, "Spoof IR %d ms", irInferenceMs);
            SpannableString text = new SpannableString(prefix + irText + recognitionText);
            text.setSpan(new ForegroundColorSpan(IR_RESULT_COLOR), prefix.length(),
                    prefix.length() + irText.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            return text;
        }
        return String.format(Locale.US,
                "Detect %d ms  %.1f FPS\nSpoof inference %d ms  %.1f FPS%s",
                detectionMs, trackingFps, inferenceMs, inferenceFps, recognitionText);
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
            sb.append(String.format(Locale.US, "%s %.1f%%", ClassificationResult.displayLabel(i), probability));
        }
    }

    private void resetResultsLabelToZero() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < ClassificationResult.LABELS.length; i++) {
            if (i > 0) sb.append("\n");
            sb.append(String.format(Locale.US, "%s 0.0%%", ClassificationResult.displayLabel(i)));
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

    private void toggleFaceDetector() {
        if (isCollecting || isAttackLiveCapturing || calibrationMode) return;
        FaceDetectionEngine next;
        synchronized (classifierLock) {
            if (activeFaceDetector == faceDetector && mediaPipeFaceDetector != null) {
                next = mediaPipeFaceDetector;
            } else if (faceDetector != null) {
                next = faceDetector;
            } else {
                return;
            }
            activeFaceDetector = next;
        }
        showTransientStatus(next == faceDetector
                ? "FaceMe detector selected"
                : "MediaPipe detector selected; live quality capture is unavailable");
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

    private void playAuthFailedTone() {
        ToneGenerator tone = captureTone;
        if (tone != null) {
            tone.startTone(ToneGenerator.TONE_PROP_BEEP, 120);
            authHandler.postDelayed(() -> {
                ToneGenerator t1 = captureTone;
                if (t1 != null) t1.startTone(ToneGenerator.TONE_PROP_BEEP, 120);
            }, 180L);
            authHandler.postDelayed(() -> {
                ToneGenerator t2 = captureTone;
                if (t2 != null) t2.startTone(ToneGenerator.TONE_PROP_BEEP, 120);
            }, 360L);
        }
    }

    private void clearPendingWork() {
        TrackingFrame tracking = pendingTracking.getAndSet(null);
        if (tracking != null) tracking.recycle();
        InferenceTask inference = pendingInference.getAndSet(null);
        if (inference != null) inference.recycle();
        invalidateRecognitionWork();
        cancelEnrollment();
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

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != FACE_MANAGEMENT_REQUEST) return;
        if (resultCode == FaceRecognitionActivity.RESULT_TOGGLE_RECOGNITION) {
            toggleRecognitionFromManagement();
            return;
        }
        if (resultCode == FaceRecognitionActivity.RESULT_TOGGLE_DELEGATE) {
            toggleRecognitionDelegateFromManagement();
            return;
        }
        if (resultCode == FaceRecognitionActivity.RESULT_TOGGLE_MODEL) {
            toggleRecognitionModelFromManagement();
            return;
        }
        FaceRecognitionManager manager = faceRecognitionManager;
        if (manager != null && manager.isReady()) {
            manager.clearTemplates();
            loadPersistedTemplates(manager, manager.getModelAssetPath(), recogModelChecksum);
        }
        if (resultCode != FaceRecognitionActivity.RESULT_ENROLL_REQUESTED || data == null) return;
        String name = data.getStringExtra(FaceRecognitionActivity.EXTRA_ENROLL_NAME);
        if (name == null || name.trim().isEmpty()) return;
        if (manager == null || !manager.isReady()) {
            showTransientStatus("Face recognition model not ready");
            return;
        }
        pendingEnrollmentId = UUID.randomUUID().toString();
        pendingEnrollmentName = name.trim();
        invalidateRecognitionWork();
        recognitionCoordinator.runExclusive(() -> {
            enrollEmbeddingBuffer.clear();
            enrollRequested.set(true);
        });
        showTransientStatus("Face the camera to enroll (" + ENROLL_TARGET_FRAME_COUNT + " frames)...");
    }

    @Override protected void onDestroy() {
        signalingClient.clearListener(signalingListener);
        signalingClient.disconnect();
        synchronized (classifierLock) {
            enginesShutDown = true;
        }
        pipelineGeneration.advance();
        if (cameras != null) cameras.stop();
        HardwareControls.setIrLed(false);
        clearPendingWork();
        trackingExecutor.shutdownNow();
        inferenceExecutor.shutdownNow();
        List<Runnable> discardedRecognitionWork = recognitionExecutor.shutdownNow();
        for (Runnable task : discardedRecognitionWork) {
            if (task instanceof RecognitionWork) {
                ((RecognitionWork) task).discard("executor stopped before inference", null);
            }
        }
        List<Runnable> discardedIoWork = ioExecutor.shutdownNow();
        for (Runnable task : discardedIoWork) {
            if (task instanceof OwnedFrameTask) ((OwnedFrameTask) task).discard();
        }
        List<Runnable> discardedAttackCaptureWork = attackCaptureExecutor.shutdownNow();
        for (Runnable task : discardedAttackCaptureWork) {
            if (task instanceof OwnedFrameTask) ((OwnedFrameTask) task).discard();
        }
        modelInitExecutor.shutdownNow();
        boolean inferenceTerminated = awaitExecutorTermination(inferenceExecutor);
        boolean recognitionTerminated = awaitExecutorTermination(recognitionExecutor);
        boolean trackingTerminated = awaitExecutorTermination(trackingExecutor);
        awaitExecutorTermination(ioExecutor);
        awaitExecutorTermination(attackCaptureExecutor);
        awaitExecutorTermination(modelInitExecutor);
        if (inferenceTerminated) closeAntiSpoofClassifiers();
        if (recognitionTerminated) closeFaceRecognitionManager();
        if (trackingTerminated) closeFaceDetectors();
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
        private FramePair pair;
        final Rect rgbFace;
        final Rect irFace;
        final Rect rgbCrop;
        final Rect irCrop;
        final PointF[] landmarks;
        final int generation;
        final ModelSlotClassifier classifier;
        final long receivedNs;
        final long enqueuedNs;
        final long motionGeneration;

        InferenceTask(FramePair pair, Rect rgbFace, Rect irFace, Rect rgbCrop, Rect irCrop, PointF[] landmarks,
                      int generation, ModelSlotClassifier classifier, long receivedNs, long motionGeneration) {
            this.pair = pair;
            this.rgbFace = rgbFace;
            this.irFace = irFace;
            this.rgbCrop = rgbCrop;
            this.irCrop = irCrop;
            this.landmarks = landmarks;
            this.generation = generation;
            this.classifier = classifier;
            this.receivedNs = receivedNs;
            this.enqueuedNs = SystemClock.elapsedRealtimeNanos();
            this.motionGeneration = motionGeneration;
        }

        void recycle() {
            if (pair != null) pair.recycle();
            pair = null;
        }

        FramePair detachPair() {
            FramePair detached = pair;
            pair = null;
            return detached;
        }
    }

    private static final class RecognitionTask {
        private Bitmap alignedFace;
        final FaceRecognitionManager manager;
        final boolean enrollment;
        final String enrollmentId;
        final String enrollmentName;
        final String modelChecksum;
        final int pipelineGeneration;
        final long invalidationGeneration;
        final long startedNs;
        final long enqueuedNs;
        final long alignMs;

        RecognitionTask(Bitmap alignedFace, FaceRecognitionManager manager, boolean enrollment,
                        String enrollmentId, String enrollmentName,
                        String modelChecksum,
                        int pipelineGeneration, long invalidationGeneration,
                        long startedNs, long alignMs) {
            this.alignedFace = alignedFace;
            this.manager = manager;
            this.enrollment = enrollment;
            this.enrollmentId = enrollmentId;
            this.enrollmentName = enrollmentName;
            this.modelChecksum = modelChecksum;
            this.pipelineGeneration = pipelineGeneration;
            this.invalidationGeneration = invalidationGeneration;
            this.startedNs = startedNs;
            this.enqueuedNs = SystemClock.elapsedRealtimeNanos();
            this.alignMs = alignMs;
        }

        void recycle() {
            Bitmap bitmap = alignedFace;
            alignedFace = null;
            if (bitmap != null && !bitmap.isRecycled()) bitmap.recycle();
        }
    }

    private final class RecognitionWork implements Runnable {
        private RecognitionTask task;

        RecognitionWork(RecognitionTask task) {
            this.task = task;
        }

        @Override public void run() {
            RecognitionTask owned = take();
            if (owned != null) runRecognition(owned);
        }

        void discard(String reason, Exception error) {
            RecognitionTask discarded = take();
            if (discarded == null) return;
            String message = "Recognition request cancelled: id=" + discarded.startedNs + " " + reason;
            if (error == null) android.util.Log.i(TAG, message);
            else android.util.Log.i(TAG, message, error);
            discarded.recycle();
            recognitionCoordinator.releaseWorker();
        }

        private synchronized RecognitionTask take() {
            RecognitionTask owned = task;
            task = null;
            return owned;
        }
    }

    private static final class OwnedFrameTask implements Runnable {
        private final FramePair pair;
        private final Runnable action;

        OwnedFrameTask(FramePair pair, Runnable action) {
            this.pair = pair;
            this.action = action;
        }

        @Override public void run() {
            try {
                action.run();
            } finally {
                discard();
            }
        }

        void discard() {
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

    private void closeAntiSpoofClassifiers() {
        synchronized (classifierLock) {
            for (ModelSlotClassifier slot : classifiers) {
                try { slot.close(); } catch (Exception ignored) {}
            }
            classifier = null;
            classifiers.clear();
        }
    }

    private void closeFaceRecognitionManager() {
        FaceRecognitionManager recManager = faceRecognitionManager;
        if (recManager != null) {
            faceRecognitionManager = null;
            try { recManager.close(); } catch (Exception ignored) {}
        }
    }

    private void closeFaceDetectors() {
        FaceDetector detector;
        MediaPipeFaceDetector mediaPipeDetector;
        synchronized (classifierLock) {
            detector = faceDetector;
            mediaPipeDetector = mediaPipeFaceDetector;
            faceDetector = null;
            mediaPipeFaceDetector = null;
            activeFaceDetector = null;
        }
        if (detector != null) detector.close();
        if (mediaPipeDetector != null) mediaPipeDetector.close();
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
