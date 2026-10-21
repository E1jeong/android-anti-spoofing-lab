package com.virditech.ac7000.capture;

import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.*;

public final class LandmarkMetadataTest {
    private static LandmarkMetadata sample(float[][] points, String className) {
        return new LandmarkMetadata(100, 120, new RectValue(10, 10, 90, 110), points,
                "FACEME", "7.8.2", 123L, className);
    }

    @Test public void missingAndInvalidPointsAreExplicitAndNeverSelected() throws Exception {
        JSONObject root = new JSONObject(sample(new float[][] {
                null, {Float.NaN, 20}, {100, 40}, {30, Float.POSITIVE_INFINITY}, {-1, 60}
        }, "live").build("image-hash", "meta-hash"));
        JSONObject points = root.getJSONObject("landmarks");
        for (String name : new String[] {"eyeImageLeft", "eyeImageRight", "noseTip",
                "mouthImageLeft", "mouthImageRight"}) {
            assertFalse(points.getJSONObject(name).getBoolean("useForTraining"));
        }
        assertTrue(points.getJSONObject("eyeImageLeft").isNull("x"));
        assertTrue(points.getJSONObject("eyeImageRight").isNull("x"));
        assertEquals("missing", points.getJSONObject("eyeImageLeft").getString("reason"));
    }

    @Test public void snapshotPreservesFrameAndSourceIndicesWhileSortingImageSides() throws Exception {
        float[][] source = {{70, 30}, {30, 30}, {50, 50}, {65, 80}, {35, 80}};
        LandmarkMetadata snapshot = sample(source, "live");
        source[1][0] = 99;
        JSONObject root = new JSONObject(snapshot.build("rgb", "meta"));
        assertEquals("original_image_pixels", root.getString("coordinateSystem"));
        assertEquals("rgb", root.getString("imageSha256"));
        assertEquals("meta", root.getString("metaSha256"));
        assertEquals(123L, root.getLong("rgbTimestampNs"));
        JSONObject left = root.getJSONObject("landmarks").getJSONObject("eyeImageLeft");
        assertEquals(30, left.getDouble("x"), 0);
        assertEquals(1, left.getInt("sourceIndex"));
        assertTrue(left.getBoolean("useForTraining"));
        assertEquals("unknown", left.getString("visibility"));
    }

    @Test public void dentalMasksRetainPredictionsButExcludeNoseAndMouth() throws Exception {
        for (String className : new String[] {"dental_white", "dental_black"}) {
            JSONObject points = new JSONObject(sample(new float[][] {
                    {30, 30}, {70, 30}, {50, 50}, {35, 80}, {65, 80}
            }, className).build("rgb", "meta")).getJSONObject("landmarks");
            assertTrue(points.getJSONObject("eyeImageLeft").getBoolean("useForTraining"));
            for (String name : new String[] {"noseTip", "mouthImageLeft", "mouthImageRight"}) {
                assertEquals("masked_by_class", points.getJSONObject(name).getString("reason"));
                assertFalse(points.getJSONObject(name).getBoolean("useForTraining"));
                assertFalse(points.getJSONObject(name).isNull("x"));
            }
        }
    }

    @Test public void unavailableDetectorStillProducesFiveDisabledRecords() throws Exception {
        JSONObject root = new JSONObject(new LandmarkMetadata(100, 120,
                new RectValue(10, 10, 90, 110), null, "MEDIAPIPE", null, 456L, "live")
                .build("rgb", "meta"));
        assertEquals("not_detected", root.getString("status"));
        assertEquals(5, root.getJSONObject("landmarks").length());
        assertFalse(root.getJSONObject("landmarks").getJSONObject("noseTip")
                .getBoolean("useForTraining"));
    }

    @Test public void finitePointOutsideExpandedFaceIsRetainedButExcluded() throws Exception {
        JSONObject nose = new JSONObject(new LandmarkMetadata(100, 120,
                new RectValue(30, 30, 70, 90), new float[][] {null, null, {5, 50}},
                "FACEME", "7.8.2", 1L, "live").build("rgb", "meta"))
                .getJSONObject("landmarks").getJSONObject("noseTip");
        assertEquals(5, nose.getDouble("x"), 0);
        assertEquals("outside_expanded_face", nose.getString("reason"));
        assertFalse(nose.getBoolean("useForTraining"));
    }
}
