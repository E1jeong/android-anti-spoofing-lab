package com.virditech.ac7000.ui;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.StateListDrawable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.view.TextureView;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.virditech.ac7000.camera.PreviewTransform;
import com.virditech.ac7000.databinding.ViewMainScreenBinding;
import com.virditech.ac7000.capture.CaptureConfig;
import com.virditech.ac7000.device.DualLightingDetector;
import com.virditech.ac7000.device.ForegroundEntryDetector;
import com.unionbiometrics.vision.AntiSpoofingResult;

import java.util.Locale;

public final class MainScreenView {
    public final FrameLayout root;
    public final FrameLayout uiContainer;
    public final TextureView rgbView;
    public final TextureView irView;
    public final OverlayView overlay;
    public final TextView cleanModeResultView;
    public final TextView cleanModeLightingView;
    public final TextView cleanModeForegroundEntryView;
    public final Button cleanModeSnapshotButton;
    public final ProgressBar loadingSpinner;
    public final ProgressBar irLoadingSpinner;
    public final TextView performance;
    public final TextView status;
    public final FrameLayout irCropContainer;
    public final ImageView faceCropView;
    public final TextView noFaceLabel;
    public final TextView irAeModeLabel;
    public final TextView resultsLabel;
    public final TextView calibrationInstruction;
    public final Button switchButton;
    public final Button modelSwitchButton;
    public final Button startCollectionButton;
    public final Button attackLiveCaptureButton;
    public final ImageButton pauseCollectionButton;
    public final ImageButton cancelCollectionButton;
    public final ImageButton stopAttackLiveCaptureButton;
    public final TextView collectionProgress;
    public final LinearLayout controlsLayout;
    public final Button calibrationConfirm;
    public final Button calibrationCancel;
    public final View calibrationHotspot;
    public final View settingsHotspot;
    public final TextView authResultView;

    private final Activity activity;
    private Bitmap currentPreviewFace;
    private boolean recognitionEnrollmentMode;
    private boolean collectionActive;
    private boolean uiVisibilityLocked;
    private boolean statusVisible = true;
    private CharSequence currentCleanResultText;
    private DualLightingDetector.Result currentLightingResult;
    private ForegroundEntryDetector.Result currentForegroundEntryResult;
    private boolean lightingTestEnabled;
    private boolean foregroundEntryTestEnabled;
    private long lastLightingUiUpdateMs;
    private DualLightingDetector.Condition lastLightingCondition;
    private final CaptureConfigDialog captureConfigDialog;

    public MainScreenView(Activity activity, Listener listener) {
        this.activity = activity;
        captureConfigDialog = new CaptureConfigDialog(activity);
        ViewMainScreenBinding binding =
                ViewMainScreenBinding.inflate(activity.getLayoutInflater());
        root = binding.root;
        uiContainer = binding.uiContainer;
        rgbView = binding.rgbView;
        irView = binding.irView;
        overlay = binding.overlay;
        cleanModeResultView = binding.cleanModeResultView;
        cleanModeLightingView = binding.cleanModeLightingView;
        cleanModeForegroundEntryView = binding.cleanModeForegroundEntryView;
        cleanModeSnapshotButton = binding.cleanModeSnapshotButton;
        loadingSpinner = binding.loadingSpinner;
        irLoadingSpinner = binding.irLoadingSpinner;
        performance = binding.performance;
        status = binding.status;
        irCropContainer = binding.irCropContainer;
        faceCropView = binding.faceCropView;
        noFaceLabel = binding.noFaceLabel;
        irAeModeLabel = binding.irAeModeLabel;
        resultsLabel = binding.resultsLabel;
        calibrationInstruction = binding.calibrationInstruction;
        switchButton = binding.switchButton;
        modelSwitchButton = binding.modelSwitchButton;
        startCollectionButton = binding.startCollectionButton;
        attackLiveCaptureButton = binding.attackLiveCaptureButton;
        pauseCollectionButton = binding.pauseCollectionButton;
        cancelCollectionButton = binding.cancelCollectionButton;
        stopAttackLiveCaptureButton = binding.stopAttackLiveCaptureButton;
        collectionProgress = binding.collectionProgress;
        controlsLayout = binding.controlsLayout;
        calibrationConfirm = binding.calibrationConfirm;
        calibrationCancel = binding.calibrationCancel;
        calibrationHotspot = binding.calibrationHotspot;
        settingsHotspot = binding.settingsHotspot;
        authResultView = binding.authResultView;

        // Keep the original color filters, including after runtime icon swaps.
        pauseCollectionButton.setColorFilter(Color.WHITE);
        cancelCollectionButton.setColorFilter(Color.WHITE);
        stopAttackLiveCaptureButton.setColorFilter(Color.WHITE);

        // XML text dimensions round to pixels; retain the original fractional SP sizes.
        performance.setTextSize(22f);
        resultsLabel.setTextSize(32f);
        status.setTextSize(22f);
        noFaceLabel.setTextSize(20f);
        irAeModeLabel.setTextSize(18f);
        collectionProgress.setTextSize(32f);
        calibrationInstruction.setTextSize(24f);
        cleanModeResultView.setTextSize(38f);
        cleanModeLightingView.setTextSize(17f);
        cleanModeForegroundEntryView.setTextSize(17f);
        cleanModeSnapshotButton.setTextSize(15f);
        authResultView.setTextSize(40f);

        int buttonWidth = activity.getResources().getDisplayMetrics().widthPixels / 3;
        buildCleanModeLightingView(listener);
        buildDiagnostics(binding.diagnosticsLayout, buttonWidth);
        buildIrCrop(listener, buttonWidth);
        buildCaptureIndicators(listener);
        buildControls(listener, buttonWidth);
        buildSettingsHotspot(listener);
        buildCalibrationControls(listener, buttonWidth);
        buildAuthResultView();
        buildTapListener();
        root.bringChildToFront(overlay);
    }

