package com.virditech.ac7000.device;

import android.graphics.Bitmap;
import android.graphics.Rect;

/**
 * Reports frame-to-frame RGB pixel change in the center observation area.
 * This is an experiment signal, not a person-identification or backlight decision.
 */
public final class ForegroundEntryDetector {

    public enum State {
        WARMING_UP("WARMING UP", 0xFFFFFFFF),
        WATCHING("WATCHING", 0xFF64C8FF),
        CHANGED("CHANGED", 0xFFFFC107);

        public final String label;
        public final int color;

        State(String label, int color) {
            this.label = label;
            this.color = color;
        }
    }

    public static final class Result {
        public final State state;
        public final float rgbDelta;

        Result(State state, float rgbDelta) {
            this.state = state;
            this.rgbDelta = rgbDelta;
        }
    }

    private static final int SAMPLE_STEP = 16;
    static final float CHANGE_DELTA_MIN = 16f;

    private int[] previousRgbSamples;
    private int previousWidth;
    private int previousHeight;

    public void reset() {
        previousRgbSamples = null;
        previousWidth = 0;
        previousHeight = 0;
    }

    public Result evaluate(Bitmap rgb) {
        if (rgb == null || rgb.isRecycled()) {
            reset();
            return new Result(State.WARMING_UP, 0f);
        }

        int width = rgb.getWidth();
        int height = rgb.getHeight();
        Rect roi = virtualCenterRoi(width, height);
        int maxSamples = ((width + SAMPLE_STEP - 1) / SAMPLE_STEP)
                * ((height + SAMPLE_STEP - 1) / SAMPLE_STEP);
        int[] currentRgbSamples = new int[maxSamples];
        int sampleCount = 0;
        for (int y = 0; y < height; y += SAMPLE_STEP) {
            for (int x = 0; x < width; x += SAMPLE_STEP) {
                if (roi.contains(x, y)) {
                    currentRgbSamples[sampleCount++] = rgb.getPixel(x, y);
                }
            }
        }

        if (previousRgbSamples == null || previousWidth != width || previousHeight != height
                || previousRgbSamples.length != sampleCount) {
            previousRgbSamples = copyOf(currentRgbSamples, sampleCount);
            previousWidth = width;
            previousHeight = height;
            return new Result(State.WARMING_UP, 0f);
        }

        long deltaSum = 0L;
        for (int i = 0; i < sampleCount; i++) {
            deltaSum += rgbDistance(previousRgbSamples[i], currentRgbSamples[i]);
        }
        float rgbDelta = sampleCount == 0 ? 0f : (float) deltaSum / sampleCount;
        State state = isChanged(rgbDelta) ? State.CHANGED : State.WATCHING;
        previousRgbSamples = copyOf(currentRgbSamples, sampleCount);
        return new Result(state, rgbDelta);
    }

    static boolean isChanged(float delta) {
        return delta >= CHANGE_DELTA_MIN;
    }

    private static int[] copyOf(int[] source, int length) {
        int[] copy = new int[length];
        System.arraycopy(source, 0, copy, 0, length);
        return copy;
    }

    private static int rgbDistance(int previous, int current) {
        int red = Math.abs(((previous >> 16) & 0xFF) - ((current >> 16) & 0xFF));
        int green = Math.abs(((previous >> 8) & 0xFF) - ((current >> 8) & 0xFF));
        int blue = Math.abs((previous & 0xFF) - (current & 0xFF));
        return (red + green + blue) / 3;
    }

    public static Rect virtualCenterRoi(int width, int height) {
        return new Rect(width / 4, (int) (height * 0.4f),
                (int) (width * 0.75f), (int) (height * 0.8f));
    }
}
