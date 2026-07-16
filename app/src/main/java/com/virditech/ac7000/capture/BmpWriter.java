package com.virditech.ac7000.capture;

import android.graphics.Bitmap;

import java.io.IOException;
import java.io.OutputStream;

public final class BmpWriter {
    private static final int STRIPE_ROWS = 16;
    private static final ThreadLocal<Buffers> BUFFERS = ThreadLocal.withInitial(Buffers::new);

    private BmpWriter() {}

    public static void write(Bitmap bitmap, OutputStream out) throws IOException {
        write(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), out);
    }

    public static void write(Bitmap bitmap, int left, int top, int width, int height,
                             OutputStream out) throws IOException {
        validateRegion(bitmap.getWidth(), bitmap.getHeight(), left, top, width, height);
        int rowSize = rowSize(width);
        writeHeader(width, height, rowSize, out);
        Buffers buffers = BUFFERS.get();
        buffers.ensureCapacity(width * STRIPE_ROWS, rowSize * STRIPE_ROWS);
        for (int bottom = height; bottom > 0; bottom -= STRIPE_ROWS) {
            int stripeHeight = Math.min(STRIPE_ROWS, bottom);
            int startY = bottom - stripeHeight;
            bitmap.getPixels(buffers.pixels, 0, width, left, top + startY, width, stripeHeight);
            int byteCount = encodeBottomUp(width, stripeHeight, rowSize,
                    buffers.pixels, buffers.bytes);
            out.write(buffers.bytes, 0, byteCount);
        }
    }

    public static void writeArgbPixels(int width, int height, int[] pixels, OutputStream out)
            throws IOException {
        writeArgbPixels(width, height, pixels, 0, 0, width, height, out);
    }

    static void writeArgbPixels(int sourceWidth, int sourceHeight, int[] pixels,
                                int left, int top, int width, int height, OutputStream out)
            throws IOException {
        validateRegion(sourceWidth, sourceHeight, left, top, width, height);
        if (pixels.length < sourceWidth * sourceHeight) {
            throw new IllegalArgumentException("Pixel buffer is too small");
        }
        int rowSize = rowSize(width);
        writeHeader(width, height, rowSize, out);
        Buffers buffers = BUFFERS.get();
        buffers.ensureCapacity(width * STRIPE_ROWS, rowSize * STRIPE_ROWS);
        for (int bottom = height; bottom > 0; bottom -= STRIPE_ROWS) {
            int stripeHeight = Math.min(STRIPE_ROWS, bottom);
            int startY = bottom - stripeHeight;
            for (int y = 0; y < stripeHeight; y++) {
                System.arraycopy(pixels, (top + startY + y) * sourceWidth + left,
                        buffers.pixels, y * width, width);
            }
            int byteCount = encodeBottomUp(width, stripeHeight, rowSize,
                    buffers.pixels, buffers.bytes);
            out.write(buffers.bytes, 0, byteCount);
        }
    }

    private static void validateRegion(int sourceWidth, int sourceHeight,
                                       int left, int top, int width, int height) {
        if (left < 0 || top < 0 || width <= 0 || height <= 0
                || left + width > sourceWidth || top + height > sourceHeight) {
            throw new IllegalArgumentException("Bitmap region is out of bounds");
        }
    }

    private static int encodeBottomUp(int width, int height, int rowSize,
                                      int[] pixels, byte[] bytes) {
        int byteOffset = 0;
        for (int y = height - 1; y >= 0; y--) {
            int pixelOffset = y * width;
            int rowEnd = byteOffset + rowSize;
            for (int x = 0; x < width; x++) {
                int color = pixels[pixelOffset + x];
                bytes[byteOffset++] = (byte) (color & 0xFF);
                bytes[byteOffset++] = (byte) ((color >> 8) & 0xFF);
                bytes[byteOffset++] = (byte) ((color >> 16) & 0xFF);
            }
            while (byteOffset < rowEnd) bytes[byteOffset++] = 0;
        }
        return byteOffset;
    }

    private static void writeHeader(int width, int height, int rowSize, OutputStream out)
            throws IOException {
        int pixelDataSize = rowSize * height;
        byte[] header = new byte[54];
        header[0] = 'B';
        header[1] = 'M';
        writeIntLE(header, 2, 54 + pixelDataSize);
        header[10] = 54;
        header[14] = 40;
        writeIntLE(header, 18, width);
        writeIntLE(header, 22, height);
        header[26] = 1;
        header[28] = 24;
        writeIntLE(header, 34, pixelDataSize);
        out.write(header);
    }

    public static int rowSize(int width) {
        return ((width * 24 + 31) / 32) * 4;
    }

    private static void writeIntLE(byte[] bytes, int offset, int value) {
        bytes[offset] = (byte) (value & 0xFF);
        bytes[offset + 1] = (byte) ((value >> 8) & 0xFF);
        bytes[offset + 2] = (byte) ((value >> 16) & 0xFF);
        bytes[offset + 3] = (byte) ((value >> 24) & 0xFF);
    }

    private static final class Buffers {
        int[] pixels = new int[0];
        byte[] bytes = new byte[0];

        void ensureCapacity(int pixelCount, int byteCount) {
            if (pixels.length < pixelCount) pixels = new int[pixelCount];
            if (bytes.length < byteCount) bytes = new byte[byteCount];
        }
    }
}
