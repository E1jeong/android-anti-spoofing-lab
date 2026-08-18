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
     * Aligns face using 5-point facial landmarks to 112x112 canonical ArcFace coordinates
     * using the closed-form Umeyama 2D Least-Squares Similarity Transform (Scale, Rotation, Translation).
     *
     * Expected landmark order:
     * 0: Left Eye (image left / smaller X)
     * 1: Right Eye (image right / larger X)
     * 2: Nose tip
     * 3: Left Mouth corner
     * 4: Right Mouth corner
     *
     * @param source RGB Bitmap.
     * @param srcPoints Array of at least 5 facial landmark PointF coordinates.
     * @return 112x112 aligned Bitmap or null on failure.
     */
    public static Bitmap align5PointsTo112(Bitmap source, PointF[] srcPoints) {
        if (source == null || srcPoints == null || srcPoints.length < 5) return null;
        for (int i = 0; i < 5; i++) {
            if (srcPoints[i] == null) return null;
        }

        PointF[] targetPoints = new PointF[] {
                TARGET_LEFT_EYE,
                TARGET_RIGHT_EYE,
                TARGET_NOSE,
                TARGET_LEFT_MOUTH,
                TARGET_RIGHT_MOUTH
        };

        // Compute mean of source and target points
        float meanSrcX = 0f, meanSrcY = 0f;
        float meanTgtX = 0f, meanTgtY = 0f;
        for (int i = 0; i < 5; i++) {
            meanSrcX += srcPoints[i].x;
            meanSrcY += srcPoints[i].y;
            meanTgtX += targetPoints[i].x;
            meanTgtY += targetPoints[i].y;
        }
        meanSrcX /= 5f;
        meanSrcY /= 5f;
        meanTgtX /= 5f;
        meanTgtY /= 5f;

        // Compute variance of source points and covariance between source and target
        float srcVar = 0f;
        float covXX = 0f;
        float covXY = 0f;
        for (int i = 0; i < 5; i++) {
            float sX = srcPoints[i].x - meanSrcX;
            float sY = srcPoints[i].y - meanSrcY;
            float tX = targetPoints[i].x - meanTgtX;
            float tY = targetPoints[i].y - meanTgtY;

            srcVar += sX * sX + sY * sY;
            covXX += sX * tX + sY * tY;
            covXY += sX * tY - sY * tX;
        }
        srcVar /= 5f;
        covXX /= 5f;
        covXY /= 5f;

        if (srcVar < 1e-6f) {
            // Degenerate source points: fallback to eye alignment
            return alignEyesTo112(source, srcPoints[0], srcPoints[1]);
        }

        // Similarity transform parameters: a = s * cos(theta), b = s * sin(theta)
        float a = covXX / srcVar;
        float b = covXY / srcVar;

        // Translation in source coordinate space
        float transX = meanTgtX - (a * meanSrcX - b * meanSrcY);
        float transY = meanTgtY - (b * meanSrcX + a * meanSrcY);

        // Optimize performance: compute landmark bounding box and crop sub-region first
        // to avoid expensive software Canvas filtering over a full 1080x1920 bitmap on CPU
        float minX = srcPoints[0].x, maxX = srcPoints[0].x;
        float minY = srcPoints[0].y, maxY = srcPoints[0].y;
        for (int i = 1; i < 5; i++) {
            minX = Math.min(minX, srcPoints[i].x);
            maxX = Math.max(maxX, srcPoints[i].x);
            minY = Math.min(minY, srcPoints[i].y);
            maxY = Math.max(maxY, srcPoints[i].y);
        }
        int padX = Math.max(30, Math.round((maxX - minX) * 0.8f));
        int padY = Math.max(30, Math.round((maxY - minY) * 0.8f));

        int cropLeft = Math.max(0, Math.round(minX - padX));
        int cropTop = Math.max(0, Math.round(minY - padY));
        int cropRight = Math.min(source.getWidth(), Math.round(maxX + padX));
        int cropBottom = Math.min(source.getHeight(), Math.round(maxY + padY));
        int cropW = cropRight - cropLeft;
        int cropH = cropBottom - cropTop;

        Bitmap subSource = null;
        boolean subAllocated = false;
        Matrix matrix = new Matrix();

        if (cropW > 0 && cropH > 0 && (cropW < source.getWidth() || cropH < source.getHeight())) {
            subSource = Bitmap.createBitmap(source, cropLeft, cropTop, cropW, cropH);
            subAllocated = true;
            // Adjust matrix translation for subSource origin
            float subTransX = transX + a * cropLeft - b * cropTop;
            float subTransY = transY + b * cropLeft + a * cropTop;
            float[] values = new float[] {
                    a,  -b,  subTransX,
                    b,   a,  subTransY,
                    0f, 0f,  1f
            };
            matrix.setValues(values);
        } else {
            subSource = source;
            float[] values = new float[] {
                    a,  -b,  transX,
                    b,   a,  transY,
                    0f, 0f,  1f
            };
            matrix.setValues(values);
        }

        Bitmap aligned = Bitmap.createBitmap(TARGET_SIZE, TARGET_SIZE, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(aligned);
        Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
        canvas.drawBitmap(subSource, matrix, paint);

        if (subAllocated && !subSource.isRecycled()) {
            subSource.recycle();
        }
        return aligned;
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
