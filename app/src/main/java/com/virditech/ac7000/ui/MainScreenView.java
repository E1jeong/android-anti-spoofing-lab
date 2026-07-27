package com.virditech.ac7000.ui;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
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

public final class MainScreenView {
    public final FrameLayout root;
    public final TextureView rgbView;
    public final TextureView irView;
    public final OverlayView overlay;
    public final ProgressBar loadingSpinner;
    public final ProgressBar irLoadingSpinner;
    public final TextView performance;
    public final TextView status;
    public final FrameLayout irCropContainer;
    public final ImageView faceCropView;
    public final TextView noFaceLabel;
    public final TextView resultsLabel;
    public final TextView calibrationInstruction;
    public final Button switchButton;
    public final Button modelSwitchButton;
    public final Button detectorSwitchButton;
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
    public final LinearLayout expandableLayout;

    private final Activity activity;
    private Bitmap currentPreviewFace;
    private boolean highQualityOnly;

    public MainScreenView(Activity activity, Listener listener) {
        this.activity = activity;
        root = new FrameLayout(activity);
        rgbView = new TextureView(activity);
        irView = new TextureView(activity);
        overlay = new OverlayView(activity);
        loadingSpinner = new ProgressBar(activity);
        performance = label(22f);
        resultsLabel = label(32f);
        status = label(22f);
        irCropContainer = new FrameLayout(activity);
        faceCropView = new ImageView(activity);
        noFaceLabel = label(20f);
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
        detectorSwitchButton = new Button(activity);
        calibrationInstruction = label(24f);
        calibrationConfirm = new Button(activity);
        calibrationCancel = new Button(activity);
        calibrationHotspot = new View(activity);

        int buttonWidth = activity.getResources().getDisplayMetrics().widthPixels / 3;
        buildPreview();
        buildDiagnostics(buttonWidth);
        buildIrCrop(buttonWidth);
        buildCaptureIndicators(listener);
        buildControls(listener, buttonWidth);
        buildCalibrationControls(listener, buttonWidth);
    }

    private void buildPreview() {
        root.setBackgroundColor(Color.BLACK);
        irView.setAlpha(0f);
        root.addView(rgbView, match());
        root.addView(irView, match());
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
        root.addView(resultsLabel, resultsParams);
        root.addView(overlay, match());

        loadingSpinner.setIndeterminate(true);
        root.addView(loadingSpinner, wrap(Gravity.CENTER, 0, 0));

        status.setText("Initializing...");
        diagnosticsLayout.addView(status, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        root.addView(diagnosticsLayout, diagnosticsParams);
    }

    private void buildIrCrop(int buttonWidth) {
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
    }

    private void buildCaptureIndicators(Listener listener) {
        collectionProgress.setText("");
        collectionProgress.setGravity(Gravity.CENTER);
        collectionProgress.setVisibility(View.GONE);
        FrameLayout.LayoutParams collectionProgressParams =
                wrap(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, 16, 16);
        root.addView(collectionProgress, collectionProgressParams);

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
        root.addView(button, params);
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
        configureControlButton(detectorSwitchButton, "DETECTOR: FACEME", buttonWidth,
                v -> listener.onToggleDetector());

        root.addView(controlsLayout, wrap(Gravity.BOTTOM | Gravity.END, 16, 16));
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

        String[] classes = {"live", "display", "picture", "print", "mask", "pmask"};
        for (String className : classes) {
            Button button = new Button(activity);
            button.setText(className);
            button.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                    Color.parseColor("#37474F")));
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
        params.bottomMargin = dp(4);
        return params;
    }

    private void configureControlButton(
            Button button, String text, int buttonWidth, View.OnClickListener listener) {
        button.setText(text);
        button.setEnabled(false);
        button.setOnClickListener(listener);
        controlsLayout.addView(button, new LinearLayout.LayoutParams(
                buttonWidth, LinearLayout.LayoutParams.WRAP_CONTENT));
    }

    private void buildCalibrationControls(Listener listener, int buttonWidth) {
        calibrationInstruction.setText("Fit one face inside the guide, then press CONFIRM");
        calibrationInstruction.setGravity(Gravity.CENTER);
        calibrationInstruction.setVisibility(View.GONE);
        root.addView(calibrationInstruction, wrap(Gravity.TOP | Gravity.CENTER_HORIZONTAL, 16, 24));

        calibrationConfirm.setText("CONFIRM");
        calibrationConfirm.setVisibility(View.GONE);
        calibrationConfirm.setOnClickListener(v -> listener.onCalibrationConfirm());
        FrameLayout.LayoutParams confirmParams = wrap(Gravity.BOTTOM | Gravity.END, 16, 16);
        confirmParams.width = buttonWidth;
        root.addView(calibrationConfirm, confirmParams);

        calibrationCancel.setText("CANCEL");
        calibrationCancel.setVisibility(View.GONE);
        calibrationCancel.setOnClickListener(v -> listener.onCalibrationCancel());
        FrameLayout.LayoutParams cancelParams = wrap(Gravity.BOTTOM | Gravity.START, 16, 16);
        cancelParams.width = buttonWidth;
        root.addView(calibrationCancel, cancelParams);

        calibrationHotspot.setOnClickListener(v -> listener.onCalibrationTap());
        root.addView(calibrationHotspot, new FrameLayout.LayoutParams(
                dp(180), dp(180), Gravity.TOP | Gravity.START));
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

    public void enterCalibrationMode() {
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
    }

    public void exitCalibrationMode(String normalStatusMessage) {
        overlay.setCalibrationMode(false);
        overlay.clearResult();
        calibrationInstruction.setVisibility(View.GONE);
        calibrationConfirm.setVisibility(View.GONE);
        calibrationCancel.setVisibility(View.GONE);
        performance.setVisibility(View.VISIBLE);
        status.setText(normalStatusMessage);
        status.setVisibility(View.VISIBLE);
        resultsLabel.setVisibility(View.VISIBLE);
        controlsLayout.setVisibility(View.VISIBLE);
        calibrationHotspot.setVisibility(View.VISIBLE);
    }

    public void setCollectionChromeVisible(boolean visible) {
        int visibility = visible ? View.VISIBLE : View.GONE;
        performance.setVisibility(visibility);
        status.setVisibility(visibility);
        resultsLabel.setVisibility(visibility);
        irCropContainer.setVisibility(visibility);
        controlsLayout.setVisibility(visibility);
        calibrationHotspot.setVisibility(visibility);
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
        void onToggleDetector();
        void onCalibrationConfirm();
        void onCalibrationCancel();
        void onCalibrationTap();
    }
}
