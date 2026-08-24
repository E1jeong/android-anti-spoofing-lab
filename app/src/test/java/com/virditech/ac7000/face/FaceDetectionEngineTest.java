package com.virditech.ac7000.face;

import android.graphics.Bitmap;
import android.graphics.Rect;

import org.junit.Test;

import static org.junit.Assert.assertNull;

public class FaceDetectionEngineTest {
    @Test
    public void detectorWithoutLandmarkSupportCannotReuseAnotherDetectorsLandmarks() {
        FaceDetectionEngine detector = new FaceDetectionEngine() {
            @Override public String label() { return "BOX_ONLY"; }
            @Override public Rect detectLargest(Bitmap bitmap) { return null; }
            @Override public Rect detectSingle(Bitmap bitmap) { return null; }
            @Override public void close() {}
        };

        assertNull(detector.getLastDetectedLandmarks());
    }
}
