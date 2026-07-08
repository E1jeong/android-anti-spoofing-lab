package com.virditech.ac7000.capture;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public final class SampleMetadataTest {
    @Test public void buildsExpectedMetadataJson() throws Exception {
        String json = SampleMetadata.build(
                768, 432, new RectValue(1, 2, 3, 4), new RectValue(5, 6, 7, 8),
                768, 432, new RectValue(9, 10, 11, 12), new RectValue(13, 14, 15, 16),
                0.1f, "high", 2, 2, 0.95f);

        JSONObject root = new JSONObject(json);
        assertEquals(1, root.getInt("schemaVersion"));
        assertEquals(0.1, root.getDouble("cropMarginRatio"), 0.0001);
        assertEquals("high", root.getString("qualityMode"));
        assertEquals(2, root.getInt("minQualityLevel"));
        assertEquals(2, root.getInt("actualQualityLevel"));
        assertEquals(0.95, root.getDouble("qualityScore"), 0.0001);

        JSONObject rgb = root.getJSONObject("rgb");
        assertEquals(768, rgb.getInt("width"));
        assertEquals(432, rgb.getInt("height"));
        assertArrayEquals(new int[]{1, 2, 3, 4}, rgb.getJSONArray("faceRect"));
        assertArrayEquals(new int[]{5, 6, 7, 8}, rgb.getJSONArray("cropRect"));

        JSONObject ir = root.getJSONObject("ir");
        assertEquals(768, ir.getInt("width"));
        assertEquals(432, ir.getInt("height"));
        assertArrayEquals(new int[]{9, 10, 11, 12}, ir.getJSONArray("mappedFaceRect"));
        assertArrayEquals(new int[]{13, 14, 15, 16}, ir.getJSONArray("cropRect"));
    }

    private static void assertArrayEquals(int[] expected, JSONArray actual) throws Exception {
        assertEquals(expected.length, actual.length());
        for (int i = 0; i < expected.length; i++) {
            assertEquals(expected[i], actual.getInt(i));
        }
    }
}
