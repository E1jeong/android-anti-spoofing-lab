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
    public static final String EXTRA_MODEL_LABEL = "modelLabel";
    public static final String EXTRA_RECOGNITION_ENABLED = "recognitionEnabled";
    public static final String EXTRA_DELEGATE_LABEL = "delegateLabel";
    public static final String EXTRA_ENROLL_NAME = "enrollName";
    public static final int RESULT_ENROLL_REQUESTED = RESULT_FIRST_USER;
    public static final int RESULT_TOGGLE_RECOGNITION = RESULT_FIRST_USER + 1;
    public static final int RESULT_TOGGLE_DELEGATE = RESULT_FIRST_USER + 2;
    public static final int RESULT_TOGGLE_MODEL = RESULT_FIRST_USER + 3;

    private final ExecutorService databaseExecutor = Executors.newSingleThreadExecutor();
    private FaceTemplateRepository repository;
    private String modelAssetPath;
    private String modelChecksum;
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
        repository = new FaceTemplateRepository(getApplicationContext());
        setContentView(createContentView());
        loadTemplates();
    }

    private View createContentView() {
        int pad = dp(20);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, pad);
        root.setBackgroundColor(Color.rgb(20, 20, 20));

        TextView title = text("FACE MANAGEMENT", 24f);
        root.addView(title);
        String modelLabel = getIntent().getStringExtra(EXTRA_MODEL_LABEL);
        TextView model = text("Model: " + (modelLabel == null ? modelAssetPath : modelLabel), 15f);
        root.addView(model);

        boolean recognitionEnabled = getIntent().getBooleanExtra(EXTRA_RECOGNITION_ENABLED, false);
        Button recognitionMode = new Button(this);
        recognitionMode.setText("FACE RECOGNITION: " + (recognitionEnabled ? "ON" : "OFF"));
        recognitionMode.setOnClickListener(view -> finishWithResult(RESULT_TOGGLE_RECOGNITION));
        actionControls.add(recognitionMode);
        root.addView(recognitionMode, fullWidthParams());

        Button delegate = new Button(this);
        String delegateLabel = getIntent().getStringExtra(EXTRA_DELEGATE_LABEL);
        delegate.setText("RECOG DELEGATE: " + (delegateLabel == null ? "N/A" : delegateLabel));
        delegate.setOnClickListener(view -> finishWithResult(RESULT_TOGGLE_DELEGATE));
        actionControls.add(delegate);
        root.addView(delegate, fullWidthParams());

        Button modelSwitch = new Button(this);
        modelSwitch.setText("RECOG MODEL: " + (modelLabel == null ? modelAssetPath : modelLabel));
        modelSwitch.setOnClickListener(view -> finishWithResult(RESULT_TOGGLE_MODEL));
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
        close.setOnClickListener(view -> finish());
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
                    setResult(RESULT_ENROLL_REQUESTED, result);
                    finish();
                })
                .show();
    }

    private void finishWithResult(int resultCode) {
        setResult(resultCode);
        finish();
    }

    private void confirmClear() {
        new AlertDialog.Builder(this)
                .setTitle("Clear templates?")
                .setMessage("Only templates registered with the current model will be removed.")
                .setNegativeButton("CANCEL", null)
                .setPositiveButton("CLEAR", (dialog, which) -> deleteAll())
                .show();
    }

    private void loadTemplates() {
        databaseExecutor.execute(() -> {
            List<FaceTemplate> templates = repository.loadForModel(modelAssetPath, modelChecksum);
            runOnUiThread(() -> showTemplates(templates));
        });
    }

    private void showTemplates(List<FaceTemplate> templates) {
        if (isFinishing() || isDestroyed()) return;
        summary.setText("Registered faces: " + templates.size());
        templateList.removeAllViews();
        templateActionControls.clear();
        if (templates.isEmpty()) {
            templateList.addView(text("No templates for this model.", 16f));
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
        runDatabaseWrite(() -> repository.deleteAllForModel(modelAssetPath, modelChecksum));
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
                templates = repository.loadForModel(modelAssetPath, modelChecksum);
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
