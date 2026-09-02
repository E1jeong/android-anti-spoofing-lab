package com.virditech.ac7000.device;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class DualLightingDetectorTest {

    @Test
    public void evaluateWithNullBitmapReturnsNormalResult() {
        DualLightingDetector.Result result = DualLightingDetector.evaluate(null, null, null);
        assertNotNull(result);
        assertEquals(DualLightingDetector.Condition.NORMAL, result.condition);
        assertFalse(result.hasFace);
        assertEquals(0f, result.rgbFaceMean, 0.001f);
        assertEquals(0f, result.rgbBgMean, 0.001f);
        assertEquals(0f, result.irFullMean, 0.001f);
        assertFalse(result.hasIrFrame);
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
    public void resultRetainsSnapshotAnalysisFields() {
        DualLightingDetector.Result result = new DualLightingDetector.Result(
                DualLightingDetector.Condition.BACKLIGHT,
                true,
                80f, 240f, 25f,
                180f, 20f, true,
                160f, 255f, 230f, 140f, 50f,
                4.6f, 1725184912000L
        );

        assertEquals(DualLightingDetector.Condition.BACKLIGHT, result.condition);
        assertEquals(255f, result.rgbP99, 0.001f);
        assertEquals(140f, result.rgbP50, 0.001f);
        assertEquals(20f, result.irSatPct, 0.001f);
        assertTrue(result.hasIrFrame);
    }

    @Test
    public void classifyRgbRequiresAllBacklightThresholds() {
        assertEquals(DualLightingDetector.Condition.BACKLIGHT,
                DualLightingDetector.classifyRgb(100f, 200f));
        assertEquals(DualLightingDetector.Condition.BACKLIGHT,
                DualLightingDetector.classifyRgb(0f, 160f));
        assertEquals(DualLightingDetector.Condition.NORMAL,
                DualLightingDetector.classifyRgb(106f, 220f));
        assertEquals(DualLightingDetector.Condition.NORMAL,
                DualLightingDetector.classifyRgb(80f, 159f));
        assertEquals(DualLightingDetector.Condition.NORMAL,
                DualLightingDetector.classifyRgb(100f, 199f));
    }

}
