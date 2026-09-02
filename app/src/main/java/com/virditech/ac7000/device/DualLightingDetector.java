package com.virditech.ac7000.device;

import android.graphics.Bitmap;
import android.graphics.Rect;

import java.util.Arrays;

/**
 * Evaluates RGB backlight conditions while retaining IR measurements for analysis.
 * Computes global distribution percentiles (P10, P50, P90, P99) and contrast ratios
 * for empirical lighting research and optimal threshold discovery.
 */
public final class DualLightingDetector {

    public enum Condition {
        NORMAL("NORMAL", 0xFF00E676),
        BACKLIGHT("BACKLIGHT", 0xFFFF9100);

        public final String label;
        public final int color;

        Condition(String label, int color) {
            this.label = label;
            this.color = color;
        }
    }

    public static final class Result {
        public final Condition condition;
        public final boolean hasFace;
        public final float rgbFaceMean;
        public final float rgbBgMean;
        public final float rgbSatPct;
        public final float irFullMean;
        public final float irSatPct;
        public final boolean hasIrFrame;

        // Empirical lighting research metrics (Distribution & Quantiles)
        public final float rgbGlobalMean;
        public final float rgbP99;
        public final float rgbP90;
        public final float rgbP50;
        public final float rgbP10;
        public final float rgbContrastRatio;
        public final long timestampMs;

        public Result(Condition condition, boolean hasFace,
                      float rgbFaceMean, float rgbBgMean, float rgbSatPct,
                      float irFullMean, float irSatPct, boolean hasIrFrame,
                      float rgbGlobalMean, float rgbP99, float rgbP90, float rgbP50, float rgbP10,
                      float rgbContrastRatio, long timestampMs) {
            this.condition = condition;
            this.hasFace = hasFace;
            this.rgbFaceMean = rgbFaceMean;
            this.rgbBgMean = rgbBgMean;
            this.rgbSatPct = rgbSatPct;
            this.irFullMean = irFullMean;
            this.irSatPct = irSatPct;
            this.hasIrFrame = hasIrFrame;
            this.rgbGlobalMean = rgbGlobalMean;
            this.rgbP99 = rgbP99;
            this.rgbP90 = rgbP90;
            this.rgbP50 = rgbP50;
            this.rgbP10 = rgbP10;
            this.rgbContrastRatio = rgbContrastRatio;
            this.timestampMs = timestampMs;
        }
    }

    private static final int SAMPLE_STEP = 16; // 16-pixel step (~1300 samples for 432x768, <0.3ms)
    private static final int SATURATION_THRESHOLD = 245;

    // Provisional RGB-only thresholds. Tune only from labeled device snapshots.
    public static final float BACKLIGHT_RATIO_THRESHOLD = 2.0f;
    public static final float BACKLIGHT_BG_MEAN_MIN = 160.0f;
    public static final float BACKLIGHT_FACE_MEAN_MAX = 105.0f;

    private DualLightingDetector() {}

