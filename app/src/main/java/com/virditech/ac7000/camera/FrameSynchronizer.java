package com.virditech.ac7000.camera;

public final class FrameSynchronizer {
    private static final long MAX_DELTA_NS = 50_000_000L;

    public interface Listener {
        void onPair(FramePair pair);
    }

    private final Listener listener;
    private FrameData rgb;
    private FrameData ir;

    public FrameSynchronizer(Listener listener) {
        this.listener = listener;
    }

    public synchronized void offerRgb(FrameData frame) {
        if (rgb != null) rgb.recycle();
        rgb = frame;
        match();
    }

    public synchronized void offerIr(FrameData frame) {
        if (ir != null) ir.recycle();
        ir = frame;
        match();
    }

    private void match() {
        if (rgb == null || ir == null) return;
        long delta = rgb.timestampNs - ir.timestampNs;
        if (Math.abs(delta) <= MAX_DELTA_NS) {
            FramePair pair = new FramePair(rgb, ir);
            rgb = null;
            ir = null;
            listener.onPair(pair);
        } else if (delta < 0) {
            rgb.recycle();
            rgb = null;
        } else {
            ir.recycle();
            ir = null;
        }
    }

    public synchronized void clear() {
        if (rgb != null) rgb.recycle();
        if (ir != null) ir.recycle();
        rgb = null;
        ir = null;
    }
}
