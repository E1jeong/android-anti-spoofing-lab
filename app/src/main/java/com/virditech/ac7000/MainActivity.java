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
import com.virditech.ac7000.camera.FrameSynchronizer;
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

    private final ExecutorService inferenceExecutor = Executors.newSingleThreadExecutor();
    private final AtomicReference<FramePair> pendingPair = new AtomicReference<>();
    private final AtomicBoolean workerRunning = new AtomicBoolean();
    private TextureView rgbView;
    private TextureView irView;
    private OverlayView overlay;
    private TextView performance;
    private TextView status;
    private DualCameraController cameras;
    private FrameSynchronizer synchronizer;
    private FaceDetector faceDetector;
    private AntiSpoofingClassifier classifier;
    private Calibration calibration;
    private final AppWatchdog appWatchdog = new AppWatchdog();
    private boolean showIr;
    private boolean resumed;
    private int completedFrames;
    private long fpsWindowStartNs;
    private float processingFps;

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
        synchronizer = new FrameSynchronizer(this::submitLatest);
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
        inferenceExecutor.execute(() -> {
            StringBuilder messages = new StringBuilder();
            try { calibration = Calibration.load(); }
            catch (Exception e) { messages.append("CALIBRATION REQUIRED: ").append(e.getMessage()); }
            try { faceDetector = new FaceDetector(getApplicationContext()); }
            catch (Exception e) { append(messages, e.getMessage()); }
            try { classifier = new AntiSpoofingClassifier(getApplicationContext()); }
            catch (Exception e) { append(messages, "MODEL REQUIRED: " + e.getMessage()); }
            String message = messages.length() == 0 ? "Ready" : messages.toString();
            runOnUiThread(() -> status.setText(message));
        });
    }

    private void startCameras() {
        if (!resumed || cameras != null || checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) return;
        HardwareControls.setLcdBrightness(90);
        HardwareControls.setIrLed(true);
        cameras = new DualCameraController(this, rgbView, irView, new DualCameraController.Listener() {
            @Override public void onRgb(FrameData frame) { synchronizer.offerRgb(frame); }
            @Override public void onIr(FrameData frame) { synchronizer.offerIr(frame); }
            @Override public void onError(String message) { runOnUiThread(() -> status.setText(message)); }
        });
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
        if (synchronizer != null) synchronizer.clear();
        FramePair pair = pendingPair.getAndSet(null);
        if (pair != null) pair.recycle();
        super.onPause();
    }

    private void submitLatest(FramePair pair) {
        FramePair replaced = pendingPair.getAndSet(pair);
        if (replaced != null) replaced.recycle();
        if (workerRunning.compareAndSet(false, true)) inferenceExecutor.execute(this::drainPairs);
    }

    private void drainPairs() {
        try {
            FramePair pair;
            while ((pair = pendingPair.getAndSet(null)) != null) {
                try { process(pair); }
                finally { pair.recycle(); }
            }
        } finally {
            workerRunning.set(false);
            if (pendingPair.get() != null && workerRunning.compareAndSet(false, true)) inferenceExecutor.execute(this::drainPairs);
        }
    }

    private void process(FramePair pair) {
        if (faceDetector == null || classifier == null || calibration == null) return;
        Rect detected = faceDetector.detectLargest(pair.rgb.bitmap);
        if (detected == null) {
            runOnUiThread(overlay::clearResult);
            return;
        }
        Rect irDetected = calibration.rgbToIr(detected, pair.ir.bitmap.getWidth(), pair.ir.bitmap.getHeight());
        Rect rgbCrop = FaceCrop.expand(detected, classifier.cropMarginRatio(), pair.rgb.bitmap.getWidth(), pair.rgb.bitmap.getHeight());
        Rect irCrop = FaceCrop.expand(irDetected, classifier.cropMarginRatio(), pair.ir.bitmap.getWidth(), pair.ir.bitmap.getHeight());
        ClassificationResult result = classifier.classify(pair.rgb.bitmap, rgbCrop, pair.ir.bitmap, irCrop);
        updateFps();
        runOnUiThread(() -> {
            overlay.show(detected, irDetected, result);
            performance.setText(String.format(Locale.US, "Inference %d ms\nProcessing %.1f FPS", result.inferenceMs, processingFps));
        });
    }

    private void updateFps() {
        long now = SystemClock.elapsedRealtimeNanos();
        if (fpsWindowStartNs == 0L) fpsWindowStartNs = now;
        completedFrames++;
        long elapsed = now - fpsWindowStartNs;
        if (elapsed >= 1_000_000_000L) {
            processingFps = completedFrames * 1_000_000_000f / elapsed;
            completedFrames = 0;
            fpsWindowStartNs = now;
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
        if (synchronizer != null) synchronizer.clear();
        FramePair pair = pendingPair.getAndSet(null);
        if (pair != null) pair.recycle();
        inferenceExecutor.shutdownNow();
        if (classifier != null) classifier.close();
        if (faceDetector != null) faceDetector.close();
        appWatchdog.close();
        super.onDestroy();
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
