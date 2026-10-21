package com.unionbiometrics.facedetection;

/** One face in source-image coordinates, with no crop margin or display mirroring. */
public final class FaceDetection {
    public final float left;
    public final float top;
    public final float right;
    public final float bottom;
    public final float confidence;

    FaceDetection(float left, float top, float right, float bottom, float confidence) {
        this.left = left;
        this.top = top;
        this.right = right;
        this.bottom = bottom;
        this.confidence = confidence;
    }
}
