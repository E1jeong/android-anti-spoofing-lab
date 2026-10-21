package com.unionbiometrics.facedetection;

final class Letterbox {
    final int sourceWidth;
    final int sourceHeight;
    final int resizedWidth;
    final int resizedHeight;
    final int padLeft;
    final int padTop;
    final double scale;

    Letterbox(int sourceWidth, int sourceHeight, int targetWidth, int targetHeight) {
        if (sourceWidth <= 0 || sourceHeight <= 0 || targetWidth <= 0 || targetHeight <= 0) {
            throw new IllegalArgumentException("Image dimensions must be positive");
        }
        this.sourceWidth = sourceWidth;
        this.sourceHeight = sourceHeight;
        scale = Math.min((double) targetWidth / sourceWidth, (double) targetHeight / sourceHeight);
        // Match Python round(), including half-to-even ties.
        resizedWidth = (int) Math.rint(sourceWidth * scale);
        resizedHeight = (int) Math.rint(sourceHeight * scale);
        if (resizedWidth <= 0 || resizedHeight <= 0) {
            throw new IllegalArgumentException("Source aspect ratio produces an empty resized image");
        }
        padLeft = (targetWidth - resizedWidth) / 2;
        padTop = (targetHeight - resizedHeight) / 2;
    }

    FaceDetection restore(FaceDetection box) {
        float[] points = box.landmarks.isEmpty() ? null : new float[10];
        if (points != null) {
            for (int i = 0; i < 5; i++) {
                FaceDetection.Landmark point = box.landmarks.get(i);
                points[i * 2] = clip((point.x - padLeft) / scale, sourceWidth);
                points[i * 2 + 1] = clip((point.y - padTop) / scale, sourceHeight);
            }
        }
        return new FaceDetection(
                clip((box.left - padLeft) / scale, sourceWidth),
                clip((box.top - padTop) / scale, sourceHeight),
                clip((box.right - padLeft) / scale, sourceWidth),
                clip((box.bottom - padTop) / scale, sourceHeight), box.confidence, points);
    }

    private static float clip(double value, int limit) {
        return (float) Math.max(0, Math.min(limit, value));
    }
}
