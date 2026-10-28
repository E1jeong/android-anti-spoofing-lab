package com.virditech.ac7000.capture;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

public final class SampleMetadata {
    private SampleMetadata() {}

    public static String build(int rgbWidth, int rgbHeight, RectValue rgbFaceRect, RectValue rgbCropRect,
                               int irWidth, int irHeight, RectValue irMappedFaceRect, RectValue irCropRect,
                               float cropMarginRatio, String className, String environment,
                               String qualityMode, int minQualityLevel,
                               int actualQualityLevel, float qualityScore) {
        try {
            JSONObject root = new JSONObject();
            root.put("schemaVersion", 2);
            if (className != null) root.put("className", className);
            if (environment != null) root.put("environment", environment);
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

    public static String buildNoFace(int rgbWidth, int rgbHeight, String environment, String sceneType) {
        try {
            JSONObject root = new JSONObject();
            root.put("schemaVersion", 2);
            root.put("captureType", CaptureConfig.MODE_NO_FACE);
            root.put("environment", environment);
            root.put("sceneType", sceneType);
            root.put("rgb", frameMetadata(rgbWidth, rgbHeight, "faceRect", null, null));
            root.put("ir", JSONObject.NULL);
            root.put("cropMarginRatio", JSONObject.NULL);
            root.put("qualityMode", JSONObject.NULL);
            root.put("minQualityLevel", CaptureConfig.LEVEL_NONE);
            root.put("actualQualityLevel", CaptureConfig.LEVEL_NONE);
            root.put("qualityScore", 0.0f);
            return root.toString(2);
        } catch (JSONException e) {
            throw new IllegalStateException("Unable to build no-face metadata", e);
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

    /** Version 2 binds landmark provenance to the capture fields in this same file. */
    public static String withLandmarks(String captureJson, LandmarkMetadata landmarks,
                                       String imageSha256) {
        if (landmarks == null) throw new IllegalArgumentException("landmark metadata is required");
        try {
            JSONObject root = new JSONObject(captureJson);
            if (root.has("landmarks") || !Integer.valueOf(2).equals(root.opt("schemaVersion"))) {
                throw new IllegalArgumentException("Expected version-2 capture metadata");
            }
            JSONObject label = new JSONObject(landmarks.build(imageSha256));
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonicalJson(root).getBytes(StandardCharsets.US_ASCII));
            StringBuilder hex = new StringBuilder();
            for (byte value : digest) {
                hex.append(Character.forDigit((value >>> 4) & 15, 16));
                hex.append(Character.forDigit(value & 15, 16));
            }
            label.put("captureSha256", hex.toString());
            root.put("landmarks", label);
            return root.toString(2);
        } catch (JSONException | NoSuchAlgorithmException e) {
            throw new IllegalStateException("Unable to build integrated metadata", e);
        }
    }

    // Shared with common/landmark_metadata.py: sorted ASCII JSON and plain decimal numbers.
    private static String canonicalJson(Object value) throws JSONException {
        if (value == null || value == JSONObject.NULL) return "null";
        if (value instanceof JSONObject) {
            JSONObject object = (JSONObject) value;
            List<String> keys = new ArrayList<>();
            Iterator<String> iterator = object.keys();
            while (iterator.hasNext()) keys.add(iterator.next());
            Collections.sort(keys);
            StringBuilder result = new StringBuilder("{");
            for (String key : keys) {
                if (result.length() > 1) result.append(',');
                result.append(quoteAscii(key)).append(':').append(canonicalJson(object.get(key)));
            }
            return result.append('}').toString();
        }
        if (value instanceof JSONArray) {
            JSONArray array = (JSONArray) value;
            StringBuilder result = new StringBuilder("[");
            for (int index = 0; index < array.length(); index++) {
                if (index > 0) result.append(',');
                result.append(canonicalJson(array.get(index)));
            }
            return result.append(']').toString();
        }
        if (value instanceof Number) {
            return new BigDecimal(value.toString()).stripTrailingZeros().toPlainString();
        }
        if (value instanceof Boolean) return value.toString();
        return quoteAscii(value.toString());
    }

    private static String quoteAscii(String value) {
        StringBuilder result = new StringBuilder("\"");
        for (char character : value.toCharArray()) {
            switch (character) {
                case '"': result.append("\\\""); break;
                case '\\': result.append("\\\\"); break;
                case '\b': result.append("\\b"); break;
                case '\f': result.append("\\f"); break;
                case '\n': result.append("\\n"); break;
                case '\r': result.append("\\r"); break;
                case '\t': result.append("\\t"); break;
                default:
                    if (character < 32 || character >= 127) {
                        String hex = Integer.toHexString(character);
                        result.append("\\u");
                        for (int pad = hex.length(); pad < 4; pad++) result.append('0');
                        result.append(hex);
                    } else result.append(character);
            }
        }
        return result.append('"').toString();
    }

    private static Object rectToJson(RectValue rect) {
        if (rect == null) return JSONObject.NULL;
        JSONArray array = new JSONArray();
        array.put(rect.left);
        array.put(rect.top);
        array.put(rect.right);
        array.put(rect.bottom);
        return array;
    }
}
