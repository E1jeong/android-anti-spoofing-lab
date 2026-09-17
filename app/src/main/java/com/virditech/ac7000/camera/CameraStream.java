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
import android.util.Log;
import android.util.Size;
import android.view.Surface;
import android.view.TextureView;

import com.virditech.ac7000.concurrent.GenerationGuard;
import com.virditech.ac7000.device.IrCameraExposureController;

import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.RejectedExecutionException;

final class CameraStream {
    private static final String TAG = "CameraStream";
    private static final long IR_ORIENTATION_REASSERT_DELAY_MS = 3_000L;
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
    private final GenerationGuard generationGuard = new GenerationGuard();
    private CameraDevice device;
    private CameraCaptureSession session;
    private ImageReader reader;
    private Surface preview;
    private Handler handler;
    private boolean closing;
    private final AtomicBoolean resourcesFinished = new AtomicBoolean(false);
    private volatile Runnable stopCallback;
    private volatile boolean frameDeliveryEnabled = true;
    private Runnable irOrientationReassertion;
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
        int generation = generationGuard.advance();
        if (textureView.isAvailable()) open(generation);
        else textureView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
                open(generation);
            }
            @Override public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {}
            @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) { return true; }
            @Override public void onSurfaceTextureUpdated(SurfaceTexture surface) {}
        });
    }

    void setFrameDeliveryEnabled(boolean enabled) {
        frameDeliveryEnabled = enabled;
    }

    @SuppressLint("MissingPermission")
    private void open(int generation) {
        if (!generationGuard.isCurrent(generation)) return;
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
                @Override public void onOpened(CameraDevice camera) {
                    if (!generationGuard.isCurrent(generation)) {
                        camera.close();
                        return;
                    }
                    device = camera;
                    normalizeIrOrientation(generation, camera);
                    createSession(generation, camera);
                }
                @Override public void onDisconnected(CameraDevice camera) {
                    cancelIrOrientationReassertion();
                    closing = true;
                    camera.close();
                    if (generationGuard.isCurrent(generation)) listener.onError("Camera disconnected");
                }
                @Override public void onError(CameraDevice camera, int error) {
                    cancelIrOrientationReassertion();
                    closing = true;
                    camera.close();
                    if (generationGuard.isCurrent(generation)) listener.onError("Camera error " + error);
                }
                @Override public void onClosed(CameraDevice camera) {
                    cancelIrOrientationReassertion();
                    if (camera == device) device = null;
                    if (closing) finishCameraClose();
                }
            }, handler);
        } catch (Exception e) {
            if (generationGuard.isCurrent(generation)) listener.onError(e.getMessage());
        }
    }

    private String findCameraId(CameraManager manager, int facing) throws CameraAccessException {
        for (String id : manager.getCameraIdList()) {
            Integer value = manager.getCameraCharacteristics(id).get(CameraCharacteristics.LENS_FACING);
            if (value != null && value == facing) return id;
        }
        return null;
    }

    private void createSession(int generation, CameraDevice camera) {
        try {
            SurfaceTexture texture = textureView.getSurfaceTexture();
            if (texture == null || !generationGuard.isCurrent(generation)) {
                camera.close();
                return;
            }
            texture.setDefaultBufferSize(FRAME_SIZE.getWidth(), FRAME_SIZE.getHeight());
            Surface generationPreview = new Surface(texture);
            preview = generationPreview;
            ImageReader generationReader = ImageReader.newInstance(
                    FRAME_SIZE.getWidth(), FRAME_SIZE.getHeight(), ImageFormat.YUV_420_888, 3);
            reader = generationReader;
            generationReader.setOnImageAvailableListener(r -> {
                try (Image image = r.acquireLatestImage()) {
                    if (image == null) return;
                    if (!generationGuard.isCurrent(generation)) return;
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
                            FrameData frame = converter.toPortraitFrame(nv21Data, w, h, timestamp, !color, false);
                            if (generationGuard.isCurrent(generation)) listener.onFrame(frame);
                            else frame.recycle();
                        } catch (Exception e) {
                            if (generationGuard.isCurrent(generation)) {
                                listener.onError("Frame conversion failed: " + e.getMessage());
                            }
                        } finally {
                            conversionBusy.set(false);
                        }
                    });
                } catch (Exception e) {
                    if (generationGuard.isCurrent(generation)) {
                        listener.onError("Frame acquisition failed: " + e.getMessage());
                    }
                }
            }, handler);
            camera.createCaptureSession(Arrays.asList(generationPreview, generationReader.getSurface()), new CameraCaptureSession.StateCallback() {
                @Override public void onConfigured(CameraCaptureSession configured) {
                    if (!generationGuard.isCurrent(generation)) {
                        configured.close();
                        closing = true;
                        camera.close();
                        return;
                    }
                    session = configured;
                    try {
                        CaptureRequest.Builder request = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                        request.addTarget(generationPreview);
                        request.addTarget(generationReader.getSurface());
                        configured.setRepeatingRequest(request.build(), null, handler);
                    } catch (CameraAccessException e) {
                        if (generationGuard.isCurrent(generation)) listener.onError(e.getMessage());
                    }
                }
                @Override public void onConfigureFailed(CameraCaptureSession failed) {
                    failed.close();
                    closing = true;
                    camera.close();
                    if (generationGuard.isCurrent(generation)) {
                        listener.onError("Camera session configuration failed");
                    }
                }
            }, handler);
        } catch (CameraAccessException e) {
            closing = true;
            camera.close();
            if (generationGuard.isCurrent(generation)) listener.onError(e.getMessage());
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
        textureView.setScaleX(PreviewTransform.forColorCamera(color).textureScaleX());
    }

    private void normalizeIrOrientation(int generation, CameraDevice camera) {
        if (color) return;
        IrCameraExposureController.applyNormalOrientation();
        cancelIrOrientationReassertion();
        irOrientationReassertion = () -> {
            if (!generationGuard.isCurrent(generation) || closing || device != camera) return;
            IrCameraExposureController.applyNormalOrientation();
        };
        handler.postDelayed(irOrientationReassertion, IR_ORIENTATION_REASSERT_DELAY_MS);
    }

    private void cancelIrOrientationReassertion() {
        Handler cameraHandler = handler;
        Runnable reassertion = irOrientationReassertion;
        irOrientationReassertion = null;
        if (cameraHandler != null && reassertion != null) {
            cameraHandler.removeCallbacks(reassertion);
        }
    }

    void stop(Runnable onStopped) {
        generationGuard.advance();
        cancelIrOrientationReassertion();
        frameDeliveryEnabled = false;
        textureView.setSurfaceTextureListener(null);
        stopCallback = onStopped;
        if (resourcesFinished.get()) {
            notifyStopped();
            return;
        }
        closeCameraOnHandler();
    }

    private void closeCameraOnHandler() {
        Handler cameraHandler = handler;
        if (cameraHandler == null) {
            closing = true;
            if (device != null) {
                device.close();
            } else {
                finishCameraClose();
            }
            return;
        }
        if (!cameraHandler.post(() -> {
            closing = true;
            beginCameraClose();
            cameraHandler.postDelayed(() -> {
                if (!resourcesFinished.get()) {
                    Log.e(TAG, "Camera onClosed callback is still pending; reader/surface retained");
                }
            }, 1_500L);
        })) {
            closing = true;
            Log.e(TAG, "Camera handler rejected close request; retaining reader/surface until onClosed");
            if (device != null) device.close();
        }
    }

    private void beginCameraClose() {
        if (session != null) {
            try { session.stopRepeating(); } catch (Exception ignored) {}
            try { session.abortCaptures(); } catch (Exception ignored) {}
            session.close();
            session = null;
        }
        if (device != null) {
            device.close();
        } else {
            finishCameraClose();
        }
    }

    private void finishCameraClose() {
        if (!resourcesFinished.compareAndSet(false, true)) return;
        if (session != null) {
            session.close();
            session = null;
        }
        if (reader != null) {
            reader.close();
            reader = null;
        }
        if (preview != null) {
            preview.release();
            preview = null;
        }
        handler = null;
        try {
            conversionExecutor.execute(() -> {
                converter.close();
                notifyStopped();
            });
            conversionExecutor.shutdown();
        } catch (RejectedExecutionException e) {
            converter.close();
            notifyStopped();
        }
    }

    private void notifyStopped() {
        Runnable callback = stopCallback;
        stopCallback = null;
        if (callback != null) callback.run();
    }
}
