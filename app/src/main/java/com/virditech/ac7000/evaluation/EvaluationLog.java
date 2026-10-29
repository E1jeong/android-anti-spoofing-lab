package com.virditech.ac7000.evaluation;

import com.virditech.ac7000.model.AuthFrameAccumulator;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/** Single-writer JSONL frame/trial tables; no images or bitmaps are retained. */
final class EvaluationLog implements Closeable {
    private final BufferedWriter frames;
    private final BufferedWriter trials;
    private final JSONObject conditions;
    private final List<JSONObject> pending = new ArrayList<>();
    private long nextTrialId;

    EvaluationLog(File directory, JSONObject metadata, EvaluationConditions conditions)
            throws IOException, JSONException {
        Files.createDirectory(directory.toPath());
        this.conditions = conditions.toJson();
        metadata.put("schema_version", 1).put("session_id", directory.getName())
                .put("conditions", this.conditions);
        Files.write(directory.toPath().resolve("session.json"),
                metadata.toString(2).getBytes(StandardCharsets.UTF_8));
        frames = Files.newBufferedWriter(directory.toPath().resolve("frames.jsonl"), StandardCharsets.UTF_8);
        try {
            trials = Files.newBufferedWriter(directory.toPath().resolve("trials.jsonl"), StandardCharsets.UTF_8);
        } catch (IOException e) {
            frames.close();
            throw e;
        }
    }

    void frame(JSONObject frame, boolean auth, AuthFrameAccumulator.Verdict verdict, float threshold)
            throws IOException, JSONException {
        JSONArray scores = frame.getJSONArray("probabilities");
        if (scores.length() != 12) throw new IllegalArgumentException("Expected twelve probabilities");
        for (int i = 0; i < scores.length(); i++) {
            double value = scores.getDouble(i);
            if (!Double.isFinite(value) || value < 0 || value > 1) {
                throw new IllegalArgumentException("Invalid probability");
            }
            scores.put(i, value);
        }
        if (!auth) {
            reset("auth_mode_changed");
            pending.add(frame);
            finish("raw_frame", null, null, null);
            return;
        }
        pending.add(frame);
        if (verdict != null) {
            boolean idsMatch = verdict.frameIds.length == pending.size();
            for (int i = 0; idsMatch && i < pending.size(); i++) {
                idsMatch = verdict.frameIds[i] == pending.get(i).getLong("frame_id");
            }
            finish("auth", verdict, threshold, idsMatch ? null : "auth_frame_mismatch");
        }
    }

    void reset(String reason) throws IOException, JSONException {
        if (!pending.isEmpty()) finish("auth", null, null, reason);
    }

    void inferenceError(String reason, long rgbTimestampNs, long irTimestampNs)
            throws IOException, JSONException {
        line(trials, new JSONObject().put("trial_id", ++nextTrialId).put("kind", "inference_error")
                .put("conditions", conditions).put("timestamp_ms", System.currentTimeMillis())
                .put("valid", false).put("invalid_reason", reason).put("frame_ids", new JSONArray())
                .put("rgb_timestamp_ns", rgbTimestampNs).put("ir_timestamp_ns", irTimestampNs));
        trials.flush();
    }

    private void finish(String kind, AuthFrameAccumulator.Verdict verdict, Float threshold, String reason)
            throws IOException, JSONException {
        long trialId = ++nextTrialId;
        JSONArray ids = new JSONArray();
        JSONArray liveScores = new JSONArray();
        JSONObject first = pending.get(0);
        for (JSONObject frame : pending) {
            ids.put(frame.getLong("frame_id"));
            liveScores.put(frame.getJSONArray("probabilities").getDouble(0));
            if (reason == null && (!first.get("model_slot").equals(frame.get("model_slot"))
                    || !first.get("detector").equals(frame.get("detector"))
                    || !first.get("ir_ae_profile").equals(frame.get("ir_ae_profile")))) {
                reason = "runtime_configuration_changed";
            }
            frame.put("trial_id", trialId);
            line(frames, frame);
        }
        JSONObject trial = new JSONObject().put("trial_id", trialId).put("kind", kind)
                .put("conditions", conditions).put("frame_ids", ids).put("live_scores", liveScores)
                .put("auth_frame_ids", verdict == null ? JSONObject.NULL : new JSONArray(verdict.frameIds))
                .put("timestamp_ms", first.getLong("timestamp_ms"))
                .put("valid", reason == null).put("invalid_reason", reason == null ? JSONObject.NULL : reason)
                .put("live_threshold", threshold == null ? JSONObject.NULL : threshold.doubleValue())
                .put("average_live_score", verdict == null ? JSONObject.NULL : (double) verdict.averageLiveScore)
                .put("verdict", verdict == null ? JSONObject.NULL : (verdict.live ? "LIVE" : "SPOOF"))
                .put("auth_elapsed_ms", verdict == null ? JSONObject.NULL : verdict.elapsedMs);
        line(trials, trial);
        frames.flush();
        trials.flush();
        pending.clear();
    }

    private static void line(BufferedWriter writer, JSONObject value) throws IOException {
        writer.write(value.toString());
        writer.newLine();
    }

    @Override public void close() throws IOException {
        try {
            frames.close();
        } finally {
            trials.close();
        }
    }
}
