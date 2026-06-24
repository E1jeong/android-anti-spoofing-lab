package com.virditech.ac7000.calibration;

import android.graphics.Rect;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
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

    public static Calibration identity() {
        return new Calibration(0f, 0f, 1f);
    }

    public static Calibration fromFaces(Rect rgb, Rect ir) {
        float vertical = rgb.centerY() - ir.centerY();
        float horizontal = ir.centerX() - rgb.centerX();
        float faceWidth = rgb.width();
        if (Math.abs(vertical) > 200f || Math.abs(horizontal) > 200f || faceWidth <= 0f || faceWidth > 500f) {
            throw new IllegalArgumentException("Measured calibration values are invalid");
        }
        return new Calibration(vertical, horizontal, faceWidth);
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

    public void save() throws IOException {
        byte[] bytes = new byte[64];
        writeFloat(bytes, 0, vertical);
        writeFloat(bytes, 4, horizontal);
        writeFloat(bytes, 8, referenceFaceWidth);
        File parent = FILE.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("Unable to create calibration directory");
        }
        try (FileOutputStream output = new FileOutputStream(FILE)) {
            output.write(bytes);
            output.flush();
        }
    }

    private static float readFloat(byte[] bytes, int offset) {
        int bits = bytes[offset] << 24
                | (bytes[offset + 1] & 0xff) << 16
                | (bytes[offset + 2] & 0xff) << 8
                | (bytes[offset + 3] & 0xff);
        return Float.intBitsToFloat(bits);
    }

    private static void writeFloat(byte[] bytes, int offset, float value) {
        int bits = Float.floatToIntBits(value);
        bytes[offset] = (byte) (bits >>> 24);
        bytes[offset + 1] = (byte) (bits >>> 16);
        bytes[offset + 2] = (byte) (bits >>> 8);
        bytes[offset + 3] = (byte) bits;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
