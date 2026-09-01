package com.virditech.ac7000.ui;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.view.Gravity;
import android.view.TextureView;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.virditech.ac7000.device.DualLightingDetector;
import com.virditech.ac7000.model.ClassificationResult;
import com.virditech.ac7000.model.SlotClassificationResult;

import java.util.Locale;

public final class MainScreenView {
    public final FrameLayout root;
    public final FrameLayout uiContainer;
    public final TextureView rgbView;
    public final TextureView irView;
    public final OverlayView overlay;
    public final TextView cleanModeResultView;
    public final TextView cleanModeLightingView;
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
    public final ImageButton pauseCollectionButton;
    public final ImageButton cancelCollectionButton;
    public final ImageButton stopAttackLiveCaptureButton;
    public final FrameLayout highQualityOnlyContainer;
    public final Button highQualityOnlyButton;
    public final TextView collectionProgress;
    public final LinearLayout controlsLayout;
    public final Button calibrationConfirm;
    public final Button calibrationCancel;
    public final View calibrationHotspot;
    public final View settingsHotspot;
    public final LinearLayout expandableLayout;
    public final TextView authResultView;

    private final Activity activity;
    private Bitmap currentPreviewFace;
    private boolean highQualityOnly;
    private boolean recognitionEnrollmentMode;
    private boolean collectionActive;
    private CharSequence currentCleanResultText;
    private DualLightingDetector.Result currentLightingResult;
    private boolean lightingTestEnabled;
    private long lastLightingUiUpdateMs;
    private DualLightingDetector.Condition lastLightingCondition;

    public MainScreenView(Activity activity, Listener listener) {
        this.activity = activity;
        root = new FrameLayout(activity);
        uiContainer = new FrameLayout(activity);
        rgbView = new TextureView(activity);
        irView = new TextureView(activity);
        overlay = new OverlayView(activity);
        cleanModeResultView = new TextView(activity);
        cleanModeLightingView = new TextView(activity);
        loadingSpinner = new ProgressBar(activity);
        performance = label(22f);
        resultsLabel = label(32f);
        status = label(22f);
        irCropContainer = new FrameLayout(activity);
        faceCropView = new ImageView(activity);
        noFaceLabel = label(20f);
        irAeModeLabel = label(18f);
        irLoadingSpinner = new ProgressBar(activity);
        controlsLayout = new LinearLayout(activity);
        collectionProgress = label(32f);
        pauseCollectionButton = iconButton(android.R.drawable.ic_media_pause, Color.parseColor("#37474F"));
        cancelCollectionButton = iconButton(android.R.drawable.ic_menu_close_clear_cancel,
                Color.parseColor("#C49A00"));
        stopAttackLiveCaptureButton = iconButton(android.R.drawable.ic_menu_close_clear_cancel,
                Color.parseColor("#B71C1C"));
        expandableLayout = new LinearLayout(activity);
        highQualityOnlyContainer = new FrameLayout(activity);
        highQualityOnlyButton = new Button(activity);
        startCollectionButton = new Button(activity);
        switchButton = new Button(activity);
        modelSwitchButton = new Button(activity);
        calibrationInstruction = label(24f);
        calibrationConfirm = new Button(activity);
        calibrationCancel = new Button(activity);
        calibrationHotspot = new View(activity);
        settingsHotspot = new View(activity);
        authResultView = new TextView(activity);

        int buttonWidth = activity.getResources().getDisplayMetrics().widthPixels / 3;
        buildPreview();
        buildCleanModeResultView();
        buildCleanModeLightingView();
        buildDiagnostics(buttonWidth);
        buildIrCrop(listener, buttonWidth);
        buildCaptureIndicators(listener);
        buildControls(listener, buttonWidth);
        buildSettingsHotspot(listener);
        buildCalibrationControls(listener, buttonWidth);
        buildAuthResultView();
        buildTapListener();
        root.bringChildToFront(overlay);
    }

    private void buildPreview() {
        root.setBackgroundColor(Color.BLACK);
        irView.setAlpha(0f);
        root.addView(rgbView, match());
        root.addView(irView, match());
        root.addView(overlay, match());
        root.addView(uiContainer, match());
    }

