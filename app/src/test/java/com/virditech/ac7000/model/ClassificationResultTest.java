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
                new float[]{0.05f, 0.10f, 0.15f, 0.20f, 0.10f, 0.40f}, 2L, 1L);

        assertEquals(5, result.topIndex);
        assertEquals(2L, result.preprocessMs);
    }

    @Test
    public void pairedSlotSumsPreprocessAndInvokeDurations() {
        ClassificationResult rgb = new ClassificationResult(
                new float[]{1f, 0f, 0f, 0f, 0f, 0f}, 3L, 5L);
        ClassificationResult ir = new ClassificationResult(
                new float[]{1f, 0f, 0f, 0f, 0f, 0f}, 7L, 11L);

        SlotClassificationResult result = new SlotClassificationResult(null, rgb, ir);

        assertEquals(10L, result.preprocessMs);
        assertEquals(16L, result.inferenceMs);
    }
}
