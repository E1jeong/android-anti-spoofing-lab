package com.virditech.ac7000.model;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

final class ModelSpec {
    static final String RGB_NORMALIZATION_IMAGENET = "imagenet";
    static final String RGB_NORMALIZATION_MINUS_ONE_TO_ONE = "minus_one_to_one";

    final int rgbInputIndex;
    final int irInputIndex;
    final int inputWidth;
    final int inputHeight;
    final InputNames inputs;
    final String inputKind;
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
        Object inputsObj = json.opt("inputs");
        if (inputsObj instanceof JSONArray) {
            // 1. 새롭게 자동 추출된 manifest 스펙 파싱
            JSONArray inputsArray = (JSONArray) inputsObj;
            inputs = null;
            inputWidth = json.optInt("inputWidth", 224);
            inputHeight = json.optInt("inputHeight", 224);
            bgr = false; // 기본 채널 오더는 RGB

            String kind = "";
            float[] rgbM = new float[]{0.5f, 0.5f, 0.5f};
            float[] rgbS = new float[]{0.5f, 0.5f, 0.5f};
            float[] irM = new float[]{0.5f};
            float[] irS = new float[]{0.5f};
            String rgbNorm = "minus_one_to_one";

            for (int i = 0; i < inputsArray.length(); i++) {
                JSONObject inputTensor = inputsArray.getJSONObject(i);
                String itemKind = inputTensor.optString("input_kind", "unknown");
                if (inputsArray.length() == 1) {
                    kind = itemKind;
                }
                int channels = 1;
                JSONArray shape = inputTensor.optJSONArray("shape");
                if (shape != null && shape.length() == 4) {
                    channels = shape.optInt(3, 1);
                }

                JSONObject normObj = inputTensor.optJSONObject("normalization");
                if (normObj != null) {
                    float[] mean = parseFloatArray(normObj, "mean");
                    float[] std = parseFloatArray(normObj, "std");
                    String range = normObj.optString("range", "minus_one_to_one");
                    if (channels == 3) {
                        rgbM = mean;
                        rgbS = std;
                        rgbNorm = range;
                    } else {
                        irM = mean;
                        irS = std;
                    }
                }
            }
            inputKind = kind;
            rgbMean = rgbM;
            rgbStd = rgbS;
            irMean = irM;
            irStd = irS;
            rgbNormalization = rgbNorm;

            JSONArray outputsArray = json.optJSONArray("outputs");
            boolean logits = true;
            if (outputsArray != null && outputsArray.length() > 0) {
                logits = outputsArray.getJSONObject(0).optBoolean("output_is_logits", true);
            }
            outputIsLogits = logits;

            cropMarginRatio = (float) json.optDouble("crop_margin_ratio", 0.10);
            delegate = json.optString("delegate", "nnapi");
            rgbInputIndex = json.optInt("rgbInputIndex", -1);
            irInputIndex = json.optInt("irInputIndex", -1);
        } else {
            // 2. 레거시 model_spec.json 스펙 파싱
            rgbInputIndex = json.optInt("rgbInputIndex", -1);
            irInputIndex = json.optInt("irInputIndex", -1);
            inputWidth = json.optInt("inputWidth", -1);
            inputHeight = json.optInt("inputHeight", -1);
            inputs = json.has("inputs") && !json.isNull("inputs")
                    ? new InputNames(json.getJSONObject("inputs"))
                    : null;
            inputKind = json.optString("inputKind", "").toLowerCase(Locale.US);
            bgr = "BGR".equalsIgnoreCase(json.optString("channelOrder", "RGB"));
            rgbNormalization = json.optString("rgbNormalization", RGB_NORMALIZATION_IMAGENET);
            rgbMean = parseFloatArray(json, "rgbMean");
            rgbStd = parseFloatArray(json, "rgbStd");
            irMean = parseFloatArray(json, "irMean");
            irStd = parseFloatArray(json, "irStd");
            delegate = json.optString("delegate", inputs == null ? "nnapi" : "cpu");
            outputIsLogits = json.getBoolean("outputIsLogits");
            cropMarginRatio = (float) json.getDouble("cropMarginRatio");
        }

        if (cropMarginRatio < 0f || cropMarginRatio > 1f) {
            throw new IllegalArgumentException("model_spec.json contains invalid cropMarginRatio");
        }
        if (inputs == null && !(inputsObj instanceof JSONArray)) {
            boolean singleInput = "rgb".equals(inputKind) || "ir".equals(inputKind);
            if (!singleInput && (rgbInputIndex < 0 || irInputIndex < 0 || rgbInputIndex == irInputIndex)) {
                throw new IllegalArgumentException("model_spec.json must define inputKind=rgb/ir or distinct rgbInputIndex and irInputIndex");
            }
        } else if (inputs != null && (inputWidth <= 0 || inputHeight <= 0)) {
            throw new IllegalArgumentException("5-input model_spec.json contains invalid input dimensions");
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
        return load(context, "model_spec.json");
    }

    static ModelSpec load(Context context, String assetName) throws Exception {
        try (InputStream input = context.getAssets().open(assetName)) {
            byte[] bytes = new byte[input.available()];
            int read = input.read(bytes);
            if (read != bytes.length) throw new IllegalStateException("Unable to read " + assetName);
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
