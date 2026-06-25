package com.virditech.ac7000.model;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONException;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

final class ModelSpec {
    final int rgbInputIndex;
    final int irInputIndex;
    final boolean bgr;
    final float[] rgbMean;
    final float[] rgbStd;
    final float[] irMean;
    final float[] irStd;
    final boolean outputIsLogits;
    final float cropMarginRatio;

    private ModelSpec(JSONObject json) throws JSONException {
        rgbInputIndex = json.getInt("rgbInputIndex");
        irInputIndex = json.getInt("irInputIndex");
        bgr = "BGR".equalsIgnoreCase(json.getString("channelOrder"));
        rgbMean = parseFloatArray(json, "rgbMean");
        rgbStd = parseFloatArray(json, "rgbStd");
        irMean = parseFloatArray(json, "irMean");
        irStd = parseFloatArray(json, "irStd");
        outputIsLogits = json.getBoolean("outputIsLogits");
        cropMarginRatio = (float) json.getDouble("cropMarginRatio");
        
        if (rgbInputIndex == irInputIndex || cropMarginRatio < 0f || cropMarginRatio > 1f) {
            throw new IllegalArgumentException("model_spec.json contains invalid values");
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
}
