package com.virditech.ac7000.recognition;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Isolated developer screen for camera-free fixed-input embedding diagnostics. */
@SuppressLint("SetTextI18n")
public final class FixedInputRecognitionActivity extends Activity {
    public static final String EXTRA_MODEL_ASSET_PATH = "modelAssetPath";

    private final ExecutorService runnerExecutor = Executors.newSingleThreadExecutor();
    private TextView status;
    private Button closeButton;
    private volatile boolean runFinished;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(createContentView());

        String modelAssetPath = getIntent().getStringExtra(EXTRA_MODEL_ASSET_PATH);
        if (modelAssetPath == null || modelAssetPath.isEmpty()) {
            modelAssetPath = FaceEmbeddingModel.DEFAULT_MODEL_PATH;
        }
        String selectedModel = modelAssetPath;
        status.setText("Running fixed-input recognition test...\n\nModel: " + selectedModel
                + "\nInput: " + FixedInputRecognitionRunner.resolveInputDirectory().getAbsolutePath()
                + "\nRepeats: " + FixedInputRecognitionRunner.REPEAT_COUNT
                + "\n\nCPU and NNAPI run sequentially. Cameras remain stopped during this test.");
        runnerExecutor.execute(() -> {
            FixedInputRecognitionRunner.Result result =
                    FixedInputRecognitionRunner.run(getApplicationContext(), selectedModel);
            runOnUiThread(() -> showResult(result));
        });
    }

    private LinearLayout createContentView() {
        int padding = Math.round(24f * getResources().getDisplayMetrics().density);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setGravity(Gravity.CENTER_HORIZONTAL);
        layout.setPadding(padding, padding, padding, padding);
        layout.setBackgroundColor(Color.rgb(20, 20, 20));

        status = new TextView(this);
        status.setTextColor(Color.WHITE);
        status.setTextSize(18f);
        status.setGravity(Gravity.START);
        layout.addView(status, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        closeButton = new Button(this);
        closeButton.setText("RUNNING...");
        closeButton.setEnabled(false);
        closeButton.setOnClickListener(view -> finish());
        layout.addView(closeButton, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return layout;
    }

    private void showResult(FixedInputRecognitionRunner.Result result) {
        if (isFinishing() || isDestroyed()) return;
        String output = result.outputFile == null ? "not written" : result.outputFile.getAbsolutePath();
        status.setText((result.completed ? "COMPLETE" : "FAILED") + "\n\n"
                + result.message + "\n\nResult: " + output
                + "\n\nNNAPI active: " + result.nnapiActive);
        runFinished = true;
        closeButton.setText("CLOSE");
        closeButton.setEnabled(true);
    }

    @Override
    public void onBackPressed() {
        if (runFinished) super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        runnerExecutor.shutdownNow();
        super.onDestroy();
    }
}
