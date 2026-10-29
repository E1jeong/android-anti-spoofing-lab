package com.virditech.ac7000.evaluation;

import com.virditech.ac7000.model.AuthFrameAccumulator;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.file.Files;
import java.util.List;

import static org.junit.Assert.*;

public class EvaluationLogTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    static EvaluationConditions conditions() {
        return new EvaluationConditions(0, "S01", "", 5, 35, "indoor");
    }

    static JSONObject frame(long id, float live, int slot) throws Exception {
        float[] scores = new float[12];
        scores[0] = live;
        scores[1] = 1f - live;
        return new JSONObject().put("frame_id", id).put("timestamp_ms", 1234L)
                .put("model_slot", slot).put("detector", "FaceMe").put("ir_ae_profile", "FULL")
                .put("probabilities", new JSONArray(scores));
    }

    private List<String> lines(File dir, String name) throws Exception {
        return Files.readAllLines(dir.toPath().resolve(name));
    }

    @Test public void recordsExactAuthFramesAndExistingVerdict() throws Exception {
        File dir = new File(temporary.getRoot(), "auth");
        AuthFrameAccumulator accumulator = new AuthFrameAccumulator(5, 0.85f);
        AuthFrameAccumulator.Verdict verdict = null;
        try (EvaluationLog log = new EvaluationLog(dir, new JSONObject(), conditions())) {
            for (int id = 1; id <= 5; id++) {
                JSONObject frame = frame(id, 0.85f, 0);
                float[] scores = new float[12];
                scores[0] = 0.85f;
                scores[1] = 0.15f;
                verdict = accumulator.add(scores, id * 1_000_000L, id * 2_000_000L, id);
                log.frame(frame, true, verdict, 0.85f);
            }
        }
        assertNotNull(verdict);
        assertArrayEquals(new long[]{1, 2, 3, 4, 5}, verdict.frameIds);
        JSONObject trial = new JSONObject(lines(dir, "trials.jsonl").get(0));
        assertTrue(trial.getBoolean("valid"));
        assertEquals(verdict.live ? "LIVE" : "SPOOF", trial.getString("verdict"));
        assertEquals(verdict.averageLiveScore, trial.getDouble("average_live_score"), 0);
        assertEquals(verdict.elapsedMs, trial.getLong("auth_elapsed_ms"));
        assertEquals(5, trial.getJSONArray("frame_ids").length());
        assertEquals(5, trial.getJSONArray("live_scores").length());
        List<String> frames = lines(dir, "frames.jsonl");
        assertEquals(5, frames.size());
        for (String row : frames) {
            assertEquals(trial.getLong("trial_id"), new JSONObject(row).getLong("trial_id"));
        }
    }

    @Test public void resetAndStopKeepPartialTrialsExplicitlyInvalid() throws Exception {
        File dir = new File(temporary.getRoot(), "partial");
        try (EvaluationLog log = new EvaluationLog(dir, new JSONObject(), conditions())) {
            log.frame(frame(1, 0.9f, 0), true, null, 0.85f);
            log.reset("no_face");
            log.frame(frame(2, 0.1f, 0), true, null, 0.85f);
            log.reset("operator_stop");
        }
        List<String> trials = lines(dir, "trials.jsonl");
        assertEquals(2, trials.size());
        assertFalse(new JSONObject(trials.get(0)).getBoolean("valid"));
        assertEquals("no_face", new JSONObject(trials.get(0)).getString("invalid_reason"));
        assertEquals("operator_stop", new JSONObject(trials.get(1)).getString("invalid_reason"));
    }

    @Test public void mixedModelTrialCannotEnterAccuracyAnalysis() throws Exception {
        File dir = new File(temporary.getRoot(), "mixed");
        AuthFrameAccumulator accumulator = new AuthFrameAccumulator(2, 0.85f);
        try (EvaluationLog log = new EvaluationLog(dir, new JSONObject(), conditions())) {
            for (int id = 1; id <= 2; id++) {
                float[] scores = new float[12];
                scores[0] = 1;
                log.frame(frame(id, 1, id - 1), true,
                        accumulator.add(scores, id, id, id), 0.85f);
            }
        }
        JSONObject trial = new JSONObject(lines(dir, "trials.jsonl").get(0));
        assertFalse(trial.getBoolean("valid"));
        assertEquals("runtime_configuration_changed", trial.getString("invalid_reason"));
        assertEquals("LIVE", trial.getString("verdict"));
    }

    @Test public void rawFramesKeepAllTwelveScoresWithoutInventingAuthVerdicts() throws Exception {
        File dir = new File(temporary.getRoot(), "raw");
        try (EvaluationLog log = new EvaluationLog(dir, new JSONObject(), conditions())) {
            log.frame(frame(1, 0.2f, 0), false, null, 0.85f);
        }
        JSONObject frame = new JSONObject(lines(dir, "frames.jsonl").get(0));
        JSONObject trial = new JSONObject(lines(dir, "trials.jsonl").get(0));
        assertEquals(12, frame.getJSONArray("probabilities").length());
        assertEquals((double) 0.2f, frame.getJSONArray("probabilities").getDouble(0), 0);
        assertEquals("raw_frame", trial.getString("kind"));
        assertTrue(trial.isNull("verdict"));
    }

    @Test public void rejectsMalformedProbabilities() throws Exception {
        File dir = new File(temporary.getRoot(), "invalid");
        try (EvaluationLog log = new EvaluationLog(dir, new JSONObject(), conditions())) {
            assertThrows(IllegalArgumentException.class, () ->
                    log.frame(frame(1, 2, 0), false, null, 0.85f));
            assertThrows(IllegalArgumentException.class, () ->
                    log.frame(frame(2, 1, 0).put("probabilities", new JSONArray()), false, null, 0.85f));
        }
    }

    @Test public void inferenceFailureAndMissingAuthFramesRemainInvalid() throws Exception {
        File dir = new File(temporary.getRoot(), "errors");
        AuthFrameAccumulator accumulator = new AuthFrameAccumulator(2, 0.85f);
        float[] scores = new float[12];
        scores[0] = 1;
        accumulator.add(scores, 1, 1, 100);
        try (EvaluationLog log = new EvaluationLog(dir, new JSONObject(), conditions())) {
            log.inferenceError("NNAPI error", 123, 124);
            log.frame(frame(101, 1, 0), true, accumulator.add(scores, 2, 2, 101), 0.85f);
        }
        List<String> rows = lines(dir, "trials.jsonl");
        assertEquals(2, rows.size());
        JSONObject error = new JSONObject(rows.get(0));
        assertFalse(error.getBoolean("valid"));
        assertEquals("NNAPI error", error.getString("invalid_reason"));
        JSONObject mismatch = new JSONObject(rows.get(1));
        assertFalse(mismatch.getBoolean("valid"));
        assertEquals("auth_frame_mismatch", mismatch.getString("invalid_reason"));
        assertEquals(100, mismatch.getJSONArray("auth_frame_ids").getLong(0));
    }
}
