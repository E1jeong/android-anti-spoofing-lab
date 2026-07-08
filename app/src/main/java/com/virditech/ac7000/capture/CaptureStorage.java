package com.virditech.ac7000.capture;

import android.graphics.Bitmap;
import android.graphics.Rect;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public final class CaptureStorage {
    private static final String TAG = "CaptureStorage";

    private CaptureStorage() {}

    public static File resolveRawRoot() {
        return new File("/sdcard/Pictures/raw");
    }

    public static int getNextSubjectNumber(File rawRoot, String className) {
        File baseDir = new File(rawRoot, className);
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

    public static boolean deleteSubject(File rawRoot, String className, String subjectDirName) {
        File classDir = new File(rawRoot, className);
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

    public static void recycleBitmaps(Bitmap... bitmaps) {
        for (Bitmap bitmap : bitmaps) {
            if (bitmap != null && !bitmap.isRecycled()) {
                bitmap.recycle();
            }
        }
    }

    public static SaveResult saveBitmapAsBmp(Bitmap bitmap, File file) {
        try (OutputStream out = new FileOutputStream(file)) {
            BmpWriter.write(bitmap, out);
            return SaveResult.success();
        } catch (IOException e) {
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

    public static String buildSampleMetadataJson(int rgbWidth, int rgbHeight, Rect rgbFaceRect, Rect rgbCropRect,
                                                 int irWidth, int irHeight, Rect irMappedFaceRect, Rect irCropRect,
                                                 float cropMarginRatio) {
        return SampleMetadata.build(
                rgbWidth, rgbHeight, RectValue.from(rgbFaceRect), RectValue.from(rgbCropRect),
                irWidth, irHeight, RectValue.from(irMappedFaceRect), RectValue.from(irCropRect),
                cropMarginRatio);
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
