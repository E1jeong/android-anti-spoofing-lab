package com.virditech.ac7000.camera;

public final class FramePair {
    public final FrameData rgb;
    public final FrameData ir;

    public FramePair(FrameData rgb, FrameData ir) {
        this.rgb = rgb;
        this.ir = ir;
    }

    public void recycle() {
        rgb.recycle();
        ir.recycle();
    }
}
