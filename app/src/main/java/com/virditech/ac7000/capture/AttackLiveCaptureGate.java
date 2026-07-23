package com.virditech.ac7000.capture;

public final class AttackLiveCaptureGate {
    public static final float LIVE_THRESHOLD = 0.80f;

    private AttackLiveCaptureGate() {}

    public static boolean shouldSave(float[] probabilities) {
        return probabilities != null && probabilities.length > 0
                && probabilities[0] >= LIVE_THRESHOLD;
    }
}
