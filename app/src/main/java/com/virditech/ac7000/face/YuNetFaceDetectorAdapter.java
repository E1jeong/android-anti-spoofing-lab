package com.virditech.ac7000.face;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Rect;

import com.unionbiometrics.facedetection.FaceDetection;
import com.unionbiometrics.facedetection.YuNetFaceDetector;

import java.util.List;

public final class YuNetFaceDetectorAdapter implements FaceDetectionEngine {
    private final YuNetFaceDetector detector;

    public YuNetFaceDetectorAdapter(Context applicationContext, YuNetFaceDetector.Mode mode) throws Exception {
        detector = YuNetFaceDetector.create(applicationContext, mode);
    }

    @Override public String label() {
        return detector.mode() == YuNetFaceDetector.Mode.INT8_NNAPI
                ? "YUNET INT8/NNAPI" : "YUNET FLOAT/CPU";
    }

    @Override public Rect detectLargest(Bitmap bitmap) {
        FaceDetection largest = null;
        float largestArea = -1;
        for (FaceDetection face : detector.detect(bitmap)) {
            float area = (face.right - face.left) * (face.bottom - face.top);
            if (area > largestArea) {
                largest = face;
                largestArea = area;
            }
        }
        return largest == null ? null : toRect(largest);
    }

    @Override public Rect detectSingle(Bitmap bitmap) {
        List<FaceDetection> faces = detector.detect(bitmap);
        return faces.size() == 1 ? toRect(faces.get(0)) : null;
    }

    private static Rect toRect(FaceDetection face) {
        return new Rect((int) Math.floor(face.left), (int) Math.floor(face.top),
                (int) Math.ceil(face.right), (int) Math.ceil(face.bottom));
    }

    @Override public void close() { detector.close(); }
}
