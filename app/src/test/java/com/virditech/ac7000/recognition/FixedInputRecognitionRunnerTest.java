package com.virditech.ac7000.recognition;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class FixedInputRecognitionRunnerTest {
    @Test
    public void recognizesOnlySupportedImageExtensions() {
        assertTrue(FixedInputRecognitionRunner.isImageFilename("face.PNG"));
        assertTrue(FixedInputRecognitionRunner.isImageFilename("face.jpg"));
        assertTrue(FixedInputRecognitionRunner.isImageFilename("face.JPEG"));
        assertTrue(FixedInputRecognitionRunner.isImageFilename("face.bmp"));
        assertFalse(FixedInputRecognitionRunner.isImageFilename("result.json"));
        assertFalse(FixedInputRecognitionRunner.isImageFilename(null));
    }

    @Test
    public void percentileUsesNearestRank() {
        long[] values = {500, 100, 300, 200, 400};
        assertEquals(300L, FixedInputRecognitionRunner.percentile(values, 50));
        assertEquals(500L, FixedInputRecognitionRunner.percentile(values, 95));
    }

    @Test
    public void percentileRejectsEmptyInput() {
        try {
            FixedInputRecognitionRunner.percentile(new long[0], 50);
            fail("Expected empty input rejection");
        } catch (IllegalArgumentException expected) {
            // Expected.
        }
    }
}
