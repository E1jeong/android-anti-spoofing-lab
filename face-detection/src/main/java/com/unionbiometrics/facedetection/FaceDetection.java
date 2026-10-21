package com.unionbiometrics.facedetection;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** One face in source-image coordinates, with no crop margin or display mirroring. */
public final class FaceDetection {
    public static final List<String> LANDMARK_NAMES = Collections.unmodifiableList(Arrays.asList(
            "eyeImageLeft", "eyeImageRight", "noseTip", "mouthImageLeft", "mouthImageRight"));
    public final float left;
    public final float top;
    public final float right;
    public final float bottom;
    public final float confidence;
    /** Five named points in original RGB pixels; empty for box-only v1. No visibility estimate. */
    public final List<Landmark> landmarks;

    FaceDetection(float left, float top, float right, float bottom, float confidence) {
        this(left, top, right, bottom, confidence, null);
    }

    FaceDetection(float left, float top, float right, float bottom, float confidence, float[] points) {
        this.left = left;
        this.top = top;
        this.right = right;
        this.bottom = bottom;
        this.confidence = confidence;
        if (points == null) {
            landmarks = Collections.emptyList();
        } else {
            if (points.length != 10) throw new IllegalArgumentException("Expected five XY landmarks");
            ArrayList<Landmark> named = new ArrayList<>(5);
            for (int i = 0; i < 5; i++) {
                if (!Float.isFinite(points[i * 2]) || !Float.isFinite(points[i * 2 + 1])) {
                    throw new IllegalArgumentException("Non-finite landmark coordinate");
                }
                named.add(new Landmark(LANDMARK_NAMES.get(i), points[i * 2], points[i * 2 + 1]));
            }
            landmarks = Collections.unmodifiableList(named);
        }
    }

    public static final class Landmark {
        public final String name;
        public final float x;
        public final float y;

        private Landmark(String name, float x, float y) {
            this.name = name;
            this.x = x;
            this.y = y;
        }
    }
}
