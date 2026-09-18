package com.virditech.ac7000;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
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
import android.view.View;
import android.view.WindowManager;
import android.widget.ArrayAdapter;

import java.io.File;
import com.cyberlink.faceme.FaceQualityLevel;
import com.virditech.ac7000.calibration.Calibration;
import com.virditech.ac7000.camera.DualCameraController;
import com.virditech.ac7000.camera.FrameData;
import com.virditech.ac7000.camera.FramePair;
import com.virditech.ac7000.camera.FrameSynchronizer;
import com.virditech.ac7000.capture.AttackLiveCaptureGate;
import com.virditech.ac7000.capture.CaptureProgressText;
import com.virditech.ac7000.capture.CaptureSchedule;
import com.virditech.ac7000.capture.CaptureSession;
import com.virditech.ac7000.capture.CaptureStep;
import com.virditech.ac7000.capture.CaptureStorage;
import com.virditech.ac7000.call.WebRtcCallActivity;
import com.virditech.ac7000.concurrent.GenerationGuard;
import com.virditech.ac7000.concurrent.LatestWinsExecutor;
import com.virditech.ac7000.face.FaceDetector;
import com.virditech.ac7000.face.FaceDetectionEngine;
import com.virditech.ac7000.face.MediaPipeFaceDetector;
import com.virditech.ac7000.device.DualLightingDetector;
import com.virditech.ac7000.device.ForegroundEntryDetector;
import com.virditech.ac7000.device.HardwareControls;
import com.virditech.ac7000.device.LightingExperimentLogger;
import com.virditech.ac7000.device.IrCameraExposureController;
import com.virditech.ac7000.device.AppWatchdog;
import com.virditech.ac7000.device.UbimDaemonClient;
import com.unionbiometrics.vision.VisionSdk;
import com.unionbiometrics.vision.api.AntiSpoofingEngine;
import com.unionbiometrics.vision.api.IrLedController;
import com.unionbiometrics.vision.api.EngineInfo;
import com.unionbiometrics.vision.api.FaceCrop;
import com.unionbiometrics.vision.api.AntiSpoofingFrame;
import com.unionbiometrics.vision.api.InferenceResult;
import com.unionbiometrics.vision.api.ClassLabels;
import com.unionbiometrics.vision.api.EngineLoadResult;
import com.unionbiometrics.vision.api.AntiSpoofingOptions;
import com.unionbiometrics.vision.api.ProbabilityResult;
import com.virditech.ac7000.model.AuthFrameAccumulator;
import com.virditech.ac7000.model.FaceMotionGate;
import com.virditech.ac7000.performance.LatencyWindow;
import com.virditech.ac7000.recognition.FaceEmbeddingModel;
import com.virditech.ac7000.recognition.FaceModelFingerprint;
import com.virditech.ac7000.recognition.FaceRecognitionActivity;
import com.virditech.ac7000.recognition.FaceRecognitionManager;
import com.virditech.ac7000.recognition.FaceTemplate;
import com.virditech.ac7000.recognition.FaceTemplateRepository;
import com.virditech.ac7000.recognition.RecognitionModelConfig;
import com.virditech.ac7000.recognition.RecognitionEnrollmentSession;
import com.virditech.ac7000.recognition.RecognitionPolicy;
import com.virditech.ac7000.recognition.RecognitionResult;
import com.virditech.ac7000.recognition.RecognitionWorkCoordinator;
import com.virditech.ac7000.ui.MainScreenView;

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

public final class MainActivity extends Activity {
    private static final String TAG = "MainActivity";
    private static final int CAMERA_PERMISSION_REQUEST = 10;
    private static final int FACE_MANAGEMENT_REQUEST = 11;
    private static final long MAX_PAIR_DELTA_NS = 150_000_000L;
    private static final int COLLECTION_TARGET_COUNT = CaptureSchedule.TARGET_COUNT;
    private static final int IR_RESULT_COLOR = Color.rgb(64, 196, 255);
    private static final int COLLECTION_MEDIUM_QUALITY_LEVEL = 1;
    private static final int LATENCY_WINDOW_SIZE = 120;
    private static final long MOTION_DIAGNOSTIC_LOG_INTERVAL_MS = 250L;
    private static final long IR_LED_OFF_DELAY_MS = 1_000L;
    private static final long CAMERA_CLOSE_WARNING_MS = 1_500L;
    private static final String[] VISION_LABELS = ClassLabels.values();

