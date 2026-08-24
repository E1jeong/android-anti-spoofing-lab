package com.virditech.ac7000.recognition;

/** Pure admission policy for the independent face-recognition experiment. */
public final class RecognitionPolicy {
    private RecognitionPolicy() {}

    public static boolean shouldSchedule(boolean enrollmentRequested,
                                         boolean recognitionModeEnabled,
                                         boolean modelReady,
                                         int enrolledCount) {
        if (enrolledCount < 0) {
            throw new IllegalArgumentException("enrolledCount must not be negative");
        }
        if (!modelReady) return false;
        return enrollmentRequested || (recognitionModeEnabled && enrolledCount > 0);
    }
}
