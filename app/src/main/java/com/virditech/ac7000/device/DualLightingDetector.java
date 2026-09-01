package com.virditech.ac7000.device;

import android.graphics.Bitmap;
import android.graphics.Rect;

import java.util.Arrays;
import java.util.Locale;

/**
 * Evaluates lighting conditions by fusing RGB luminance and IR camera intensity.
 * Computes global distribution percentiles (P10, P50, P90, P99) and contrast ratios
 * for empirical lighting research and optimal threshold discovery.
 */
public final class DualLightingDetector {

    public enum Condition {
        NORMAL("NORMAL", 0xFF00E676),                 // Normal balanced lighting (Green)
        INDOOR_BACKLIGHT("INDOOR BACKLIGHT", 0xFFFF9100), // Indoor backlight (Orange)
        DIRECT_SUNLIGHT("DIRECT SUNLIGHT", 0xFFFF1744),   // Direct sunlight / Natural glare (Red)
        FACE_OVEREXPOSED("OVEREXPOSED", 0xFFFF5252),      // Highlight clipped (Light Red)
        LOW_LIGHT("LOW LIGHT", 0xFF29B6F6);               // Very dark environment (Blue)

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
        public final float rgbRatio;
        public final float rgbSatPct;
        public final float irFullMean;
        public final float irFaceMean;
        public final float irSatPct;

        // Empirical lighting research metrics (Distribution & Quantiles)
        public final float rgbGlobalMean;
        public final float rgbP99;
        public final float rgbP90;
        public final float rgbP50;
        public final float rgbP10;
        public final float rgbContrastRatio;
        public final long timestampMs;

        public Result(Condition condition, boolean hasFace,
                      float rgbFaceMean, float rgbBgMean, float rgbRatio, float rgbSatPct,
                      float irFullMean, float irFaceMean, float irSatPct,
                      float rgbGlobalMean, float rgbP99, float rgbP90, float rgbP50, float rgbP10,
                      float rgbContrastRatio, long timestampMs) {
            this.condition = condition;
            this.hasFace = hasFace;
            this.rgbFaceMean = rgbFaceMean;
            this.rgbBgMean = rgbBgMean;
            this.rgbRatio = rgbRatio;
            this.rgbSatPct = rgbSatPct;
            this.irFullMean = irFullMean;
            this.irFaceMean = irFaceMean;
            this.irSatPct = irSatPct;
            this.rgbGlobalMean = rgbGlobalMean;
            this.rgbP99 = rgbP99;
            this.rgbP90 = rgbP90;
            this.rgbP50 = rgbP50;
            this.rgbP10 = rgbP10;
            this.rgbContrastRatio = rgbContrastRatio;
            this.timestampMs = timestampMs;
        }

        public String toSummary() {
            String facePrefix = hasFace ? "F" : "C";
            return String.format(Locale.US,
                    "[%s] RGB %s:%.0f B:%.0f (%.1fx, Sat:%.1f%%) | Mean:%.0f P90:%.0f P10:%.0f (CR:%.1fx) | IR:%.0f",
                    condition.label, facePrefix, rgbFaceMean, rgbBgMean, rgbRatio, rgbSatPct,
                    rgbGlobalMean, rgbP90, rgbP10, rgbContrastRatio, irFullMean);
        }

        public String toCsvRow(int sampleIndex, String tag) {
            return String.format(Locale.US,
                    "%d,%d,%s,%s,%b,%.1f,%.1f,%.1f,%.1f,%.1f,%.2f,%.1f,%.1f,%.1f,%.1f,%.1f",
                    timestampMs, sampleIndex, tag != null ? tag : "DEFAULT", condition.name(),
                    hasFace, rgbGlobalMean, rgbP99, rgbP90, rgbP50, rgbP10,
                    rgbContrastRatio, rgbSatPct, rgbFaceMean, rgbBgMean, irFullMean, irSatPct);
        }
    }

    private static final int SAMPLE_STEP = 16; // 16-pixel step (~1300 samples for 432x768, <0.3ms)
    private static final int SATURATION_THRESHOLD = 245;

    public static final float SUNLIGHT_IR_MEAN_THRESHOLD = 150.0f;
    public static final float SUNLIGHT_IR_SAT_THRESHOLD = 12.0f; // IR highlight ratio >= 12%
    public static final float SUNLIGHT_RGB_SAT_THRESHOLD = 15.0f; // RGB highlight ratio >= 15%

