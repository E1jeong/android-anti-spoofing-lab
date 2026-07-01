package com.virditech.ac7000.model;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

final class ModelSpec {
    static final String RGB_NORMALIZATION_IMAGENET = "imagenet";
    static final String RGB_NORMALIZATION_MINUS_ONE_TO_ONE = "minus_one_to_one";

    final int inputWidth;
    final int inputHeight;
    final InputNames inputs;
    final boolean bgr;
    final String rgbNormalization;
    final float[] rgbMean;
    final float[] rgbStd;
    final float[] irMean;
    final float[] irStd;
    final String delegate;
    final boolean outputIsLogits;
    final float cropMarginRatio;

    private ModelSpec(JSONObject json) throws JSONException {
        inputWidth = json.getInt("inputWidth");
        inputHeight = json.getInt("inputHeight");
        inputs = new InputNames(json.getJSONObject("inputs"));
        bgr = "BGR".equalsIgnoreCase(json.optString("channelOrder", "RGB"));
        rgbNormalization = json.optString("rgbNormalization", RGB_NORMALIZATION_IMAGENET);
        rgbMean = parseFloatArray(json, "rgbMean");
        rgbStd = parseFloatArray(json, "rgbStd");
        irMean = parseFloatArray(json, "irMean");
        irStd = parseFloatArray(json, "irStd");
        delegate = json.optString("delegate", "cpu");
        outputIsLogits = json.getBoolean("outputIsLogits");
        cropMarginRatio = (float) json.getDouble("cropMarginRatio");

        if (inputWidth <= 0 || inputHeight <= 0 || cropMarginRatio < 0f || cropMarginRatio > 1f) {
            throw new IllegalArgumentException("model_spec.json contains invalid dimensions or cropMarginRatio");
        }
        if (!RGB_NORMALIZATION_IMAGENET.equals(rgbNormalization)
                && !RGB_NORMALIZATION_MINUS_ONE_TO_ONE.equals(rgbNormalization)) {
            throw new IllegalArgumentException("Unsupported rgbNormalization: " + rgbNormalization);
        }
        if (!"cpu".equals(delegate) && !"nnapi".equals(delegate)) {
            throw new IllegalArgumentException("Unsupported delegate: " + delegate);
        }
        for (float s : rgbStd) {
            if (s == 0f) throw new IllegalArgumentException("rgbStd contains 0f");
        }
        for (float s : irStd) {
            if (s == 0f) throw new IllegalArgumentException("irStd contains 0f");
        }
    }

    private static float[] parseFloatArray(JSONObject json, String key) throws JSONException {
        if (json.isNull(key)) {
            throw new JSONException("Key " + key + " is null");
        }
        Object val = json.get(key);
        if (val instanceof JSONArray) {
            JSONArray jsonArr = (JSONArray) val;
            float[] arr = new float[jsonArr.length()];
            for (int i = 0; i < jsonArr.length(); i++) {
                arr[i] = (float) jsonArr.getDouble(i);
            }
            return arr;
        } else if (val instanceof Number) {
            return new float[] { ((Number) val).floatValue() };
        } else {
            throw new JSONException("Key " + key + " is neither an array nor a number");
        }
    }

    static ModelSpec load(Context context) throws Exception {
        try (InputStream input = context.getAssets().open("model_spec.json")) {
            byte[] bytes = new byte[input.available()];
            int read = input.read(bytes);
            if (read != bytes.length) throw new IllegalStateException("Unable to read model_spec.json");
            return new ModelSpec(new JSONObject(new String(bytes, StandardCharsets.UTF_8)));
        }
    }

    static final class InputNames {
        final String cropRgb;
        final String cropIr;
        final String fullRgb;
        final String fullIr;
        final String heatmap;

        InputNames(JSONObject json) throws JSONException {
            cropRgb = json.getString("cropRgb");
            cropIr = json.getString("cropIr");
            fullRgb = json.getString("fullRgb");
            fullIr = json.getString("fullIr");
            heatmap = json.getString("heatmap");
        }
    }
}
