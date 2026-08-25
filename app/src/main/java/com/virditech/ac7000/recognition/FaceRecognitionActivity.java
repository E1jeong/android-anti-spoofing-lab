package com.virditech.ac7000.recognition;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.text.DateFormat;
import java.util.Date;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Template management screen. Camera enrollment is intentionally delegated back to MainActivity. */
public final class FaceRecognitionActivity extends Activity {
    public static final String EXTRA_MODEL_ASSET_PATH = "modelAssetPath";
    public static final String EXTRA_MODEL_CHECKSUM = "modelChecksum";
    public static final String EXTRA_RECOGNITION_ENABLED = "recognitionEnabled";
    public static final String EXTRA_DELEGATE_TYPE = "delegateType";
    public static final String EXTRA_ENROLL_NAME = "enrollName";
    public static final int RESULT_ENROLL_REQUESTED = RESULT_FIRST_USER;
    public static final int RESULT_APPLY_SETTINGS = RESULT_FIRST_USER + 1;

    private final ExecutorService databaseExecutor = Executors.newSingleThreadExecutor();
    private FaceTemplateRepository repository;
    private String modelAssetPath;
    private String modelChecksum;
    private String selectedModelAssetPath;
    private String selectedModelChecksum;
    private FaceEmbeddingModel.DelegateType selectedDelegate;
    private boolean selectedRecognitionEnabled;
    private TextView summary;
    private LinearLayout templateList;
    private final List<View> actionControls = new ArrayList<>();
    private final List<View> templateActionControls = new ArrayList<>();
    private boolean databaseWriteInProgress;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        modelAssetPath = getIntent().getStringExtra(EXTRA_MODEL_ASSET_PATH);
        if (modelAssetPath == null || modelAssetPath.isEmpty()) modelAssetPath = FaceEmbeddingModel.DEFAULT_MODEL_PATH;
        modelChecksum = getIntent().getStringExtra(EXTRA_MODEL_CHECKSUM);
        if (modelChecksum == null || modelChecksum.isEmpty()) {
            finish();
            return;
        }
        selectedModelAssetPath = modelAssetPath;
        selectedModelChecksum = modelChecksum;
        selectedRecognitionEnabled = getIntent().getBooleanExtra(EXTRA_RECOGNITION_ENABLED, false);
        selectedDelegate = parseDelegate(getIntent().getStringExtra(EXTRA_DELEGATE_TYPE));
        repository = new FaceTemplateRepository(getApplicationContext());
        setContentView(createContentView());
        loadSelectedModelTemplates();
    }

    private View createContentView() {
        int pad = dp(20);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, pad);
        root.setBackgroundColor(Color.rgb(20, 20, 20));

        TextView title = text("FACE MANAGEMENT", 24f);
        root.addView(title);
        TextView model = text("Model: " + modelLabel(selectedModelAssetPath), 15f);
        root.addView(model);

        Button recognitionMode = new Button(this);
        updateRecognitionModeText(recognitionMode);
        recognitionMode.setOnClickListener(view -> {
            selectedRecognitionEnabled = !selectedRecognitionEnabled;
            updateRecognitionModeText(recognitionMode);
        });
        actionControls.add(recognitionMode);
        root.addView(recognitionMode, fullWidthParams());

        Button delegate = new Button(this);
        updateDelegateText(delegate);
        delegate.setOnClickListener(view -> {
            selectedDelegate = selectedDelegate == FaceEmbeddingModel.DelegateType.CPU
                    ? FaceEmbeddingModel.DelegateType.NNAPI
                    : FaceEmbeddingModel.DelegateType.CPU;
            updateDelegateText(delegate);
        });
        actionControls.add(delegate);
        root.addView(delegate, fullWidthParams());

        Button modelSwitch = new Button(this);
        updateModelText(modelSwitch);
        modelSwitch.setOnClickListener(view -> {
            selectedModelAssetPath = nextModelPath(selectedModelAssetPath);
            selectedModelChecksum = null;
            updateModelText(modelSwitch);
            model.setText("Model: " + modelLabel(selectedModelAssetPath));
            loadSelectedModelTemplates();
        });
        actionControls.add(modelSwitch);
        root.addView(modelSwitch, fullWidthParams());

        summary = text("Loading templates...", 16f);
        LinearLayout.LayoutParams summaryParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        summaryParams.topMargin = dp(16);
        root.addView(summary, summaryParams);

        Button enroll = new Button(this);
        enroll.setText("ENROLL CURRENT FACE");
        enroll.setOnClickListener(view -> requestEnrollment());
        actionControls.add(enroll);
        root.addView(enroll, fullWidthParams());

        Button fixedInput = new Button(this);
        fixedInput.setText("FIXED-INPUT RECOG TEST");
        fixedInput.setOnClickListener(view -> {
            Intent intent = new Intent(this, FixedInputRecognitionActivity.class);
            intent.putExtra(FixedInputRecognitionActivity.EXTRA_MODEL_ASSET_PATH, modelAssetPath);
            startActivity(intent);
        });
        actionControls.add(fixedInput);
        root.addView(fixedInput, fullWidthParams());

        Button clear = new Button(this);
        clear.setText("CLEAR CURRENT MODEL TEMPLATES");
        clear.setTextColor(Color.WHITE);
        clear.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                Color.rgb(183, 28, 28)));
        clear.setOnClickListener(view -> confirmClear());
        actionControls.add(clear);
        root.addView(clear, fullWidthParams());

        ScrollView scroll = new ScrollView(this);
        templateList = new LinearLayout(this);
        templateList.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(templateList);
        LinearLayout.LayoutParams listParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        listParams.topMargin = dp(12);
        root.addView(scroll, listParams);

        Button close = new Button(this);
        close.setText("CLOSE");
        close.setOnClickListener(view -> finishWithSettings());
        actionControls.add(close);
        root.addView(close, fullWidthParams());
        return root;
    }

    private void requestEnrollment() {
        EditText input = new EditText(this);
        input.setHint("Name");
        input.setSingleLine();
        new AlertDialog.Builder(this)
                .setTitle("Enroll current face")
                .setMessage("The camera screen will reopen and collect 5 frames.")
                .setView(input)
                .setNegativeButton("CANCEL", null)
                .setPositiveButton("START", (dialog, which) -> {
                    String name = input.getText().toString().trim();
                    if (name.isEmpty()) return;
                    Intent result = new Intent();
                    result.putExtra(EXTRA_ENROLL_NAME, name);
                    putSelectedSettings(result);
                    setResult(RESULT_ENROLL_REQUESTED, result);
                    finish();
                })
                .show();
    }

    private void finishWithSettings() {
        Intent result = new Intent();
        putSelectedSettings(result);
        setResult(RESULT_APPLY_SETTINGS, result);
        finish();
    }

    private void putSelectedSettings(Intent result) {
        result.putExtra(EXTRA_MODEL_ASSET_PATH, selectedModelAssetPath);
        result.putExtra(EXTRA_DELEGATE_TYPE, selectedDelegate.name());
        result.putExtra(EXTRA_RECOGNITION_ENABLED, selectedRecognitionEnabled);
    }

    private FaceEmbeddingModel.DelegateType parseDelegate(String value) {
        if (FaceEmbeddingModel.DelegateType.NNAPI.name().equals(value)) {
            return FaceEmbeddingModel.DelegateType.NNAPI;
        }
        return FaceEmbeddingModel.DelegateType.CPU;
    }

    private void updateRecognitionModeText(Button button) {
        button.setText("FACE RECOGNITION: " + (selectedRecognitionEnabled ? "ON" : "OFF"));
    }

    private void updateDelegateText(Button button) {
        button.setText("RECOG DELEGATE: " + selectedDelegate.name());
    }

    private void updateModelText(Button button) {
        button.setText("RECOG MODEL: " + modelLabel(selectedModelAssetPath));
    }

    private String modelLabel(String modelPath) {
        if (FaceEmbeddingModel.MODEL_NPU_INT8.equals(modelPath)) return "W600K INT8";
        if (FaceEmbeddingModel.MODEL_FLOAT16.equals(modelPath)) return "W600K FP16";
        if (FaceEmbeddingModel.MODEL_FLOAT32.equals(modelPath)) return "W600K FP32";
        if (FaceEmbeddingModel.MODEL_RESEARCH_MOBILENETV4.equals(modelPath)) return "MobileNetV4 FP32";
        if (FaceEmbeddingModel.MODEL_RESEARCH_MOBILENETV4_INT8.equals(modelPath)) return "MobileNetV4 INT8";
        return modelPath;
    }

    private String nextModelPath(String modelPath) {
        if (FaceEmbeddingModel.MODEL_NPU_INT8.equals(modelPath)) return FaceEmbeddingModel.MODEL_FLOAT16;
        if (FaceEmbeddingModel.MODEL_FLOAT16.equals(modelPath)) return FaceEmbeddingModel.MODEL_FLOAT32;
        if (FaceEmbeddingModel.MODEL_FLOAT32.equals(modelPath)) return FaceEmbeddingModel.MODEL_RESEARCH_MOBILENETV4;
        if (FaceEmbeddingModel.MODEL_RESEARCH_MOBILENETV4.equals(modelPath)) {
            return FaceEmbeddingModel.MODEL_RESEARCH_MOBILENETV4_INT8;
        }
        return FaceEmbeddingModel.MODEL_NPU_INT8;
    }

    private void confirmClear() {
        new AlertDialog.Builder(this)
                .setTitle("Clear templates?")
                .setMessage("Only templates registered with the current model will be removed.")
                .setNegativeButton("CANCEL", null)
                .setPositiveButton("CLEAR", (dialog, which) -> deleteAll())
                .show();
    }

    private void loadSelectedModelTemplates() {
        String requestedModelPath = selectedModelAssetPath;
        summary.setText("Loading templates...");
        templateList.removeAllViews();
        templateActionControls.clear();
        databaseExecutor.execute(() -> {
            String requestedModelChecksum;
            try {
                requestedModelChecksum = FaceModelFingerprint.sha256(
                        getApplicationContext(), requestedModelPath);
            } catch (Exception e) {
                requestedModelChecksum = null;
            }
            List<FaceTemplate> templates = requestedModelChecksum == null
                    ? null : repository.loadForModel(requestedModelPath, requestedModelChecksum);
            String checksum = requestedModelChecksum;
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()
                        || !requestedModelPath.equals(selectedModelAssetPath)) return;
                selectedModelChecksum = checksum;
                if (templates == null) {
                    summary.setText("Failed to load templates");
                    return;
                }
                showTemplates(templates);
            });
        });
    }

    private void showTemplates(List<FaceTemplate> templates) {
        if (isFinishing() || isDestroyed()) return;
        summary.setText("Registered faces: " + templates.size());
        templateList.removeAllViews();
        templateActionControls.clear();
        if (templates.isEmpty()) {
            templateList.addView(text("등록된 얼굴 데이터가 없습니다.", 16f));
            return;
        }
        for (FaceTemplate template : templates) addTemplateRow(template);
    }

    private void addTemplateRow(FaceTemplate template) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(8), 0, dp(8));
        TextView label = text(template.getName() + "\n" + template.getSampleCount() + " frames · "
                + DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                .format(new Date(template.getEnrolledAtMs())), 16f);
        row.addView(label, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        Button delete = new Button(this);
        delete.setText("DELETE");
        delete.setOnClickListener(view -> confirmDelete(template));
        templateActionControls.add(delete);
        row.addView(delete);
        templateList.addView(row);
    }

    private void confirmDelete(FaceTemplate template) {
        new AlertDialog.Builder(this)
                .setTitle("Delete " + template.getName() + "?")
                .setNegativeButton("CANCEL", null)
                .setPositiveButton("DELETE", (dialog, which) -> {
                    runDatabaseWrite(() -> repository.delete(template.getId()));
                })
                .show();
    }

    private void deleteAll() {
        if (selectedModelChecksum == null) {
            summary.setText("Loading templates...");
            return;
        }
        String selectedPath = selectedModelAssetPath;
        String selectedChecksum = selectedModelChecksum;
        runDatabaseWrite(() -> repository.deleteAllForModel(
                selectedPath, selectedChecksum));
    }

    private void runDatabaseWrite(Runnable write) {
        if (databaseWriteInProgress) return;
        databaseWriteInProgress = true;
        setActionControlsEnabled(false);
        databaseExecutor.execute(() -> {
            List<FaceTemplate> templates = null;
            RuntimeException error = null;
            try {
                write.run();
                templates = repository.loadForModel(selectedModelAssetPath, selectedModelChecksum);
            } catch (RuntimeException e) {
                error = e;
            }
            List<FaceTemplate> result = templates;
            RuntimeException failure = error;
            runOnUiThread(() -> {
                databaseWriteInProgress = false;
                setActionControlsEnabled(true);
                if (failure != null) {
                    summary.setText("Template update failed");
                    return;
                }
                setResult(RESULT_OK);
                showTemplates(result);
            });
        });
    }

    private void setActionControlsEnabled(boolean enabled) {
        for (View control : actionControls) control.setEnabled(enabled);
        for (View control : templateActionControls) control.setEnabled(enabled);
    }

    private TextView text(String value, float size) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(Color.WHITE);
        return view;
    }

    private LinearLayout.LayoutParams fullWidthParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(8);
        return params;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onDestroy() {
        databaseExecutor.shutdown();
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (!databaseWriteInProgress) super.onBackPressed();
    }
}
