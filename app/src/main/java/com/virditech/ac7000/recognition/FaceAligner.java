package com.virditech.ac7000.recognition;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PointF;
import android.graphics.Rect;

/**
 * Utility for aligning and cropping face regions to 112x112 for MobileFaceNet.
 * Supports both 5-point landmark alignment (Similarity Transform) and bounding box center crop.
 */
public final class FaceAligner {
    public static final int TARGET_SIZE = 112;

    // Standard ArcFace / InsightFace 112x112 target landmark coordinates
    public static final PointF TARGET_LEFT_EYE = new PointF(38.2946f, 51.6963f);
    public static final PointF TARGET_RIGHT_EYE = new PointF(73.5318f, 51.5014f);
    public static final PointF TARGET_NOSE = new PointF(56.0252f, 71.7366f);
    public static final PointF TARGET_LEFT_MOUTH = new PointF(41.5493f, 92.3655f);
    public static final PointF TARGET_RIGHT_MOUTH = new PointF(70.7299f, 92.2041f);

    private FaceAligner() {}

    /**
     * Crops and scales the face bounding box to 112x112 with margin.
     *
     * @param source RGB Bitmap.
     * @param faceBox Detected face bounding box.
     * @param marginRatio Margin ratio to add around face (e.g. 0.15f).
     * @return 112x112 Bitmap or null on failure.
     */
    public static Bitmap cropTo112(Bitmap source, Rect faceBox, float marginRatio) {
        if (source == null || faceBox == null || faceBox.isEmpty()) return null;

        int width = source.getWidth();
        int height = source.getHeight();

        int marginX = Math.round(faceBox.width() * marginRatio);
        int marginY = Math.round(faceBox.height() * marginRatio);

        int left = Math.max(0, faceBox.left - marginX);
        int top = Math.max(0, faceBox.top - marginY);
        int right = Math.min(width, faceBox.right + marginX);
        int bottom = Math.min(height, faceBox.bottom + marginY);

        int cropW = right - left;
        int cropH = bottom - top;
        if (cropW <= 0 || cropH <= 0) return null;

        Bitmap cropped = Bitmap.createBitmap(source, left, top, cropW, cropH);
        if (cropW == TARGET_SIZE && cropH == TARGET_SIZE) {
            return cropped;
        }

        Bitmap scaled = Bitmap.createScaledBitmap(cropped, TARGET_SIZE, TARGET_SIZE, true);
        if (scaled != cropped && !cropped.isRecycled()) {
            cropped.recycle();
        }
        return scaled;
    }

    /**
     * Aligns face using 2 eye landmarks to 112x112 using rotation, scaling, and translation.
     */
    public static Bitmap alignEyesTo112(Bitmap source, PointF leftEye, PointF rightEye) {
        if (source == null || leftEye == null || rightEye == null) return null;

        float srcDx = rightEye.x - leftEye.x;
        float srcDy = rightEye.y - leftEye.y;
        float srcDist = (float) Math.hypot(srcDx, srcDy);
        if (srcDist < 1f) return null;

        float targetDx = TARGET_RIGHT_EYE.x - TARGET_LEFT_EYE.x;
        float targetDy = TARGET_RIGHT_EYE.y - TARGET_LEFT_EYE.y;
        float targetDist = (float) Math.hypot(targetDx, targetDy);

        float scale = targetDist / srcDist;
        float angle = (float) Math.toDegrees(Math.atan2(srcDy, srcDx));

        Matrix matrix = new Matrix();
        // Translate source eye center to origin
        float srcCenterX = (leftEye.x + rightEye.x) / 2f;
        float srcCenterY = (leftEye.y + rightEye.y) / 2f;
        matrix.postTranslate(-srcCenterX, -srcCenterY);

        // Rotate
        matrix.postRotate(-angle);

        // Scale
        matrix.postScale(scale, scale);

        // Translate to target eye center in 112x112
        float targetCenterX = (TARGET_LEFT_EYE.x + TARGET_RIGHT_EYE.x) / 2f;
        float targetCenterY = (TARGET_LEFT_EYE.y + TARGET_RIGHT_EYE.y) / 2f;
        matrix.postTranslate(targetCenterX, targetCenterY);

        Bitmap aligned = Bitmap.createBitmap(TARGET_SIZE, TARGET_SIZE, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(aligned);
        Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
        canvas.drawBitmap(source, matrix, paint);
        return aligned;
    }
}
