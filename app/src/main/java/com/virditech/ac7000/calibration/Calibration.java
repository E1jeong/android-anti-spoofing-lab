package com.virditech.ac7000.calibration;

import android.graphics.Rect;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

public final class Calibration {
    private static final File FILE = new File("/sdcard/devlocal/CalibConfig.dat");
    private static volatile File appFile;
    private final float vertical;
    private final float horizontal;
    private final float referenceFaceWidth;

    public float getVertical() { return vertical; }
    public float getHorizontal() { return horizontal; }
    public float getReferenceFaceWidth() { return referenceFaceWidth; }

    private Calibration(float vertical, float horizontal, float referenceFaceWidth) {
        this.vertical = vertical;
        this.horizontal = horizontal;
        this.referenceFaceWidth = referenceFaceWidth;
    }

    /**
     * Registers an app-private fallback location for the calibration file. The production
     * path ({@link #FILE}) stays the primary so device firmware can still read it; on
     * hardware where the app cannot write external storage (e.g. running as the system
     * UID under FUSE) the calibration transparently falls back to this app-owned file.
     */
    public static void setAppStorageDir(File dir) {
        appFile = new File(dir, "CalibConfig.dat");
    }

    public static Calibration identity() {
        return new Calibration(0f, 0f, 1f);
    }

    public static Calibration fromFaces(Rect rgb, Rect ir, int width) {
        float vertical = rgb.centerY() - ir.centerY();
        float horizontal = horizontalCalibration(rgb.centerX(), ir.centerX());
        float faceWidth = rgb.width();
        if (Math.abs(vertical) > 200f || Math.abs(horizontal) > 200f || faceWidth <= 0f || faceWidth > 500f) {
            throw new IllegalArgumentException("Measured calibration values are invalid");
        }
        return new Calibration(vertical, horizontal, faceWidth);
    }

    public static Calibration load() throws IOException {
        try {
            return readFrom(FILE);
        } catch (IOException primary) {
            if (appFile != null) return readFrom(appFile);
            throw primary;
        }
    }

    private static Calibration readFrom(File file) throws IOException {
        byte[] bytes = new byte[12];
        try (FileInputStream input = new FileInputStream(file)) {
            int offset = 0;
            while (offset < bytes.length) {
                int count = input.read(bytes, offset, bytes.length - offset);
                if (count < 0) throw new IOException("Calibration file is truncated");
                offset += count;
            }
        }
        return decode(bytes);
    }

    static Calibration decode(byte[] bytes) throws IOException {
        if (bytes == null || bytes.length < 12) throw new IOException("Calibration file is truncated");
        float vertical = readBigEndianFloat(bytes, 0);
        float horizontal = readBigEndianFloat(bytes, 4);
        float faceWidth = readBigEndianFloat(bytes, 8);
        if (!Float.isFinite(vertical) || !Float.isFinite(horizontal) || !Float.isFinite(faceWidth)
                || Math.abs(vertical) > 200f || Math.abs(horizontal) > 200f || faceWidth < 1f || faceWidth > 500f) {
            throw new IOException("Calibration values are invalid");
        }
        return new Calibration(vertical, horizontal, faceWidth);
    }

    public Rect rgbToIr(Rect rgb, int width, int height) {
        Rect ir = new Rect(rgb);
        if ((vertical != 0f || horizontal != 0f) && referenceFaceWidth != 0f) {
            float verticalOffset = rgb.width() * vertical / referenceFaceWidth;
            ir.left = mapHorizontal(rgb.left, rgb.width(), horizontal, referenceFaceWidth);
            ir.right = mapHorizontal(rgb.right, rgb.width(), horizontal, referenceFaceWidth);
            ir.top = (int) (rgb.top - verticalOffset);
            ir.bottom = (int) (rgb.bottom - verticalOffset);

            if (ir.left < 0) ir.left = 0;
            if (ir.right >= width) ir.right = width - 1;
            if (ir.top < 0) ir.top = 0;
            if (ir.bottom >= height) ir.bottom = height - 1;
        }
        return ir;
    }

    public void save() throws IOException {
        try {
            writeTo(FILE);
        } catch (IOException primary) {
            if (appFile == null) throw primary;
            writeTo(appFile);
        }
    }

    private void writeTo(File file) throws IOException {
        byte[] bytes = encode();
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("Unable to create calibration directory");
        }
        try (FileOutputStream output = new FileOutputStream(file)) {
            output.write(bytes);
            output.flush();
        }
    }

    byte[] encode() {
        byte[] bytes = new byte[64];
        writeBigEndianFloat(bytes, 0, vertical);
        writeBigEndianFloat(bytes, 4, horizontal);
        writeBigEndianFloat(bytes, 8, referenceFaceWidth);
        return bytes;
    }

    static float horizontalCalibration(float rgbCenterX, float irCenterX) {
        return rgbCenterX - irCenterX;
    }

    static int mapHorizontal(int coordinate, float rgbFaceWidth, float horizontal,
                             float referenceFaceWidth) {
        return (int) (coordinate - rgbFaceWidth * horizontal / referenceFaceWidth);
    }

    private static float readBigEndianFloat(byte[] bytes, int offset) {
        int bits = bytes[offset] << 24
                | (bytes[offset + 1] & 0xff) << 16
                | (bytes[offset + 2] & 0xff) << 8
                | (bytes[offset + 3] & 0xff);
        return Float.intBitsToFloat(bits);
    }

    private static void writeBigEndianFloat(byte[] bytes, int offset, float value) {
        int bits = Float.floatToIntBits(value);
        bytes[offset] = (byte) (bits >>> 24);
        bytes[offset + 1] = (byte) (bits >>> 16);
        bytes[offset + 2] = (byte) (bits >>> 8);
        bytes[offset + 3] = (byte) bits;
    }

}
