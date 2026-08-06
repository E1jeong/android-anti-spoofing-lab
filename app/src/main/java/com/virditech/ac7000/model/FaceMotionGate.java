package com.virditech.ac7000.model;

public final class FaceMotionGate {
    private static final double MAX_CENTER_SPEED_FACE_WIDTHS_PER_SECOND = 0.7;
    private static final int REQUIRED_STABLE_FRAMES = 2;

    private double previousCenterX;
    private double previousCenterY;
    private double previousFaceWidth;
    private long previousTimestampNs;
    private int stableFrames;

    public Decision evaluate(int left, int top, int right, int bottom, int imageWidth,
                             int imageHeight, long timestampNs) {
        boolean touchesEdge = left <= 0 || top <= 0 || right >= imageWidth || bottom >= imageHeight;
        double centerX = (left + right) / 2.0;
        double centerY = (top + bottom) / 2.0;
        double faceWidth = Math.max(1.0, right - left);
        double speed = 0.0;
        boolean moving = false;
        if (previousTimestampNs > 0 && timestampNs > previousTimestampNs) {
            double elapsedSeconds = (timestampNs - previousTimestampNs) / 1_000_000_000.0;
            double distance = Math.hypot(centerX - previousCenterX, centerY - previousCenterY);
            speed = distance / ((faceWidth + previousFaceWidth) / 2.0) / elapsedSeconds;
            moving = speed > MAX_CENTER_SPEED_FACE_WIDTHS_PER_SECOND;
        }
        previousCenterX = centerX;
        previousCenterY = centerY;
        previousFaceWidth = faceWidth;
        previousTimestampNs = timestampNs;

        if (touchesEdge || moving) {
            stableFrames = 0;
        } else {
            stableFrames++;
        }
        return new Decision(stableFrames >= REQUIRED_STABLE_FRAMES, touchesEdge, moving, speed, stableFrames);
    }

    public void reset() {
        previousTimestampNs = 0L;
        stableFrames = 0;
    }

    public static final class Decision {
        public final boolean allowInference;
        public final boolean touchesEdge;
        public final boolean moving;
        public final double speedFaceWidthsPerSecond;
        public final int stableFrames;

        Decision(boolean allowInference, boolean touchesEdge, boolean moving,
                 double speedFaceWidthsPerSecond, int stableFrames) {
            this.allowInference = allowInference;
            this.touchesEdge = touchesEdge;
            this.moving = moving;
            this.speedFaceWidthsPerSecond = speedFaceWidthsPerSecond;
            this.stableFrames = stableFrames;
        }
    }
}
