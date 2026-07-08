package com.virditech.ac7000.capture;

import android.graphics.Rect;

public final class RectValue {
    public final int left;
    public final int top;
    public final int right;
    public final int bottom;

    public RectValue(int left, int top, int right, int bottom) {
        this.left = left;
        this.top = top;
        this.right = right;
        this.bottom = bottom;
    }

    public static RectValue from(Rect rect) {
        return new RectValue(rect.left, rect.top, rect.right, rect.bottom);
    }
}
