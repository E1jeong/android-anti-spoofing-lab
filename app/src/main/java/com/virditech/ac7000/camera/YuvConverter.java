package com.virditech.ac7000.camera;

import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.media.Image;

import java.nio.ByteBuffer;

final class YuvConverter {
    private YuvConverter() {}

    static Bitmap toPortraitBitmap(Image image, boolean grayscale) {
        int width = image.getWidth();
        int height = image.getHeight();
        int[] pixels = new int[width * height];
        Image.Plane[] planes = image.getPlanes();
        ByteBuffer yBuffer = planes[0].getBuffer();
        ByteBuffer uBuffer = planes[1].getBuffer();
        ByteBuffer vBuffer = planes[2].getBuffer();
        int yRowStride = planes[0].getRowStride();
        int uvRowStride = planes[1].getRowStride();
        int uvPixelStride = planes[1].getPixelStride();

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int yy = yBuffer.get(y * yRowStride + x) & 0xff;
                int r = yy;
                int g = yy;
                int b = yy;
                if (!grayscale) {
                    int uvIndex = (y / 2) * uvRowStride + (x / 2) * uvPixelStride;
                    int u = (uBuffer.get(uvIndex) & 0xff) - 128;
                    int v = (vBuffer.get(uvIndex) & 0xff) - 128;
                    r = clamp((int) (yy + 1.402f * v));
                    g = clamp((int) (yy - 0.344136f * u - 0.714136f * v));
                    b = clamp((int) (yy + 1.772f * u));
                }
                pixels[y * width + x] = 0xff000000 | (r << 16) | (g << 8) | b;
            }
        }

        Bitmap landscape = Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888);
        Matrix matrix = new Matrix();
        matrix.postRotate(-90f);
        Bitmap portrait = Bitmap.createBitmap(landscape, 0, 0, width, height, matrix, true);
        if (portrait != landscape) landscape.recycle();
        return portrait;
    }

    private static int clamp(int value) {
        return Math.max(0, Math.min(255, value));
    }
}
