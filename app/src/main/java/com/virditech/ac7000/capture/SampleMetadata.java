package com.virditech.ac7000.capture;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public final class SampleMetadata {
    private SampleMetadata() {}

    public static String build(int rgbWidth, int rgbHeight, RectValue rgbFaceRect, RectValue rgbCropRect,
                               int irWidth, int irHeight, RectValue irMappedFaceRect, RectValue irCropRect,
                               float cropMarginRatio, String qualityMode, int minQualityLevel,
                               int actualQualityLevel, float qualityScore) {
        try {
            JSONObject root = new JSONObject();
            root.put("schemaVersion", 1);
            root.put("rgb", frameMetadata(rgbWidth, rgbHeight, "faceRect", rgbFaceRect, rgbCropRect));
            root.put("ir", frameMetadata(irWidth, irHeight, "mappedFaceRect", irMappedFaceRect, irCropRect));
            root.put("cropMarginRatio", cropMarginRatio);
            root.put("qualityMode", qualityMode == null ? JSONObject.NULL : qualityMode);
            root.put("minQualityLevel", minQualityLevel);
            root.put("actualQualityLevel", actualQualityLevel);
            root.put("qualityScore", qualityScore);
            return root.toString(2);
        } catch (JSONException e) {
            throw new IllegalStateException("Unable to build sample metadata", e);
        }
    }

    private static JSONObject frameMetadata(int width, int height, String faceRectName,
                                            RectValue faceRect, RectValue cropRect)
            throws JSONException {
        JSONObject object = new JSONObject();
        object.put("width", width);
        object.put("height", height);
        object.put(faceRectName, rectToJson(faceRect));
        object.put("cropRect", rectToJson(cropRect));
        return object;
    }

    private static JSONArray rectToJson(RectValue rect) {
        JSONArray array = new JSONArray();
        array.put(rect.left);
        array.put(rect.top);
        array.put(rect.right);
        array.put(rect.bottom);
        return array;
    }
}
