package com.virditech.ac7000.capture;

import android.graphics.Bitmap;
import android.graphics.Rect;
import android.util.Log;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public final class CaptureStorage {
    private static final String TAG = "CaptureStorage";
    public static final String QUALITY_HIGH = "high";
    public static final String QUALITY_MEDIUM = "medium";
    public static final String QUALITY_OFF = "off";

    private CaptureStorage() {}

    public static File resolveRawRoot() {
        return new File("/sdcard/Pictures/raw");
    }

    /** Used by attack_live without quality or environment parameters. */
    public static int getNextSubjectNumber(File rawRoot, String className) {
        return getNextSubjectNumber(rawRoot, className, null, null);
    }

    public static int getNextSubjectNumber(File rawRoot, String className, String environment, String qualityMode) {
        return getNextSubjectNumberInBase(collectionBaseDir(rawRoot, className, environment, qualityMode), className);
    }

    private static int getNextSubjectNumberInBase(File baseDir, String className) {
        if (!baseDir.exists()) {
            return 1;
        }
        int maxNum = 0;
        File[] files = baseDir.listFiles();
        if (files != null) {
            String prefix = className + "_";
            for (File file : files) {
                if (file.isDirectory()) {
                    String name = file.getName();
                    if (name.startsWith(prefix)) {
                        try {
                            int num = Integer.parseInt(name.substring(prefix.length()));
                            if (num > maxNum) {
                                maxNum = num;
                            }
                        } catch (NumberFormatException e) {
                            // ignore
                        }
                    }
                }
            }
        }
        return maxNum + 1;
    }

    public static int getNextSubjectNumber(File rawRoot, CaptureConfig config) {
        if (config == null) throw new IllegalArgumentException("config must not be null");
        if (config.isNoFace()) {
            return getNextSubjectNumberInBase(collectionBaseDir(rawRoot, config), config.className);
        }
        return getNextSubjectNumber(rawRoot, config.className, config.environment, config.qualityMode);
    }

    public static boolean prepareRawRoot(File rawRoot) {
        try {
            if (!rawRoot.isDirectory() && !rawRoot.mkdirs()) {
                Log.e(TAG, "Unable to create collection raw root: "
                        + rawRoot.getAbsolutePath());
                return false;
            }
            File probe = new File(rawRoot, ".wtest");
            try (FileOutputStream out = new FileOutputStream(probe)) {
                out.write(0);
            }
            if (!probe.delete() && probe.exists()) {
                Log.e(TAG, "Unable to delete collection raw root probe: "
                        + probe.getAbsolutePath());
            }
            return true;
        } catch (IOException | SecurityException e) {
            Log.e(TAG, "Collection raw root is not writable: "
                    + rawRoot.getAbsolutePath(), e);
            return false;
        }
    }

    public static boolean deleteSubject(File rawRoot, String className, String environment,
                                        String qualityMode, String subjectDirName) {
        return deleteSubjectInBase(collectionBaseDir(rawRoot, className, environment, qualityMode), subjectDirName);
    }

    private static boolean deleteSubjectInBase(File classDir, String subjectDirName) {
        File subjectDir = new File(classDir, subjectDirName);
        try {
            String classPath = classDir.getCanonicalPath();
            String subjectPath = subjectDir.getCanonicalPath();
            if (!subjectPath.startsWith(classPath + File.separator)) {
                Log.e(TAG, "Refusing to delete outside collection class folder: " + subjectPath);
                return false;
            }
            return deleteRecursively(subjectDir);
        } catch (IOException e) {
            Log.e(TAG, "Unable to delete canceled collection folder", e);
            return false;
        }
    }

    public static boolean deleteSubject(File rawRoot, CaptureConfig config, String subjectDirName) {
        if (config == null) throw new IllegalArgumentException("config must not be null");
        if (config.isNoFace()) {
            return deleteSubjectInBase(collectionBaseDir(rawRoot, config), subjectDirName);
        }
        return deleteSubject(rawRoot, config.className, config.environment, config.qualityMode, subjectDirName);
    }

    public static SaveResult saveBitmapAsBmp(Bitmap bitmap, File file) {
        try (OutputStream out = new BufferedOutputStream(new FileOutputStream(file))) {
            BmpWriter.write(bitmap, out);
            return SaveResult.success();
        } catch (IOException e) {
            Log.e(TAG, "Unable to save " + file.getAbsolutePath(), e);
            return SaveResult.failure(e.getMessage());
        }
    }

    public static SaveResult saveBitmapRegionAsBmp(Bitmap bitmap, Rect region, File file) {
        try (OutputStream out = new BufferedOutputStream(new FileOutputStream(file))) {
            BmpWriter.write(bitmap, region.left, region.top, region.width(), region.height(), out);
            return SaveResult.success();
        } catch (IOException | IllegalArgumentException e) {
            Log.e(TAG, "Unable to save " + file.getAbsolutePath(), e);
            return SaveResult.failure(e.getMessage());
        }
    }

    public static SaveResult saveTextFile(String text, File file) {
        try (OutputStream out = new FileOutputStream(file)) {
            out.write(text.getBytes(StandardCharsets.UTF_8));
            return SaveResult.success();
        } catch (IOException e) {
            Log.e(TAG, "Unable to save " + file.getAbsolutePath(), e);
            return SaveResult.failure(e.getMessage());
        }
    }

    public static SaveResult saveCompleteSample(Bitmap rgb, Rect rgbCrop, Bitmap ir, Rect irCrop,
                                                String metadataJson, LandmarkMetadata landmarks, File sampleDir) {
        if (landmarks == null) return SaveResult.failure("landmark metadata is required");
        if (!sampleDir.isDirectory() && !sampleDir.mkdirs()) {
            Log.e(TAG, "Unable to create collection sample folder: " + sampleDir.getAbsolutePath());
            return SaveResult.failure("unable to create " + sampleDir.getAbsolutePath());
        }
        File landmarkFile = new File(sampleDir, "landmarks.json");
        if (landmarkFile.exists()) {
            return SaveResult.failure("Separate landmarks.json is unsupported: " + landmarkFile.getAbsolutePath());
        }
        File metadataFile = new File(sampleDir, "meta.json");
        if (metadataFile.exists() && (!metadataFile.isFile() || !metadataFile.delete())) {
            return SaveResult.failure("unable to replace " + metadataFile.getAbsolutePath());
        }
        SaveResult result = saveBitmapAsBmp(rgb, new File(sampleDir, "RGB.bmp"));
        if (!result.saved) return result;
        result = saveBitmapRegionAsBmp(rgb, rgbCrop, new File(sampleDir, "cropRGB.bmp"));
        if (!result.saved) return result;
        result = saveBitmapAsBmp(ir, new File(sampleDir, "IR.bmp"));
        if (!result.saved) return result;
        result = saveBitmapRegionAsBmp(ir, irCrop, new File(sampleDir, "cropIR.bmp"));
        if (!result.saved) return result;
        return saveMetadata(metadataJson, landmarks, sampleDir);
    }

    public static SaveResult saveNoFaceSample(Bitmap rgb, String metadataJson,
                                              LandmarkMetadata landmarks, File sampleDir) {
        if (landmarks == null) return SaveResult.failure("landmark metadata is required");
        if (!sampleDir.isDirectory() && !sampleDir.mkdirs()) {
            Log.e(TAG, "Unable to create no-face sample folder: " + sampleDir.getAbsolutePath());
            return SaveResult.failure("unable to create " + sampleDir.getAbsolutePath());
        }
        File landmarkFile = new File(sampleDir, "landmarks.json");
        if (landmarkFile.exists()) {
            return SaveResult.failure("Separate landmarks.json is unsupported: " + landmarkFile.getAbsolutePath());
        }
        File metadataFile = new File(sampleDir, "meta.json");
        if (metadataFile.exists() && (!metadataFile.isFile() || !metadataFile.delete())) {
            return SaveResult.failure("unable to replace " + metadataFile.getAbsolutePath());
        }
        SaveResult result = saveBitmapAsBmp(rgb, new File(sampleDir, "RGB.bmp"));
        if (!result.saved) return result;
        return saveMetadata(metadataJson, landmarks, sampleDir);
    }

    static SaveResult saveMetadata(String metadataJson, LandmarkMetadata landmarks, File sampleDir) {
        try {
            String merged = SampleMetadata.withLandmarks(metadataJson, landmarks,
                    sha256(new File(sampleDir, "RGB.bmp")));
            return saveTextFile(merged, new File(sampleDir, "meta.json"));
        } catch (IOException | SecurityException | IllegalArgumentException | IllegalStateException e) {
            Log.e(TAG, "Unable to save capture landmark provenance", e);
            return SaveResult.failure(e.getMessage());
        }
    }

    private static String sha256(File file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (FileInputStream in = new FileInputStream(file)) {
                byte[] buffer = new byte[8192];
                int count;
                while ((count = in.read(buffer)) != -1) digest.update(buffer, 0, count);
            }
            StringBuilder hex = new StringBuilder(64);
            for (byte value : digest.digest()) {
                hex.append(Character.forDigit((value & 0xff) >>> 4, 16));
                hex.append(Character.forDigit(value & 0xf, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    public static String buildSampleMetadataJson(int rgbWidth, int rgbHeight, Rect rgbFaceRect, Rect rgbCropRect,
                                                 int irWidth, int irHeight, Rect irMappedFaceRect, Rect irCropRect,
                                                 float cropMarginRatio, String className, String environment,
                                                 String qualityMode, int minQualityLevel,
                                                 int actualQualityLevel, float qualityScore) {
        return SampleMetadata.build(
                rgbWidth, rgbHeight, RectValue.from(rgbFaceRect), RectValue.from(rgbCropRect),
                irWidth, irHeight, RectValue.from(irMappedFaceRect), RectValue.from(irCropRect),
                cropMarginRatio, className, environment, qualityMode, minQualityLevel, actualQualityLevel, qualityScore);
    }

    public static String buildNoFaceMetadataJson(int rgbWidth, int rgbHeight,
                                                 String environment, String sceneType) {
        return SampleMetadata.buildNoFace(rgbWidth, rgbHeight, environment, sceneType);
    }

    public static File sampleDir(File rawRoot, String className, String environment, String qualityMode,
                                 String subjectDirName, int sampleIndex) {
        return new File(new File(collectionBaseDir(rawRoot, className, environment, qualityMode), subjectDirName),
                String.valueOf(sampleIndex));
    }

    /** Used by attack_live without environment parameter. */
    public static File sampleDir(File rawRoot, String className, String qualityMode,
                                 String subjectDirName, int sampleIndex) {
        return sampleDir(rawRoot, className, null, qualityMode, subjectDirName, sampleIndex);
    }

    public static File sampleDir(File rawRoot, CaptureConfig config, String subjectDirName, int sampleIndex) {
        if (config == null) throw new IllegalArgumentException("config must not be null");
        return new File(new File(collectionBaseDir(rawRoot, config), subjectDirName),
                String.valueOf(sampleIndex));
    }

    public static File collectionBaseDir(File rawRoot, String className, String environment, String qualityMode) {
        File classDir = new File(rawRoot, className);
        if ("live".equals(className)) {
            if (qualityMode == null || qualityMode.isEmpty()) return classDir;
            return new File(classDir, qualityMode);
        } else {
            if (environment == null || environment.isEmpty()) return classDir;
            return new File(classDir, environment);
        }
    }

    public static File collectionBaseDir(File rawRoot, CaptureConfig config) {
        if (config == null) throw new IllegalArgumentException("config must not be null");
        if (config.isNoFace()) {
            File environmentDir = new File(new File(rawRoot, CaptureConfig.NO_FACE_CLASS_NAME), config.environment);
            return new File(environmentDir, config.sceneType);
        }
        return collectionBaseDir(rawRoot, config.className, config.environment, config.qualityMode);
    }

    private static boolean deleteRecursively(File file) {
        if (!file.exists()) return true;
        boolean deleted = true;
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) {
                deleted &= deleteRecursively(child);
            }
        }
        return deleted && (file.delete() || !file.exists());
    }

    public static final class SaveResult {
        public final boolean saved;
        public final String errorMessage;

        private SaveResult(boolean saved, String errorMessage) {
            this.saved = saved;
            this.errorMessage = errorMessage;
        }

        static SaveResult success() {
            return new SaveResult(true, "");
        }

        static SaveResult failure(String errorMessage) {
            return new SaveResult(false, errorMessage == null ? "" : errorMessage);
        }
    }
}
