package com.virditech.ac7000.device;

import android.graphics.Bitmap;
import android.graphics.Rect;

import java.util.Locale;

/**
 * Evaluates lighting conditions by fusing RGB luminance and IR camera intensity.
 * Distinguishes natural sunlight/glare (strong IR component) from indoor backlight (low IR component).
 * Supports evaluation both with and without detected faces (using a virtual center ROI when no face is present).
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

        public Result(Condition condition, boolean hasFace,
                      float rgbFaceMean, float rgbBgMean, float rgbRatio, float rgbSatPct,
                      float irFullMean, float irFaceMean, float irSatPct) {
            this.condition = condition;
            this.hasFace = hasFace;
            this.rgbFaceMean = rgbFaceMean;
            this.rgbBgMean = rgbBgMean;
            this.rgbRatio = rgbRatio;
            this.rgbSatPct = rgbSatPct;
            this.irFullMean = irFullMean;
            this.irFaceMean = irFaceMean;
            this.irSatPct = irSatPct;
        }

        public String toSummary() {
            String facePrefix = hasFace ? "F" : "C";
            return String.format(Locale.US,
                    "[%s] RGB %s:%.0f B:%.0f (%.1fx, Sat:%.1f%%) | IR Mean:%.0f (Sat:%.1f%%)",
                    condition.label, facePrefix, rgbFaceMean, rgbBgMean, rgbRatio, rgbSatPct,
                    irFullMean, irSatPct);
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
        if (rgb == null) {
            return new Result(Condition.NORMAL, false, 0f, 0f, 0f, 0f, 0f, 0f, 0f);
        }

        int rgbW = rgb.getWidth();
        int rgbH = rgb.getHeight();

        boolean hasFace = (rgbFace != null);
        // If no face is detected, evaluate using a virtual center ROI where a user's face would normally be located
        Rect effectiveRgbTarget = hasFace ? rgbFace
                : new Rect(rgbW / 4, (int)(rgbH * 0.15f), (int)(rgbW * 0.75f), (int)(rgbH * 0.65f));

        long rgbFaceSum = 0; int rgbFaceCount = 0;
        long rgbBgSum = 0;   int rgbBgCount = 0;
        int rgbSatCount = 0; int rgbTotal = 0;

        for (int y = 0; y < rgbH; y += SAMPLE_STEP) {
            for (int x = 0; x < rgbW; x += SAMPLE_STEP) {
                int pixel = rgb.getPixel(x, y);
                int r = (pixel >> 16) & 0xFF;
                int g = (pixel >> 8) & 0xFF;
                int b = pixel & 0xFF;
                int luma = (r * 299 + g * 587 + b * 114) / 1000;

                rgbTotal++;
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
                irFullMean, irFaceMean, irSatPct);
    }
}
