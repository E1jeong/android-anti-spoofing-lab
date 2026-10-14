package com.virditech.ac7000.recognition;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.virditech.ac7000.databinding.ActivityFaceRecognitionBinding;
import com.virditech.ac7000.databinding.DialogEnrollmentNameBinding;
import com.virditech.ac7000.databinding.ItemFaceTemplateBinding;
import com.virditech.ac7000.databinding.ViewEmptyFaceTemplatesBinding;

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
    private List<RecognitionModelConfig> availableModels;
    private TextView summary;
    private LinearLayout templateList;
    private final List<View> actionControls = new ArrayList<>();
    private final List<View> templateActionControls = new ArrayList<>();
    private boolean databaseWriteInProgress;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        availableModels = RecognitionModelConfig.loadAll(getApplicationContext());
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
        ActivityFaceRecognitionBinding binding =
                ActivityFaceRecognitionBinding.inflate(getLayoutInflater());
        // XML text dimensions round to pixels; retain the original fractional SP sizes.
        binding.title.setTextSize(24f);
        TextView model = binding.model;
        model.setTextSize(15f);
        model.setText("Model: " + modelLabel(selectedModelAssetPath));

        Button recognitionMode = binding.recognitionMode;
        updateRecognitionModeText(recognitionMode);
        recognitionMode.setOnClickListener(view -> {
            selectedRecognitionEnabled = !selectedRecognitionEnabled;
            updateRecognitionModeText(recognitionMode);
        });
        actionControls.add(recognitionMode);

        Button modelSwitch = binding.modelSwitch;
        updateModelText(modelSwitch);
        modelSwitch.setEnabled(availableModels != null && availableModels.size() > 1);
        modelSwitch.setOnClickListener(view -> {
            if (availableModels == null || availableModels.size() <= 1) return;
            RecognitionModelConfig next = nextModelConfig(selectedModelAssetPath);
            if (next == null) return;
            selectedModelAssetPath = next.getModelPath();
            selectedDelegate = next.getDelegateType();
            selectedModelChecksum = null;
            updateModelText(modelSwitch);
            model.setText("Model: " + modelLabel(selectedModelAssetPath));
            loadSelectedModelTemplates();
        });
        actionControls.add(modelSwitch);

        summary = binding.summary;
        summary.setTextSize(16f);

        Button enroll = binding.enroll;
        enroll.setOnClickListener(view -> requestEnrollment());
        actionControls.add(enroll);

        Button fixedInput = binding.fixedInput;
        fixedInput.setOnClickListener(view -> {
            Intent intent = new Intent(this, FixedInputRecognitionActivity.class);
            intent.putExtra(FixedInputRecognitionActivity.EXTRA_MODEL_ASSET_PATH, selectedModelAssetPath);
            startActivity(intent);
        });
        actionControls.add(fixedInput);

        Button clear = binding.clear;
        clear.setOnClickListener(view -> confirmClear());
        actionControls.add(clear);

        templateList = binding.templateList;

        Button close = binding.close;
        close.setOnClickListener(view -> finishWithSettings());
        actionControls.add(close);
        return binding.getRoot();
    }

    private void requestEnrollment() {
        EditText input = DialogEnrollmentNameBinding.inflate(getLayoutInflater()).getRoot();
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

    private void updateModelText(Button button) {
        button.setText("RECOG MODEL: " + modelLabel(selectedModelAssetPath));
    }

    private String modelLabel(String modelPath) {
        if (availableModels != null) {
            for (RecognitionModelConfig config : availableModels) {
                if (config.getModelPath().equals(modelPath)) {
                    return config.getLabel();
                }
            }
        }
        if (FaceEmbeddingModel.MODEL_MOBILENET_EMORE_INT8.equals(modelPath)) {
            return "MobileNet Emore INT8";
        }
        return modelPath;
    }

    private RecognitionModelConfig nextModelConfig(String currentModelPath) {
        if (availableModels == null || availableModels.isEmpty()) return null;
        int currentIndex = -1;
        for (int i = 0; i < availableModels.size(); i++) {
            if (availableModels.get(i).getModelPath().equals(currentModelPath)) {
                currentIndex = i;
                break;
            }
        }
        int nextIndex = (currentIndex + 1) % availableModels.size();
        return availableModels.get(nextIndex);
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
            TextView empty = ViewEmptyFaceTemplatesBinding.inflate(
                    getLayoutInflater(), templateList, false).getRoot();
            empty.setTextSize(16f);
            templateList.addView(empty);
            return;
        }
        for (FaceTemplate template : templates) addTemplateRow(template);
    }

    private void addTemplateRow(FaceTemplate template) {
        ItemFaceTemplateBinding binding =
                ItemFaceTemplateBinding.inflate(getLayoutInflater(), templateList, false);
        binding.label.setTextSize(16f);
        binding.label.setText(template.getName() + "\n" + template.getSampleCount() + " frames · "
                + DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                .format(new Date(template.getEnrolledAtMs())));
        Button delete = binding.delete;
        delete.setOnClickListener(view -> confirmDelete(template));
        templateActionControls.add(delete);
        templateList.addView(binding.getRoot());
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
