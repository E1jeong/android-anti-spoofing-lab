package com.virditech.ac7000.capture;

import java.io.File;

/** Owns the mutable state and transitions of one 100-sample collection session. */
public final class CaptureSession {
    private boolean active;
    private boolean ioBusy;
    private boolean paused;
    private int sessionId;
    private int count;
    private int stepIndex;
    private int stepCount;
    private long countdownEndMs;
    private long pausedCountdownMs;
    private int minQualityLevel;
    private String qualityMode;
    private File rawRoot;
    private int subjectId;
    private String className = "live";

    public synchronized void start(String className, String qualityMode, int subjectId,
                                   File rawRoot, int minQualityLevel, long nowMs) {
        if (className == null || className.isEmpty()) throw new IllegalArgumentException("className is required");
        if (subjectId <= 0) throw new IllegalArgumentException("subjectId must be positive");
        if (rawRoot == null) throw new IllegalArgumentException("rawRoot is required");
        this.className = className;
        this.qualityMode = qualityMode;
        this.subjectId = subjectId;
        this.rawRoot = rawRoot;
        this.minQualityLevel = minQualityLevel;
        count = 0;
        sessionId++;
        stepIndex = 0;
        stepCount = 0;
        paused = false;
        countdownEndMs = nowMs + CaptureSchedule.STEP_COUNTDOWN_MS;
        pausedCountdownMs = 0L;
        ioBusy = false;
        active = true;
    }

    public synchronized void finish() {
        stop();
    }

    public synchronized CancelledSession cancel() {
        if (!active) return null;
        CancelledSession cancelled = new CancelledSession(className, qualityMode,
                className + "_" + subjectId);
        stop();
        return cancelled;
    }

    private void stop() {
        active = false;
        paused = false;
        pausedCountdownMs = 0L;
        sessionId++;
        ioBusy = false;
    }

    public synchronized boolean togglePaused(long nowMs) {
        if (!active) return paused;
        if (paused) {
            countdownEndMs = nowMs + pausedCountdownMs;
            pausedCountdownMs = 0L;
            paused = false;
        } else {
            pausedCountdownMs = Math.max(0L, countdownEndMs - nowMs);
            paused = true;
        }
        return paused;
    }

    public synchronized SaveCandidate snapshotForSave() {
        if (!active || paused || ioBusy || count >= CaptureSchedule.TARGET_COUNT) return null;
        return new SaveCandidate(sessionId, className, qualityMode, subjectId, rawRoot,
                minQualityLevel);
    }

    public synchronized SavePermit beginSave(SaveCandidate candidate) {
        if (!isCurrent(candidate) || paused || ioBusy || count >= CaptureSchedule.TARGET_COUNT) {
            return null;
        }
        ioBusy = true;
        return new SavePermit(sessionId, className, qualityMode, subjectId, count + 1);
    }

    public synchronized SaveCommit commitSave(SavePermit permit, long nowMs) {
        if (!isActive(permit)) return SaveCommit.NOT_COMMITTED;
        count = permit.sampleIndex;
        CaptureStep step = CaptureSchedule.currentStep(stepIndex);
        stepCount++;
        boolean sectorCompleted = stepCount >= step.targetCount;
        if (sectorCompleted && count < CaptureSchedule.TARGET_COUNT) {
            stepIndex = Math.min(stepIndex + 1, CaptureSchedule.DEFAULT_STEPS.length - 1);
            stepCount = 0;
            countdownEndMs = nowMs + CaptureSchedule.STEP_COUNTDOWN_MS;
        }
        return new SaveCommit(true, sectorCompleted, count == CaptureSchedule.TARGET_COUNT);
    }

    public synchronized void releaseSave(SavePermit permit) {
        if (permit != null && permit.sessionId == sessionId) ioBusy = false;
    }

    public synchronized boolean isActive(SavePermit permit) {
        return permit != null && active && permit.sessionId == sessionId
                && permit.subjectId == subjectId && permit.className.equals(className);
    }

    private boolean isCurrent(SaveCandidate candidate) {
        return candidate != null && active && candidate.sessionId == sessionId
                && candidate.subjectId == subjectId && candidate.className.equals(className);
    }

    public synchronized boolean isActive() { return active; }
    public synchronized boolean isPaused() { return paused; }
    public synchronized boolean isIoBusy() { return ioBusy; }
    public synchronized int getCount() { return count; }
    public synchronized int getStepCount() { return stepCount; }
    public synchronized String getClassName() { return className; }
    public synchronized CaptureStep currentStep() { return CaptureSchedule.currentStep(stepIndex); }

    public synchronized int countdownSeconds(long nowMs) {
        if (paused) return CaptureSchedule.countdownSeconds(pausedCountdownMs, 0L);
        return CaptureSchedule.countdownSeconds(countdownEndMs, nowMs);
    }

    public static final class SaveCandidate {
        private final int sessionId;
        public final String className;
        public final String qualityMode;
        public final int subjectId;
        public final File rawRoot;
        public final int minQualityLevel;

        private SaveCandidate(int sessionId, String className, String qualityMode,
                              int subjectId, File rawRoot, int minQualityLevel) {
            this.sessionId = sessionId;
            this.className = className;
            this.qualityMode = qualityMode;
            this.subjectId = subjectId;
            this.rawRoot = rawRoot;
            this.minQualityLevel = minQualityLevel;
        }
    }

    public static final class SavePermit {
        public final int sessionId;
        public final String className;
        public final String qualityMode;
        public final int subjectId;
        public final int sampleIndex;

        private SavePermit(int sessionId, String className, String qualityMode,
                           int subjectId, int sampleIndex) {
            this.sessionId = sessionId;
            this.className = className;
            this.qualityMode = qualityMode;
            this.subjectId = subjectId;
            this.sampleIndex = sampleIndex;
        }
    }

    public static final class SaveCommit {
        private static final SaveCommit NOT_COMMITTED = new SaveCommit(false, false, false);
        public final boolean committed;
        public final boolean sectorCompleted;
        public final boolean collectionCompleted;

        private SaveCommit(boolean committed, boolean sectorCompleted, boolean collectionCompleted) {
            this.committed = committed;
            this.sectorCompleted = sectorCompleted;
            this.collectionCompleted = collectionCompleted;
        }
    }

    public static final class CancelledSession {
        public final String className;
        public final String qualityMode;
        public final String subjectDirName;

        private CancelledSession(String className, String qualityMode, String subjectDirName) {
            this.className = className;
            this.qualityMode = qualityMode;
            this.subjectDirName = subjectDirName;
        }
    }
}
