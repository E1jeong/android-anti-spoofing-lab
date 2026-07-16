package com.virditech.ac7000.capture;

import org.junit.Test;

import java.io.ByteArrayOutputStream;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public final class BmpWriterTest {
    @Test public void writesTwentyFourBitBmpHeaderAndBottomUpRows() throws Exception {
        int[] pixels = {
                0xFFFF0000, 0xFF00FF00,
                0xFF0000FF, 0xFFFFFFFF
        };
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        BmpWriter.writeArgbPixels(2, 2, pixels, out);

        byte[] bytes = out.toByteArray();
        assertEquals('B', bytes[0]);
        assertEquals('M', bytes[1]);
        assertEquals(70, readIntLE(bytes, 2));
        assertEquals(54, readIntLE(bytes, 10));
        assertEquals(40, readIntLE(bytes, 14));
        assertEquals(2, readIntLE(bytes, 18));
        assertEquals(2, readIntLE(bytes, 22));
        assertEquals(16, readIntLE(bytes, 34));

        byte[] pixelBytes = new byte[16];
        System.arraycopy(bytes, 54, pixelBytes, 0, pixelBytes.length);
        assertArrayEquals(new byte[]{
                (byte) 0xFF, 0x00, 0x00, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, 0x00, 0x00,
                0x00, 0x00, (byte) 0xFF, 0x00, (byte) 0xFF, 0x00, 0x00, 0x00
        }, pixelBytes);
    }

    @Test public void rowSizePadsRowsToFourByteBoundary() {
        assertEquals(4, BmpWriter.rowSize(1));
        assertEquals(8, BmpWriter.rowSize(2));
        assertEquals(12, BmpWriter.rowSize(3));
    }

    @Test public void preservesBottomUpOrderAcrossStripeBoundary() throws Exception {
        int width = 2;
        int height = 20;
        int[] pixels = new int[width * height];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) pixels[y * width + x] = y;
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        BmpWriter.writeArgbPixels(width, height, pixels, out);

        byte[] bytes = out.toByteArray();
        int rowSize = BmpWriter.rowSize(width);
        assertEquals(54 + rowSize * height, bytes.length);
        assertEquals(19, bytes[54]);
        assertEquals(0, bytes[54 + rowSize * (height - 1)]);
    }

    @Test public void writesSelectedRegionWithoutCreatingCroppedPixels() throws Exception {
        int[] pixels = {
                0xFF000001, 0xFF000002, 0xFF000003, 0xFF000004,
                0xFF000005, 0xFF000006, 0xFF000007, 0xFF000008,
                0xFF000009, 0xFF00000A, 0xFF00000B, 0xFF00000C
        };
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        BmpWriter.writeArgbPixels(4, 3, pixels, 1, 1, 2, 2, out);

        byte[] bytes = out.toByteArray();
        assertEquals(2, readIntLE(bytes, 18));
        assertEquals(2, readIntLE(bytes, 22));
        assertEquals(0x0A, bytes[54]);
        assertEquals(0x0B, bytes[57]);
        assertEquals(0x06, bytes[62]);
        assertEquals(0x07, bytes[65]);
    }

    private static int readIntLE(byte[] bytes, int offset) {
        return (bytes[offset] & 0xFF)
                | ((bytes[offset + 1] & 0xFF) << 8)
                | ((bytes[offset + 2] & 0xFF) << 16)
                | ((bytes[offset + 3] & 0xFF) << 24);
    }
}
