package com.virditech.ac7000.camera;

import android.graphics.Bitmap;

public final class FrameData {
    public final Bitmap bitmap;
    public final long timestampNs;

    public FrameData(Bitmap bitmap, long timestampNs) {
        this.bitmap = bitmap;
        this.timestampNs = timestampNs;
    }

    public void recycle() {
        if (!bitmap.isRecycled()) bitmap.recycle();
    }
}