    private final ExecutorService trackingExecutor = Executors.newSingleThreadExecutor();
    private final ExecutorService inferenceExecutor = Executors.newSingleThreadExecutor();
    private final ExecutorService recognitionExecutor = Executors.newSingleThreadExecutor();
    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();
    private final ExecutorService attackCaptureExecutor = Executors.newSingleThreadExecutor();
    private final ExecutorService modelInitExecutor = Executors.newSingleThreadExecutor();
    private final LatestWinsExecutor<TrackingFrame> trackingQueue = new LatestWinsExecutor<>(
            trackingExecutor, this::processTracking, TrackingFrame::recycle,
            (frame, error) -> {
                android.util.Log.e(TAG, "Tracking failed", error);
                if (isPipelineCurrent(frame.generation)) showTransientStatus("Tracking failed");
            }, this::closeFaceDetectorsIfShutDown);
    private final LatestWinsExecutor<InferenceTask> inferenceQueue = new LatestWinsExecutor<>(
            inferenceExecutor, this::processInferenceIfAllowed,
            InferenceTask::recycle, (task, error) -> {
                android.util.Log.e(TAG, "Inference failed", error);
                if (isPipelineCurrent(task.generation)) showTransientStatus("Inference failed");
            }, this::closeAntiSpoofingEnginesIfShutDown);
    private final RecognitionWorkCoordinator recognitionCoordinator = new RecognitionWorkCoordinator();
    private final AtomicBoolean recognitionFaceVisible = new AtomicBoolean();
    private final AtomicLong inferenceMotionGeneration = new AtomicLong();
    private final FaceMotionGate inferenceMotionGate = new FaceMotionGate();
    private final AtomicBoolean calibrationRequested = new AtomicBoolean();
    private final GenerationGuard pipelineGeneration = new GenerationGuard();
    private final Object attackCaptureLock = new Object();
    private final FrameSynchronizer<FrameData> frameSynchronizer = new FrameSynchronizer<>(
            MAX_PAIR_DELTA_NS, frame -> frame.timestampNs, FrameData::recycle);
    private final Object irPreviewLock = new Object();
    private Bitmap latestIrBitmapForCrop;
    private final Canvas irPreviewCanvas = new Canvas();
    private MainScreenView screen;
    private final Object engineLock = new Object();
    private final StringBuilder engineErrors = new StringBuilder();
    private final AtomicInteger pendingEngineLoads = new AtomicInteger(2);
    private final ArrayList<AntiSpoofingEngine> antiSpoofingEngines = new ArrayList<>();
    private final ArrayList<EngineInfo> antiSpoofingEngineInfos = new ArrayList<>();
    private int activeEngineIndex;
    private volatile boolean enginesShutDown;
    // NNAPI compilation of the NPU model monopolizes the VSI NPU driver, which FaceMe
    // detection also uses, so tracking stalls until every warmup finishes. Keep the
    // loading spinner up until then instead of pretending the camera is usable.
    private volatile boolean enginesWarmedUp;
    private volatile boolean qualityWarmedUp;
    private volatile boolean bundledQualityWarmupAttempted;
    private boolean highQualityOnly;
    private DualCameraController cameras;
    private DualCameraController stoppingCameras;
    private boolean cameraStopInProgress;
    private volatile FaceDetector faceDetector;
    private volatile MediaPipeFaceDetector mediaPipeFaceDetector;
    private volatile FaceDetectionEngine activeFaceDetector;
    private volatile AntiSpoofingEngine antiSpoofingEngine;
    private volatile EngineInfo antiSpoofingEngineInfo;
    private volatile Calibration calibration;
    private final IrLedController irLedController = HardwareControls::setIrLed;
    private final AppWatchdog appWatchdog = AppWatchdog.getInstance();
    private final CaptureSession collectionSession = new CaptureSession();
    private volatile boolean isAttackLiveCapturing;
    private volatile boolean attackCaptureSaveBusy;
    private volatile FaceDetector.FaceQualityCheckResult lastCollectionQuality;
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
    private boolean irCenterAutoExposure = false;
    private static final int AUTH_FRAME_COUNT = 5;
    private static final float AUTH_LIVE_THRESHOLD = 0.85f;
    private volatile FaceRecognitionManager faceRecognitionManager;
    private volatile boolean faceRecognitionMode;
    private final RecognitionEnrollmentSession enrollmentSession =
            new RecognitionEnrollmentSession();
    private static final int ENROLL_TARGET_FRAME_COUNT = 5;
    private FaceTemplateRepository faceTemplateRepository;
    private volatile String recogModelChecksum;
    private FaceEmbeddingModel.DelegateType recogDelegate = FaceEmbeddingModel.DEFAULT_DELEGATE;
    private String recogModelPath = FaceEmbeddingModel.DEFAULT_MODEL_PATH;
    private volatile boolean authMode;
    private volatile boolean motionGateEnabled = false;
    private volatile boolean lightingTestEnabled = false;
    private volatile boolean foregroundEntryTestEnabled = false;
    private final AtomicBoolean lightingSnapshotRequested = new AtomicBoolean(false);
    private final ForegroundEntryDetector foregroundEntryDetector = new ForegroundEntryDetector();
    private volatile boolean authVerdictShowing;
    private volatile boolean testMenuShowing;
    private final AuthFrameAccumulator authFrames =
            new AuthFrameAccumulator(AUTH_FRAME_COUNT, AUTH_LIVE_THRESHOLD);
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
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED ||
            checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE}, CAMERA_PERMISSION_REQUEST);
        }
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
                screen.calibrationInstruction.setText("Hold still while RGB and IR faces are measured...");
            }

            @Override public void onCalibrationCancel() { exitCalibrationMode(); }

            @Override public void onRecognitionEnrollmentStart() { startRecognitionEnrollment(); }

            @Override public void onCalibrationTap() { recordCalibrationTap(); }

            @Override public void onSettingsTap() { recordSettingsTap(); }

            @Override public void onLightingSnapshotRequested() { recordLightingSnapshot(); }
        });
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
        if (++settingsTapCount >= 3) {
            settingsTapCount = 0;
            showHiddenTestMenu();
        }
    }

    private void showHiddenTestMenu() {
        testMenuShowing = true;
        inferenceQueue.clear();
        invalidateRecognitionWork();
        authFrames.reset();
        String[] items = testMenuItems();
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_list_item_1, items);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("MENU")
                .setAdapter(adapter, null)
                .setNegativeButton("CLOSE", null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getListView().setOnItemClickListener(
                (parent, view, which, id) -> {
                    TestMenuAction[] actions = TestMenuAction.values();
                    if (which < 0 || which >= actions.length) return;
                    handleTestMenuAction(actions[which], dialog, items, adapter);
                }));
        dialog.setOnDismissListener(d -> {
            testMenuShowing = false;
            authFrames.reset();
        });
        dialog.show();
    }

    private String[] testMenuItems() {
        TestMenuAction[] actions = TestMenuAction.values();
        String[] items = new String[actions.length];
        for (int i = 0; i < actions.length; i++) {
            items[i] = testMenuLabel(actions[i]);
        }
        return items;
    }

    private String testMenuLabel(TestMenuAction action) {
        switch (action) {
            case SETTINGS:
                return "SETTINGS";
            case WEBRTC:
                return "WEBRTC";
            case AUTH_MODE:
                return "AUTH MODE (" + (authMode ? "ON" : "OFF") + ")";
            case MOTION_GATE:
                return "MOTION GATE (" + (motionGateEnabled ? "ON" : "OFF") + ")";
            case LIGHTING_TEST:
                return "LIGHTING TEST (" + (lightingTestEnabled ? "ON" : "OFF") + ")";
            case ENTRY_DETECTOR:
                return "ENTRY DETECTOR (" + (foregroundEntryTestEnabled ? "ON" : "OFF") + ")";
            case DETECTOR:
                return "DETECTOR: " + (activeFaceDetector != null ? activeFaceDetector.label() : "UNAVAILABLE");
            case FACE_MANAGEMENT:
                return "FACE MANAGEMENT";
            default:
                return action.name();
        }
    }

    private void handleTestMenuAction(TestMenuAction action, AlertDialog dialog,
                                      String[] items, ArrayAdapter<String> adapter) {
        switch (action) {
            case SETTINGS:
                dialog.dismiss();
                startActivity(new Intent(Settings.ACTION_SETTINGS));
                return;
            case WEBRTC:
                dialog.dismiss();
                startActivity(new Intent(this, WebRtcCallActivity.class));
                return;
            case AUTH_MODE:
                toggleAuthMode();
                break;
            case MOTION_GATE:
                toggleMotionGate();
                break;
            case LIGHTING_TEST:
                toggleLightingTest();
                break;
            case ENTRY_DETECTOR:
                toggleForegroundEntryTest();
                break;
            case DETECTOR:
                toggleFaceDetector();
                break;
            case FACE_MANAGEMENT:
                dialog.dismiss();
                openFaceManagement();
                return;
        }
        if (action.refreshOnSelect()) {
            refreshTestMenuItems(items, adapter);
        }
    }

    private void refreshTestMenuItems(String[] items, ArrayAdapter<String> adapter) {
        String[] currentItems = testMenuItems();
        System.arraycopy(currentItems, 0, items, 0, items.length);
        adapter.notifyDataSetChanged();
    }

    private void toggleMotionGate() {
        motionGateEnabled = !motionGateEnabled;
        inferenceMotionGate.reset();
        inferenceBlockedByMotion = false;
        inferenceMotionGeneration.incrementAndGet();
        screen.overlay.clearClassificationResult();
        if (screen != null) screen.clearCleanModeResult();
        resetResultsLabelToZero();
        showTransientStatus("Motion Gate " + (motionGateEnabled ? "ON" : "OFF"));
    }

    private void toggleLightingTest() {
        lightingTestEnabled = !lightingTestEnabled;
        if (!lightingTestEnabled) lightingSnapshotRequested.set(false);
        if (!lightingTestEnabled && screen != null) {
            screen.clearCleanModeLighting();
            screen.overlay.setObservationGuides(false, foregroundEntryTestEnabled && !screen.isUiVisible());
        }
        showTransientStatus("Lighting Test " + (lightingTestEnabled ? "ON" : "OFF"));
    }

    private void toggleForegroundEntryTest() {
        foregroundEntryTestEnabled = !foregroundEntryTestEnabled;
        foregroundEntryDetector.reset();
        if (!foregroundEntryTestEnabled && screen != null) {
            screen.clearCleanModeForegroundEntry();
            screen.overlay.setObservationGuides(lightingTestEnabled && !screen.isUiVisible(), false);
        }
        showTransientStatus("Entry Detector " + (foregroundEntryTestEnabled ? "ON" : "OFF"));
    }

    private void recordLightingSnapshot() {
        if (captureTone != null) {
            try {
                captureTone.startTone(ToneGenerator.TONE_PROP_BEEP, 60);
            } catch (Exception ignored) {}
        }
        lightingSnapshotRequested.set(true);
        showTransientStatus("Capturing lighting snapshot & photos...");
    }

    private void saveLightingSnapshot(TrackingFrame frame, DualLightingDetector.Result result) {
        if (frame == null || frame.rgb == null || frame.rgb.bitmap == null || frame.rgb.bitmap.isRecycled()) {
            return;
        }
        Bitmap rgbCopy = Bitmap.createBitmap(frame.rgb.bitmap);
        Bitmap irCopy = null;
        if (frame.ir != null && frame.ir.bitmap != null && !frame.ir.bitmap.isRecycled()) {
            irCopy = Bitmap.createBitmap(frame.ir.bitmap);
        } else {
            synchronized (irPreviewLock) {
                if (latestIrBitmapForCrop != null && !latestIrBitmapForCrop.isRecycled()) {
                    irCopy = Bitmap.createBitmap(latestIrBitmapForCrop);
                }
            }
        }
        DualLightingDetector.Result effectiveResult = result != null ? result
                : DualLightingDetector.evaluate(frame.rgb.bitmap, irCopy, null);

        LightingExperimentLogger.recordSnapshot(effectiveResult, rgbCopy, irCopy, "EXP",
                new LightingExperimentLogger.LogCallback() {
                    @Override
                    public void onLogged(int sampleId, String rgbFileName, String message) {
                        runOnUiThread(() -> showTransientStatus("Saved " + rgbFileName + " (" + message + ")"));
                    }

                    @Override
                    public void onError(String error) {
                        runOnUiThread(() -> showTransientStatus("Log Error: " + error));
                    }
                });
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
        intent.putExtra(FaceRecognitionActivity.EXTRA_RECOGNITION_ENABLED, faceRecognitionMode);
        intent.putExtra(FaceRecognitionActivity.EXTRA_DELEGATE_TYPE, recogDelegate.name());
        startActivityForResult(intent, FACE_MANAGEMENT_REQUEST);
    }

    private void reloadRecognitionModel(String requestedModelPath,
                                        FaceEmbeddingModel.DelegateType requestedDelegate) {
        reloadRecognitionModel(requestedModelPath, requestedDelegate, null);
    }

    private void reloadRecognitionModel(String requestedModelPath,
                                        FaceEmbeddingModel.DelegateType requestedDelegate,
                                        Runnable onReloaded) {
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
                    if (onReloaded != null) {
                        onReloaded.run();
                    } else {
                        loadPersistedTemplates(manager, requestedModelPath, checksum);
                    }
                }
                showTransientStatus(formatRecognitionReloadStatus(reloaded, manager,
                        requestedModelPath, requestedDelegate));
            });
        });
    }

    private final Handler authHandler = new Handler(Looper.getMainLooper());
    private final Runnable hideAuthResultRunnable = () -> {
        authVerdictShowing = false;
        authFrames.reset();
        screen.hideAuthResult();
    };

    private void toggleAuthMode() {
        if (collectionSession.isActive() || isAttackLiveCapturing || calibrationMode) {
            showTransientStatus("Cannot toggle Auth Mode during capture/calibration");
            return;
        }
        authMode = !authMode;
        authHandler.removeCallbacks(hideAuthResultRunnable);
        authVerdictShowing = false;
        authFrames.reset();
        screen.setAuthMode(authMode);
        if (!authMode) {
            resetResultsLabelToZero();
        }
        showTransientStatus(authMode ? "AUTH MODE: ON" : "AUTH MODE: OFF");
    }

    private void showAuthVerdict(boolean isLive, float avgLiveScore, String topSpoofLabel, long elapsedMs) {
        authVerdictShowing = true;
        inferenceQueue.clear();
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
        suspendEvaluationForExclusiveMode();
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

    private void suspendEvaluationForExclusiveMode() {
        authHandler.removeCallbacks(hideAuthResultRunnable);
        authVerdictShowing = false;
        authFrames.reset();
        inferenceMotionGeneration.incrementAndGet();
        inferenceQueue.clear();
        invalidateRecognitionWork();
        screen.overlay.clearClassificationResult();
        screen.clearCleanModeResult();
        resetResultsLabelToZero();
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
                synchronized (engineLock) {
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
                    android.util.Log.i(TAG, "Face quality initialized; waiting for a valid face to warm up");
                }
            } catch (Exception e) {
                String message = e.getMessage();
                reportEngineError(message == null ? "Face detector unavailable" : message);
            }
            try {
                MediaPipeFaceDetector detector = new MediaPipeFaceDetector(getApplicationContext());
                synchronized (engineLock) {
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
        modelInitExecutor.execute(this::loadAntiSpoofingEngines);
    }

    private void loadAntiSpoofingEngines() {
        EngineLoadResult result = null;
        try {
            result = VisionSdk.loadAll(
                    getApplicationContext(), irLedController, AntiSpoofingOptions.defaults());
        } catch (Exception e) {
            reportEngineError("MODEL LOAD FAILED: " + e.getMessage());
        }
        try {
            List<RecognitionModelConfig> recogConfigs = RecognitionModelConfig.loadAll(getApplicationContext());
            if (!recogConfigs.isEmpty()) {
                RecognitionModelConfig defaultRecog = recogConfigs.get(0);
                recogModelPath = defaultRecog.getModelPath();
                recogDelegate = defaultRecog.getDelegateType();
            }
            FaceRecognitionManager recManager = new FaceRecognitionManager(getApplicationContext(), recogModelPath, recogDelegate);
            if (recManager.isReady()) {
                String modelChecksum = modelChecksum(recManager.getModelAssetPath());
                if (modelChecksum == null) throw new IllegalStateException("Model checksum unavailable");
                synchronized (engineLock) {
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
        List<AntiSpoofingEngine> loaded = result != null ? result.engines() : new ArrayList<>();
        List<EngineInfo> loadedInfos = result != null
                ? result.engineInfos() : new ArrayList<>();
        if (result != null) {
            for (String error : result.errors()) reportEngineError(error);
        }
        synchronized (engineLock) {
            if (enginesShutDown) {
                for (AntiSpoofingEngine slot : loaded) {
                    try { slot.close(); } catch (Exception ignored) {}
                }
                return;
            }
            antiSpoofingEngines.clear();
            antiSpoofingEngines.addAll(loaded);
            antiSpoofingEngineInfos.clear();
            antiSpoofingEngineInfos.addAll(loadedInfos);
            activeEngineIndex = 0;
            antiSpoofingEngine = antiSpoofingEngines.isEmpty() ? null : antiSpoofingEngines.get(0);
            antiSpoofingEngineInfo = antiSpoofingEngineInfos.isEmpty()
                    ? null : antiSpoofingEngineInfos.get(0);
        }
        runOnUiThread(() -> {
            screen.modelSwitchButton.setEnabled(antiSpoofingEngines.size() > 1);
            EngineInfo activeInfo = antiSpoofingEngineInfo;
            if (activeInfo != null) screen.modelSwitchButton.setText(activeInfo.label());
            if (antiSpoofingEngine != null && cameras != null) cameras.setIrFramesEnabled(true);
        });
        if (antiSpoofingEngine == null) reportEngineError("No model slots loaded");
        updateEngineStatus();
        onEngineLoadFinished();
    }

    private void onEngineLoadFinished() {
        if (pendingEngineLoads.decrementAndGet() != 0) return;
        if (enginesShutDown) return;
        enginesWarmedUp = true;
        runOnUiThread(() -> {
            if (!resumed) return;
            updatePerformanceHud();
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
        EngineInfo activeInfo = antiSpoofingEngineInfo;
        String message = errors.isEmpty()
                ? (activeInfo != null ? activeInfo.backendStatus() : "Loading model...")
                : errors;
        normalStatusMessage = message;
        runOnUiThread(() -> screen.status.setText(message));
    }

    private void startCameras() {
        if (!resumed || cameras != null || cameraStopInProgress
                || checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) return;
        final int generation = pipelineGeneration.advance();
        HardwareControls.setLcdBrightness(90);
        HardwareControls.setIrLed(false);
        applyIrAutoExposure();
        cameras = new DualCameraController(this, screen.rgbView, screen.irView,
                new DualCameraController.Listener() {
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
        authFrames.reset();
        if (screen != null) screen.hideAuthResult();
        pipelineGeneration.advance();
        stopCameras();
        HardwareControls.setIrLed(false);
        lastFaceDetectedMs = 0L;
        clearPendingWork();
        screen.overlay.clearResult();
        if (screen != null) screen.clearCleanModeResult();
        synchronized (irPreviewLock) {
            releaseIrPreviewBufferLocked();
        }
        super.onPause();
    }

    private void stopCameras() {
        DualCameraController controller = cameras;
        cameras = null;
        if (controller == null) return;
        cameraStopInProgress = true;
        stoppingCameras = controller;
        screen.root.postDelayed(() -> {
            if (stoppingCameras == controller && cameraStopInProgress
                    && resumed && !enginesShutDown) {
                showTransientStatus("Camera shutdown delayed; waiting for safe close");
            }
        }, CAMERA_CLOSE_WARNING_MS);
        controller.stop(() -> runOnUiThread(() -> {
            if (stoppingCameras != controller) return;
            stoppingCameras = null;
            cameraStopInProgress = false;
            if (resumed && !enginesShutDown) startCameras();
        }));
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
        frameSynchronizer.offerSecondary(frame);
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
        FrameData ir = frameSynchronizer.takeFor(rgb.timestampNs);
        trackingQueue.offer(new TrackingFrame(rgb, ir, generation,
                SystemClock.elapsedRealtimeNanos()));
    }

    private void processTracking(TrackingFrame frame) {
        if (!isPipelineCurrent(frame.generation)) return;
        FaceDetectionEngine activeDetector = activeFaceDetector;
        if (activeDetector == null || calibration == null) return;
        if (enginesWarmedUp && !qualityWarmedUp && !bundledQualityWarmupAttempted
                && activeDetector == faceDetector && faceDetector.isQualityAvailable()) {
            bundledQualityWarmupAttempted = true;
            warmupFaceQualityFromBundledImage(frame.generation);
            return;
        }
        boolean captureCalibration = calibrationMode && calibrationRequested.getAndSet(false);
        boolean prepareCollectionQuality = !captureCalibration
                && collectionSession.isActive() && !collectionSession.isPaused()
                && frame.ir != null && !collectionSession.isIoBusy()
                && activeDetector == faceDetector
                && shouldCheckCollectionQuality(collectionSession.getClassName())
                && getCollectionCountdownSeconds(SystemClock.elapsedRealtime()) <= 0;
        boolean prepareQualityWarmup = enginesWarmedUp && !captureCalibration && !qualityWarmedUp
                && activeDetector == faceDetector && faceDetector.isQualityAvailable();
        long qualityWarmupStartNs = prepareQualityWarmup
                ? SystemClock.elapsedRealtimeNanos() : 0L;
        long start = SystemClock.elapsedRealtimeNanos();
        Rect detected = captureCalibration
                ? activeDetector.detectSingle(frame.rgb.bitmap)
                : prepareCollectionQuality || prepareQualityWarmup
                        ? faceDetector.detectLargestWithQualityData(frame.rgb.bitmap)
                        : activeDetector.detectLargest(frame.rgb.bitmap);
        PointF[] currentLandmarks = activeDetector.getLastDetectedLandmarks();
        if (!isPipelineCurrent(frame.generation)) return;
        detectionMs = (SystemClock.elapsedRealtimeNanos() - start) / 1_000_000L;
        updateTrackingFps();
        ForegroundEntryDetector.Result foregroundEntryResult = foregroundEntryTestEnabled
                ? foregroundEntryDetector.evaluate(frame.rgb.bitmap) : null;
        if (detected == null) {
            long now = SystemClock.elapsedRealtime();
            if (now - lastFaceDetectedMs >= IR_LED_OFF_DELAY_MS) {
                HardwareControls.setIrLed(false);
            }
            inferenceMotionGate.reset();
            if (recognitionFaceVisible.getAndSet(false)) invalidateRecognitionWork();
            authFrames.reset();

            DualLightingDetector.Result noFaceLighting = null;
            if (lightingTestEnabled) {
                if (frame.ir != null) {
                    noFaceLighting = DualLightingDetector.evaluate(
                            frame.rgb.bitmap, frame.ir.bitmap, null);
                } else {
                    synchronized (irPreviewLock) {
                        Bitmap fallback = (latestIrBitmapForCrop != null && !latestIrBitmapForCrop.isRecycled())
                                ? latestIrBitmapForCrop : null;
                        noFaceLighting = DualLightingDetector.evaluate(
                                frame.rgb.bitmap, fallback, null);
                    }
                }
            }
            final DualLightingDetector.Result finalNoFaceLighting = noFaceLighting;
            if (lightingTestEnabled && lightingSnapshotRequested.compareAndSet(true, false)) {
                saveLightingSnapshot(frame, finalNoFaceLighting);
            }

            runOnUiThread(() -> {
                if (!isPipelineCurrent(frame.generation)) return;
                screen.overlay.clearResult();
                screen.overlay.clearRecognitionResult();
                boolean showObservationGuides = screen != null && !screen.isUiVisible();
                screen.overlay.setObservationGuides(lightingTestEnabled && showObservationGuides,
                        foregroundEntryTestEnabled && showObservationGuides);
                recognitionInferenceMs = -1L;
                if (screen != null) {
                    screen.clearCleanModeResult();
                    screen.showCleanModeLighting(finalNoFaceLighting, lightingTestEnabled);
                    screen.showCleanModeForegroundEntry(foregroundEntryResult, foregroundEntryTestEnabled);
                }
                clearPreviewFace();
                screen.faceCropView.setScaleX(1f);
                screen.noFaceLabel.setVisibility(View.VISIBLE);
                if (collectionSession.isActive()) {
                    updateCollectionUi(SystemClock.elapsedRealtime());
                }
                if (captureCalibration) {
                    screen.calibrationInstruction.setText("Exactly one RGB face is required. Try again.");
                } else {
                    long nowUi = SystemClock.elapsedRealtime();
                    if (nowUi - lastUiUpdateTimeMs >= 150L) {
                        updatePerformanceHud();
                        lastUiUpdateTimeMs = nowUi;
                    }
                    if (SystemClock.elapsedRealtime() - lastFaceDetectedMs > 10_000L) {
                        resetResultsLabelToZero();
                    }
                }
            });
            return;
        } else {
            lastFaceDetectedMs = SystemClock.elapsedRealtime();
            HardwareControls.setIrLed(true);
        }
        if (prepareQualityWarmup) {
            FaceDetector.FaceQualityCheckResult warmup =
                    faceDetector.checkFaceQuality(frame.rgb.bitmap, 0);
            if (warmup.passed) {
                qualityWarmedUp = true;
                long warmupMs = (SystemClock.elapsedRealtimeNanos() - qualityWarmupStartNs)
                        / 1_000_000L;
                android.util.Log.i(TAG, "Face quality warmup completed in " + warmupMs + " ms");
                runOnUiThread(() -> {
                    if (isPipelineCurrent(frame.generation)) updatePerformanceHud();
                });
            } else {
                android.util.Log.w(TAG, "Face quality warmup failed: " + warmup.reason);
            }
            return;
        }
        int irWidth = frame.ir == null ? frame.rgb.bitmap.getWidth() : frame.ir.bitmap.getWidth();
        int irHeight = frame.ir == null ? frame.rgb.bitmap.getHeight() : frame.ir.bitmap.getHeight();
        Rect irDetected = calibration.rgbToIr(detected, irWidth, irHeight);

        if (captureCalibration) {
            if (frame.ir == null) {
                calibrationRequested.set(true);
                runOnUiThread(() -> {
                    if (isPipelineCurrent(frame.generation)) {
                        screen.calibrationInstruction.setText("Waiting for a synchronized IR frame. Hold still...");
                    }
                });
                return;
            }
            Rect detectedIr = activeDetector.detectSingle(frame.ir.bitmap);
            if (detectedIr == null) {
                runOnUiThread(() -> {
                    if (isPipelineCurrent(frame.generation)) {
                        screen.calibrationInstruction.setText("Exactly one IR face is required. Try again.");
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
                    screen.status.setText("Calibration saved");
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (isPipelineCurrent(frame.generation)) {
                        screen.calibrationInstruction.setText("Unable to save calibration: " + e.getMessage());
                    }
                });
            }
            return;
        }

        Rect rgbCrop = null;
        Rect irCrop = null;
        AntiSpoofingEngine activeEngine;
        EngineInfo activeEngineInfo;
        synchronized (engineLock) {
            activeEngine = antiSpoofingEngine;
            activeEngineInfo = antiSpoofingEngineInfo;
        }
        if (activeEngine != null) {
            float margin = activeEngineInfo.cropMarginRatio();
            rgbCrop = FaceCrop.expand(
                    detected, margin, frame.rgb.bitmap.getWidth(), frame.rgb.bitmap.getHeight());
            irCrop = FaceCrop.expand(irDetected, margin, irWidth, irHeight);
        }

        Bitmap previewFace = null;
        boolean previewRgb = showIr;
        long previewNow = SystemClock.elapsedRealtime();
        if (previewNow - lastPreviewUpdateTimeMs >= 66L && activeEngine != null) {
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

        DualLightingDetector.Result lightingResult = null;
        if (lightingTestEnabled) {
            if (frame.ir != null) {
                lightingResult = DualLightingDetector.evaluate(
                        frame.rgb.bitmap, frame.ir.bitmap, detected);
            } else {
                synchronized (irPreviewLock) {
                    Bitmap fallback = (latestIrBitmapForCrop != null && !latestIrBitmapForCrop.isRecycled())
                            ? latestIrBitmapForCrop : null;
                    lightingResult = DualLightingDetector.evaluate(
                            frame.rgb.bitmap, fallback, detected);
                }
            }
        }
        final DualLightingDetector.Result finalLightingResult = lightingResult;
        if (lightingTestEnabled && lightingSnapshotRequested.compareAndSet(true, false)) {
            saveLightingSnapshot(frame, finalLightingResult);
        }

        runOnUiThread(() -> {
            if (!isPipelineCurrent(frame.generation)) {
                if (finalPreviewFace != null) finalPreviewFace.recycle();
                return;
            }
            screen.overlay.showFace(detected, irDetected);
            screen.overlay.setObservationGuides(false,
                    foregroundEntryTestEnabled && screen != null && !screen.isUiVisible());
            if (screen != null) {
                screen.showCleanModeLighting(finalLightingResult, lightingTestEnabled);
                screen.showCleanModeForegroundEntry(foregroundEntryResult, foregroundEntryTestEnabled);
            }
            if (collectionSession.isActive()) {
                updateCollectionUi(SystemClock.elapsedRealtime());
            }
            long now = SystemClock.elapsedRealtime();
            if (now - lastUiUpdateTimeMs >= 150L) {
                updatePerformanceHud();
                lastUiUpdateTimeMs = now;
            }
            screen.noFaceLabel.setVisibility(View.GONE);
            if (finalPreviewFace != null) {
                setPreviewFace(finalPreviewFace, finalPreviewRgb);
            }
        });
        if (calibrationMode) return;

        CaptureSession.SaveCandidate saveCandidate = frame.ir != null
                ? collectionSession.snapshotForSave() : null;
        if (saveCandidate != null) {
            final String className = saveCandidate.className;
            long nowMs = SystemClock.elapsedRealtime();
            if (getCollectionCountdownSeconds(nowMs) > 0) return;
            FaceDetector.FaceQualityCheckResult sampleQuality = null;
            if (shouldCheckCollectionQuality(className)) {
                if (!prepareCollectionQuality) return;
                FaceDetector.FaceQualityCheckResult quality =
                        faceDetector.checkFaceQuality(frame.rgb.bitmap,
                                saveCandidate.minQualityLevel);
                lastCollectionQuality = quality;
                if (!quality.passed) {
                    android.util.Log.i(TAG, "Collection quality skipped: " + quality.reason);
                    runOnUiThread(() -> {
                        if (isPipelineCurrent(frame.generation) && collectionSession.isActive()) {
                            updateCollectionUi(SystemClock.elapsedRealtime());
                        }
                    });
                    return;
                }
                sampleQuality = quality;
            }
            if (!isPipelineCurrent(frame.generation)) return;
            CaptureSession.SavePermit savePermit = collectionSession.beginSave(saveCandidate);
            if (savePermit != null) {
                final int currentCount = savePermit.sampleIndex;
                final String subjectDirName = savePermit.className + "_" + savePermit.subjectId;
                float margin = activeEngineInfo != null
                        ? activeEngineInfo.cropMarginRatio() : 0.10f;
                Rect rgbR = FaceCrop.expand(detected, margin,
                        frame.rgb.bitmap.getWidth(), frame.rgb.bitmap.getHeight());
                Rect irR = FaceCrop.expand(irDetected, margin,
                        frame.ir.bitmap.getWidth(), frame.ir.bitmap.getHeight());
                final int minQualityLevel = shouldCheckCollectionQuality(savePermit.className)
                        ? saveCandidate.minQualityLevel : -1;
                final int actualQualityLevel = sampleQuality != null ? sampleQuality.actualLevel : -1;
                final float qualityScore = sampleQuality != null ? sampleQuality.score : 0f;
                final String metadataJson = CaptureStorage.buildSampleMetadataJson(
                        frame.rgb.bitmap.getWidth(), frame.rgb.bitmap.getHeight(), detected, rgbR,
                        frame.ir.bitmap.getWidth(), frame.ir.bitmap.getHeight(), irDetected, irR, margin,
                        savePermit.qualityMode, minQualityLevel, actualQualityLevel, qualityScore);

                final File root = saveCandidate.rawRoot;
                final File sampleDir = CaptureStorage.sampleDir(root, savePermit.className,
                        savePermit.qualityMode,
                        subjectDirName, currentCount);
                final String displayDir = sampleDir.getAbsolutePath();

                if (!collectionSession.isActive(savePermit)) {
                    collectionSession.releaseSave(savePermit);
                    return;
                }

                final FramePair capturePair = frame.detachPair();
                OwnedFrameTask saveTask = new OwnedFrameTask(capturePair, () -> {
                    boolean saved = false;
                    boolean sectorCompleted = false;
                    boolean collectionCompleted = false;
                    long saveStartNs = 0L;
                    try {
                        if (!isPipelineCurrent(frame.generation)
                                || !collectionSession.isActive(savePermit)) {
                            return;
                        }
                        saveStartNs = SystemClock.elapsedRealtimeNanos();
                        CaptureStorage.SaveResult writeResult = CaptureStorage.saveCompleteSample(
                                capturePair.rgb.bitmap, rgbR, capturePair.ir.bitmap, irR,
                                metadataJson, sampleDir);
                        if (!writeResult.saved) {
                            showTransientStatus("Save failed: " + writeResult.errorMessage);
                        }
                        if (writeResult.saved && isPipelineCurrent(frame.generation)
                                && collectionSession.isActive(savePermit)) {
                            CaptureSession.SaveCommit commit = collectionSession.commitSave(
                                    savePermit, SystemClock.elapsedRealtime());
                            saved = commit.committed;
                            sectorCompleted = commit.sectorCompleted;
                            collectionCompleted = commit.collectionCompleted;
                            if (saved) android.util.Log.i(TAG, "Saved collection sample: " + displayDir);
                        }
                    } finally {
                        if (saveStartNs != 0L) {
                            recordCaptureSaveLatency((SystemClock.elapsedRealtimeNanos() - saveStartNs)
                                    / 1_000_000L);
                        }
                        collectionSession.releaseSave(savePermit);
                    }
                    final boolean savedSample = saved;
                    final boolean completedSector = sectorCompleted;
                    final boolean completedCollection = collectionCompleted;
                    runOnUiThread(() -> {
                        if (!isPipelineCurrent(frame.generation) || !collectionSession.isActive()) return;
                        updateCollectionUi(SystemClock.elapsedRealtime());
                        if (savedSample && completedSector) {
                            playCollectionFinishedTone();
                        } else if (savedSample) {
                            playCaptureSavedTone();
                        }
                        if (savedSample && completedCollection) {
                            finishDataCollection();
                        }
                    });
                });
                try {
                    ioExecutor.execute(saveTask);
                } catch (RejectedExecutionException e) {
                    saveTask.discard();
                    collectionSession.releaseSave(savePermit);
                    android.util.Log.w(TAG, "Capture save rejected during shutdown", e);
                }
            }
        }

        if (!calibrationMode && !collectionSession.isActive() && !testMenuShowing
                && (!enrollmentSession.isUiActive() || enrollmentSession.isRequested())) {
            scheduleRecognition(frame, detected, currentLandmarks);
        }

        if (calibrationMode || enrollmentSession.isUiActive()
                || collectionSession.isActive() || authVerdictShowing || testMenuShowing
                || rgbCrop == null || irCrop == null || frame.ir == null) return;
        if (!authMode && motionGateEnabled) {
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
                frame.generation, activeEngine, activeEngineInfo.cropMarginRatio(),
                frame.receivedNs, inferenceMotionGeneration.get()));
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
        authFrames.reset();
        long motionGeneration = inferenceMotionGeneration.incrementAndGet();
        inferenceQueue.clear();
        runOnUiThread(() -> {
            if (!isPipelineCurrent(generation) || motionGeneration != inferenceMotionGeneration.get()) return;
            screen.overlay.clearClassificationResult();
            if (screen != null) screen.clearCleanModeResult();
            resetResultsLabelToZero();
        });
    }

    private void submitInference(InferenceTask task) {
        inferenceQueue.offer(task);
    }

    private void warmupFaceQualityFromBundledImage(int generation) {
        FaceDetector detector = faceDetector;
        if (detector == null || enginesShutDown || qualityWarmedUp) return;
        Bitmap sample = null;
        long startNs = SystemClock.elapsedRealtimeNanos();
        try {
            sample = BitmapFactory.decodeResource(getResources(), R.drawable.test_image);
            if (sample == null || detector.detectLargestWithQualityData(sample) == null) {
                android.util.Log.w(TAG, "Bundled face quality warmup image was not detected");
                return;
            }
            FaceDetector.FaceQualityCheckResult warmup = detector.checkFaceQuality(sample, 0);
            if (!warmup.passed) {
                android.util.Log.w(TAG, "Bundled face quality warmup failed: " + warmup.reason);
                return;
            }
            qualityWarmedUp = true;
            long warmupMs = (SystemClock.elapsedRealtimeNanos() - startNs) / 1_000_000L;
            android.util.Log.i(TAG, "Face quality warmup completed from bundled image in "
                    + warmupMs + " ms");
            runOnUiThread(() -> {
                if (isPipelineCurrent(generation)) updatePerformanceHud();
            });
        } catch (Exception e) {
            android.util.Log.w(TAG, "Bundled face quality warmup failed", e);
        } finally {
            if (sample != null && !sample.isRecycled()) sample.recycle();
        }
    }

    private void processInference(InferenceTask task) {
        if (isExclusiveEvaluationMode() || authVerdictShowing || testMenuShowing
                || !isPipelineCurrent(task.generation) || task.engine == null) return;
        long startNs = SystemClock.elapsedRealtimeNanos();
        long queueMs = (startNs - task.enqueuedNs) / 1_000_000L;
        InferenceResult result = task.engine.infer(new AntiSpoofingFrame(
                task.pair.rgb.bitmap, task.rgbFace, task.pair.rgb.timestampNs,
                task.pair.ir.bitmap, task.irFace, task.pair.ir.timestampNs));
        if (!result.successful()) throw new IllegalStateException(result.errorMessage());
        long endToEndMs = (SystemClock.elapsedRealtimeNanos() - task.receivedNs) / 1_000_000L;
        if (isExclusiveEvaluationMode() || authVerdictShowing || testMenuShowing
                || !isPipelineCurrent(task.generation)
                || task.motionGeneration != inferenceMotionGeneration.get()) return;
        inferenceMs = result.inferenceMs();
        rgbInferenceMs = result.rgbResult() != null ? result.rgbResult().inferenceMs() : -1L;
        irInferenceMs = result.irResult() != null ? result.irResult().inferenceMs() : -1L;
        recordInferenceMetrics(result.preprocessMs(), result.inferenceMs(), queueMs, endToEndMs);
        updateInferenceFps();

        maybeSaveAttackLiveCapture(task, result);
        runOnUiThread(() -> {
            if (isExclusiveEvaluationMode() || authVerdictShowing || !isPipelineCurrent(task.generation)
                    || task.motionGeneration != inferenceMotionGeneration.get()) return;
            if (authMode) {
                ProbabilityResult primary = result.primaryResult();
                if (primary != null && primary.probabilities().length > 0) {
                    AuthFrameAccumulator.Verdict verdict = authFrames.add(
                            primary.probabilities(), task.receivedNs,
                            SystemClock.elapsedRealtimeNanos());
                    if (verdict != null) {
                        String topSpoofLabel = verdict.topSpoofIndex < VISION_LABELS.length
                                ? ClassLabels.displayLabel(verdict.topSpoofIndex)
                                : "UNKNOWN";
                        showAuthVerdict(verdict.live, verdict.averageLiveScore,
                                topSpoofLabel, verdict.elapsedMs);
                    }
                }
                return;
            }
            screen.overlay.showResult(result.primaryResult(), result.irResult());
            screen.resultsLabel.setText(formatClassificationResults(result));
            if (screen != null) screen.showCleanModeResult(result);
            
            long now = SystemClock.elapsedRealtime();
            if (now - lastUiUpdateTimeMs >= 150L) {
                updatePerformanceHud();
                lastUiUpdateTimeMs = now;
            }
        });
    }

    private void processInferenceIfAllowed(InferenceTask task) {
        if (!isExclusiveEvaluationMode() && !authVerdictShowing && !testMenuShowing) {
            processInference(task);
        }
    }

    private boolean isExclusiveEvaluationMode() {
        return calibrationMode || enrollmentSession.isUiActive();
    }

    private static String recognitionModelLabel(String modelPath) {
        if (modelPath == null) return "N/A";
        if (modelPath.contains("mobilenet_emore")) {
            return "MobileNet Emore INT8";
        }
        if (modelPath.contains("pure_mbf")) {
            return "Pure-MBF INT8";
        }
        if (modelPath.contains("se_mobilefacenet")) {
            return "SE-MBF INT8";
        }
        if (modelPath.contains("mobilenetv4")) {
            return modelPath.contains("int8") ? "MNV4 INT8 RESEARCH" : "MNV4 FP32 RESEARCH";
        }
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
        boolean enrollment = enrollmentSession.isRequested();
        boolean managerReady = manager != null && manager.isReady();
        int enrolledCount = manager != null ? manager.getEnrolledCount() : 0;
        if (!RecognitionPolicy.shouldSchedule(enrollment, faceRecognitionMode,
                managerReady, enrolledCount)) {
            if (enrollment && !managerReady) {
                final boolean[] cancelled = new boolean[1];
                recognitionCoordinator.runExclusive(() ->
                        cancelled[0] = enrollmentSession.cancelRequest());
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
                || (enrollment ? !enrollmentSession.isRequested() : !faceRecognitionMode)) {
            alignedFace.recycle();
            recognitionCoordinator.releaseWorker();
            android.util.Log.i(TAG, "Recognition request cancelled: id=" + startNs
                    + " mode=" + mode + " invalidated before submission");
            return;
        }

        String enrollmentId = enrollment ? enrollmentSession.getEnrollmentId() : null;
        String enrollmentName = enrollment ? enrollmentSession.getEnrollmentName() : null;
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
            final RecognitionEnrollmentSession.Progress[] progressHolder =
                    new RecognitionEnrollmentSession.Progress[1];
            boolean committed = recognitionCoordinator.commitIfCurrent(task.invalidationGeneration,
                    () -> !enginesShutDown && !testMenuShowing
                            && isPipelineCurrent(task.pipelineGeneration)
                            && enrollmentSession.isRequested(), () -> {
                        progressHolder[0] = enrollmentSession.add(embedding,
                                ENROLL_TARGET_FRAME_COUNT);
                        if (progressHolder[0].isComplete()) {
                            templateHolder[0] = task.manager.enrollFaceAverage(
                                    task.enrollmentId, task.enrollmentName,
                                    progressHolder[0].completedEmbeddings);
                            enrollmentSession.finishCompletedAttempt();
                        }
                    });
            if (!committed) {
                android.util.Log.i(TAG, "Recognition request cancelled: id=" + task.startedNs
                        + " enrollment invalidated before commit");
                return;
            }
            template = templateHolder[0];
            collected = progressHolder[0].collectedCount;
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
                    exitRecognitionEnrollmentMode("Enrollment failed");
                    showTransientStatus("Enrollment failed: average error");
                } else {
                    if (enrollmentSession.isUiActive()) {
                        screen.setRecognitionEnrollmentCollecting(collectedCount,
                                ENROLL_TARGET_FRAME_COUNT);
                    }
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
                screen.overlay.showRecognitionResult(String.format(Locale.US, "%s %.1f%%",
                        recognition.matchedTemplate().getName(), recognition.similarityScore() * 100f), true);
            } else {
                screen.overlay.showRecognitionResult(String.format(Locale.US, "UNRECOGNIZED %.1f%%",
                        recognition.similarityScore() * 100f), false);
            }
            updatePerformanceHud();
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
            screen.overlay.clearRecognitionResult();
            updatePerformanceHud();
        });
    }

    private void cancelEnrollment() {
        final boolean[] uiWasActive = new boolean[1];
        recognitionCoordinator.runExclusive(() ->
                uiWasActive[0] = enrollmentSession.cancel());
        if (uiWasActive[0]) {
            runOnUiThread(() -> exitRecognitionEnrollmentMode(normalStatusMessage));
        }
    }

    private void enterRecognitionEnrollmentMode() {
        suspendEvaluationForExclusiveMode();
        screen.enterRecognitionEnrollmentMode();
    }

    private void startRecognitionEnrollment() {
        FaceRecognitionManager manager = faceRecognitionManager;
        if (!enrollmentSession.isUiActive() || manager == null || !manager.isReady()
                || enrollmentSession.getEnrollmentId() == null
                || enrollmentSession.getEnrollmentName() == null) {
            exitRecognitionEnrollmentMode("Face recognition model not ready");
            showTransientStatus("Face recognition model not ready");
            return;
        }
        recognitionCoordinator.runExclusive(enrollmentSession::start);
        screen.setRecognitionEnrollmentCollecting(0, ENROLL_TARGET_FRAME_COUNT);
    }

    private void exitRecognitionEnrollmentMode(String statusMessage) {
        enrollmentSession.exitUi();
        if (screen != null) screen.exitRecognitionEnrollmentMode(statusMessage);
    }

    private void prepareEnrollmentWithActiveModel(String name) {
        FaceRecognitionManager manager = faceRecognitionManager;
        String modelAssetPath = recogModelPath;
        String modelChecksum = recogModelChecksum;
        if (manager == null || !manager.isReady() || modelChecksum == null) {
            showTransientStatus("Face recognition model not ready");
            return;
        }
        ioExecutor.execute(() -> {
            List<FaceTemplate> templates;
            try {
                templates = faceTemplateRepository.loadForModel(modelAssetPath, modelChecksum);
            } catch (RuntimeException e) {
                android.util.Log.e(TAG, "Failed to load templates before enrollment", e);
                runOnUiThread(() -> showTransientStatus("Failed to load face templates"));
                return;
            }
            runOnUiThread(() -> {
                if (enginesShutDown || manager != faceRecognitionManager || !manager.isReady()
                        || !modelAssetPath.equals(manager.getModelAssetPath())
                        || !modelChecksum.equals(recogModelChecksum)) {
                    showTransientStatus("Face recognition model changed");
                    return;
                }
                manager.replaceTemplates(templates);
                enrollmentSession.prepare(UUID.randomUUID().toString(), name);
                enterRecognitionEnrollmentMode();
            });
        });
    }

    private void persistEnrollmentAndReturn(FaceRecognitionManager manager, String modelChecksum,
                                            FaceTemplate template) {
        String modelAssetPath = manager.getModelAssetPath();
        ioExecutor.execute(() -> {
            try {
                faceTemplateRepository.save(modelAssetPath, modelChecksum, template);
                runOnUiThread(() -> {
                    if (!isFinishing() && !isDestroyed()) {
                        exitRecognitionEnrollmentMode("Enrollment complete");
                        openFaceManagement();
                    }
                });
            } catch (RuntimeException e) {
                manager.removeTemplate(template.getId());
                android.util.Log.e(TAG, "Face template save failed", e);
                runOnUiThread(() -> {
                    exitRecognitionEnrollmentMode("Enrollment save failed");
                    showTransientStatus("Enrollment save failed");
                });
            }
        });
    }

    private void updateCollectionUi(long nowMs) {
        CaptureStep step = currentCollectionStep();
        int countdownSeconds = getCollectionCountdownSeconds(nowMs);
        screen.overlay.setCollectionGuide(step.sector, countdownSeconds);
        screen.collectionProgress.setText(formatCollectionProgress(step));
    }

    private SpannableString formatCollectionProgress(CaptureStep step) {
        String qualityLine = shouldCheckCollectionQuality() ? formatCollectionQualityLine() : null;
        CaptureProgressText progress = CaptureProgressText.format(collectionSession.getClassName(), step,
                collectionSession.getStepCount(), collectionSession.getCount(),
                COLLECTION_TARGET_COUNT, qualityLine);
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
        return shouldCheckCollectionQuality(collectionSession.getClassName());
    }

    private boolean shouldCheckCollectionQuality(String className) {
        return CaptureSchedule.shouldCheckQuality(className);
    }

    private void updateHighQualityOnlyButton() {
        if (screen != null) screen.setHighQualityOnly(highQualityOnly);
    }

    private CaptureStep currentCollectionStep() {
        return collectionSession.currentStep();
    }

    private int getCollectionCountdownSeconds(long nowMs) {
        return collectionSession.countdownSeconds(nowMs);
    }

    private void finishDataCollection() {
        collectionSession.finish();
        screen.overlay.setCollecting(false);
        screen.setCollectionPaused(false);
        setCollectionChromeVisible(true);
        screen.startCollectionButton.setEnabled(true);
        screen.switchButton.setEnabled(true);
        screen.highQualityOnlyContainer.setEnabled(true);
        screen.highQualityOnlyButton.setEnabled(true);
        screen.startCollectionButton.setText("START CAPTURE");
        screen.collectionProgress.setVisibility(View.GONE);
        screen.pauseCollectionButton.setVisibility(View.GONE);
        screen.cancelCollectionButton.setVisibility(View.GONE);
    }

    private void cancelDataCollection() {
        CaptureSession.CancelledSession cancelled = collectionSession.cancel();
        if (cancelled == null) return;
        screen.overlay.setCollecting(false);
        screen.setCollectionPaused(false);
        setCollectionChromeVisible(true);
        screen.startCollectionButton.setEnabled(false);
        screen.switchButton.setEnabled(true);
        screen.highQualityOnlyContainer.setEnabled(true);
        screen.highQualityOnlyButton.setEnabled(true);
        screen.startCollectionButton.setText("START CAPTURE");
        screen.collectionProgress.setVisibility(View.GONE);
        screen.pauseCollectionButton.setVisibility(View.GONE);
        screen.cancelCollectionButton.setVisibility(View.GONE);
        showTransientStatus("Capture canceled");
        ioExecutor.execute(() -> {
            deleteCollectionSubject(cancelled.className, cancelled.qualityMode,
                    cancelled.subjectDirName);
            runOnUiThread(() -> {
                if (!collectionSession.isActive()) {
                    FaceDetectionEngine detector = activeFaceDetector;
                    screen.startCollectionButton.setEnabled(detector != null);
                }
            });
        });
    }

    private void toggleCollectionPaused() {
        if (!collectionSession.isActive()) return;
        long nowMs = SystemClock.elapsedRealtime();
        boolean paused = collectionSession.togglePaused(nowMs);
        screen.setCollectionPaused(paused);
        updateCollectionUi(nowMs);
        showTransientStatus(paused ? "Capture paused" : "Capture resumed");
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
        if (collectionSession.isActive() || isAttackLiveCapturing) return;
        FaceDetectionEngine activeDetector = activeFaceDetector;
        FaceDetector qualityDetector = faceDetector;
        if (activeDetector == null) {
            showTransientStatus("Face detector unavailable");
            screen.startCollectionButton.setText("START CAPTURE");
            return;
        }
        if ("live".equals(className) && activeDetector != qualityDetector) {
            showTransientStatus("Live quality capture requires FaceMe");
            screen.startCollectionButton.setText("START CAPTURE");
            return;
        }
        if ("live".equals(className) && (qualityDetector == null || !qualityDetector.isQualityAvailable())) {
            String message = qualityDetector == null ? "Face detector unavailable" : qualityDetector.qualityError();
            showTransientStatus(message.isEmpty() ? "Face quality unavailable" : message);
            screen.startCollectionButton.setText("START CAPTURE");
            return;
        }
        if (!Environment.isExternalStorageManager()) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
            intent.setData(Uri.parse("package:" + getPackageName()));
            startActivity(intent);
            showTransientStatus("Please grant All Files Access and try again.");
            screen.startCollectionButton.setText("START CAPTURE");
            return;
        }
        File collectionRawRoot = resolveRawRoot();
        if (!prepareRawRoot(collectionRawRoot)) {
            showTransientStatus("Save failed: unable to write " + collectionRawRoot.getAbsolutePath());
            screen.startCollectionButton.setText("START CAPTURE");
            return;
        }
        android.util.Log.i(TAG, "Collection raw root: " + collectionRawRoot.getAbsolutePath());
        screen.startCollectionButton.setEnabled(false);
        screen.switchButton.setEnabled(false);
        screen.highQualityOnlyContainer.setEnabled(false);
        screen.highQualityOnlyButton.setEnabled(false);
        screen.startCollectionButton.setText("COLLECTING...");
        screen.collectionProgress.setVisibility(View.VISIBLE);
        screen.pauseCollectionButton.setVisibility(View.VISIBLE);
        screen.pauseCollectionButton.setEnabled(true);
        screen.cancelCollectionButton.setVisibility(View.VISIBLE);

        long nowMs = SystemClock.elapsedRealtime();
        collectionSession.start(className, captureQualityMode(className, highQualityOnly), subjectNum,
                collectionRawRoot,
                highQualityOnly ? FaceQualityLevel.HIGH : COLLECTION_MEDIUM_QUALITY_LEVEL,
                nowMs);
        lastCollectionQuality = null;
        screen.overlay.setCollecting(true);
        screen.setCollectionPaused(false);
        updateCollectionUi(nowMs);
        setCollectionChromeVisible(false);
    }

    private void startAttackLiveCapture() {
        if (collectionSession.isActive() || isAttackLiveCapturing) return;
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
        screen.startCollectionButton.setEnabled(false);
        screen.startCollectionButton.setText("START CAPTURE");
        screen.stopAttackLiveCaptureButton.setVisibility(View.VISIBLE);
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
        screen.stopAttackLiveCaptureButton.setVisibility(View.GONE);
        FaceDetectionEngine detector = activeFaceDetector;
        screen.startCollectionButton.setEnabled(detector != null);
        screen.startCollectionButton.setText("START CAPTURE");
        showTransientStatus("Attack Live capture stopped: " + attackCaptureCount + " saved");
    }

    private void maybeSaveAttackLiveCapture(InferenceTask task, InferenceResult result) {
        ProbabilityResult primary = result.primaryResult();
        if (primary == null || !AttackLiveCaptureGate.shouldSave(primary.probabilities())) {
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
                task.cropMarginRatio, "attack_live", -1, -1, 0f);
        OwnedFrameTask saveTask = new OwnedFrameTask(pair, () -> {
            boolean saved = false;
            try {
                CaptureStorage.SaveResult writeResult = CaptureStorage.saveCompleteSample(
                        pair.rgb.bitmap, task.rgbCrop, pair.ir.bitmap, task.irCrop,
                        metadataJson, sampleDir);
                if (!writeResult.saved) {
                    showTransientStatus("Save failed: " + writeResult.errorMessage);
                    return;
                }
                saved = true;
                attackCaptureCount = sampleIndex;
                android.util.Log.i(TAG, "Saved attack Live sample: " + sampleDir.getAbsolutePath());
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

    private void updatePerformanceHud() {
        revealUiAfterWarmup();
        screen.performance.setText(formatPerformance());
    }

    private void revealUiAfterWarmup() {
        if (enginesWarmedUp && qualityWarmedUp && screen.loadingSpinner.getVisibility() == View.VISIBLE) {
            screen.loadingSpinner.setVisibility(View.GONE);
            screen.irLoadingSpinner.setVisibility(View.GONE);
            if (!collectionSession.isActive()) {
                FaceDetectionEngine detector = activeFaceDetector;
                screen.startCollectionButton.setEnabled(detector != null);
                screen.switchButton.setEnabled(true);
            }
        }
    }

    private CharSequence formatPerformance() {
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

    private CharSequence formatClassificationResults(InferenceResult result) {
        if (result.hasPairedResults()) {
            StringBuilder sb = new StringBuilder();
            appendClassificationResult(sb, null, result.rgbResult());
            sb.append("\n\n");
            int irStart = sb.length();
            appendClassificationResult(sb, null, result.irResult());
            SpannableString text = new SpannableString(sb.toString());
            text.setSpan(new ForegroundColorSpan(IR_RESULT_COLOR), irStart, text.length(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            return text;
        }
        StringBuilder sb = new StringBuilder();
        appendClassificationResult(sb, null, result.result());
        return sb.toString();
    }

    private void appendClassificationResult(StringBuilder sb, String title, ProbabilityResult result) {
        if (title != null) sb.append(title).append("\n");
        for (int i = 0; i < VISION_LABELS.length; i++) {
            if (i > 0) sb.append("\n");
            float probability = result != null ? result.probability(i) * 100f : 0f;
            sb.append(String.format(Locale.US, "%s %.1f%%", ClassLabels.displayLabel(i), probability));
        }
    }

    private void resetResultsLabelToZero() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < VISION_LABELS.length; i++) {
            if (i > 0) sb.append("\n");
            sb.append(String.format(Locale.US, "%s 0.0%%", ClassLabels.displayLabel(i)));
        }
        screen.resultsLabel.setText(sb.toString());
    }

    private void toggleModel() {
        synchronized (engineLock) {
            if (antiSpoofingEngines.isEmpty()) return;
            activeEngineIndex = (activeEngineIndex + 1) % antiSpoofingEngines.size();
            antiSpoofingEngine = antiSpoofingEngines.get(activeEngineIndex);
            antiSpoofingEngineInfo = antiSpoofingEngineInfos.get(activeEngineIndex);

            final String btnText = antiSpoofingEngineInfo.label();
            final String message = antiSpoofingEngineInfo.backendStatus();
            normalStatusMessage = message;

            runOnUiThread(() -> {
                screen.status.setText(message);
                screen.modelSwitchButton.setText(btnText);
                updatePerformanceHud();
            });
        }
    }

    private void toggleFaceDetector() {
        if (collectionSession.isActive() || isAttackLiveCapturing || calibrationMode) return;
        FaceDetectionEngine next;
        synchronized (engineLock) {
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
        screen.status.setText(normalStatusMessage);
    };

    private void showTransientStatus(String message) {
        runOnUiThread(() -> {
            screen.status.setText(message);
            screen.status.removeCallbacks(restoreStatusRunnable);
            screen.status.postDelayed(restoreStatusRunnable, 3000L);
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
        trackingQueue.clear();
        inferenceQueue.clear();
        invalidateRecognitionWork();
        cancelEnrollment();
        frameSynchronizer.clear();
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
        if (resultCode == FaceRecognitionActivity.RESULT_APPLY_SETTINGS && data != null) {
            boolean requestedRecognitionEnabled = data.getBooleanExtra(
                    FaceRecognitionActivity.EXTRA_RECOGNITION_ENABLED, faceRecognitionMode);
            String requestedModelPath = data.getStringExtra(FaceRecognitionActivity.EXTRA_MODEL_ASSET_PATH);
            String requestedDelegateName = data.getStringExtra(FaceRecognitionActivity.EXTRA_DELEGATE_TYPE);
            FaceEmbeddingModel.DelegateType requestedDelegate =
                    FaceEmbeddingModel.DelegateType.NNAPI.name().equals(requestedDelegateName)
                            ? FaceEmbeddingModel.DelegateType.NNAPI
                            : FaceEmbeddingModel.DelegateType.CPU;
            if (requestedModelPath == null || requestedModelPath.isEmpty()) {
                requestedModelPath = recogModelPath;
            }
            boolean modelConfigurationChanged = !requestedModelPath.equals(recogModelPath)
                    || requestedDelegate != recogDelegate;
            faceRecognitionMode = requestedRecognitionEnabled;
            invalidateRecognitionWork();
            if (modelConfigurationChanged) {
                reloadRecognitionModel(requestedModelPath, requestedDelegate);
            } else {
                showTransientStatus("FACE RECOGNITION: " + (faceRecognitionMode ? "ON" : "OFF"));
            }
            return;
        }
        if (resultCode != FaceRecognitionActivity.RESULT_ENROLL_REQUESTED || data == null) {
            FaceRecognitionManager manager = faceRecognitionManager;
            if (manager != null && manager.isReady()) {
                manager.clearTemplates();
                loadPersistedTemplates(manager, manager.getModelAssetPath(), recogModelChecksum);
            }
            return;
        }
        String name = data.getStringExtra(FaceRecognitionActivity.EXTRA_ENROLL_NAME);
        if (name == null || name.trim().isEmpty()) return;
        boolean requestedRecognitionEnabled = data.getBooleanExtra(
                FaceRecognitionActivity.EXTRA_RECOGNITION_ENABLED, faceRecognitionMode);
        String requestedModelPath = data.getStringExtra(FaceRecognitionActivity.EXTRA_MODEL_ASSET_PATH);
        if (requestedModelPath == null || requestedModelPath.isEmpty()) {
            requestedModelPath = recogModelPath;
        }
        String requestedDelegateName = data.getStringExtra(FaceRecognitionActivity.EXTRA_DELEGATE_TYPE);
        FaceEmbeddingModel.DelegateType requestedDelegate =
                FaceEmbeddingModel.DelegateType.NNAPI.name().equals(requestedDelegateName)
                        ? FaceEmbeddingModel.DelegateType.NNAPI
                        : FaceEmbeddingModel.DelegateType.CPU;
        boolean modelConfigurationChanged = !requestedModelPath.equals(recogModelPath)
                || requestedDelegate != recogDelegate;
        faceRecognitionMode = requestedRecognitionEnabled;
        invalidateRecognitionWork();
        String enrollmentName = name.trim();
        if (modelConfigurationChanged) {
            reloadRecognitionModel(requestedModelPath, requestedDelegate,
                    () -> prepareEnrollmentWithActiveModel(enrollmentName));
        } else {
            prepareEnrollmentWithActiveModel(enrollmentName);
        }
    }

    @Override protected void onDestroy() {
        synchronized (engineLock) {
            enginesShutDown = true;
        }
        pipelineGeneration.advance();
        stopCameras();
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
        Thread cleanupThread = new Thread(() -> {
            boolean inferenceTerminated = awaitExecutorTermination(inferenceExecutor);
            boolean recognitionTerminated = awaitExecutorTermination(recognitionExecutor);
            boolean trackingTerminated = awaitExecutorTermination(trackingExecutor);
            awaitExecutorTermination(ioExecutor);
            awaitExecutorTermination(attackCaptureExecutor);
            awaitExecutorTermination(modelInitExecutor);
            if (inferenceTerminated) closeAntiSpoofingEngines();
            if (recognitionTerminated) closeFaceRecognitionManager();
            if (trackingTerminated) closeFaceDetectors();
        }, "main-runtime-cleanup");
        cleanupThread.start();
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

    private enum TestMenuAction {
        SETTINGS,
        WEBRTC,
        AUTH_MODE,
        MOTION_GATE,
        LIGHTING_TEST,
        ENTRY_DETECTOR,
        DETECTOR,
        FACE_MANAGEMENT;

        boolean refreshOnSelect() {
            return this == AUTH_MODE
                    || this == MOTION_GATE
                    || this == LIGHTING_TEST
                    || this == ENTRY_DETECTOR
                    || this == DETECTOR;
        }
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
        final AntiSpoofingEngine engine;
        final float cropMarginRatio;
        final long receivedNs;
        final long enqueuedNs;
        final long motionGeneration;

        InferenceTask(FramePair pair, Rect rgbFace, Rect irFace, Rect rgbCrop, Rect irCrop, PointF[] landmarks,
                      int generation, AntiSpoofingEngine engine, float cropMarginRatio,
                      long receivedNs, long motionGeneration) {
            this.pair = pair;
            this.rgbFace = rgbFace;
            this.irFace = irFace;
            this.rgbCrop = rgbCrop;
            this.irCrop = irCrop;
            this.landmarks = landmarks;
            this.generation = generation;
            this.engine = engine;
            this.cropMarginRatio = cropMarginRatio;
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

    private void setPreviewFace(Bitmap bitmap, boolean color) {
        screen.setPreviewFace(bitmap, color);
    }

    private void clearPreviewFace() {
        screen.clearPreviewFace();
    }

    private static void append(StringBuilder builder, String message) {
        if (builder.length() > 0) builder.append('\n');
        builder.append(message);
    }

    private void closeAntiSpoofingEngines() {
        synchronized (engineLock) {
            for (AntiSpoofingEngine engine : antiSpoofingEngines) {
                try { engine.close(); } catch (Exception ignored) {}
            }
            antiSpoofingEngine = null;
            antiSpoofingEngines.clear();
            antiSpoofingEngineInfo = null;
            antiSpoofingEngineInfos.clear();
        }
    }

    private void closeAntiSpoofingEnginesIfShutDown() {
        if (enginesShutDown) closeAntiSpoofingEngines();
    }

    private synchronized void closeFaceRecognitionManager() {
        FaceRecognitionManager recManager = faceRecognitionManager;
        if (recManager != null) {
            faceRecognitionManager = null;
            try { recManager.close(); } catch (Exception ignored) {}
        }
    }

    private void closeFaceDetectors() {
        FaceDetector detector;
        MediaPipeFaceDetector mediaPipeDetector;
        synchronized (engineLock) {
            detector = faceDetector;
            mediaPipeDetector = mediaPipeFaceDetector;
            faceDetector = null;
            mediaPipeFaceDetector = null;
            activeFaceDetector = null;
        }
        if (detector != null) detector.close();
        if (mediaPipeDetector != null) mediaPipeDetector.close();
    }

    private void closeFaceDetectorsIfShutDown() {
        if (enginesShutDown) closeFaceDetectors();
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
