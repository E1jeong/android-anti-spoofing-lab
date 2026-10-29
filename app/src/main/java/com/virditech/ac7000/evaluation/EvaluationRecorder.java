package com.virditech.ac7000.evaluation;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Build;
import android.provider.Settings;

import com.virditech.ac7000.BuildConfig;
import com.virditech.ac7000.model.AuthFrameAccumulator;
import com.virditech.ac7000.recognition.FaceModelFingerprint;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/** Bounded, nonblocking producer queue; metadata hashing and file I/O stay on its worker. */
public final class EvaluationRecorder {
    public interface MetadataSource { JSONObject load() throws Exception; }
    public interface Listener {
        void onStarted();
        void onFinished(boolean complete, String message);
    }
    private interface Action { void write(EvaluationLog log) throws Exception; }

    private final ArrayBlockingQueue<Action> queue = new ArrayBlockingQueue<>(128);
    private final AtomicLong frameIds = new AtomicLong();
    private volatile boolean accepting = true;
    private volatile String stopReason = "operator_stop";
    private volatile String failure;

    public EvaluationRecorder(File directory, EvaluationConditions conditions,
                              MetadataSource metadata, Listener listener) {
        Thread worker = new Thread(() -> run(directory, conditions, metadata, listener), "evaluation-log");
        worker.start();
    }

    public long nextFrameId() { return frameIds.incrementAndGet(); }
    public boolean isRecording() { return accepting; }

    public void record(JSONObject frame, boolean auth, AuthFrameAccumulator.Verdict verdict,
                       float threshold) {
        offer(log -> log.frame(frame, auth, verdict, threshold));
    }

    public void reset(String reason) { offer(log -> log.reset(reason)); }

    public void inferenceError(String reason, long rgbTimestampNs, long irTimestampNs) {
        offer(log -> log.inferenceError(reason, rgbTimestampNs, irTimestampNs));
    }

    private synchronized void offer(Action action) {
        if (!accepting) return;
        if (!queue.offer(action)) {
            failure = "queue_overflow";
            stop("queue_overflow");
        }
    }

    public synchronized void stop(String reason) {
        if (!accepting) return;
        stopReason = reason;
        accepting = false;
    }

    public synchronized void fail(String reason) {
        failure = reason;
        stop(reason);
    }

    private void run(File directory, EvaluationConditions conditions,
                     MetadataSource metadata, Listener listener) {
        EvaluationLog log = null;
        String error = null;
        try {
            Files.createDirectories(directory.getParentFile().toPath());
            log = new EvaluationLog(directory, metadata.load(), conditions);
            if (accepting) listener.onStarted();
            while (accepting || !queue.isEmpty()) {
                Action action = queue.poll(100, TimeUnit.MILLISECONDS);
                if (action != null) action.write(log);
            }
            log.reset(stopReason);
            error = failure;
        } catch (Exception e) {
            error = e.getClass().getSimpleName() + ": " + e.getMessage();
        } finally {
            accepting = false;
            queue.clear();
            if (log != null) {
                try { log.close(); } catch (Exception e) { error = "close_failed: " + e.getMessage(); }
            }
            try {
                if (log != null) {
                    JSONObject completion = new JSONObject().put("complete", error == null)
                            .put("stop_reason", stopReason).put("error", error == null ? JSONObject.NULL : error);
                    Files.write(directory.toPath().resolve("completion.json"),
                            completion.toString(2).getBytes(StandardCharsets.UTF_8));
                }
            } catch (Exception e) {
                error = "completion_write_failed: " + e.getMessage();
            }
            listener.onFinished(error == null, error == null ? directory.getAbsolutePath() : error);
        }
    }

    @SuppressLint("HardwareIds") // Local evaluation evidence must identify the tested device.
    public static JSONObject metadata(Context context, List<String> labels) throws Exception {
        JSONObject metadata = new JSONObject().put("started_at_ms", System.currentTimeMillis())
                .put("package_name", context.getPackageName()).put("version_name", BuildConfig.VERSION_NAME)
                .put("version_code", BuildConfig.VERSION_CODE).put("apk_sha256", apkHash(context))
                .put("app_commit", JSONObject.NULL).put("app_commit_unavailable_reason", "not_embedded_in_build")
                .put("device_model", Build.MODEL).put("android_sdk", Build.VERSION.SDK_INT)
                .put("device_id", Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID))
                .put("device_id_kind", "android_id").put("class_labels", new JSONArray(labels))
                .put("delegate_request", "NNAPI").put("nnapi_partition_evidence", JSONObject.NULL)
                .put("nnapi_partition_unavailable_reason", "requires_separate_logcat_capture")
                .put("source_images_saved", false).put("sdk_session_duration_ms", JSONObject.NULL)
                .put("source_image_hashes", JSONObject.NULL)
                .put("source_image_hashes_unavailable_reason", "images_not_saved_by_evaluation_logger")
                .put("sdk_session_duration_unavailable_reason", "lab_uses_inferFrame");
        JSONObject manifest;
        try (InputStream input = context.getAssets().open("ubio-vision/model_manifest.json")) {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int count;
            while ((count = input.read(buffer)) != -1) bytes.write(buffer, 0, count);
            manifest = new JSONObject(new String(bytes.toByteArray(), StandardCharsets.UTF_8));
        }
        JSONArray models = manifest.getJSONArray("models");
        JSONArray identities = new JSONArray();
        for (int i = 0; i < models.length(); i++) {
            JSONObject slot = models.getJSONObject(i);
            String model = slot.getString("model");
            String spec = slot.getString("spec");
            identities.put(new JSONObject().put("slot", i).put("label", "MODEL " + (i + 1))
                    .put("type", slot.getString("type")).put("model_file", model)
                    .put("model_sha256", FaceModelFingerprint.sha256(context, "ubio-vision/" + model))
                    .put("sidecar_file", spec)
                    .put("sidecar_sha256", FaceModelFingerprint.sha256(context, "ubio-vision/" + spec)));
        }
        return metadata.put("models", identities);
    }

    private static String apkHash(Context context) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = Files.newInputStream(new File(context.getApplicationInfo().sourceDir).toPath())) {
            byte[] buffer = new byte[64 * 1024];
            int count;
            while ((count = input.read(buffer)) != -1) digest.update(buffer, 0, count);
        }
        StringBuilder hex = new StringBuilder();
        for (byte b : digest.digest()) hex.append(String.format(Locale.US, "%02x", b & 0xff));
        return hex.toString();
    }
}
