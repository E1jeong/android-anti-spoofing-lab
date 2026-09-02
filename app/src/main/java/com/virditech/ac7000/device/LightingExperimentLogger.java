package com.virditech.ac7000.device;

import android.graphics.Bitmap;
import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Handles logging and CSV persistence of real-time lighting experiment snapshots
 * along with corresponding RGB/IR JPEG images to /sdcard/Pictures/lighting_snapshots/
 * for 1:1 empirical visual and numerical analysis.
 */
public final class LightingExperimentLogger {

    private static final String TAG = "LightingExperiment";
    private static final String CSV_HEADER =
            "timestamp_iso,sample_id,tag,rgb_image,ir_image,condition,has_face,has_ir_frame,rgb_mean,rgb_p99,rgb_p90,rgb_p50,rgb_p10,contrast_ratio,rgb_sat_pct,face_luma,bg_luma,ir_mean,ir_sat_pct";

    private static final ExecutorService logExecutor = Executors.newSingleThreadExecutor();
    private static final AtomicInteger sampleCounter = new AtomicInteger(0);

    public interface LogCallback {
        void onLogged(int sampleId, String rgbFileName, String message);
        void onError(String error);
    }

    private LightingExperimentLogger() {}

    public static File getPicturesDir() {
        File dir = new File(Environment.getExternalStorageDirectory(), "Pictures");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }

    public static File getSnapshotsDir() {
        File dir = new File(getPicturesDir(), "lighting_snapshots");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }

    public static File getCsvFile() {
        return new File(getPicturesDir(), "lighting_experiment.csv");
    }

    public static void recordSnapshot(DualLightingDetector.Result result,
                                       Bitmap rgbBitmap,
                                       Bitmap irBitmap,
                                       String tag,
                                       LogCallback callback) {
        if (result == null) {
            if (callback != null) callback.onError("No active lighting frame data");
            if (rgbBitmap != null) rgbBitmap.recycle();
            if (irBitmap != null) irBitmap.recycle();
            return;
        }

        final int sampleId = sampleCounter.incrementAndGet();
        final String effectiveTag = (tag != null && !tag.trim().isEmpty()) ? tag.trim() : "EXP";

        logExecutor.execute(() -> {
            try {
                File snapshotsDir = getSnapshotsDir();
                String rgbFileName = String.format(Locale.US, "exp_%03d_RGB.jpg", sampleId);
                String irFileName = (irBitmap != null)
                        ? String.format(Locale.US, "exp_%03d_IR.jpg", sampleId)
                        : "N/A";

                // Save RGB JPEG image
                if (rgbBitmap != null && !rgbBitmap.isRecycled()) {
                    File rgbFile = new File(snapshotsDir, rgbFileName);
                    try (FileOutputStream out = new FileOutputStream(rgbFile)) {
                        rgbBitmap.compress(Bitmap.CompressFormat.JPEG, 90, out);
                        out.flush();
                    }
                }

                // Save IR JPEG image if available
                if (irBitmap != null && !irBitmap.isRecycled()) {
                    File irFile = new File(snapshotsDir, irFileName);
                    try (FileOutputStream out = new FileOutputStream(irFile)) {
                        irBitmap.compress(Bitmap.CompressFormat.JPEG, 90, out);
                        out.flush();
                    }
                }

                // Append CSV row
                File csvFile = getCsvFile();
                boolean isNewFile = !csvFile.exists() || csvFile.length() == 0;

                try (PrintWriter writer = new PrintWriter(new OutputStreamWriter(
                        new FileOutputStream(csvFile, true), StandardCharsets.UTF_8))) {
                    if (isNewFile) {
                        writer.println(CSV_HEADER);
                    }

                    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US);
                    String isoTime = sdf.format(new Date(result.timestampMs));

                    String row = String.format(Locale.US,
                            "%s,%d,%s,%s,%s,%s,%b,%b,%.1f,%.1f,%.1f,%.1f,%.1f,%.2f,%.1f,%.1f,%.1f,%.1f,%.1f",
                            isoTime, sampleId, effectiveTag, rgbFileName, irFileName,
                            result.condition.name(), result.hasFace, result.hasIrFrame, result.rgbGlobalMean,
                            result.rgbP99, result.rgbP90, result.rgbP50, result.rgbP10,
                            result.rgbContrastRatio, result.rgbSatPct, result.rgbFaceMean,
                            result.rgbBgMean, result.irFullMean, result.irSatPct);

                    writer.println(row);
                    writer.flush();
                }

                String summary = String.format(Locale.US,
                        "#%d [%s] %s | Mean:%.0f P90:%.0f P10:%.0f CR:%.1fx",
                        sampleId, effectiveTag, rgbFileName, result.rgbGlobalMean,
                        result.rgbP90, result.rgbP10, result.rgbContrastRatio);

                Log.i(TAG, "Recorded snapshot & images: " + summary + " in " + snapshotsDir.getAbsolutePath());

                if (callback != null) {
                    callback.onLogged(sampleId, rgbFileName, summary);
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to record lighting snapshot: " + e.getMessage(), e);
                if (callback != null) {
                    callback.onError(e.getMessage());
                }
            } finally {
                if (rgbBitmap != null && !rgbBitmap.isRecycled()) rgbBitmap.recycle();
                if (irBitmap != null && !irBitmap.isRecycled()) irBitmap.recycle();
            }
        });
    }
}
