package com.virditech.ac7000.model;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class ClassificationResultTest {
    @Test
    public void labelsMatchModelOutputOrder() {
        assertArrayEquals(
                new String[]{"LIVE", "PRINT", "PICTURE", "MASK", "DISPLAY", "PMASK"},
                ClassificationResult.LABELS);
    }

    @Test
    public void topIndexSupportsPictureMaskClass() {
        ClassificationResult result = new ClassificationResult(
                new float[]{0.05f, 0.10f, 0.15f, 0.20f, 0.10f, 0.40f}, 1L);

        assertEquals(5, result.topIndex);
    }
}
