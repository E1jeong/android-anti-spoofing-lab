package com.virditech.ac7000.model;

import android.graphics.Rect;

public final class FaceCrop {
    private FaceCrop() {}

    public static Rect expand(Rect box, float marginRatio, int width, int height) {
        int marginX = Math.round(box.width() * marginRatio);
        int marginY = Math.round(box.height() * marginRatio);
        Rect expanded = new Rect(
                Math.max(0, box.left - marginX),
                Math.max(0, box.top - marginY),
                Math.min(width, box.right + marginX),
                Math.min(height, box.bottom + marginY));
        if (expanded.width() <= 0 || expanded.height() <= 0) throw new IllegalArgumentException("Face crop is empty");
        return expanded;
    }
}
