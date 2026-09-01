package com.virditech.ac7000.device;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class DualLightingDetectorTest {

    @Test
    public void evaluateWithNullBitmapReturnsNormalResult() {
        DualLightingDetector.Result result = DualLightingDetector.evaluate(null, null, null, null);
        assertNotNull(result);
        assertEquals(DualLightingDetector.Condition.NORMAL, result.condition);
        assertFalse(result.hasFace);
        assertEquals(0f, result.rgbFaceMean, 0.001f);
        assertEquals(0f, result.rgbBgMean, 0.001f);
        assertEquals(0f, result.irFullMean, 0.001f);
        assertEquals(0f, result.rgbGlobalMean, 0.001f);
        assertEquals(1.0f, result.rgbContrastRatio, 0.001f);
    }

    @Test
    public void conditionColorAndLabelsAreDefined() {
        for (DualLightingDetector.Condition condition : DualLightingDetector.Condition.values()) {
            assertNotNull(condition.label);
            assertTrue(condition.label.length() > 0);
            assertTrue(condition.color != 0);
        }
    }

    @Test
    public void resultSummaryAndCsvContainExpectedFields() {
        DualLightingDetector.Result result = new DualLightingDetector.Result(
                DualLightingDetector.Condition.DIRECT_SUNLIGHT,
                true,
                80f, 240f, 3.0f, 25f,
                180f, 150f, 20f,
                160f, 255f, 230f, 140f, 50f,
                4.6f, 1725184912000L
        );

        String summary = result.toSummary();
        assertTrue(summary.contains("DIRECT SUNLIGHT"));
        assertTrue(summary.contains("Mean:160"));
        assertTrue(summary.contains("P90:230"));
        assertTrue(summary.contains("CR:4.6x"));

        String csvRow = result.toCsvRow(1, "INDOOR_WHITE");
        assertTrue(csvRow.contains("INDOOR_WHITE"));
        assertTrue(csvRow.contains("DIRECT_SUNLIGHT"));
        assertTrue(csvRow.contains("4.60"));
    }
}
