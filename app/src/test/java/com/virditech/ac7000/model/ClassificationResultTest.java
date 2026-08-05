package com.virditech.ac7000.model;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class ClassificationResultTest {
    @Test
    public void labelsMatchModelOutputOrder() {
        assertArrayEquals(
                new String[]{
                        "LIVE", "PRINT", "PICTURE", "MASK", "DISPLAY", "PMASK",
                        "CURVED_PRINT", "CURVED_MASK", "CURVED_PICTURE", "CURVED_PMASK"
                },
                ClassificationResult.LABELS);
    }

    @Test
    public void topIndexSupportsCurvedPictureMaskClass() {
        ClassificationResult result = new ClassificationResult(
                new float[]{0.01f, 0.02f, 0.03f, 0.04f, 0.05f, 0.06f, 0.07f, 0.08f, 0.09f, 0.55f},
                2L, 1L);

        assertEquals(9, result.topIndex);
        assertEquals(2L, result.preprocessMs);
    }

    @Test
    public void displayLabelShortensCurvedClassNames() {
        assertEquals("C PRINT", ClassificationResult.displayLabel(6));
        assertEquals("C PMASK", ClassificationResult.displayLabel(9));
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
