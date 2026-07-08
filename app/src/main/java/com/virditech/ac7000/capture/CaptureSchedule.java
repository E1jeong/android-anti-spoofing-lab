package com.virditech.ac7000.capture;

public final class CaptureSchedule {
    public static final long STEP_COUNTDOWN_MS = 5_000L;
    public static final CaptureStep[] DEFAULT_STEPS = {
            new CaptureStep(5, "CENTER", 20),
            new CaptureStep(1, "LEFT TOP", 10),
            new CaptureStep(2, "TOP", 10),
            new CaptureStep(3, "RIGHT TOP", 10),
            new CaptureStep(4, "LEFT", 10),
            new CaptureStep(6, "RIGHT", 10),
            new CaptureStep(7, "LEFT BOTTOM", 10),
            new CaptureStep(8, "BOTTOM", 10),
            new CaptureStep(9, "RIGHT BOTTOM", 10)
    };
    public static final int TARGET_COUNT = calculateTargetCount(DEFAULT_STEPS);

    private CaptureSchedule() {}

    public static CaptureStep currentStep(int index) {
        int boundedIndex = Math.max(0, Math.min(index, DEFAULT_STEPS.length - 1));
        return DEFAULT_STEPS[boundedIndex];
    }

    public static int countdownSeconds(long countdownEndMs, long nowMs) {
        long remainingMs = countdownEndMs - nowMs;
        return remainingMs > 0L ? (int) ((remainingMs + 999L) / 1000L) : 0;
    }

    public static boolean shouldCheckQuality(String className) {
        return "live".equals(className);
    }

    static int calculateTargetCount(CaptureStep[] steps) {
        int total = 0;
        for (CaptureStep step : steps) {
            total += step.targetCount;
        }
        return total;
    }
}
