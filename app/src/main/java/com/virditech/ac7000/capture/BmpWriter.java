package com.virditech.ac7000.capture;

import android.graphics.Bitmap;

import java.io.IOException;
import java.io.OutputStream;

public final class BmpWriter {
    private BmpWriter() {}

    public static void write(Bitmap bitmap, OutputStream out) throws IOException {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int[] pixels = new int[width * height];
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height);
        writeArgbPixels(width, height, pixels, out);
    }

    public static void writeArgbPixels(int width, int height, int[] pixels, OutputStream out)
            throws IOException {
        int rowSize = rowSize(width);
        int pixelDataSize = rowSize * height;
        int fileSize = 54 + pixelDataSize;

        byte[] header = new byte[54];
        header[0] = 'B';
        header[1] = 'M';
        writeIntLE(header, 2, fileSize);
        header[10] = 54;
        header[14] = 40;
        writeIntLE(header, 18, width);
        writeIntLE(header, 22, height);
        header[26] = 1;
        header[28] = 24;
        writeIntLE(header, 34, pixelDataSize);

        out.write(header);
        byte[] rowBytes = new byte[rowSize];
        for (int y = height - 1; y >= 0; y--) {
            int offset = y * width;
            int rowOffset = 0;
            for (int x = 0; x < width; x++) {
                int color = pixels[offset + x];
                rowBytes[rowOffset++] = (byte) (color & 0xFF);
                rowBytes[rowOffset++] = (byte) ((color >> 8) & 0xFF);
                rowBytes[rowOffset++] = (byte) ((color >> 16) & 0xFF);
            }
            while (rowOffset < rowBytes.length) {
                rowBytes[rowOffset++] = 0;
            }
            out.write(rowBytes);
        }
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
}
