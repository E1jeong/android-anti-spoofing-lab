package com.virditech.ac7000.camera;

import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;
import android.view.TextureView;

public final class DualCameraController {
    public interface Listener {
        void onRgb(FrameData frame);
        void onIr(FrameData frame);
        void onError(String message);
    }

    private final CameraStream rgb;
    private final CameraStream ir;
    private HandlerThread rgbThread;
    private HandlerThread irThread;

    public DualCameraController(Context context, TextureView rgbView, TextureView irView, Listener listener) {
        rgb = new CameraStream(context, rgbView, true, new CameraStream.Listener() {
            @Override public void onFrame(FrameData frame) { listener.onRgb(frame); }
            @Override public void onError(String message) { listener.onError("RGB: " + message); }
        });
        ir = new CameraStream(context, irView, false, new CameraStream.Listener() {
            @Override public void onFrame(FrameData frame) { listener.onIr(frame); }
            @Override public void onError(String message) { listener.onError("IR: " + message); }
        });
    }

    public void start() {
        rgbThread = new HandlerThread("rgb-camera");
        irThread = new HandlerThread("ir-camera");
        rgbThread.start();
        irThread.start();
        rgb.start(new Handler(rgbThread.getLooper()));
        ir.start(new Handler(irThread.getLooper()));
    }

    public void setIrFramesEnabled(boolean enabled) {
        ir.setFrameDeliveryEnabled(enabled);
    }

    public void stop() {
        rgb.stop();
        ir.stop();
        if (rgbThread != null) rgbThread.quitSafely();
        if (irThread != null) irThread.quitSafely();
        rgbThread = null;
        irThread = null;
    }
}
