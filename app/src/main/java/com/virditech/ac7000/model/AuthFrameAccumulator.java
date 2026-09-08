package com.virditech.ac7000.model;

import java.util.ArrayList;
import java.util.List;

/** Accumulates a fixed number of anti-spoofing outputs into one auth verdict. */
public final class AuthFrameAccumulator {
    private final int targetFrameCount;
    private final float liveThreshold;
    private final List<float[]> probabilities = new ArrayList<>();
    private long startNs;

    public AuthFrameAccumulator(int targetFrameCount, float liveThreshold) {
        if (targetFrameCount <= 0) throw new IllegalArgumentException("targetFrameCount must be positive");
        if (!Float.isFinite(liveThreshold) || liveThreshold < 0f || liveThreshold > 1f) {
            throw new IllegalArgumentException("liveThreshold must be between 0 and 1");
        }
        this.targetFrameCount = targetFrameCount;
        this.liveThreshold = liveThreshold;
    }

    public synchronized Verdict add(float[] frameProbabilities, long receivedNs, long nowNs) {
        if (frameProbabilities == null || frameProbabilities.length < 2) {
            throw new IllegalArgumentException("At least LIVE and one spoof probability are required");
        }
        if (probabilities.isEmpty()) startNs = receivedNs;
        probabilities.add(frameProbabilities.clone());
        if (probabilities.size() < targetFrameCount) return null;

        int classCount = frameProbabilities.length;
        float[] sums = new float[classCount];
        for (float[] frame : probabilities) {
            for (int i = 0; i < classCount && i < frame.length; i++) sums[i] += frame[i];
        }
        int actualFrameCount = probabilities.size();
        float averageLive = sums[0] / actualFrameCount;
        int topSpoofIndex = 1;
        for (int i = 2; i < classCount; i++) {
            if (sums[i] > sums[topSpoofIndex]) topSpoofIndex = i;
        }
        Verdict verdict = new Verdict(averageLive >= liveThreshold, averageLive,
                topSpoofIndex, (nowNs - startNs) / 1_000_000L);
        reset();
        return verdict;
    }

    public synchronized void reset() {
        probabilities.clear();
        startNs = 0L;
    }

    public static final class Verdict {
        public final boolean live;
        public final float averageLiveScore;
        public final int topSpoofIndex;
        public final long elapsedMs;

        private Verdict(boolean live, float averageLiveScore, int topSpoofIndex,
                        long elapsedMs) {
            this.live = live;
            this.averageLiveScore = averageLiveScore;
            this.topSpoofIndex = topSpoofIndex;
            this.elapsedMs = elapsedMs;
        }
    }
}
