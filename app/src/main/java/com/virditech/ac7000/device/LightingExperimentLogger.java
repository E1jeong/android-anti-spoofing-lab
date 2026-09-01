package com.virditech.ac7000.device;

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
 * to /sdcard/Pictures/lighting_experiment.csv for empirical threshold analysis.
 */
public final class LightingExperimentLogger {

    private static final String TAG = "LightingExperiment";
    private static final String CSV_HEADER =
            "timestamp_iso,sample_id,tag,condition,has_face,rgb_mean,rgb_p99,rgb_p90,rgb_p50,rgb_p10,contrast_ratio,rgb_sat_pct,face_luma,bg_luma,ir_mean,ir_sat_pct";

    private static final ExecutorService logExecutor = Executors.newSingleThreadExecutor();
    private static final AtomicInteger sampleCounter = new AtomicInteger(0);

    public interface LogCallback {
        void onLogged(int sampleId, String message);
        void onError(String error);
    }

    private LightingExperimentLogger() {}

    public static File getCsvFile() {
        File dir = new File(Environment.getExternalStorageDirectory(), "Pictures");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return new File(dir, "lighting_experiment.csv");
    }

    public static void recordSnapshot(DualLightingDetector.Result result, String tag, LogCallback callback) {
        if (result == null) {
            if (callback != null) callback.onError("No active lighting frame data");
            return;
        }

        final int sampleId = sampleCounter.incrementAndGet();
        final String effectiveTag = (tag != null && !tag.trim().isEmpty()) ? tag.trim() : "SNAPSHOT";

        logExecutor.execute(() -> {
            try {
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
                            "%s,%d,%s,%s,%b,%.1f,%.1f,%.1f,%.1f,%.1f,%.2f,%.1f,%.1f,%.1f,%.1f,%.1f",
                            isoTime, sampleId, effectiveTag, result.condition.name(),
                            result.hasFace, result.rgbGlobalMean, result.rgbP99, result.rgbP90,
                            result.rgbP50, result.rgbP10, result.rgbContrastRatio,
                            result.rgbSatPct, result.rgbFaceMean, result.rgbBgMean,
                            result.irFullMean, result.irSatPct);

                    writer.println(row);
                    writer.flush();
                }

                String summary = String.format(Locale.US,
                        "#%d [%s] Mean:%.0f High:%.0f Low:%.0f CR:%.1fx (IR:%.0f)",
                        sampleId, effectiveTag, result.rgbGlobalMean, result.rgbP90,
                        result.rgbP10, result.rgbContrastRatio, result.irFullMean);

                Log.i(TAG, "Recorded snapshot: " + summary + " -> " + csvFile.getAbsolutePath());

                if (callback != null) {
                    callback.onLogged(sampleId, summary);
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to record lighting snapshot: " + e.getMessage(), e);
                if (callback != null) {
                    callback.onError(e.getMessage());
                }
            }
        });
    }
}
