package com.unionbiometrics.facedetection;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class YuNetPostprocessor {
    private static final int[] STRIDES = {8, 16, 32};
    private final int width;
    private final int height;
    final int candidateCount;
    private final float scoreThreshold;
    private final float nmsThreshold;
    private final int maxDetections;

    YuNetPostprocessor(int width, int height, float scoreThreshold, float nmsThreshold,
                       int maxDetections) {
        if (width <= 0 || height <= 0 || width % 32 != 0 || height % 32 != 0) {
            throw new IllegalArgumentException("YuNet dimensions must be positive multiples of 32");
        }
        if (!Float.isFinite(scoreThreshold) || scoreThreshold < 0 || scoreThreshold > 1
                || !Float.isFinite(nmsThreshold) || nmsThreshold < 0 || nmsThreshold > 1
                || maxDetections <= 0) {
            throw new IllegalArgumentException("Invalid YuNet postprocessing thresholds/count");
        }
        this.width = width;
        this.height = height;
        this.scoreThreshold = scoreThreshold;
        this.nmsThreshold = nmsThreshold;
        this.maxDetections = maxDetections;
        int count = 0;
        for (int stride : STRIDES) count += (width / stride) * (height / stride);
        candidateCount = count;
    }

    List<FaceDetection> decode(float[] logits, float[] boxes, Letterbox geometry) {
        if (logits.length != candidateCount * 2 || boxes.length != candidateCount * 4) {
            throw new IllegalArgumentException("YuNet requires separate [1,N,2] logits and [1,N,4] boxes");
        }
        ArrayList<FaceDetection> candidates = new ArrayList<>();
        int index = 0;
        for (int stride : STRIDES) {
            for (int y = 0; y < height / stride; y++) {
                for (int x = 0; x < width / stride; x++, index++) {
                    float classLogit = logits[index * 2];
                    float objectLogit = logits[index * 2 + 1];
                    int offset = index * 4;
                    if (!Float.isFinite(classLogit) || !Float.isFinite(objectLogit)
                            || !Float.isFinite(boxes[offset]) || !Float.isFinite(boxes[offset + 1])
                            || !Float.isFinite(boxes[offset + 2]) || !Float.isFinite(boxes[offset + 3])) {
                        throw new IllegalArgumentException("Non-finite YuNet prediction at " + index);
                    }
                    float score = sigmoid(classLogit) * sigmoid(objectLogit);
                    if (score < scoreThreshold) continue;
                    float centerX = (x + boxes[offset]) * stride;
                    float centerY = (y + boxes[offset + 1]) * stride;
                    float boxWidth = size(boxes[offset + 2], stride);
                    float boxHeight = size(boxes[offset + 3], stride);
                    FaceDetection box = new FaceDetection(clip(centerX - boxWidth / 2, width),
                            clip(centerY - boxHeight / 2, height), clip(centerX + boxWidth / 2, width),
                            clip(centerY + boxHeight / 2, height), score);
                    if (box.right > box.left && box.bottom > box.top) candidates.add(box);
                }
            }
        }
        candidates.sort((left, right) -> Float.compare(right.confidence, left.confidence));
        ArrayList<FaceDetection> selected = new ArrayList<>();
        ArrayList<FaceDetection> restored = new ArrayList<>();
        for (FaceDetection candidate : candidates) {
            boolean suppressed = false;
            for (FaceDetection previous : selected) {
                if (iou(candidate, previous) > nmsThreshold) {
                    suppressed = true;
                    break;
                }
            }
            if (suppressed) continue;
            selected.add(candidate);
            FaceDetection face = geometry.restore(candidate);
            if (face.right > face.left && face.bottom > face.top) restored.add(face);
            if (selected.size() == maxDetections) break;
        }
        return Collections.unmodifiableList(restored);
    }

    private static float sigmoid(float value) { return (float) (1 / (1 + Math.exp(-value))); }

    private static float size(float value, int stride) {
        return (float) Math.exp(Math.max(-16, Math.min(16, value))) * stride;
    }

    private static float clip(float value, int limit) { return Math.max(0, Math.min(limit, value)); }

    private static float iou(FaceDetection a, FaceDetection b) {
        float intersection = Math.max(0, Math.min(a.right, b.right) - Math.max(a.left, b.left))
                * Math.max(0, Math.min(a.bottom, b.bottom) - Math.max(a.top, b.top));
        float areaA = (a.right - a.left) * (a.bottom - a.top);
        float areaB = (b.right - b.left) * (b.bottom - b.top);
        return intersection / (areaA + areaB - intersection + 1e-7f);
    }
}
