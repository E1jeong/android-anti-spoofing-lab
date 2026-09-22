package com.virditech.ac7000.calibration;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.util.Arrays;

import org.junit.Test;

public final class CalibrationTest {
    private static final byte[] BIG_ENDIAN_VALUES = {
            (byte) 0xc1, 0x10, 0x00, 0x00,
            0x41, (byte) 0xd8, 0x00, 0x00,
            0x43, 0x24, 0x00, 0x00
    };
    private static final byte[] LITTLE_ENDIAN_VALUES = {
            0x00, 0x00, 0x10, (byte) 0xc1,
            0x00, 0x00, (byte) 0xd8, 0x41,
            0x00, 0x00, 0x24, 0x43
    };

    @Test public void usesNProHorizontalConvention() {
        assertEquals(27f, Calibration.horizontalCalibration(100f, 73f), 0f);
        assertEquals(73, Calibration.mapHorizontal(100, 164f, 27f, 164f));
        assertEquals(117, Calibration.mapHorizontal(100, 164f, -19f, 177f));
        assertEquals(202, (int) (200 - 164f * -3f / 177f));
    }

    @Test public void decodesCurrentBigEndianFile() throws Exception {
        assertValues(Calibration.decode(BIG_ENDIAN_VALUES));
    }

    @Test public void writesCurrentNProBigEndianFormat() throws Exception {
        Calibration calibration = Calibration.decode(BIG_ENDIAN_VALUES);
        assertArrayEquals(BIG_ENDIAN_VALUES, Arrays.copyOf(calibration.encode(), 12));
    }

    @Test public void rejectsLegacyLittleEndianData() {
        try {
            Calibration.decode(LITTLE_ENDIAN_VALUES);
            fail("Expected little-endian calibration data to be rejected");
        } catch (IOException expected) {
            assertEquals("Calibration values are invalid", expected.getMessage());
        }
    }

    @Test public void rejectsTruncatedData() {
        try {
            Calibration.decode(new byte[11]);
            fail("Expected truncated calibration data to be rejected");
        } catch (IOException expected) {
            assertEquals("Calibration file is truncated", expected.getMessage());
        }
    }

    private static void assertValues(Calibration calibration) {
        assertEquals(-9f, calibration.getVertical(), 0f);
        assertEquals(27f, calibration.getHorizontal(), 0f);
        assertEquals(164f, calibration.getReferenceFaceWidth(), 0f);
    }
}