    public static Result evaluate(Bitmap rgb, Bitmap ir, Rect rgbFace) {
        long now = System.currentTimeMillis();
        if (rgb == null) {
            return new Result(Condition.NORMAL, false, 0f, 0f, 0f, 0f, 0f, false,
                    0f, 0f, 0f, 0f, 0f, 1.0f, now);
        }

        int rgbW = rgb.getWidth();
        int rgbH = rgb.getHeight();

        boolean hasFace = (rgbFace != null);
        Rect effectiveRgbTarget = hasFace ? rgbFace : virtualCenterRoi(rgbW, rgbH);

        int maxSamples = ((rgbW / SAMPLE_STEP) + 2) * ((rgbH / SAMPLE_STEP) + 2);
        int[] lumaSamples = new int[maxSamples];

        long rgbFaceSum = 0; int rgbFaceCount = 0;
        long rgbBgSum = 0;   int rgbBgCount = 0;
        long rgbGlobalSum = 0;
        int rgbSatCount = 0; int rgbTotal = 0;

        for (int y = 0; y < rgbH; y += SAMPLE_STEP) {
            for (int x = 0; x < rgbW; x += SAMPLE_STEP) {
                int pixel = rgb.getPixel(x, y);
                int r = (pixel >> 16) & 0xFF;
                int g = (pixel >> 8) & 0xFF;
                int b = pixel & 0xFF;
                int luma = (r * 299 + g * 587 + b * 114) / 1000;

                if (rgbTotal < lumaSamples.length) {
                    lumaSamples[rgbTotal] = luma;
                }
                rgbTotal++;
                rgbGlobalSum += luma;

                if (luma >= SATURATION_THRESHOLD) {
                    rgbSatCount++;
                }

                if (effectiveRgbTarget.contains(x, y)) {
                    rgbFaceSum += luma;
                    rgbFaceCount++;
                } else {
                    rgbBgSum += luma;
                    rgbBgCount++;
                }
            }
        }

        float rgbFaceMean = rgbFaceCount > 0 ? (float) rgbFaceSum / rgbFaceCount : 0f;
        float rgbBgMean = rgbBgCount > 0 ? (float) rgbBgSum / rgbBgCount : 0f;
        float rgbSatPct = rgbTotal > 0 ? ((float) rgbSatCount / rgbTotal) * 100f : 0f;
        float rgbGlobalMean = rgbTotal > 0 ? (float) rgbGlobalSum / rgbTotal : 0f;

        // Calculate distribution quantiles (P10, P50, P90, P99)
        float rgbP10 = 0f, rgbP50 = 0f, rgbP90 = 0f, rgbP99 = 0f;
        if (rgbTotal > 0) {
            int validCount = Math.min(rgbTotal, lumaSamples.length);
            Arrays.sort(lumaSamples, 0, validCount);
            rgbP10 = lumaSamples[(int)(validCount * 0.10f)];
            rgbP50 = lumaSamples[(int)(validCount * 0.50f)];
            rgbP90 = lumaSamples[Math.min((int)(validCount * 0.90f), validCount - 1)];
            rgbP99 = lumaSamples[Math.min((int)(validCount * 0.99f), validCount - 1)];
        }
        float rgbContrastRatio = rgbP90 / Math.max(rgbP10, 1.0f);

        float irFullMean = 0f;
        float irSatPct = 0f;

        if (ir != null) {
            int irW = ir.getWidth();
            int irH = ir.getHeight();

            long irFullSum = 0; int irFullCount = 0;
            int irSatCount = 0;

            for (int y = 0; y < irH; y += SAMPLE_STEP) {
                for (int x = 0; x < irW; x += SAMPLE_STEP) {
                    int val = ir.getPixel(x, y) & 0xFF;

                    irFullSum += val;
                    irFullCount++;
                    if (val >= SATURATION_THRESHOLD) {
                        irSatCount++;
                    }
                }
            }

            irFullMean = irFullCount > 0 ? (float) irFullSum / irFullCount : 0f;
            irSatPct = irFullCount > 0 ? ((float) irSatCount / irFullCount) * 100f : 0f;
        }

        Condition condition = classifyRgb(rgbFaceMean, rgbBgMean);

        return new Result(condition, hasFace, rgbFaceMean, rgbBgMean, rgbSatPct,
                irFullMean, irSatPct, ir != null,
                rgbGlobalMean, rgbP99, rgbP90, rgbP50, rgbP10, rgbContrastRatio, now);
    }

    static Condition classifyRgb(float targetMean, float backgroundMean) {
        float ratio = backgroundMean / Math.max(targetMean, 1.0f);
        return ratio >= BACKLIGHT_RATIO_THRESHOLD
                && backgroundMean >= BACKLIGHT_BG_MEAN_MIN
                && targetMean <= BACKLIGHT_FACE_MEAN_MAX
                ? Condition.BACKLIGHT : Condition.NORMAL;
    }

    public static Rect virtualCenterRoi(int width, int height) {
        return new Rect(width / 4, (int) (height * 0.4f),
                (int) (width * 0.75f), (int) (height * 0.8f));
    }
}
