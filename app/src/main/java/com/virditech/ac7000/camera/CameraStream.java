package com.virditech.ac7000.camera;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.media.Image;
import android.media.ImageReader;
import android.os.Handler;
import android.util.Size;
import android.view.Surface;
import android.view.TextureView;

import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.TimeUnit;

final class CameraStream {
    interface Listener {
        void onFrame(FrameData frame);
        void onError(String message);
    }

    private static final Size FRAME_SIZE = new Size(768, 432);
    private final Context context;
    private final TextureView textureView;
    private final boolean color;
    private final Listener listener;
    private final YuvConverter converter;
    private final ExecutorService conversionExecutor = Executors.newSingleThreadExecutor();
    private final AtomicBoolean conversionBusy = new AtomicBoolean(false);
    private CameraDevice device;
    private CameraCaptureSession session;
    private ImageReader reader;
    private Handler handler;
    private volatile boolean frameDeliveryEnabled = true;
    // Reused NV21 buffer; safe because frames are dropped while a conversion is in flight,
    // so the camera thread only rewrites it after the previous conversion finished.
    private byte[] nv21Buffer;

    CameraStream(Context context, TextureView textureView, boolean color, Listener listener) {
        this.context = context;
        this.textureView = textureView;
        this.color = color;
        this.listener = listener;
        converter = new YuvConverter(context);
    }

    void start(Handler handler) {
        this.handler = handler;
        if (textureView.isAvailable()) open();
        else textureView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) { open(); }
            @Override public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {}
            @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) { return true; }
            @Override public void onSurfaceTextureUpdated(SurfaceTexture surface) {}
        });
    }

    void setFrameDeliveryEnabled(boolean enabled) {
        frameDeliveryEnabled = enabled;
    }

    @SuppressLint("MissingPermission")
    private void open() {
        if (context.checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            listener.onError("CAMERA permission is missing");
            return;
        }
        CameraManager manager = context.getSystemService(CameraManager.class);
        try {
            String cameraId = findCameraId(manager, color ? CameraCharacteristics.LENS_FACING_FRONT : CameraCharacteristics.LENS_FACING_BACK);
            if (cameraId == null) throw new IllegalStateException(color ? "RGB camera not found" : "IR camera not found");
            rotatePreview();
            manager.openCamera(cameraId, new CameraDevice.StateCallback() {
                @Override public void onOpened(CameraDevice camera) { device = camera; createSession(); }
                @Override public void onDisconnected(CameraDevice camera) { camera.close(); listener.onError("Camera disconnected"); }
                @Override public void onError(CameraDevice camera, int error) { camera.close(); listener.onError("Camera error " + error); }
            }, handler);
        } catch (Exception e) {
            listener.onError(e.getMessage());
        }
    }

    private String findCameraId(CameraManager manager, int facing) throws CameraAccessException {
        for (String id : manager.getCameraIdList()) {
            Integer value = manager.getCameraCharacteristics(id).get(CameraCharacteristics.LENS_FACING);
            if (value != null && value == facing) return id;
        }
        return null;
    }

    private void createSession() {
        try {
            SurfaceTexture texture = textureView.getSurfaceTexture();
            if (texture == null || device == null) return;
            texture.setDefaultBufferSize(FRAME_SIZE.getWidth(), FRAME_SIZE.getHeight());
            Surface preview = new Surface(texture);
            reader = ImageReader.newInstance(FRAME_SIZE.getWidth(), FRAME_SIZE.getHeight(), ImageFormat.YUV_420_888, 3);
            reader.setOnImageAvailableListener(r -> {
                try (Image image = r.acquireLatestImage()) {
                    if (image == null) return;
                    if (!frameDeliveryEnabled) return;
                    if (conversionBusy.get()) return;

                    int w = image.getWidth();
                    int h = image.getHeight();
                    int required = w * h * 3 / 2;
                    if (nv21Buffer == null || nv21Buffer.length != required) {
                        nv21Buffer = new byte[required];
                    }
                    byte[] nv21Data = nv21Buffer;
                    YuvConverter.copyToNv21(image, nv21Data);
                    long timestamp = image.getTimestamp();

                    conversionBusy.set(true);
                    conversionExecutor.execute(() -> {
                        try {
                            FrameData frame = converter.toPortraitFrame(nv21Data, w, h, timestamp, !color, mirrorHorizontally());
                            listener.onFrame(frame);
                        } catch (Exception e) {
                            listener.onError("Frame conversion failed: " + e.getMessage());
                        } finally {
                            conversionBusy.set(false);
                        }
                    });
                } catch (Exception e) {
                    listener.onError("Frame acquisition failed: " + e.getMessage());
                }
            }, handler);
            device.createCaptureSession(Arrays.asList(preview, reader.getSurface()), new CameraCaptureSession.StateCallback() {
                @Override public void onConfigured(CameraCaptureSession configured) {
                    session = configured;
                    try {
                        CaptureRequest.Builder request = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                        request.addTarget(preview);
                        request.addTarget(reader.getSurface());
                        session.setRepeatingRequest(request.build(), null, handler);
                    } catch (CameraAccessException e) {
                        listener.onError(e.getMessage());
                    }
                }
                @Override public void onConfigureFailed(CameraCaptureSession failed) { listener.onError("Camera session configuration failed"); }
            }, handler);
        } catch (CameraAccessException e) {
            listener.onError(e.getMessage());
        }
    }

    private void rotatePreview() {
        float width = textureView.getWidth();
        float height = textureView.getHeight();
        if (width <= 0f || height <= 0f) return;
        Matrix matrix = new Matrix();
        float degrees = color ? 90f : 270f;
        matrix.postRotate(degrees, width / 2f, height / 2f);
        matrix.postScale(width / height, height / width, width / 2f, height / 2f);
        textureView.setTransform(matrix);
        textureView.setScaleX(1f);
    }

    private boolean mirrorHorizontally() {
        return !color;
    }

    void stop() {
        textureView.setSurfaceTextureListener(null);
        if (session != null) {
            try { session.stopRepeating(); } catch (Exception ignored) {}
            try { session.abortCaptures(); } catch (Exception ignored) {}
        }
        if (reader != null) { reader.close(); reader = null; }
        if (session != null) { session.close(); session = null; }
        if (device != null) { device.close(); device = null; }

        conversionExecutor.shutdown();
        try {
            if (!conversionExecutor.awaitTermination(500, TimeUnit.MILLISECONDS)) {
                conversionExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            conversionExecutor.shutdownNow();
        }

        if (handler != null) {
            handler.post(converter::close);
            handler = null;
        } else {
            converter.close();
        }
    }
}
