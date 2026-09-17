package com.unionbiometrics.vision.api;

import android.graphics.Bitmap;
import android.graphics.Rect;

/** One borrowed RGB/IR frame pair. Face boxes are unexpanded camera-coordinate boxes. */
public final class VisionFrame {
    private final Bitmap rgb;
    private final Bitmap ir;
    private final Rect rgbFaceBox;
    private final Rect irFaceBox;
    private final long rgbTimestampNs;
    private final long irTimestampNs;

    public VisionFrame(Bitmap rgb, Rect rgbFaceBox, long rgbTimestampNs,
                       Bitmap ir, Rect irFaceBox, long irTimestampNs) {
        if (rgb == null) throw new IllegalArgumentException("rgb must not be null");
        if (ir == null) throw new IllegalArgumentException("ir must not be null");
        if (rgbFaceBox == null) throw new IllegalArgumentException("rgbFaceBox must not be null");
        if (rgbTimestampNs < 0L || irTimestampNs < 0L) {
            throw new IllegalArgumentException("timestamps must be >= 0");
        }
        this.rgb = rgb;
        this.ir = ir;
        this.rgbFaceBox = new Rect(rgbFaceBox);
        this.irFaceBox = irFaceBox == null ? null : new Rect(irFaceBox);
        this.rgbTimestampNs = rgbTimestampNs;
        this.irTimestampNs = irTimestampNs;
    }

    public Bitmap rgb() {
        return rgb;
    }

    public Bitmap ir() {
        return ir;
    }

    public Rect rgbFaceBox() {
        return new Rect(rgbFaceBox);
    }

    /** Returns null when the engine must obtain the mapping from {@link AntiSpoofingHost}. */
    public Rect irFaceBox() {
        return irFaceBox == null ? null : new Rect(irFaceBox);
    }

    public long rgbTimestampNs() {
        return rgbTimestampNs;
    }

    public long irTimestampNs() {
        return irTimestampNs;
    }
}
