package com.virditech.ac7000.camera;

import android.graphics.Bitmap;

public final class FrameData {
    public final Bitmap bitmap;
    public final long timestampNs;
    public final long conversionMs;

    public FrameData(Bitmap bitmap, long timestampNs, long conversionMs) {
        this.bitmap = bitmap;
        this.timestampNs = timestampNs;
        this.conversionMs = conversionMs;
    }

    public void recycle() {
        if (!bitmap.isRecycled()) bitmap.recycle();
    }
}
