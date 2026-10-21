package com.virditech.ac7000.capture;

import com.unionbiometrics.vision.AntiSpoofingResult;

public final class AttackLiveCaptureGate {
    public static final float LIVE_THRESHOLD = 0.80f;

    private AttackLiveCaptureGate() {}

    public static boolean shouldSave(float[] probabilities) {
        return AntiSpoofingResult.liveProbability(probabilities) >= LIVE_THRESHOLD;
    }
}
