package com.virditech.ac7000.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;

import com.virditech.ac7000.R;
import com.virditech.ac7000.capture.CaptureConfig;
import com.virditech.ac7000.databinding.DialogCaptureConfigBinding;

import java.util.function.Consumer;

import static com.virditech.ac7000.ui.MainScreenView.CLASS_OPTIONS;

public final class CaptureConfigDialog {
    private final Activity activity;
    private AlertDialog captureConfigDialog;

    public CaptureConfigDialog(Activity activity) {
        this.activity = activity;
    }

    public void show(Consumer<CaptureConfig> onStart) {
        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setTitle("Capture Configuration");

        DialogCaptureConfigBinding binding =
                DialogCaptureConfigBinding.inflate(activity.getLayoutInflater());
        Spinner classSpinner = binding.classSpinner;
        RadioGroup envRadioGroup = binding.environmentGroup;
        RadioButton outdoorRadio = binding.outdoor;
        LinearLayout qualityContainer = binding.qualityContainer;
        RadioButton mediumRadio = binding.medium;
        RadioButton highRadio = binding.high;
        RadioButton offRadio = binding.off;

        String[] displayNames = new String[CLASS_OPTIONS.length];
        for (int i = 0; i < CLASS_OPTIONS.length; i++) {
            displayNames[i] = CLASS_OPTIONS[i][0];
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<>(activity,
                R.layout.item_capture_class_selected, displayNames);
        adapter.setDropDownViewResource(R.layout.item_capture_class_option);
        classSpinner.setAdapter(adapter);
        builder.setView(binding.getRoot());

        // Dynamic State synchronization
        Runnable updateState = () -> {
            int selectedIdx = classSpinner.getSelectedItemPosition();
            if (selectedIdx < 0 || selectedIdx >= CLASS_OPTIONS.length) return;
            String className = CLASS_OPTIONS[selectedIdx][1];
            boolean isLive = "live".equals(className);
            boolean isOutdoor = outdoorRadio.isChecked();

            if (isLive) {
                qualityContainer.setVisibility(View.VISIBLE);
                if (isOutdoor) {
                    offRadio.setEnabled(true);
                    offRadio.setChecked(true);
                    highRadio.setEnabled(false);
                    mediumRadio.setEnabled(false);
                } else {
                    offRadio.setEnabled(false);
                    highRadio.setEnabled(true);
                    mediumRadio.setEnabled(true);
                    if (offRadio.isChecked()) {
                        mediumRadio.setChecked(true);
                    }
                }
            } else {
                qualityContainer.setVisibility(View.GONE);
            }
        };

        classSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                updateState.run();
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        envRadioGroup.setOnCheckedChangeListener((group, checkedId) -> updateState.run());

        updateState.run();

        builder.setPositiveButton("START", (dialog, which) -> {
            int selectedIdx = classSpinner.getSelectedItemPosition();
            if (selectedIdx < 0 || selectedIdx >= CLASS_OPTIONS.length) return;
            String className = CLASS_OPTIONS[selectedIdx][1];
            String env = outdoorRadio.isChecked() ? CaptureConfig.ENV_OUTDOOR : CaptureConfig.ENV_INDOOR;
            String quality = null;
            if ("live".equals(className)) {
                if (outdoorRadio.isChecked()) {
                    quality = CaptureConfig.QUALITY_OFF;
                } else if (highRadio.isChecked()) {
                    quality = CaptureConfig.QUALITY_HIGH;
                } else {
                    quality = CaptureConfig.QUALITY_MEDIUM;
                }
            }
            CaptureConfig config;
            try {
                config = CaptureConfig.create(className, env, quality);
            } catch (RuntimeException e) {
                android.util.Log.e("MainScreenView", "Invalid capture config", e);
                return;
            }
            onStart.accept(config);
        });

        builder.setNegativeButton("CANCEL", (dialog, which) -> dialog.dismiss());

        dismiss();
        captureConfigDialog = builder.create();
        captureConfigDialog.setOnDismissListener(d -> {
            if (captureConfigDialog == d) {
                captureConfigDialog = null;
            }
        });
        captureConfigDialog.show();
        int edgeMargin = activity.getResources().getDimensionPixelSize(R.dimen.capture_dialog_edge_margin);
        captureConfigDialog.getWindow().setLayout(
                activity.getResources().getDisplayMetrics().widthPixels - 2 * edgeMargin,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        int touchHeight = activity.getResources().getDimensionPixelSize(R.dimen.capture_touch_height);
        captureConfigDialog.getButton(AlertDialog.BUTTON_POSITIVE).setMinHeight(touchHeight);
        captureConfigDialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextSize(18f);
        captureConfigDialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(0xFF16794B);
        captureConfigDialog.getButton(AlertDialog.BUTTON_NEGATIVE).setMinHeight(touchHeight);
        captureConfigDialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextSize(18f);
        captureConfigDialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(0xFF16794B);
    }

    public void dismiss() {
        if (captureConfigDialog != null && captureConfigDialog.isShowing()) {
            captureConfigDialog.dismiss();
        }
        captureConfigDialog = null;
    }
}
