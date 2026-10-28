package com.virditech.ac7000.capture;

import java.io.File;

/** Owns the mutable state and transitions of one collection session. */
public final class CaptureSession {
    private static final CaptureStep NO_FACE_STEP =
            new CaptureStep(0, "NO FACE", CaptureConfig.NO_FACE_TARGET_COUNT);

    private boolean active;
    private boolean ioBusy;
    private boolean paused;
    private int sessionId;
    private int count;
    private int stepIndex;
    private int stepCount;
    private long countdownEndMs;
    private long pausedCountdownMs;
    private CaptureConfig config;
    private int minQualityLevel;
    private String qualityMode;
    private String environment = CaptureConfig.ENV_INDOOR;
    private File rawRoot;
    private int subjectId;
    private String className = "live";

    public synchronized void start(CaptureConfig config, int subjectId, File rawRoot, long nowMs) {
        if (config == null) throw new IllegalArgumentException("config is required");
        if (subjectId <= 0) throw new IllegalArgumentException("subjectId must be positive");
        if (rawRoot == null) throw new IllegalArgumentException("rawRoot is required");
        this.config = config;
        this.className = config.className;
        this.environment = config.environment;
        this.qualityMode = config.qualityMode;
        this.minQualityLevel = config.minQualityLevel;
        this.subjectId = subjectId;
        this.rawRoot = rawRoot;
        count = 0;
        sessionId++;
        stepIndex = 0;
        stepCount = 0;
        paused = false;
        countdownEndMs = nowMs + config.countdownMs();
        pausedCountdownMs = 0L;
        ioBusy = false;
        active = true;
    }


    public synchronized void finish() {
        stop();
    }

    public synchronized CancelledSession cancel() {
        if (!active) return null;
        CancelledSession cancelled = new CancelledSession(config, className, environment, qualityMode,
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
        if (!active || paused || ioBusy || count >= config.targetCount()) return null;
        return new SaveCandidate(sessionId, config, className, environment, qualityMode, subjectId, rawRoot,
                minQualityLevel);
    }

    public synchronized SavePermit beginSave(SaveCandidate candidate) {
        if (!isCurrent(candidate) || paused || ioBusy || count >= config.targetCount()) {
            return null;
        }
        ioBusy = true;
        return new SavePermit(sessionId, config, className, environment, qualityMode, subjectId, count + 1);
    }

    public synchronized SaveCommit commitSave(SavePermit permit, long nowMs) {
        if (!isActive(permit)) return SaveCommit.NOT_COMMITTED;
        count = permit.sampleIndex;
        if (config.isNoFace()) {
            stepCount = count;
            boolean completed = count == config.targetCount();
            return new SaveCommit(true, completed, completed);
        }
        CaptureStep step = CaptureSchedule.currentStep(stepIndex);
        stepCount++;
        boolean sectorCompleted = stepCount >= step.targetCount;
        if (sectorCompleted && count < config.targetCount()) {
            stepIndex = Math.min(stepIndex + 1, CaptureSchedule.DEFAULT_STEPS.length - 1);
            stepCount = 0;
            countdownEndMs = nowMs + CaptureSchedule.STEP_COUNTDOWN_MS;
        }
        return new SaveCommit(true, sectorCompleted, count == config.targetCount());
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
    public synchronized int getTargetCount() { return config.targetCount(); }
    public synchronized CaptureConfig getConfig() { return config; }
    public synchronized String getClassName() { return className; }
    public synchronized String getEnvironment() { return environment; }
    public synchronized String getQualityMode() { return qualityMode; }
    public synchronized CaptureStep currentStep() {
        return config.isNoFace() ? NO_FACE_STEP : CaptureSchedule.currentStep(stepIndex);
    }

    public synchronized int countdownSeconds(long nowMs) {
        if (paused) return CaptureSchedule.countdownSeconds(pausedCountdownMs, 0L);
        return CaptureSchedule.countdownSeconds(countdownEndMs, nowMs);
    }

    public static final class SaveCandidate {
        private final int sessionId;
        public final CaptureConfig config;
        public final String className;
        public final String environment;
        public final String qualityMode;
        public final int subjectId;
        public final File rawRoot;
        public final int minQualityLevel;

        private SaveCandidate(int sessionId, CaptureConfig config, String className, String environment,
                              String qualityMode, int subjectId, File rawRoot, int minQualityLevel) {
            this.sessionId = sessionId;
            this.config = config;
            this.className = className;
            this.environment = environment;
            this.qualityMode = qualityMode;
            this.subjectId = subjectId;
            this.rawRoot = rawRoot;
            this.minQualityLevel = minQualityLevel;
        }
    }

    public static final class SavePermit {
        public final int sessionId;
        public final CaptureConfig config;
        public final String className;
        public final String environment;
        public final String qualityMode;
        public final int subjectId;
        public final int sampleIndex;

        private SavePermit(int sessionId, CaptureConfig config, String className, String environment,
                           String qualityMode, int subjectId, int sampleIndex) {
            this.sessionId = sessionId;
            this.config = config;
            this.className = className;
            this.environment = environment;
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
        public final CaptureConfig config;
        public final String className;
        public final String environment;
        public final String qualityMode;
        public final String subjectDirName;

        private CancelledSession(CaptureConfig config, String className, String environment,
                                 String qualityMode, String subjectDirName) {
            this.config = config;
            this.className = className;
            this.environment = environment;
            this.qualityMode = qualityMode;
            this.subjectDirName = subjectDirName;
        }
    }
}
