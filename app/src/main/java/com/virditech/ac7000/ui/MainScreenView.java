package com.virditech.ac7000.ui;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.TextureView;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
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
    public final Button startCollectionButton;
    public final ImageButton pauseCollectionButton;
    public final ImageButton cancelCollectionButton;
    public final FrameLayout highQualityOnlyContainer;
    public final CheckBox highQualityOnlyButton;
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
        root.setBackgroundColor(Color.BLACK);
        rgbView = new TextureView(activity);
        irView = new TextureView(activity);
        irView.setAlpha(0f);
        overlay = new OverlayView(activity);
        root.addView(rgbView, match());
        root.addView(irView, match());
        root.addView(overlay, match());

        loadingSpinner = new ProgressBar(activity);
        loadingSpinner.setIndeterminate(true);
        root.addView(loadingSpinner, wrap(Gravity.CENTER, 0, 0));

        int buttonWidth = activity.getResources().getDisplayMetrics().widthPixels / 3;

        LinearLayout diagnosticsLayout = new LinearLayout(activity);
        diagnosticsLayout.setOrientation(LinearLayout.VERTICAL);
        diagnosticsLayout.setGravity(Gravity.START | Gravity.BOTTOM);
        FrameLayout.LayoutParams diagnosticsParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.START);
        diagnosticsParams.setMargins(dp(16), dp(16), buttonWidth + dp(32), dp(16));

        performance = label(22f);
        diagnosticsLayout.addView(performance, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        resultsLabel = label(32f);
        resultsLabel.setTextColor(Color.WHITE);
        resultsLabel.setShadowLayer(5f, 1f, 1f, Color.BLACK);
        FrameLayout.LayoutParams resultsParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.START);
        resultsParams.setMargins(dp(16), dp(16), buttonWidth + dp(16), dp(16));
        root.addView(resultsLabel, resultsParams);

        status = label(22f);
        status.setText("Initializing...");
        diagnosticsLayout.addView(status, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        root.addView(diagnosticsLayout, diagnosticsParams);

        irCropContainer = new FrameLayout(activity);
        FrameLayout.LayoutParams irCropParams = wrap(Gravity.TOP | Gravity.END, 0, 0);
        irCropParams.width = buttonWidth;
        irCropParams.height = buttonWidth;
        root.addView(irCropContainer, irCropParams);

        faceCropView = new ImageView(activity);
        faceCropView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        faceCropView.setBackgroundColor(Color.parseColor("#44000000"));
        irCropContainer.addView(faceCropView, match());

        noFaceLabel = label(20f);
        noFaceLabel.setText("NO FACE");
        noFaceLabel.setVisibility(View.GONE);
        irCropContainer.addView(noFaceLabel, wrap(Gravity.CENTER, 0, 0));

        irLoadingSpinner = new ProgressBar(activity);
        irLoadingSpinner.setIndeterminate(true);
        irCropContainer.addView(irLoadingSpinner, wrap(Gravity.CENTER, 0, 0));

        controlsLayout = new LinearLayout(activity);
        controlsLayout.setOrientation(LinearLayout.VERTICAL);
        controlsLayout.setGravity(Gravity.END | Gravity.BOTTOM);

        collectionProgress = label(32f);
        collectionProgress.setText("");
        collectionProgress.setGravity(Gravity.CENTER);
        collectionProgress.setVisibility(View.GONE);
        FrameLayout.LayoutParams collectionProgressParams =
                wrap(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, 16, 16);
        root.addView(collectionProgress, collectionProgressParams);

        pauseCollectionButton = iconButton(android.R.drawable.ic_media_pause, Color.parseColor("#37474F"));
        pauseCollectionButton.setContentDescription("Pause capture");
        pauseCollectionButton.setVisibility(View.GONE);
        pauseCollectionButton.setOnClickListener(v -> listener.onPauseCollection());
        FrameLayout.LayoutParams pauseCollectionParams =
                wrap(Gravity.TOP | Gravity.CENTER_HORIZONTAL, 16, 16);
        pauseCollectionParams.width = dp(84);
        pauseCollectionParams.height = dp(84);
        root.addView(pauseCollectionButton, pauseCollectionParams);

        cancelCollectionButton = iconButton(android.R.drawable.ic_menu_close_clear_cancel,
                Color.parseColor("#C49A00"));
        cancelCollectionButton.setContentDescription("Cancel capture");
        cancelCollectionButton.setVisibility(View.GONE);
        cancelCollectionButton.setOnClickListener(v -> listener.onCancelCollection());
        FrameLayout.LayoutParams cancelCollectionParams =
                wrap(Gravity.TOP | Gravity.END, 16, 16);
        cancelCollectionParams.width = dp(84);
        cancelCollectionParams.height = dp(84);
        root.addView(cancelCollectionButton, cancelCollectionParams);

        expandableLayout = new LinearLayout(activity);
        expandableLayout.setOrientation(LinearLayout.VERTICAL);
        expandableLayout.setGravity(Gravity.END);
        expandableLayout.setVisibility(View.GONE);

        highQualityOnlyContainer = new FrameLayout(activity);
        GradientDrawable highQualityBackground = new GradientDrawable();
        highQualityBackground.setColor(Color.parseColor("#C49A00"));
        highQualityBackground.setCornerRadius(0f);
        highQualityOnlyContainer.setBackground(highQualityBackground);

        highQualityOnlyButton = new CheckBox(activity);
        highQualityOnlyButton.setGravity(Gravity.CENTER);
        highQualityOnlyButton.setMinHeight(dp(48));
        highQualityOnlyButton.setMinimumHeight(dp(48));
        highQualityOnlyButton.setIncludeFontPadding(false);
        highQualityOnlyButton.setPadding(0, 0, 0, 0);
        updateHighQualityOnlyButton();
        highQualityOnlyButton.setOnClickListener(v -> {
            highQualityOnly = highQualityOnlyButton.isChecked();
            updateHighQualityOnlyButton();
            listener.onHighQualityOnlyChanged(highQualityOnly);
        });
        highQualityOnlyContainer.setOnClickListener(v -> highQualityOnlyButton.performClick());
        highQualityOnlyContainer.addView(highQualityOnlyButton, match());
        LinearLayout.LayoutParams highQualityLp = new LinearLayout.LayoutParams(buttonWidth, dp(48));
        highQualityLp.bottomMargin = dp(4);
        expandableLayout.addView(highQualityOnlyContainer, highQualityLp);

        String[] classes = {"live", "display", "picture", "print", "mask", "pmask"};
        for (String c : classes) {
            Button btn = new Button(activity);
            btn.setText(c);
            btn.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#37474F")));
            btn.setTextColor(Color.WHITE);
            btn.setOnClickListener(v -> {
                listener.onStartCollection(c);
                expandableLayout.setVisibility(View.GONE);
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    buttonWidth, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = dp(4);
            expandableLayout.addView(btn, lp);
        }
        controlsLayout.addView(expandableLayout);

        startCollectionButton = new Button(activity);
        startCollectionButton.setText("START CAPTURE");
        startCollectionButton.setEnabled(false);
        startCollectionButton.setOnClickListener(v -> toggleCollectionClassMenu());
        controlsLayout.addView(startCollectionButton, new LinearLayout.LayoutParams(
                buttonWidth, LinearLayout.LayoutParams.WRAP_CONTENT));

        switchButton = new Button(activity);
        switchButton.setText("SHOW IR");
        switchButton.setEnabled(false);
        switchButton.setOnClickListener(v -> listener.onSwitchPreview());
        controlsLayout.addView(switchButton, new LinearLayout.LayoutParams(
                buttonWidth, LinearLayout.LayoutParams.WRAP_CONTENT));

        modelSwitchButton = new Button(activity);
        modelSwitchButton.setText("MODEL 1");
        modelSwitchButton.setEnabled(false);
        modelSwitchButton.setOnClickListener(v -> listener.onToggleModel());
        controlsLayout.addView(modelSwitchButton, new LinearLayout.LayoutParams(
                buttonWidth, LinearLayout.LayoutParams.WRAP_CONTENT));

        root.addView(controlsLayout, wrap(Gravity.BOTTOM | Gravity.END, 16, 16));

        calibrationInstruction = label(24f);
        calibrationInstruction.setText("Fit one face inside the guide, then press CONFIRM");
        calibrationInstruction.setGravity(Gravity.CENTER);
        calibrationInstruction.setVisibility(View.GONE);
        root.addView(calibrationInstruction, wrap(Gravity.TOP | Gravity.CENTER_HORIZONTAL, 16, 24));

        calibrationConfirm = new Button(activity);
        calibrationConfirm.setText("CONFIRM");
        calibrationConfirm.setVisibility(View.GONE);
        calibrationConfirm.setOnClickListener(v -> listener.onCalibrationConfirm());
        FrameLayout.LayoutParams confirmParams = wrap(Gravity.BOTTOM | Gravity.END, 16, 16);
        confirmParams.width = buttonWidth;
        root.addView(calibrationConfirm, confirmParams);

        calibrationCancel = new Button(activity);
        calibrationCancel.setText("CANCEL");
        calibrationCancel.setVisibility(View.GONE);
        calibrationCancel.setOnClickListener(v -> listener.onCalibrationCancel());
        FrameLayout.LayoutParams cancelParams = wrap(Gravity.BOTTOM | Gravity.START, 16, 16);
        cancelParams.width = buttonWidth;
        root.addView(calibrationCancel, cancelParams);

        calibrationHotspot = new View(activity);
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
        highQualityOnlyButton.setChecked(highQualityOnly);
        highQualityOnlyButton.setTextColor(Color.WHITE);
        highQualityOnlyButton.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        highQualityOnlyButton.setBackgroundColor(Color.TRANSPARENT);
        highQualityOnlyButton.setButtonTintList(android.content.res.ColorStateList.valueOf(Color.WHITE));
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
        void onHighQualityOnlyChanged(boolean highQualityOnly);
        void onStartCollection(String className);
        void onSwitchPreview();
        void onToggleModel();
        void onCalibrationConfirm();
        void onCalibrationCancel();
        void onCalibrationTap();
    }
}
