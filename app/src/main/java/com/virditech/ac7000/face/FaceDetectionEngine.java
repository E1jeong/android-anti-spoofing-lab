package com.virditech.ac7000.face;

import android.graphics.Bitmap;
import android.graphics.PointF;
import android.graphics.Rect;

public interface FaceDetectionEngine extends AutoCloseable {
    String label();

    Rect detectLargest(Bitmap bitmap);

    Rect detectSingle(Bitmap bitmap);

    default PointF[] getLastDetectedLandmarks() {
        return null;
    }

    @Override void close();
}
