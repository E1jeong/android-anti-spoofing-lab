package com.virditech.ac7000.evaluation;

import org.json.JSONObject;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.*;

public class EvaluationRecorderTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    private EvaluationRecorder.Listener listener(AtomicBoolean complete, CountDownLatch finished) {
        return new EvaluationRecorder.Listener() {
            @Override public void onStarted() {}
            @Override public void onFinished(boolean success, String message) {
                complete.set(success);
                finished.countDown();
            }
        };
    }

    @Test public void stopDrainsAcceptedFramesAndWritesCompletionMarker() throws Exception {
        File dir = new File(temporary.getRoot(), "drain");
        AtomicBoolean complete = new AtomicBoolean();
        CountDownLatch finished = new CountDownLatch(1);
        EvaluationRecorder recorder = new EvaluationRecorder(dir, EvaluationLogTest.conditions(),
                JSONObject::new, listener(complete, finished));
        for (int id = 1; id <= 10; id++) {
            recorder.record(EvaluationLogTest.frame(id, 0.9f, 0), false, null, 0.85f);
        }
        recorder.stop("operator_stop");
        assertTrue(finished.await(5, TimeUnit.SECONDS));
        assertTrue(complete.get());
        assertEquals(10, Files.readAllLines(dir.toPath().resolve("frames.jsonl")).size());
        JSONObject marker = new JSONObject(new String(Files.readAllBytes(
                dir.toPath().resolve("completion.json")), StandardCharsets.UTF_8));
        assertTrue(marker.getBoolean("complete"));
        assertEquals("operator_stop", marker.getString("stop_reason"));
    }

    @Test public void overflowIsAnExplicitIncompleteSession() throws Exception {
        File dir = new File(temporary.getRoot(), "overflow");
        CountDownLatch releaseMetadata = new CountDownLatch(1);
        CountDownLatch finished = new CountDownLatch(1);
        AtomicBoolean complete = new AtomicBoolean(true);
        EvaluationRecorder recorder = new EvaluationRecorder(dir, EvaluationLogTest.conditions(),
                () -> {
                    if (!releaseMetadata.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("timeout");
                    return new JSONObject();
                }, listener(complete, finished));
        try {
            for (int i = 0; i < 129; i++) recorder.reset("reset");
            assertFalse(recorder.isRecording());
        } finally {
            releaseMetadata.countDown();
        }
        assertTrue(finished.await(5, TimeUnit.SECONDS));
        assertFalse(complete.get());
        JSONObject marker = new JSONObject(new String(Files.readAllBytes(
                dir.toPath().resolve("completion.json")), StandardCharsets.UTF_8));
        assertEquals("queue_overflow", marker.getString("error"));
    }

    @Test public void refusesExistingSessionWithoutOverwritingFiles() throws Exception {
        File dir = temporary.newFolder("existing");
        File marker = new File(dir, "completion.json");
        Files.write(marker.toPath(), "original".getBytes(StandardCharsets.UTF_8));
        CountDownLatch finished = new CountDownLatch(1);
        AtomicBoolean complete = new AtomicBoolean(true);
        new EvaluationRecorder(dir, EvaluationLogTest.conditions(), JSONObject::new, listener(complete, finished));
        assertTrue(finished.await(5, TimeUnit.SECONDS));
        assertFalse(complete.get());
        assertEquals("original", new String(Files.readAllBytes(marker.toPath()), StandardCharsets.UTF_8));
    }

    @Test public void metadataFailureStopsRecordingWithoutInventingSuccess() throws Exception {
        File dir = new File(temporary.getRoot(), "failure");
        CountDownLatch finished = new CountDownLatch(1);
        AtomicBoolean complete = new AtomicBoolean(true);
        EvaluationRecorder recorder = new EvaluationRecorder(dir, EvaluationLogTest.conditions(),
                () -> { throw new java.io.IOException("asset unavailable"); }, listener(complete, finished));
        assertTrue(finished.await(5, TimeUnit.SECONDS));
        assertFalse(complete.get());
        assertFalse(recorder.isRecording());
        assertFalse(new File(dir, "completion.json").exists());
    }
}
