package com.virditech.ac7000.face;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Rect;

import com.cyberlink.faceme.DetectionMode;
import com.cyberlink.faceme.DetectionModelSpeedLevel;
import com.cyberlink.faceme.DetectionOutputOrder;
import com.cyberlink.faceme.DetectionSpeedLevel;
import com.cyberlink.faceme.EnginePreference;
import com.cyberlink.faceme.ExtractConfig;
import com.cyberlink.faceme.ExtractionOption;
import com.cyberlink.faceme.FaceInfo;
import com.cyberlink.faceme.FaceMeRecognizer;
import com.cyberlink.faceme.FaceMeSdk;
import com.cyberlink.faceme.LicenseManager;
import com.cyberlink.faceme.RecognizerConfig;
import com.cyberlink.faceme.RecognizerMode;
import com.virditech.ac7000.BuildConfig;

import java.util.Collections;
import java.util.List;

public final class FaceDetector implements AutoCloseable {
    private FaceMeRecognizer recognizer;
    private final ExtractConfig extractConfig = new ExtractConfig();

    public FaceDetector(Context context) {
        if (BuildConfig.FACEME_LICENSE_KEY.isEmpty()) {
            throw new IllegalStateException("FACEME_LICENSE_KEY Gradle property is missing");
        }
        FaceMeSdk.initialize(context, BuildConfig.FACEME_LICENSE_KEY);
        LicenseManager licenseManager = new LicenseManager();
        int result = licenseManager.initializeEx();
        if (result == 0) result = licenseManager.registerLicense();
        licenseManager.release();
        if (result != 0) throw new IllegalStateException("FaceMe license initialization failed: " + result);

        recognizer = new FaceMeRecognizer();
        RecognizerConfig config = new RecognizerConfig();
        config.preference = EnginePreference.PREFER_NXP_DETECTION;
        config.detectionModelSpeedLevel = DetectionModelSpeedLevel.DEFAULT;
        config.maxDetectionThreads = Math.min(Runtime.getRuntime().availableProcessors(), 2);
        config.mode = RecognizerMode.IMAGE;
        config.maxFrameWidth = 432;
        config.maxFrameHeight = 768;
        config.minFaceWidthRatio = 0.15f;
        result = recognizer.initializeEx(config);
        if (result < 0) {
            recognizer = null;
            throw new IllegalStateException("FaceMe detector initialization failed: " + result);
        }
        recognizer.setExtractionOption(ExtractionOption.DETECTION_OUTPUT_ORDER, DetectionOutputOrder.FACE_WIDTH);
        recognizer.setExtractionOption(ExtractionOption.DETECTION_MODE, DetectionMode.NORMAL);
        recognizer.setExtractionOption(ExtractionOption.DETECTION_SPEED_LEVEL, DetectionSpeedLevel.FAST);

        extractConfig.extractFeature = false;
        extractConfig.extractFeatureLandmark = false;
        extractConfig.extractAge = false;
        extractConfig.extractGender = false;
        extractConfig.extractEmotion = false;
        extractConfig.extractPose = false;
        extractConfig.extractOcclusion = false;
    }

    public Rect detectLargest(Bitmap rgb) {
        List<Integer> counts = recognizer.extractFaceEx(extractConfig, Collections.singletonList(rgb));
        if (counts.isEmpty() || counts.get(0) == 0) return null;
        Rect largest = null;
        long largestArea = -1;
        for (int i = 0; i < counts.get(0); i++) {
            FaceInfo info = recognizer.getFaceInfo(0, i);
            if (info == null || info.boundingBox == null) continue;
            Rect box = new Rect(info.boundingBox);
            long area = (long) box.width() * box.height();
            if (area > largestArea) {
                largestArea = area;
                largest = box;
            }
        }
        return largest;
    }

    public Rect detectSingle(Bitmap image) {
        List<Integer> counts = recognizer.extractFaceEx(extractConfig, Collections.singletonList(image));
        if (counts.isEmpty() || counts.get(0) != 1) return null;
        FaceInfo info = recognizer.getFaceInfo(0, 0);
        return info == null || info.boundingBox == null ? null : new Rect(info.boundingBox);
    }

    @Override public void close() {
        if (recognizer != null) {
            recognizer.release();
            recognizer = null;
        }
    }
}
