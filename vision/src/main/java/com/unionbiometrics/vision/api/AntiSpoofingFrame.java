package com.unionbiometrics.vision.api;

import android.graphics.Bitmap;
import android.graphics.Rect;

/**
 * One borrowed RGB/IR frame pair. Both face boxes are unexpanded camera-coordinate boxes.
 */
public record AntiSpoofingFrame(Bitmap rgb, Rect rgbFaceBox, Bitmap ir, Rect irFaceBox) {
    public AntiSpoofingFrame(Bitmap rgb, Rect rgbFaceBox, Bitmap ir, Rect irFaceBox) {
        if (rgb == null) throw new IllegalArgumentException("rgb must not be null");
        if (ir == null) throw new IllegalArgumentException("ir must not be null");
        if (rgbFaceBox == null) throw new IllegalArgumentException("rgbFaceBox must not be null");
        if (irFaceBox == null) throw new IllegalArgumentException("irFaceBox must not be null");
        this.rgb = rgb;
        this.ir = ir;
        this.rgbFaceBox = new Rect(rgbFaceBox);
        this.irFaceBox = new Rect(irFaceBox);
    }

    @Override
    public Rect rgbFaceBox() {
        return new Rect(rgbFaceBox);
    }

    @Override
    public Rect irFaceBox() {
        return new Rect(irFaceBox);
    }
}
