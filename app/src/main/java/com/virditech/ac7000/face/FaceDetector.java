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
import com.cyberlink.faceme.FaceAttribute;
import com.cyberlink.faceme.FaceInfo;
import com.cyberlink.faceme.FaceLandmark;
import com.cyberlink.faceme.FaceQuality;
import com.cyberlink.faceme.FaceQualityInfo;
import com.cyberlink.faceme.FaceMeRecognizer;
import com.cyberlink.faceme.FaceMeSdk;
import com.cyberlink.faceme.LicenseManager;
import com.cyberlink.faceme.Pose;
import com.cyberlink.faceme.QualityCheckMode;
import com.cyberlink.faceme.QualityCheckPreference;
import com.cyberlink.faceme.QualityCheckResult;
import com.cyberlink.faceme.QualityDetectConfig;
import com.cyberlink.faceme.QualityDetector;
import com.cyberlink.faceme.QualityDetectorConfig;
import com.cyberlink.faceme.QualityIssueOption;
import com.cyberlink.faceme.RecognizerConfig;
import com.cyberlink.faceme.RecognizerMode;
import com.virditech.ac7000.BuildConfig;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class FaceDetector implements AutoCloseable {
    private FaceMeRecognizer recognizer;
    private QualityDetector qualityDetector;
    private final ExtractConfig extractConfig = new ExtractConfig();
    private final ExtractConfig qualityExtractConfig = new ExtractConfig();
    private FaceInfo lastLargestFaceInfo;
    private String qualityError;

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

        qualityExtractConfig.extractFeature = false;
        qualityExtractConfig.extractFeatureLandmark = true;
        qualityExtractConfig.extractAge = false;
        qualityExtractConfig.extractGender = false;
        qualityExtractConfig.extractEmotion = false;
        qualityExtractConfig.extractPose = true;
        qualityExtractConfig.extractOcclusion = false;

        try {
            qualityDetector = new QualityDetector(context);
            QualityDetectorConfig qualityConfig = new QualityDetectorConfig();
            qualityConfig.preference = QualityCheckPreference.PREFER_FAST_DETECTION;
            result = qualityDetector.initialize(qualityConfig);
            if (result < 0) {
                qualityError = "Face quality unavailable: " + result;
                qualityDetector.release();
                qualityDetector = null;
            }
        } catch (Exception e) {
            qualityError = "Face quality unavailable: " + e.getMessage();
            if (qualityDetector != null) qualityDetector.release();
            qualityDetector = null;
        }
    }

    public Rect detectLargest(Bitmap rgb) {
        lastLargestFaceInfo = null;
        List<Integer> counts = recognizer.extractFaceEx(extractConfig, Collections.singletonList(rgb));
        if (counts.isEmpty() || counts.get(0) == 0) return null;
        Rect largest = null;
        FaceInfo largestInfo = null;
        long largestArea = -1;
        for (int i = 0; i < counts.get(0); i++) {
            FaceInfo info = recognizer.getFaceInfo(0, i);
            if (info == null || info.boundingBox == null) continue;
            Rect box = new Rect(info.boundingBox);
            long area = (long) box.width() * box.height();
            if (area > largestArea) {
                largestArea = area;
                largest = box;
                largestInfo = info;
            }
        }
        lastLargestFaceInfo = copyFaceInfo(largestInfo);
        return largest;
    }

    public boolean isQualityAvailable() {
        return qualityDetector != null;
    }

    public String qualityError() {
        return qualityError != null ? qualityError : "";
    }

    public FaceQualityCheckResult checkFaceQuality(Bitmap rgb, int minLevel) {
        if (qualityDetector == null) {
            return FaceQualityCheckResult.failed(minLevel, -1, 0f,
                    qualityError != null ? qualityError : "Face quality unavailable");
        }
        if (lastLargestFaceInfo == null) {
            return FaceQualityCheckResult.failed(minLevel, -1, 0f, "No detected face");
        }

        QualityFaceData faceData = detectLargestForQuality(rgb);
        if (faceData == null) {
            return FaceQualityCheckResult.failed(minLevel, -1, 0f, "No quality face data");
        }

        QualityIssueOption issueOption = new QualityIssueOption();
        issueOption.QualityFace = true;

        QualityDetectConfig config = new QualityDetectConfig();
        config.detectType = issueOption;
        config.checkMode = QualityCheckMode.ONE_FAILURE;
        config.faceCount = 1;
        config.faceInfos = new ArrayList<>();
        config.faceInfos.add(copyFaceInfo(faceData.faceInfo));
        config.landmarks = new ArrayList<>();
        config.landmarks.add(faceData.landmark);
        config.poses = new ArrayList<>();
        config.poses.add(faceData.pose);
        config.minFaceQualityLevel = minLevel;

        QualityCheckResult[] results;
        try {
            results = qualityDetector.detect(config, rgb);
        } catch (Exception e) {
            return FaceQualityCheckResult.failed(minLevel, -1, 0f,
                    "Quality check failed: " + e.getMessage());
        }
        if (results == null) {
            return FaceQualityCheckResult.failed(minLevel, -1, 0f, "No quality result");
        }
        if (results.length == 0) {
            return new FaceQualityCheckResult(true, minLevel, minLevel, 0f, "");
        }

        QualityCheckResult result = results[0];
        FaceQualityInfo info = result.faceQualityInfo;
        FaceQuality quality = info == null ? null : info.faceQuality;
        if (quality == null) {
            return FaceQualityCheckResult.failed(minLevel, -1, 0f, "No face quality");
        }

        boolean passed = quality.level >= minLevel;
        String reason = passed ? "" : "Face quality below " + levelName(minLevel);
        return new FaceQualityCheckResult(passed, minLevel, quality.level, quality.score, reason);
    }

    private QualityFaceData detectLargestForQuality(Bitmap rgb) {
        List<Integer> counts = recognizer.extractFaceEx(qualityExtractConfig, Collections.singletonList(rgb));
        if (counts.isEmpty() || counts.get(0) == 0) return null;

        int largestIndex = -1;
        FaceInfo largestInfo = null;
        long largestArea = -1;
        for (int i = 0; i < counts.get(0); i++) {
            FaceInfo info = recognizer.getFaceInfo(0, i);
            if (info == null || info.boundingBox == null) continue;
            long area = (long) info.boundingBox.width() * info.boundingBox.height();
            if (area > largestArea) {
                largestArea = area;
                largestIndex = i;
                largestInfo = info;
            }
        }
        if (largestIndex < 0 || largestInfo == null) return null;

        FaceLandmark landmark = recognizer.getFaceLandmark(0, largestIndex);
        FaceAttribute attribute = recognizer.getFaceAttribute(0, largestIndex);
        Pose pose = attribute == null ? null : attribute.pose;
        if (landmark == null || pose == null) return null;

        return new QualityFaceData(copyFaceInfo(largestInfo), landmark, pose);
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
        if (qualityDetector != null) {
            qualityDetector.release();
            qualityDetector = null;
        }
    }

    private static FaceInfo copyFaceInfo(FaceInfo source) {
        if (source == null) return null;
        FaceInfo copy = new FaceInfo();
        copy.confidence = source.confidence;
        copy.options = source.options;
        copy.boundingBox = source.boundingBox == null ? null : new Rect(source.boundingBox);
        copy.occlusion = source.occlusion;
        return copy;
    }

    public static String levelName(int level) {
        if (level >= 2) return "HIGH";
        if (level == 1) return "MEDIUM";
        if (level == 0) return "NOT_RECOMMEND";
        return "OFF";
    }

    public static final class FaceQualityCheckResult {
        public final boolean passed;
        public final int requiredLevel;
        public final int actualLevel;
        public final float score;
        public final String reason;

        FaceQualityCheckResult(boolean passed, int requiredLevel, int actualLevel, float score, String reason) {
            this.passed = passed;
            this.requiredLevel = requiredLevel;
            this.actualLevel = actualLevel;
            this.score = score;
            this.reason = reason;
        }

        static FaceQualityCheckResult failed(int requiredLevel, int actualLevel, float score, String reason) {
            return new FaceQualityCheckResult(false, requiredLevel, actualLevel, score, reason);
        }
    }

    private static final class QualityFaceData {
        final FaceInfo faceInfo;
        final FaceLandmark landmark;
        final Pose pose;

        QualityFaceData(FaceInfo faceInfo, FaceLandmark landmark, Pose pose) {
            this.faceInfo = faceInfo;
            this.landmark = landmark;
            this.pose = pose;
        }
    }
}
