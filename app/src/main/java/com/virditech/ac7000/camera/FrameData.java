package com.virditech.ac7000.camera;

import android.graphics.Bitmap;

public final class FrameData {
    public interface Releaser {
        void release(Bitmap bitmap);
    }

    public final Bitmap bitmap;
    public final long timestampNs;
    public final long conversionMs;
    private final Releaser releaser;
    private boolean released;

    public FrameData(Bitmap bitmap, long timestampNs, long conversionMs) {
        this(bitmap, timestampNs, conversionMs, null);
    }

    public FrameData(Bitmap bitmap, long timestampNs, long conversionMs, Releaser releaser) {
        this.bitmap = bitmap;
        this.timestampNs = timestampNs;
        this.conversionMs = conversionMs;
        this.releaser = releaser;
    }

    public synchronized void recycle() {
        if (released) return;
        released = true;
        if (bitmap.isRecycled()) return;
        if (releaser != null) releaser.release(bitmap);
        else bitmap.recycle();
    }
}
