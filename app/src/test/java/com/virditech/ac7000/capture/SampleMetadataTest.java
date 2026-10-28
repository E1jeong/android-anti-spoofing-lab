package com.virditech.ac7000.capture;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;

public final class SampleMetadataTest {
    @Test public void buildsExpectedMetadataJson() throws Exception {
        String json = SampleMetadata.build(
                768, 432, new RectValue(1, 2, 3, 4), new RectValue(5, 6, 7, 8),
                768, 432, new RectValue(9, 10, 11, 12), new RectValue(13, 14, 15, 16),
                0.1f, "live", "indoor", "high", 2, 2, 0.95f);

        JSONObject root = new JSONObject(json);
        assertEquals(2, root.getInt("schemaVersion"));
        assertEquals("live", root.getString("className"));
        assertEquals("indoor", root.getString("environment"));
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

    @Test public void nonLiveMetadataIncludesClassNameAndEnvironmentWithNullQuality() throws Exception {
        String json = SampleMetadata.build(
                768, 432, new RectValue(1, 2, 3, 4), new RectValue(5, 6, 7, 8),
                768, 432, new RectValue(9, 10, 11, 12), new RectValue(13, 14, 15, 16),
                0.1f, "mask", "outdoor", null, -1, -1, 0.0f);

        JSONObject root = new JSONObject(json);
        assertEquals(2, root.getInt("schemaVersion"));
        assertEquals("mask", root.getString("className"));
        assertEquals("outdoor", root.getString("environment"));
        assertTrue(root.isNull("qualityMode"));
        assertEquals(-1, root.getInt("minQualityLevel"));
        assertEquals(-1, root.getInt("actualQualityLevel"));
        assertEquals(0.0, root.getDouble("qualityScore"), 0.0001);
    }

    @Test public void integratedMetadataPreservesCaptureAndBindsLandmarks() throws Exception {
        String original = "{\"schemaVersion\":2,\"qualityScore\":0.0,\"note\":\"한글\",\"optional\":null,\"rgb\":{\"width\":100,\"height\":120}}";
        LandmarkMetadata landmarks = new LandmarkMetadata(100, 120, new RectValue(10, 10, 90, 110),
                new float[][] {{30, 30}, {70, 30}, {50, 50}, {35, 80}, {65, 80}},
                "FACEME", "7.8.2", 123L, "live");
        JSONObject merged = new JSONObject(SampleMetadata.withLandmarks(original, landmarks, "rgb-hash"));
        assertEquals(2, merged.getInt("schemaVersion"));
        assertEquals("한글", merged.getString("note"));
        assertTrue(merged.isNull("optional"));
        JSONObject label = merged.getJSONObject("landmarks");
        assertEquals("rgb-hash", label.getString("imageSha256"));
        assertEquals(5, label.getJSONObject("points").length());
        assertFalse(label.has("metaSha256"));
        assertEquals("35592e3bd3a554c8d8bbfb897ff4f585ed917d73d42a2edb34bc2ac987b008ee", label.getString("captureSha256"));
    }

    @Test public void noFaceMetadataUsesNullFaceCropAndIrFields() throws Exception {
        JSONObject root = new JSONObject(SampleMetadata.buildNoFace(432, 768, "indoor", "hand"));

        assertEquals(2, root.getInt("schemaVersion"));
        assertEquals("no_face", root.getString("captureType"));
        assertEquals("indoor", root.getString("environment"));
        assertEquals("hand", root.getString("sceneType"));
        assertFalse(root.has("className"));
        assertTrue(root.isNull("ir"));
        assertTrue(root.isNull("cropMarginRatio"));
        JSONObject rgb = root.getJSONObject("rgb");
        assertEquals(432, rgb.getInt("width"));
        assertEquals(768, rgb.getInt("height"));
        assertTrue(rgb.isNull("faceRect"));
        assertTrue(rgb.isNull("cropRect"));

        JSONObject merged = new JSONObject(SampleMetadata.withLandmarks(
                root.toString(), LandmarkMetadata.notDetected(432, 768, 123L), "rgb-hash"));
        assertEquals("not_detected", merged.getJSONObject("landmarks").getString("status"));
        assertEquals("rgb-hash", merged.getJSONObject("landmarks").getString("imageSha256"));
        assertEquals(64, merged.getJSONObject("landmarks").getString("captureSha256").length());
    }

    @Test public void rejectsNonV2CaptureOrAlreadyEmbeddedLandmarks() {
        LandmarkMetadata landmarks = new LandmarkMetadata(100, 120, new RectValue(10, 10, 90, 110),
                null, "FACEME", "7.8.2", 123L, "live");
        for (String invalid : new String[] {
                "{}", "{\"schemaVersion\":1}", "{\"schemaVersion\":\"2\"}",
                "{\"schemaVersion\":true}", "{\"schemaVersion\":2.0}",
                "{\"schemaVersion\":2,\"landmarks\":{}}"
        }) {
            assertThrows(IllegalArgumentException.class,
                    () -> SampleMetadata.withLandmarks(invalid, landmarks, "rgb-hash"));
        }
    }

    private static void assertArrayEquals(int[] expected, JSONArray actual) throws Exception {
        assertEquals(expected.length, actual.length());
        for (int i = 0; i < expected.length; i++) {
            assertEquals(expected[i], actual.getInt(i));
        }
    }
}
