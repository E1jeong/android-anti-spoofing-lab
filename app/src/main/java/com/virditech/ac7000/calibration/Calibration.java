package com.virditech.ac7000.calibration;

import android.graphics.Rect;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

public final class Calibration {
    private static final File FILE = new File("/sdcard/devlocal/CalibConfig.dat");
    private final float vertical;
    private final float horizontal;
    private final float referenceFaceWidth;

    private Calibration(float vertical, float horizontal, float referenceFaceWidth) {
        this.vertical = vertical;
        this.horizontal = horizontal;
        this.referenceFaceWidth = referenceFaceWidth;
    }

    public static Calibration load() throws IOException {
        byte[] bytes = new byte[12];
        try (FileInputStream input = new FileInputStream(FILE)) {
            int offset = 0;
            while (offset < bytes.length) {
                int count = input.read(bytes, offset, bytes.length - offset);
                if (count < 0) throw new IOException("Calibration file is truncated");
                offset += count;
            }
        }
        float vertical = readFloat(bytes, 0);
        float horizontal = readFloat(bytes, 4);
        float faceWidth = readFloat(bytes, 8);
        if (!Float.isFinite(vertical) || !Float.isFinite(horizontal) || !Float.isFinite(faceWidth)
                || Math.abs(vertical) > 200f || Math.abs(horizontal) > 200f || faceWidth <= 0f || faceWidth > 500f) {
            throw new IOException("Calibration values are invalid");
        }
        return new Calibration(vertical, horizontal, faceWidth);
    }

    public Rect rgbToIr(Rect rgb, int width, int height) {
        float xOffset = rgb.width() * horizontal / referenceFaceWidth;
        float yOffset = rgb.width() * vertical / referenceFaceWidth;
        return new Rect(
                clamp(Math.round(rgb.left + xOffset), 0, width),
                clamp(Math.round(rgb.top - yOffset), 0, height),
                clamp(Math.round(rgb.right + xOffset), 0, width),
                clamp(Math.round(rgb.bottom - yOffset), 0, height));
    }

    private static float readFloat(byte[] bytes, int offset) {
        int bits = bytes[offset] << 24
                | (bytes[offset + 1] & 0xff) << 16
                | (bytes[offset + 2] & 0xff) << 8
                | (bytes[offset + 3] & 0xff);
        return Float.intBitsToFloat(bits);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