    public static final float BACKLIGHT_RATIO_THRESHOLD = 2.0f;
    public static final float BACKLIGHT_BG_MEAN_MIN = 160.0f;
    public static final float BACKLIGHT_FACE_MEAN_MAX = 105.0f;

    public static final float FACE_OVEREXPOSED_THRESHOLD = 230.0f;
    public static final float OVEREXPOSED_BG_THRESHOLD = 235.0f;
    public static final float LOW_LIGHT_FACE_THRESHOLD = 35.0f;

    private DualLightingDetector() {}

    public static Result evaluate(Bitmap rgb, Bitmap ir, Rect rgbFace, Rect irFace) {
        long now = System.currentTimeMillis();
        if (rgb == null) {
            return new Result(Condition.NORMAL, false, 0f, 0f, 0f, 0f, 0f, 0f, 0f,
                    0f, 0f, 0f, 0f, 0f, 1.0f, now);
        }

        int rgbW = rgb.getWidth();
        int rgbH = rgb.getHeight();

        boolean hasFace = (rgbFace != null);
        Rect effectiveRgbTarget = hasFace ? rgbFace
                : new Rect(rgbW / 4, (int)(rgbH * 0.15f), (int)(rgbW * 0.75f), (int)(rgbH * 0.65f));

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
        float rgbRatio = rgbFaceMean > 0f ? rgbBgMean / rgbFaceMean : 1.0f;
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
        float irFaceMean = 0f;
        float irSatPct = 0f;

        if (ir != null) {
            int irW = ir.getWidth();
            int irH = ir.getHeight();
            Rect effectiveIrTarget = irFace != null ? irFace
                    : new Rect(irW / 4, (int)(irH * 0.15f), (int)(irW * 0.75f), (int)(irH * 0.65f));

            long irFullSum = 0; int irFullCount = 0;
            long irFaceSum = 0; int irFaceCount = 0;
            int irSatCount = 0;

            for (int y = 0; y < irH; y += SAMPLE_STEP) {
                for (int x = 0; x < irW; x += SAMPLE_STEP) {
                    int val = ir.getPixel(x, y) & 0xFF;

                    irFullSum += val;
                    irFullCount++;
                    if (val >= SATURATION_THRESHOLD) {
                        irSatCount++;
                    }

                    if (effectiveIrTarget.contains(x, y)) {
                        irFaceSum += val;
                        irFaceCount++;
                    }
                }
            }

            irFullMean = irFullCount > 0 ? (float) irFullSum / irFullCount : 0f;
            irFaceMean = irFaceCount > 0 ? (float) irFaceSum / irFaceCount : 0f;
            irSatPct = irFullCount > 0 ? ((float) irSatCount / irFullCount) * 100f : 0f;
        }

        Condition condition = Condition.NORMAL;

        boolean strongIrSun = (irFullMean >= SUNLIGHT_IR_MEAN_THRESHOLD) || (irSatPct >= SUNLIGHT_IR_SAT_THRESHOLD);
        boolean strongRgbHighlight = (rgbSatPct >= SUNLIGHT_RGB_SAT_THRESHOLD) || (rgbBgMean >= 190.0f);

        if (strongIrSun && strongRgbHighlight) {
            condition = Condition.DIRECT_SUNLIGHT;
        } else if (hasFace && (rgbFaceMean >= FACE_OVEREXPOSED_THRESHOLD || (irFaceMean >= FACE_OVEREXPOSED_THRESHOLD && ir != null))) {
            condition = Condition.FACE_OVEREXPOSED;
        } else if (!hasFace && (rgbBgMean >= OVEREXPOSED_BG_THRESHOLD || rgbSatPct >= 30.0f)) {
            condition = Condition.FACE_OVEREXPOSED;
        } else if (rgbRatio >= BACKLIGHT_RATIO_THRESHOLD
                && rgbBgMean >= BACKLIGHT_BG_MEAN_MIN && rgbFaceMean <= BACKLIGHT_FACE_MEAN_MAX) {
            condition = Condition.INDOOR_BACKLIGHT;
        } else if (rgbFaceMean < LOW_LIGHT_FACE_THRESHOLD && rgbBgMean < LOW_LIGHT_FACE_THRESHOLD) {
            condition = Condition.LOW_LIGHT;
        }

        return new Result(condition, hasFace, rgbFaceMean, rgbBgMean, rgbRatio, rgbSatPct,
                irFullMean, irFaceMean, irSatPct,
                rgbGlobalMean, rgbP99, rgbP90, rgbP50, rgbP10, rgbContrastRatio, now);
    }
}
