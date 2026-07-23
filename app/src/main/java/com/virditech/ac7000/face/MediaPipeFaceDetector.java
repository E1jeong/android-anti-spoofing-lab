package com.virditech.ac7000.face;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.graphics.RectF;

import com.google.mediapipe.framework.image.BitmapImageBuilder;
import com.google.mediapipe.framework.image.MPImage;
import com.google.mediapipe.tasks.components.containers.Detection;
import com.google.mediapipe.tasks.core.BaseOptions;
import com.google.mediapipe.tasks.core.Delegate;
import com.google.mediapipe.tasks.vision.core.RunningMode;
import com.google.mediapipe.tasks.vision.facedetector.FaceDetectorResult;

import java.util.List;

public final class MediaPipeFaceDetector implements FaceDetectionEngine {
    private static final String MODEL_FILE = "blaze_face_short_range.tflite";

    private final com.google.mediapipe.tasks.vision.facedetector.FaceDetector detector;

    public MediaPipeFaceDetector(Context context) {
        BaseOptions baseOptions = BaseOptions.builder()
                .setDelegate(Delegate.CPU)
                .setModelAssetPath(MODEL_FILE)
                .build();
        com.google.mediapipe.tasks.vision.facedetector.FaceDetector.FaceDetectorOptions options =
                com.google.mediapipe.tasks.vision.facedetector.FaceDetector.FaceDetectorOptions.builder()
                        .setBaseOptions(baseOptions)
                        .setRunningMode(RunningMode.IMAGE)
                        .setMinDetectionConfidence(0.5f)
                        .build();
        detector = com.google.mediapipe.tasks.vision.facedetector.FaceDetector.createFromOptions(
                context, options);
    }

    @Override public String label() {
        return "MEDIAPIPE";
    }

    @Override public Rect detectLargest(Bitmap bitmap) {
        Rect largest = null;
        long largestArea = -1;
        for (Rect detected : detectAll(bitmap)) {
            long area = (long) detected.width() * detected.height();
            if (area > largestArea) {
                largestArea = area;
                largest = detected;
            }
        }
        return largest;
    }

    @Override public Rect detectSingle(Bitmap bitmap) {
        List<Rect> detected = detectAll(bitmap);
        return detected.size() == 1 ? detected.get(0) : null;
    }

    private List<Rect> detectAll(Bitmap bitmap) {
        Bitmap input = bitmap;
        boolean copied = false;
        if (bitmap.getConfig() != Bitmap.Config.ARGB_8888) {
            input = bitmap.copy(Bitmap.Config.ARGB_8888, false);
            copied = true;
        }
        try {
            MPImage image = new BitmapImageBuilder(input).build();
            FaceDetectorResult result = detector.detect(image);
            java.util.ArrayList<Rect> boxes = new java.util.ArrayList<>();
            for (Detection detection : result.detections()) {
                Rect box = toRect(detection.boundingBox(), input.getWidth(), input.getHeight());
                if (!box.isEmpty()) boxes.add(box);
            }
            return boxes;
        } finally {
            if (copied && input != null && !input.isRecycled()) input.recycle();
        }
    }

    private static Rect toRect(RectF source, int width, int height) {
        int left = Math.max(0, (int) Math.floor(source.left));
        int top = Math.max(0, (int) Math.floor(source.top));
        int right = Math.min(width, (int) Math.ceil(source.right));
        int bottom = Math.min(height, (int) Math.ceil(source.bottom));
        return new Rect(left, top, right, bottom);
    }

    @Override public void close() {
        detector.close();
    }
}
