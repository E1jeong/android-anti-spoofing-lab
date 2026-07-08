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

    private static int readIntLE(byte[] bytes, int offset) {
        return (bytes[offset] & 0xFF)
                | ((bytes[offset + 1] & 0xFF) << 8)
                | ((bytes[offset + 2] & 0xFF) << 16)
                | ((bytes[offset + 3] & 0xFF) << 24);
    }
}