    private void buildDiagnostics(LinearLayout diagnosticsLayout, int buttonWidth) {
        FrameLayout.LayoutParams diagnosticsParams =
                (FrameLayout.LayoutParams) diagnosticsLayout.getLayoutParams();
        diagnosticsParams.rightMargin = buttonWidth + dp(32);
        FrameLayout.LayoutParams resultsParams =
                (FrameLayout.LayoutParams) resultsLabel.getLayoutParams();
        resultsParams.rightMargin = buttonWidth + dp(16);
    }

    private void buildIrCrop(Listener listener, int buttonWidth) {
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) irCropContainer.getLayoutParams();
        params.width = buttonWidth;
        params.height = buttonWidth;
        irAeModeLabel.setOnClickListener(v -> listener.onToggleIrAutoExposure());
    }

    private void buildCaptureIndicators(Listener listener) {
        pauseCollectionButton.setOnClickListener(v -> listener.onPauseCollection());
        cancelCollectionButton.setOnClickListener(v -> listener.onCancelCollection());
        stopAttackLiveCaptureButton.setOnClickListener(v -> listener.onStopAttackLiveCapture());
    }

    private void buildControls(Listener listener, int buttonWidth) {
        int[][] buttonStates = {{-android.R.attr.state_enabled}, {}};
        ColorStateList captureText = startCollectionButton.getTextColors();
        Drawable disabledBackground = startCollectionButton.getBackground().getConstantState()
                .newDrawable(activity.getResources(), activity.getTheme()).mutate();
        Drawable enabledBackground = attackLiveCaptureButton.getBackground().mutate();
        enabledBackground.setTint(Color.rgb(183, 28, 28));
        StateListDrawable attackBackground = new StateListDrawable();
        attackBackground.addState(buttonStates[0], disabledBackground);
        attackBackground.addState(buttonStates[1], enabledBackground);
        attackLiveCaptureButton.setBackground(attackBackground);
        attackLiveCaptureButton.setTextColor(new ColorStateList(buttonStates, new int[]{
                captureText.getColorForState(buttonStates[0], captureText.getDefaultColor()),
                Color.WHITE}));
        configureControlButton(startCollectionButton, buttonWidth,
                v -> showCaptureConfigDialog(listener));
        configureControlButton(attackLiveCaptureButton, buttonWidth,
                v -> listener.onStartAttackLiveCapture());
        configureControlButton(switchButton, buttonWidth, v -> listener.onSwitchPreview());
        configureControlButton(modelSwitchButton, buttonWidth, v -> listener.onToggleModel());
    }

    private void configureControlButton(
            Button button, int buttonWidth, View.OnClickListener listener) {
        button.setOnClickListener(listener);
        LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) button.getLayoutParams();
        params.width = buttonWidth;
        params.bottomMargin = dp(-2);
    }

    private void buildCalibrationControls(Listener listener, int buttonWidth) {
        calibrationConfirm.setOnClickListener(v -> {
            if (recognitionEnrollmentMode) listener.onRecognitionEnrollmentStart();
            else listener.onCalibrationConfirm();
        });
        FrameLayout.LayoutParams confirmParams =
                (FrameLayout.LayoutParams) calibrationConfirm.getLayoutParams();
        confirmParams.width = buttonWidth;
        calibrationCancel.setOnClickListener(v -> listener.onCalibrationCancel());
        FrameLayout.LayoutParams cancelParams =
                (FrameLayout.LayoutParams) calibrationCancel.getLayoutParams();
        cancelParams.width = buttonWidth;
        calibrationHotspot.setOnClickListener(v -> listener.onCalibrationTap());
    }

    private void buildSettingsHotspot(Listener listener) {
        settingsHotspot.setOnClickListener(v -> listener.onSettingsTap());
    }

    private void buildAuthResultView() {
        authResultView.setLineSpacing(dp(6), 1.15f);
    }

    private void buildCleanModeLightingView(Listener listener) {
        cleanModeSnapshotButton.setOnClickListener(v -> listener.onLightingSnapshotRequested());
    }

    private void buildTapListener() {
        uiContainer.setOnClickListener(v -> toggleUiVisibility());
        root.setOnClickListener(v -> toggleUiVisibility());
        cleanModeResultView.setOnClickListener(v -> toggleUiVisibility());
        cleanModeLightingView.setOnClickListener(v -> toggleUiVisibility());
        cleanModeForegroundEntryView.setOnClickListener(v -> toggleUiVisibility());
    }

    public void toggleUiVisibility() {
        if (collectionActive || uiVisibilityLocked) {
            setUiVisible(true);
            return;
        }
        setUiVisible(uiContainer.getVisibility() != View.VISIBLE);
    }

    public void setUiVisible(boolean visible) {
        uiContainer.setVisibility(visible ? View.VISIBLE : View.GONE);
        updateCleanModeResultVisibility();
        updateCleanModeLightingVisibility();
        updateCleanModeForegroundEntryVisibility();
    }

    public boolean isUiVisible() {
        return uiContainer.getVisibility() == View.VISIBLE;
    }

    public void showCleanModeLighting(DualLightingDetector.Result lighting, boolean enabled) {
        this.currentLightingResult = lighting;
        this.lightingTestEnabled = enabled;
        if (lighting == null || !enabled) {
            cleanModeLightingView.setText("");
            lastLightingCondition = null;
            updateCleanModeLightingVisibility();
            updateCleanModeForegroundEntryVisibility();
            return;
        }
        long now = android.os.SystemClock.elapsedRealtime();
        if (lighting.condition == lastLightingCondition && (now - lastLightingUiUpdateMs < 100L)) {
            updateCleanModeLightingVisibility();
            updateCleanModeForegroundEntryVisibility();
            return;
        }
        lastLightingUiUpdateMs = now;
        lastLightingCondition = lighting.condition;

        String title = "[LIGHT EXP] " + lighting.condition.label;
        String faceLabel = lighting.hasFace ? "Face" : "Center";
        String detail = String.format(Locale.US,
                "\nMean:%.0f  P90:%.0f  P10:%.0f  (CR:%.1fx)\nRGB %s:%.0f  Bg:%.0f P90:%.0f (Sat:%.1f%%) | IR:%.0f",
                lighting.rgbGlobalMean, lighting.rgbP90, lighting.rgbP10, lighting.rgbContrastRatio,
                faceLabel, lighting.rgbFaceMean, lighting.rgbBgMean, lighting.rgbBgP90, lighting.rgbSatPct,
                lighting.irFullMean);

        SpannableString spannable = new SpannableString(title + detail);
        spannable.setSpan(new ForegroundColorSpan(lighting.condition.color), 0, title.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        spannable.setSpan(new ForegroundColorSpan(Color.WHITE), title.length(), spannable.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        cleanModeLightingView.setText(spannable);
        updateCleanModeLightingVisibility();
        updateCleanModeForegroundEntryVisibility();
    }

    public void clearCleanModeLighting() {
        this.currentLightingResult = null;
        this.lastLightingCondition = null;
        cleanModeLightingView.setText("");
        updateCleanModeLightingVisibility();
        updateCleanModeForegroundEntryVisibility();
    }

    public void showCleanModeForegroundEntry(ForegroundEntryDetector.Result entry, boolean enabled) {
        currentForegroundEntryResult = entry;
        foregroundEntryTestEnabled = enabled;
        if (entry == null || !enabled) {
            cleanModeForegroundEntryView.setText("");
            updateCleanModeForegroundEntryVisibility();
            return;
        }
        String title = "[ENTRY EXP] " + entry.state.label;
        String detail = String.format(Locale.US,
                "\nRGB Δ:%.1f", entry.rgbDelta);
        SpannableString spannable = new SpannableString(title + detail);
        spannable.setSpan(new ForegroundColorSpan(entry.state.color), 0, title.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        spannable.setSpan(new ForegroundColorSpan(Color.WHITE), title.length(), spannable.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        cleanModeForegroundEntryView.setText(spannable);
        updateCleanModeForegroundEntryVisibility();
    }

    public void clearCleanModeForegroundEntry() {
        currentForegroundEntryResult = null;
        cleanModeForegroundEntryView.setText("");
        updateCleanModeForegroundEntryVisibility();
    }

    private void updateCleanModeLightingVisibility() {
        boolean show = uiContainer.getVisibility() != View.VISIBLE
                && lightingTestEnabled
                && currentLightingResult != null;
        cleanModeLightingView.setVisibility(show ? View.VISIBLE : View.GONE);
        updateSnapshotButtonVisibility();
        if (show) {
            root.bringChildToFront(cleanModeLightingView);
        }
    }

    private void updateCleanModeForegroundEntryVisibility() {
        boolean show = uiContainer.getVisibility() != View.VISIBLE
                && foregroundEntryTestEnabled
                && currentForegroundEntryResult != null;
        boolean lightingShown = uiContainer.getVisibility() != View.VISIBLE
                && lightingTestEnabled
                && currentLightingResult != null;
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) cleanModeForegroundEntryView.getLayoutParams();
        int bottomMargin = dp(lightingShown ? 130 : 20);
        if (params.bottomMargin != bottomMargin) {
            params.bottomMargin = bottomMargin;
            cleanModeForegroundEntryView.setLayoutParams(params);
        }
        cleanModeForegroundEntryView.setVisibility(show ? View.VISIBLE : View.GONE);
        updateSnapshotButtonVisibility();
        if (show) {
            root.bringChildToFront(cleanModeForegroundEntryView);
        }
    }

    private void updateSnapshotButtonVisibility() {
        boolean show = uiContainer.getVisibility() != View.VISIBLE
                && lightingTestEnabled
                && currentLightingResult != null;
        cleanModeSnapshotButton.setVisibility(show ? View.VISIBLE : View.GONE);
        if (show) root.bringChildToFront(cleanModeSnapshotButton);
    }

    public void showCleanModeResult(AntiSpoofingResult result, String classLabel) {
        if (result == null || result.getProbabilities() == null) {
            clearCleanModeResult();
            return;
        }
        int color = result.getStatus() == AntiSpoofingResult.Status.LIVE
            ? Color.rgb(0, 230, 118) : Color.rgb(255, 82, 82);
        String text = formatResult(result, classLabel);
        SpannableString spannable = new SpannableString(text);
        spannable.setSpan(new ForegroundColorSpan(color), 0, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        currentCleanResultText = spannable;
        cleanModeResultView.setText(currentCleanResultText);
        updateCleanModeResultVisibility();
    }

    public void clearCleanModeResult() {
        currentCleanResultText = null;
        cleanModeResultView.setText("");
        updateCleanModeResultVisibility();
    }

    private void updateCleanModeResultVisibility() {
        boolean showClean = uiContainer.getVisibility() != View.VISIBLE
                && currentCleanResultText != null
                && currentCleanResultText.length() > 0;
        cleanModeResultView.setVisibility(showClean ? View.VISIBLE : View.GONE);
        if (showClean) root.bringChildToFront(cleanModeResultView);
    }

    public static String classDisplayLabel(String label) {
        return label.startsWith("CURVED_")
                ? "C " + label.substring("CURVED_".length()) : label;
    }

    private static String formatResult(AntiSpoofingResult result, String classLabel) {
        return String.format(Locale.US, "%s %.1f%%",
                classDisplayLabel(classLabel),
                result.getScore() * 100f);
    }

    public void showAuthResult(CharSequence text) {
        authResultView.setText(text);
        authResultView.setVisibility(View.VISIBLE);
    }

    public void hideAuthResult() {
        authResultView.setVisibility(View.GONE);
    }

    public void setInitialPerformanceText(String text) {
        performance.setText(text);
    }

    public void setIrVisible(boolean showIr) {
        rgbView.setAlpha(showIr ? 0f : 1f);
        irView.setAlpha(showIr ? 1f : 0f);
        overlay.setShowIr(showIr);
        overlay.setTranslationX(0f);
        overlay.setTranslationY(0f);
        switchButton.setText(showIr ? "SHOW RGB" : "SHOW IR");
    }

    public void setIrAeMode(String mode) {
        irAeModeLabel.setText("IR AE: " + mode);
        irAeModeLabel.setVisibility(mode == null ? View.GONE : View.VISIBLE);
    }

    public void setStatusText(CharSequence text) {
        status.setText(text);
        updateStatusVisibility();
    }

    private void setStatusVisible(boolean visible) {
        statusVisible = visible;
        updateStatusVisibility();
    }

    private void updateStatusVisibility() {
        status.setVisibility(statusVisible && status.length() > 0 ? View.VISIBLE : View.GONE);
    }

    public void enterCalibrationMode() {
        uiVisibilityLocked = true;
        setUiVisible(true);
        overlay.clearResult();
        clearCleanModeResult();
        overlay.setCalibrationMode(true);
        performance.setVisibility(View.GONE);
        setStatusVisible(false);
        resultsLabel.setVisibility(View.GONE);
        controlsLayout.setVisibility(View.GONE);
        calibrationHotspot.setVisibility(View.GONE);
        settingsHotspot.setVisibility(View.GONE);
        calibrationInstruction.setText("Fit one face inside the guide, then press CONFIRM");
        calibrationInstruction.setVisibility(View.VISIBLE);
        calibrationConfirm.setVisibility(View.VISIBLE);
        calibrationCancel.setVisibility(View.VISIBLE);
    }

    public void exitCalibrationMode(String normalStatusMessage) {
        uiVisibilityLocked = false;
        overlay.setCalibrationMode(false);
        overlay.setGuideOnlyMode(false);
        overlay.clearResult();
        clearCleanModeResult();
        calibrationInstruction.setVisibility(View.GONE);
        calibrationConfirm.setVisibility(View.GONE);
        calibrationCancel.setVisibility(View.GONE);
        performance.setVisibility(View.VISIBLE);
        setStatusText(normalStatusMessage);
        setStatusVisible(true);
        resultsLabel.setVisibility(View.VISIBLE);
        irCropContainer.setVisibility(View.VISIBLE);
        controlsLayout.setVisibility(View.VISIBLE);
        calibrationHotspot.setVisibility(View.VISIBLE);
        settingsHotspot.setVisibility(View.VISIBLE);
    }

    public void enterRecognitionEnrollmentMode() {
        recognitionEnrollmentMode = true;
        uiVisibilityLocked = true;
        setUiVisible(true);
        overlay.clearResult();
        clearCleanModeResult();
        overlay.setCalibrationMode(true);
        overlay.setGuideOnlyMode(true);
        performance.setVisibility(View.GONE);
        setStatusVisible(false);
        resultsLabel.setVisibility(View.GONE);
        irCropContainer.setVisibility(View.GONE);
        controlsLayout.setVisibility(View.GONE);
        calibrationHotspot.setVisibility(View.GONE);
        settingsHotspot.setVisibility(View.GONE);
        calibrationInstruction.setText("Fit one face inside the guide, then press START");
        calibrationInstruction.setVisibility(View.VISIBLE);
        calibrationConfirm.setText("START");
        calibrationConfirm.setEnabled(true);
        calibrationConfirm.setVisibility(View.VISIBLE);
        calibrationCancel.setVisibility(View.GONE);
    }

    public void setRecognitionEnrollmentCollecting(int collectedCount, int targetCount) {
        calibrationInstruction.setText("Collecting " + collectedCount + "/" + targetCount + " frames...");
        calibrationConfirm.setText("COLLECTING...");
        calibrationConfirm.setEnabled(false);
    }

    public void exitRecognitionEnrollmentMode(String normalStatusMessage) {
        recognitionEnrollmentMode = false;
        exitCalibrationMode(normalStatusMessage);
    }

    public void setCollectionChromeVisible(boolean visible) {
        collectionActive = !visible;
        if (collectionActive) setUiVisible(true);
        int visibility = visible ? View.VISIBLE : View.GONE;
        performance.setVisibility(visibility);
        setStatusVisible(visible);
        resultsLabel.setVisibility(visibility);
        irCropContainer.setVisibility(visibility);
        controlsLayout.setVisibility(visibility);
        calibrationHotspot.setVisibility(visibility);
        settingsHotspot.setVisibility(visibility);
    }

    public void setAuthMode(boolean authMode) {
        int visibility = authMode ? View.GONE : View.VISIBLE;
        performance.setVisibility(visibility);
        setStatusVisible(!authMode);
        resultsLabel.setVisibility(visibility);
        irCropContainer.setVisibility(visibility);
        controlsLayout.setVisibility(visibility);
        calibrationHotspot.setVisibility(visibility);
        settingsHotspot.setVisibility(View.VISIBLE);
        overlay.setAuthMode(authMode);
        if (!authMode) {
            hideAuthResult();
        } else {
            clearCleanModeResult();
        }
    }

    public void setCollectionActiveChrome(boolean collecting) {
        startCollectionButton.setEnabled(!collecting);
        attackLiveCaptureButton.setEnabled(!collecting);
        switchButton.setEnabled(!collecting);
        startCollectionButton.setText(collecting ? "COLLECTING..." : "START CAPTURE");
        collectionProgress.setVisibility(collecting ? View.VISIBLE : View.GONE);
        pauseCollectionButton.setVisibility(collecting ? View.VISIBLE : View.GONE);
        cancelCollectionButton.setVisibility(collecting ? View.VISIBLE : View.GONE);
    }

    public void setCollectionPaused(boolean paused) {
        pauseCollectionButton.setImageResource(paused
                ? android.R.drawable.ic_media_play
                : android.R.drawable.ic_media_pause);
        pauseCollectionButton.setContentDescription(paused ? "Resume capture" : "Pause capture");
    }

    public void setPreviewFace(Bitmap bitmap, boolean color) {
        Bitmap previous = currentPreviewFace;
        currentPreviewFace = bitmap;
        faceCropView.setScaleX(PreviewTransform.forColorCamera(color).analysisBitmapScaleX());
        faceCropView.setImageBitmap(bitmap);
        if (previous != null && previous != bitmap && !previous.isRecycled()) previous.recycle();
    }

    public void clearPreviewFace() {
        faceCropView.setImageDrawable(null);
        if (currentPreviewFace != null && !currentPreviewFace.isRecycled()) currentPreviewFace.recycle();
        currentPreviewFace = null;
    }

    public static final String[][] CLASS_OPTIONS = {
            {"live", "live"},
            {"display", "display"},
            {"picture", "picture"},
            {"print", "print"},
            {"mask", "mask"},
            {"pmask", "pmask"},
            {"C PICTURE", "curved_picture"},
            {"C PRINT", "curved_print"},
            {"C MASK", "curved_mask"},
            {"C PMASK", "curved_pmask"},
            {"DENTAL WHITE", "dental_white"},
            {"DENTAL BLACK", "dental_black"}
    };

    public void showCaptureConfigDialog(Listener listener) {
        captureConfigDialog.show(listener::onStartCollection);
    }

    public void dismissCaptureConfigDialog() {
        captureConfigDialog.dismiss();
    }

    private int dp(int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }

    public interface Listener {
        void onPauseCollection();
        void onCancelCollection();
        void onStartAttackLiveCapture();
        void onStopAttackLiveCapture();
        void onStartCollection(CaptureConfig config);
        void onSwitchPreview();
        void onToggleModel();
        void onToggleIrAutoExposure();
        void onCalibrationConfirm();
        void onCalibrationCancel();
        void onRecognitionEnrollmentStart();
        void onCalibrationTap();
        void onSettingsTap();
        void onLightingSnapshotRequested();
    }
}
