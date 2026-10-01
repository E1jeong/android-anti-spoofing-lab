package com.unionbiometrics.vision.internal;

import android.graphics.Bitmap;
import android.graphics.Rect;

/**
 * One borrowed RGB/IR pair. Lab boxes are unexpanded; live snapshots mark expanded crops.
 */
public final class FrameInput {
    private final Bitmap rgb;
    private final Rect rgbFaceBox;
    private final Bitmap ir;
    private final Rect irFaceBox;
    private final boolean expanded;

    public FrameInput(Bitmap rgb, Rect rgbFaceBox, Bitmap ir, Rect irFaceBox) {
        this(rgb, rgbFaceBox, ir, irFaceBox, false);
    }

    public FrameInput(Bitmap rgb, Rect rgbFaceBox, Bitmap ir, Rect irFaceBox,
                             boolean expanded) {
        if (rgb == null) throw new IllegalArgumentException("rgb must not be null");
        if (ir == null) throw new IllegalArgumentException("ir must not be null");
        if (rgbFaceBox == null) throw new IllegalArgumentException("rgbFaceBox must not be null");
        if (irFaceBox == null) throw new IllegalArgumentException("irFaceBox must not be null");
        this.rgb = rgb;
        this.ir = ir;
        this.rgbFaceBox = new Rect(rgbFaceBox);
        this.irFaceBox = new Rect(irFaceBox);
        this.expanded = expanded;
    }

    Bitmap rgb() {
        return rgb;
    }

    Bitmap ir() {
        return ir;
    }

    Rect rgbFaceBox() {
        return new Rect(rgbFaceBox);
    }

    Rect irFaceBox() {
        return new Rect(irFaceBox);
    }

    boolean expanded() {
        return expanded;
    }
}
