package com.virditech.ac7000.face;

import android.graphics.Bitmap;
import android.graphics.Rect;

public interface FaceDetectionEngine extends AutoCloseable {
    String label();

    Rect detectLargest(Bitmap bitmap);

    Rect detectSingle(Bitmap bitmap);

    @Override void close();
}
