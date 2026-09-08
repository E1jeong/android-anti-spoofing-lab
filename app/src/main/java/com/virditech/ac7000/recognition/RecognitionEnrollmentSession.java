package com.virditech.ac7000.recognition;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Owns one explicit multi-frame enrollment request and its invalidation state. */
public final class RecognitionEnrollmentSession {
    private final List<float[]> embeddings = new ArrayList<>();
    private boolean requested;
    private boolean uiActive;
    private String enrollmentId;
    private String enrollmentName;

    public synchronized void prepare(String id, String name) {
        if (id == null || id.isEmpty()) throw new IllegalArgumentException("id is required");
        if (name == null || name.trim().isEmpty()) throw new IllegalArgumentException("name is required");
        requested = false;
        embeddings.clear();
        enrollmentId = id;
        enrollmentName = name;
        uiActive = true;
    }

    public synchronized boolean start() {
        if (!uiActive || enrollmentId == null || enrollmentName == null) return false;
        embeddings.clear();
        requested = true;
        return true;
    }

    public synchronized Progress add(float[] embedding, int targetCount) {
        if (!requested) return Progress.NOT_ACCEPTED;
        if (embedding == null || embedding.length == 0) {
            throw new IllegalArgumentException("embedding is required");
        }
        if (targetCount <= 0) throw new IllegalArgumentException("targetCount must be positive");
        embeddings.add(embedding);
        int collected = embeddings.size();
        if (collected < targetCount) return new Progress(true, collected, Collections.emptyList());
        List<float[]> completed = new ArrayList<>(embeddings);
        return new Progress(true, collected, completed);
    }

    public synchronized void finishCompletedAttempt() {
        if (!requested) return;
        embeddings.clear();
        requested = false;
    }

    public synchronized boolean cancel() {
        boolean wasUiActive = uiActive;
        requested = false;
        uiActive = false;
        embeddings.clear();
        enrollmentId = null;
        enrollmentName = null;
        return wasUiActive;
    }

    public synchronized boolean cancelRequest() {
        boolean wasRequested = requested;
        requested = false;
        embeddings.clear();
        return wasRequested;
    }

    public synchronized void exitUi() {
        uiActive = false;
    }

    public synchronized boolean isRequested() { return requested; }
    public synchronized boolean isUiActive() { return uiActive; }
    public synchronized String getEnrollmentId() { return enrollmentId; }
    public synchronized String getEnrollmentName() { return enrollmentName; }

    public static final class Progress {
        private static final Progress NOT_ACCEPTED =
                new Progress(false, 0, Collections.emptyList());
        public final boolean accepted;
        public final int collectedCount;
        public final List<float[]> completedEmbeddings;

        private Progress(boolean accepted, int collectedCount, List<float[]> completedEmbeddings) {
            this.accepted = accepted;
            this.collectedCount = collectedCount;
            this.completedEmbeddings = completedEmbeddings;
        }

        public boolean isComplete() { return !completedEmbeddings.isEmpty(); }
    }
}
