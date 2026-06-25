package com.virditech.ac7000.camera;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.media.Image;
import android.os.SystemClock;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.ScriptIntrinsicYuvToRGB;

import java.util.ArrayDeque;
import java.nio.ByteBuffer;

@SuppressWarnings("deprecation")
final class YuvConverter implements AutoCloseable {
    private static final int PORTRAIT_POOL_LIMIT = 4;
    private final RenderScript renderScript;
    private final ScriptIntrinsicYuvToRGB yuvToRgb;
    private final Paint colorPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Paint grayscalePaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Matrix rotation = new Matrix();
    private final ArrayDeque<Bitmap> portraitPool = new ArrayDeque<>(PORTRAIT_POOL_LIMIT);
    private byte[] nv21;
    private Bitmap landscape;
    private Allocation inputAllocation;
    private Allocation outputAllocation;
    private int width;
    private int height;
    private boolean closed;

    YuvConverter(Context context) {
        renderScript = RenderScript.create(context.getApplicationContext());
        yuvToRgb = ScriptIntrinsicYuvToRGB.create(renderScript, Element.U8_4(renderScript));
        ColorMatrix grayscale = new ColorMatrix();
        grayscale.setSaturation(0f);
        grayscalePaint.setColorFilter(new ColorMatrixColorFilter(grayscale));
    }

    FrameData toPortraitFrame(Image image, boolean grayscale, boolean flipHorizontal) {
        long start = SystemClock.elapsedRealtimeNanos();
        ensureBuffers(image.getWidth(), image.getHeight());
        copyToNv21(image, nv21);
        inputAllocation.copyFromUnchecked(nv21);
        yuvToRgb.setInput(inputAllocation);
        yuvToRgb.forEach(outputAllocation);
        outputAllocation.copyTo(landscape);

        Bitmap portrait = obtainPortraitBitmap();
        rotation.reset();
        rotation.setRotate(-90f);
        rotation.postTranslate(0f, width);
        if (flipHorizontal) {
            rotation.postScale(-1f, 1f, height / 2f, width / 2f);
        }
        new Canvas(portrait).drawBitmap(landscape, rotation, grayscale ? grayscalePaint : colorPaint);
        long conversionMs = (SystemClock.elapsedRealtimeNanos() - start) / 1_000_000L;
        return new FrameData(portrait, image.getTimestamp(), conversionMs, this::releasePortraitBitmap);
    }

    private void ensureBuffers(int newWidth, int newHeight) {
        if (newWidth == width && newHeight == height) return;
        releaseBuffers();
        closed = false;
        width = newWidth;
        height = newHeight;
        nv21 = new byte[width * height * 3 / 2];
        landscape = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        inputAllocation = Allocation.createSized(renderScript, Element.U8(renderScript), nv21.length);
        outputAllocation = Allocation.createFromBitmap(renderScript, landscape);
    }

    private static void copyToNv21(Image image, byte[] output) {
        Image.Plane[] planes = image.getPlanes();
        ByteBuffer y = planes[0].getBuffer().slice();
        int yRowStride = planes[0].getRowStride();
        int yPixelStride = planes[0].getPixelStride();
        int width = image.getWidth();
        int height = image.getHeight();
        int offset = 0;
        for (int row = 0; row < height; row++) {
            int rowStart = row * yRowStride;
            if (yPixelStride == 1) {
                y.position(rowStart);
                y.get(output, offset, width);
                offset += width;
            } else {
                for (int column = 0; column < width; column++) {
                    output[offset++] = y.get(rowStart + column * yPixelStride);
                }
            }
        }

        ByteBuffer u = planes[1].getBuffer().slice();
        ByteBuffer v = planes[2].getBuffer().slice();
        int uRowStride = planes[1].getRowStride();
        int vRowStride = planes[2].getRowStride();
        int uPixelStride = planes[1].getPixelStride();
        int vPixelStride = planes[2].getPixelStride();
        for (int row = 0; row < height / 2; row++) {
            int uRowStart = row * uRowStride;
            int vRowStart = row * vRowStride;
            for (int column = 0; column < width / 2; column++) {
                output[offset++] = v.get(vRowStart + column * vPixelStride);
                output[offset++] = u.get(uRowStart + column * uPixelStride);
            }
        }
    }

    private Bitmap obtainPortraitBitmap() {
        synchronized (portraitPool) {
            Bitmap bitmap = portraitPool.pollFirst();
            if (bitmap != null && !bitmap.isRecycled()) return bitmap;
        }
        return Bitmap.createBitmap(height, width, Bitmap.Config.ARGB_8888);
    }

    private void releasePortraitBitmap(Bitmap bitmap) {
        synchronized (portraitPool) {
            if (!closed && bitmap.getWidth() == height && bitmap.getHeight() == width
                    && portraitPool.size() < PORTRAIT_POOL_LIMIT) {
                portraitPool.addLast(bitmap);
                return;
            }
        }
        bitmap.recycle();
    }

    private void releaseBuffers() {
        if (outputAllocation != null) outputAllocation.destroy();
        if (inputAllocation != null) inputAllocation.destroy();
        if (landscape != null) landscape.recycle();
        synchronized (portraitPool) {
            while (!portraitPool.isEmpty()) {
                Bitmap bitmap = portraitPool.removeFirst();
                if (!bitmap.isRecycled()) bitmap.recycle();
            }
        }
        outputAllocation = null;
        inputAllocation = null;
        landscape = null;
        nv21 = null;
    }

    @Override public void close() {
        closed = true;
        releaseBuffers();
        yuvToRgb.destroy();
        renderScript.destroy();
    }
}