    private void buildDiagnostics(int buttonWidth) {
        LinearLayout diagnosticsLayout = new LinearLayout(activity);
        diagnosticsLayout.setOrientation(LinearLayout.VERTICAL);
        diagnosticsLayout.setGravity(Gravity.START | Gravity.BOTTOM);
        FrameLayout.LayoutParams diagnosticsParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.START);
        diagnosticsParams.setMargins(dp(16), dp(16), buttonWidth + dp(32), dp(16));

        diagnosticsLayout.addView(performance, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        resultsLabel.setTextColor(Color.WHITE);
        resultsLabel.setShadowLayer(5f, 1f, 1f, Color.BLACK);
        FrameLayout.LayoutParams resultsParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.START);
        resultsParams.setMargins(dp(16), dp(16), buttonWidth + dp(16), dp(16));
        uiContainer.addView(resultsLabel, resultsParams);

        loadingSpinner.setIndeterminate(true);
        uiContainer.addView(loadingSpinner, wrap(Gravity.CENTER, 0, 0));

        status.setText("Initializing...");
        diagnosticsLayout.addView(status, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        uiContainer.addView(diagnosticsLayout, diagnosticsParams);
    }

    private void buildIrCrop(Listener listener, int buttonWidth) {
        FrameLayout.LayoutParams irCropParams = wrap(Gravity.TOP | Gravity.END, 0, 0);
        irCropParams.width = buttonWidth;
        irCropParams.height = buttonWidth;
        root.addView(irCropContainer, irCropParams);

        faceCropView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        faceCropView.setBackgroundColor(Color.parseColor("#44000000"));
        irCropContainer.addView(faceCropView, match());

        noFaceLabel.setText("NO FACE");
        noFaceLabel.setVisibility(View.GONE);
        irCropContainer.addView(noFaceLabel, wrap(Gravity.CENTER, 0, 0));

        irLoadingSpinner.setIndeterminate(true);
        irCropContainer.addView(irLoadingSpinner, wrap(Gravity.CENTER, 0, 0));

        irAeModeLabel.setTextColor(Color.WHITE);
        irAeModeLabel.setGravity(Gravity.CENTER);
        irAeModeLabel.setPadding(0, dp(4), 0, dp(4));
        irAeModeLabel.setVisibility(View.GONE);
        irAeModeLabel.setOnClickListener(v -> listener.onToggleIrAutoExposure());
        GradientDrawable irAeBorder = new GradientDrawable();
        irAeBorder.setColor(Color.TRANSPARENT);
        irAeBorder.setStroke(dp(1), Color.WHITE);
        irAeBorder.setCornerRadius(dp(4));
        irAeModeLabel.setBackground(irAeBorder);
        FrameLayout.LayoutParams irAeParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM);
        irAeParams.setMargins(dp(4), 0, dp(4), dp(4));
        irCropContainer.addView(irAeModeLabel, irAeParams);
    }

    private void buildCaptureIndicators(Listener listener) {
        collectionProgress.setText("");
        collectionProgress.setGravity(Gravity.CENTER);
        collectionProgress.setVisibility(View.GONE);
        FrameLayout.LayoutParams collectionProgressParams =
                wrap(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, 16, 16);
        uiContainer.addView(collectionProgress, collectionProgressParams);

        configureCaptureButton(pauseCollectionButton, "Pause capture",
                Gravity.TOP | Gravity.CENTER_HORIZONTAL, listener::onPauseCollection);
        configureCaptureButton(cancelCollectionButton, "Cancel capture",
                Gravity.TOP | Gravity.END, listener::onCancelCollection);
        configureCaptureButton(stopAttackLiveCaptureButton, "Stop attack Live capture",
                Gravity.TOP | Gravity.CENTER_HORIZONTAL, listener::onStopAttackLiveCapture);
    }

    private void configureCaptureButton(
            ImageButton button, String description, int gravity, Runnable action) {
        button.setContentDescription(description);
        button.setVisibility(View.GONE);
        button.setOnClickListener(v -> action.run());
        FrameLayout.LayoutParams params = wrap(gravity, 16, 16);
        params.width = dp(84);
        params.height = dp(84);
        uiContainer.addView(button, params);
    }

    private void buildControls(Listener listener, int buttonWidth) {
        controlsLayout.setOrientation(LinearLayout.VERTICAL);
        controlsLayout.setGravity(Gravity.END | Gravity.BOTTOM);

        expandableLayout.setOrientation(LinearLayout.VERTICAL);
        expandableLayout.setGravity(Gravity.END);
        expandableLayout.setVisibility(View.GONE);
        buildCollectionMenu(listener, buttonWidth);
        controlsLayout.addView(expandableLayout);

        configureControlButton(startCollectionButton, "START CAPTURE", buttonWidth,
                v -> toggleCollectionClassMenu());
        configureControlButton(switchButton, "SHOW IR", buttonWidth,
                v -> listener.onSwitchPreview());
        configureControlButton(modelSwitchButton, "MODEL 1", buttonWidth,
                v -> listener.onToggleModel());

        uiContainer.addView(controlsLayout, wrap(Gravity.BOTTOM | Gravity.END, 16, 16));
    }

    private void buildCollectionMenu(Listener listener, int buttonWidth) {
        highQualityOnlyButton.setGravity(Gravity.CENTER);
        updateHighQualityOnlyButton();
        highQualityOnlyButton.setOnClickListener(v -> {
            highQualityOnly = !highQualityOnly;
            updateHighQualityOnlyButton();
            listener.onHighQualityOnlyChanged(highQualityOnly);
        });
        highQualityOnlyContainer.setOnClickListener(v -> highQualityOnlyButton.performClick());
        highQualityOnlyContainer.addView(highQualityOnlyButton, match());
        expandableLayout.addView(highQualityOnlyContainer, menuLayoutParams(buttonWidth));

        Button attackLiveCaptureButton = new Button(activity);
        attackLiveCaptureButton.setText("ATTACK");
        attackLiveCaptureButton.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                Color.parseColor("#B71C1C")));
        attackLiveCaptureButton.setTextColor(Color.WHITE);
        attackLiveCaptureButton.setOnClickListener(v -> {
            listener.onStartAttackLiveCapture();
            expandableLayout.setVisibility(View.GONE);
        });
        expandableLayout.addView(attackLiveCaptureButton, menuLayoutParams(buttonWidth));

        String[][] classes = {
                {"live", "live", "#607D8B"},
                {"display", "display", "#607D8B"},
                {"picture", "picture", "#607D8B"},
                {"print", "print", "#607D8B"},
                {"mask", "mask", "#607D8B"},
                {"pmask", "pmask", "#607D8B"},
                {"C PICTURE", "curved_picture", "#607D8B"},
                {"C PRINT", "curved_print", "#607D8B"},
                {"C MASK", "curved_mask", "#607D8B"},
                {"C PMASK", "curved_pmask", "#607D8B"},
                {"DENTAL WHITE", "dental_white", "#607D8B"},
                {"DENTAL BLACK", "dental_black", "#607D8B"}
        };
        for (String[] classOption : classes) {
            String buttonText = classOption[0];
            String className = classOption[1];
            Button button = new Button(activity);
            button.setText(buttonText);
            button.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                    Color.parseColor(classOption[2])));
            button.setTextColor(Color.WHITE);
            button.setOnClickListener(v -> {
                listener.onStartCollection(className);
                expandableLayout.setVisibility(View.GONE);
            });
            expandableLayout.addView(button, menuLayoutParams(buttonWidth));
        }
    }

    private LinearLayout.LayoutParams menuLayoutParams(int buttonWidth) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                buttonWidth, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = dp(2);
        return params;
    }

    private void configureControlButton(
            Button button, String text, int buttonWidth, View.OnClickListener listener) {
        button.setText(text);
        button.setEnabled(false);
        button.setOnClickListener(listener);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                buttonWidth, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = dp(-2);
        controlsLayout.addView(button, params);
    }

    private void buildCalibrationControls(Listener listener, int buttonWidth) {
        calibrationInstruction.setText("Fit one face inside the guide, then press CONFIRM");
        calibrationInstruction.setGravity(Gravity.CENTER);
        calibrationInstruction.setVisibility(View.GONE);
        uiContainer.addView(calibrationInstruction, wrap(Gravity.TOP | Gravity.CENTER_HORIZONTAL, 16, 24));

        calibrationConfirm.setText("CONFIRM");
        calibrationConfirm.setVisibility(View.GONE);
        calibrationConfirm.setOnClickListener(v -> {
            if (recognitionEnrollmentMode) listener.onRecognitionEnrollmentStart();
            else listener.onCalibrationConfirm();
        });
        FrameLayout.LayoutParams confirmParams = wrap(Gravity.BOTTOM | Gravity.END, 16, 16);
        confirmParams.width = buttonWidth;
        uiContainer.addView(calibrationConfirm, confirmParams);

        calibrationCancel.setText("CANCEL");
        calibrationCancel.setVisibility(View.GONE);
        calibrationCancel.setOnClickListener(v -> listener.onCalibrationCancel());
        FrameLayout.LayoutParams cancelParams = wrap(Gravity.BOTTOM | Gravity.START, 16, 16);
        cancelParams.width = buttonWidth;
        uiContainer.addView(calibrationCancel, cancelParams);

        calibrationHotspot.setOnClickListener(v -> listener.onCalibrationTap());
        uiContainer.addView(calibrationHotspot, new FrameLayout.LayoutParams(
                dp(180), dp(180), Gravity.TOP | Gravity.START));
    }

    private void buildSettingsHotspot(Listener listener) {
        settingsHotspot.setOnClickListener(v -> listener.onSettingsTap());
        uiContainer.addView(settingsHotspot, new FrameLayout.LayoutParams(
                dp(180), dp(180), Gravity.BOTTOM | Gravity.START));
    }

    private void buildAuthResultView() {
        authResultView.setTextSize(40f);
        authResultView.setTextColor(Color.WHITE);
        authResultView.setTypeface(Typeface.DEFAULT_BOLD);
        authResultView.setGravity(Gravity.START | Gravity.TOP);
        authResultView.setShadowLayer(8f, 2f, 2f, Color.BLACK);
        authResultView.setLineSpacing(dp(6), 1.15f);
        authResultView.setVisibility(View.GONE);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.START);
        params.setMargins(dp(20), dp(24), dp(20), dp(20));
        uiContainer.addView(authResultView, params);
    }

    private void buildCleanModeResultView() {
        cleanModeResultView.setTextSize(38f);
        cleanModeResultView.setTypeface(Typeface.DEFAULT_BOLD);
        cleanModeResultView.setGravity(Gravity.CENTER);
        cleanModeResultView.setShadowLayer(8f, 2f, 2f, Color.BLACK);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#B0000000"));
        bg.setCornerRadius(dp(16));
        cleanModeResultView.setBackground(bg);
        cleanModeResultView.setPadding(dp(24), dp(10), dp(24), dp(10));
        cleanModeResultView.setVisibility(View.GONE);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        params.setMargins(dp(16), dp(28), dp(16), 0);
        root.addView(cleanModeResultView, params);
    }

    private void buildCleanModeLightingView() {
        cleanModeLightingView.setTextSize(18f);
        cleanModeLightingView.setTypeface(Typeface.DEFAULT_BOLD);
        cleanModeLightingView.setShadowLayer(6f, 1f, 1f, Color.BLACK);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#B0000000"));
        bg.setCornerRadius(dp(12));
        cleanModeLightingView.setBackground(bg);
        cleanModeLightingView.setPadding(dp(18), dp(10), dp(18), dp(10));
        cleanModeLightingView.setVisibility(View.GONE);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.START);
        params.setMargins(dp(16), 0, dp(16), dp(24));
        root.addView(cleanModeLightingView, params);
    }

    private void buildTapListener() {
        uiContainer.setOnClickListener(v -> toggleUiVisibility());
        root.setOnClickListener(v -> toggleUiVisibility());
        cleanModeResultView.setOnClickListener(v -> toggleUiVisibility());
        cleanModeLightingView.setOnClickListener(v -> toggleUiVisibility());
    }

    public void toggleUiVisibility() {
        if (collectionActive) {
            setUiVisible(true);
            return;
        }
        setUiVisible(uiContainer.getVisibility() != View.VISIBLE);
    }

    public void setUiVisible(boolean visible) {
        uiContainer.setVisibility(visible ? View.VISIBLE : View.GONE);
        updateCleanModeResultVisibility();
        updateCleanModeLightingVisibility();
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
            return;
        }
        long now = android.os.SystemClock.elapsedRealtime();
        if (lighting.condition == lastLightingCondition && (now - lastLightingUiUpdateMs < 100L)) {
            updateCleanModeLightingVisibility();
            return;
        }
        lastLightingUiUpdateMs = now;
        lastLightingCondition = lighting.condition;

        String title = "[LIGHT] " + lighting.condition.label;
        String faceLabel = lighting.hasFace ? "Face" : "Center";
        String detail = String.format(Locale.US,
                "\nRGB %s:%.0f  Bg:%.0f (%.1fx, Sat:%.1f%%)\nIR Mean:%.0f (Sat:%.1f%%)",
                faceLabel, lighting.rgbFaceMean, lighting.rgbBgMean, lighting.rgbRatio, lighting.rgbSatPct,
                lighting.irFullMean, lighting.irSatPct);

        SpannableString spannable = new SpannableString(title + detail);
        spannable.setSpan(new ForegroundColorSpan(lighting.condition.color), 0, title.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        spannable.setSpan(new ForegroundColorSpan(Color.WHITE), title.length(), spannable.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        cleanModeLightingView.setText(spannable);
        updateCleanModeLightingVisibility();
    }

    public void clearCleanModeLighting() {
        this.currentLightingResult = null;
        this.lastLightingCondition = null;
        cleanModeLightingView.setText("");
        updateCleanModeLightingVisibility();
    }

    private void updateCleanModeLightingVisibility() {
        boolean show = uiContainer.getVisibility() != View.VISIBLE
                && lightingTestEnabled
                && currentLightingResult != null;
        cleanModeLightingView.setVisibility(show ? View.VISIBLE : View.GONE);
        if (show) root.bringChildToFront(cleanModeLightingView);
    }

    public void showCleanModeResult(SlotClassificationResult slotResult) {
        if (slotResult == null) {
            clearCleanModeResult();
            return;
        }
        if (slotResult.hasPairedResults()) {
            ClassificationResult rgb = slotResult.rgbResult;
            ClassificationResult ir = slotResult.irResult;
            String rgbText = rgb != null ? formatResult(rgb) : "-";
            String irText = ir != null ? formatResult(ir) : "-";
            int rgbColor = (rgb != null && ClassificationResult.shouldHighlightFaceInGreen(rgb.topIndex))
                    ? Color.rgb(0, 230, 118) : Color.rgb(255, 82, 82);
            int irColor = (ir != null && ClassificationResult.shouldHighlightFaceInGreen(ir.topIndex))
                    ? Color.rgb(64, 196, 255) : Color.rgb(255, 82, 82);

            String fullStr = "RGB: " + rgbText + "   IR: " + irText;
            SpannableString spannable = new SpannableString(fullStr);
            int rgbEnd = ("RGB: " + rgbText).length();
            spannable.setSpan(new ForegroundColorSpan(rgbColor), 0, rgbEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            int irStart = fullStr.indexOf("IR: ");
            if (irStart >= 0) {
                spannable.setSpan(new ForegroundColorSpan(irColor), irStart, fullStr.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            currentCleanResultText = spannable;
        } else {
            ClassificationResult primary = slotResult.primaryResult();
            if (primary == null) {
                clearCleanModeResult();
                return;
            }
            int color = ClassificationResult.shouldHighlightFaceInGreen(primary.topIndex)
                ? Color.rgb(0, 230, 118) : Color.rgb(255, 82, 82);
            String text = formatResult(primary);
            SpannableString spannable = new SpannableString(text);
            spannable.setSpan(new ForegroundColorSpan(color), 0, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            currentCleanResultText = spannable;
        }
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

    private static String formatResult(ClassificationResult result) {
        return String.format(Locale.US, "%s %.1f%%",
                ClassificationResult.displayLabel(result.topIndex), result.probabilities[result.topIndex] * 100f);
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

    public void setHighQualityOnly(boolean highQualityOnly) {
        this.highQualityOnly = highQualityOnly;
        updateHighQualityOnlyButton();
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

    public void enterCalibrationMode() {
        overlay.clearResult();
        clearCleanModeResult();
        overlay.setCalibrationMode(true);
        performance.setVisibility(View.GONE);
        status.setVisibility(View.GONE);
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
        overlay.setCalibrationMode(false);
        overlay.setGuideOnlyMode(false);
        overlay.clearResult();
        clearCleanModeResult();
        calibrationInstruction.setVisibility(View.GONE);
        calibrationConfirm.setVisibility(View.GONE);
        calibrationCancel.setVisibility(View.GONE);
        performance.setVisibility(View.VISIBLE);
        status.setText(normalStatusMessage);
        status.setVisibility(View.VISIBLE);
        resultsLabel.setVisibility(View.VISIBLE);
        controlsLayout.setVisibility(View.VISIBLE);
        calibrationHotspot.setVisibility(View.VISIBLE);
        settingsHotspot.setVisibility(View.VISIBLE);
    }

    public void enterRecognitionEnrollmentMode() {
        recognitionEnrollmentMode = true;
        setUiVisible(true);
        overlay.clearResult();
        clearCleanModeResult();
        overlay.setCalibrationMode(true);
        overlay.setGuideOnlyMode(true);
        performance.setVisibility(View.GONE);
        status.setVisibility(View.GONE);
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
        status.setVisibility(visibility);
        resultsLabel.setVisibility(visibility);
        irCropContainer.setVisibility(visibility);
        controlsLayout.setVisibility(visibility);
        calibrationHotspot.setVisibility(visibility);
        settingsHotspot.setVisibility(visibility);
    }

    public void setAuthMode(boolean authMode) {
        int visibility = authMode ? View.GONE : View.VISIBLE;
        performance.setVisibility(visibility);
        status.setVisibility(visibility);
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
        switchButton.setEnabled(!collecting);
        highQualityOnlyContainer.setEnabled(!collecting);
        highQualityOnlyButton.setEnabled(!collecting);
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

    public void setPreviewFace(Bitmap bitmap) {
        Bitmap previous = currentPreviewFace;
        currentPreviewFace = bitmap;
        faceCropView.setScaleX(-1f);
        faceCropView.setImageBitmap(bitmap);
        if (previous != null && previous != bitmap && !previous.isRecycled()) previous.recycle();
    }

    public void clearPreviewFace() {
        faceCropView.setImageDrawable(null);
        if (currentPreviewFace != null && !currentPreviewFace.isRecycled()) currentPreviewFace.recycle();
        currentPreviewFace = null;
    }

    private void toggleCollectionClassMenu() {
        if (expandableLayout.getVisibility() == View.GONE) {
            expandableLayout.setVisibility(View.VISIBLE);
            startCollectionButton.setText("CANCEL");
        } else {
            expandableLayout.setVisibility(View.GONE);
            startCollectionButton.setText("START CAPTURE");
        }
    }

    private TextView label(float size) {
        TextView view = new TextView(activity);
        view.setTextColor(Color.WHITE);
        view.setTextSize(size);
        view.setShadowLayer(5f, 1f, 1f, Color.BLACK);
        return view;
    }

    private ImageButton iconButton(int imageResource, int backgroundColor) {
        ImageButton button = new ImageButton(activity);
        button.setImageResource(imageResource);
        button.setColorFilter(Color.WHITE);
        button.setBackgroundTintList(android.content.res.ColorStateList.valueOf(backgroundColor));
        button.setScaleType(ImageView.ScaleType.CENTER);
        button.setPadding(dp(21), dp(21), dp(21), dp(21));
        return button;
    }

    private void updateHighQualityOnlyButton() {
        if (highQualityOnlyButton == null) return;
        highQualityOnlyButton.setText("HIGH QUALITY");
        highQualityOnlyButton.setTextColor(Color.WHITE);
        highQualityOnlyButton.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        highQualityOnlyButton.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                Color.parseColor("#C49A00")));
        highQualityOnlyButton.setCompoundDrawablesWithIntrinsicBounds(highQualityOnly
                ? android.R.drawable.checkbox_on_background
                : android.R.drawable.checkbox_off_background, 0, 0, 0);
    }

    private static FrameLayout.LayoutParams match() {
        return new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT);
    }

    private static FrameLayout.LayoutParams wrap(int gravity, int horizontalMargin, int verticalMargin) {
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, gravity);
        params.setMargins(horizontalMargin, verticalMargin, horizontalMargin, verticalMargin);
        return params;
    }

    private int dp(int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }

    public interface Listener {
        void onPauseCollection();
        void onCancelCollection();
        void onStartAttackLiveCapture();
        void onStopAttackLiveCapture();
        void onHighQualityOnlyChanged(boolean highQualityOnly);
        void onStartCollection(String className);
        void onSwitchPreview();
        void onToggleModel();
        void onToggleIrAutoExposure();
        void onCalibrationConfirm();
        void onCalibrationCancel();
        void onRecognitionEnrollmentStart();
        void onCalibrationTap();
        void onSettingsTap();
    }
}
