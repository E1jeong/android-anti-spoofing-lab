package com.unionbiometrics.vision.internal.classification;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.unionbiometrics.vision.internal.asset.AssetLoader;

import java.util.Locale;

final class Spec {
    static final String RGB_NORMALIZATION_IMAGENET = "imagenet";
    static final String RGB_NORMALIZATION_MINUS_ONE_TO_ONE = "minus_one_to_one";

    final int rgbInputIndex;
    final int irInputIndex;
    final int inputWidth;
    final int inputHeight;
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

    private Spec(JSONObject json) throws JSONException {
        Object inputsObj = json.opt("inputs");
        if (inputsObj instanceof JSONArray) {
            JSONArray inputsArray = (JSONArray) inputsObj;
            inputWidth = json.optInt("inputWidth", 224);
            inputHeight = json.optInt("inputHeight", 224);
            bgr = false; // 기본 채널 오더는 RGB

            String kind = "";
            int rgbIndex = -1;
            int irIndex = -1;
            float[] rgbM = new float[]{0.5f, 0.5f, 0.5f};
            float[] rgbS = new float[]{0.5f, 0.5f, 0.5f};
            float[] irM = new float[]{0.5f};
            float[] irS = new float[]{0.5f};

            for (int i = 0; i < inputsArray.length(); i++) {
                JSONObject inputTensor = inputsArray.getJSONObject(i);
                String itemKind = inputTensor.optString("input_kind", "unknown");
                if (inputsArray.length() == 1) {
                    kind = itemKind;
                }
                if ("rgb".equals(itemKind)) {
                    rgbIndex = inputTensor.optInt("index", i);
                } else if ("ir".equals(itemKind)) {
                    irIndex = inputTensor.optInt("index", i);
                }
                int channels = 1;
                JSONArray shape = inputTensor.optJSONArray("shape");
                if (shape == null || shape.length() != 4 || shape.optInt(0, -1) != 1
                        || shape.optInt(1, -1) != inputHeight
                        || shape.optInt(2, -1) != inputWidth) {
                    throw new IllegalArgumentException(
                            "model_spec.json input shape must be [1," + inputHeight + ","
                                    + inputWidth + ",channels]");
                }
                channels = shape.optInt(3, -1);
                if (("rgb".equals(itemKind) && channels != 3)
                        || ("ir".equals(itemKind) && channels != 1)) {
                    throw new IllegalArgumentException(
                            "model_spec.json input channels do not match input_kind=" + itemKind);
                }

                JSONObject normObj = inputTensor.optJSONObject("normalization");
                if (normObj != null) {
                    float[] mean = parseFloatArray(normObj, "mean");
                    float[] std = parseFloatArray(normObj, "std");
                    if ("rgb".equals(itemKind)) {
                        rgbM = mean;
                        rgbS = std;
                    } else if ("ir".equals(itemKind)) {
                        irM = mean;
                        irS = std;
                    }
                }
            }
            boolean validIrOnly = inputsArray.length() == 1 && "ir".equals(kind) && irIndex == 0;
            boolean validRgbAndIr = inputsArray.length() == 2 && rgbIndex >= 0 && irIndex >= 0
                    && rgbIndex < 2 && irIndex < 2 && rgbIndex != irIndex;
            if (!validIrOnly && !validRgbAndIr) {
                throw new IllegalArgumentException(
                        "model_spec.json must define one IR input or one RGB and one IR input");
            }
            inputKind = kind;
            rgbMean = rgbM;
            rgbStd = rgbS;
            irMean = irM;
            irStd = irS;
            // manifest 는 항상 mean/std 를 선언하므로 범위 문자열 대신 mean/std 경로로 정규화한다.
            rgbNormalization = RGB_NORMALIZATION_IMAGENET;

            JSONArray outputsArray = json.optJSONArray("outputs");
            boolean logits = true;
            if (outputsArray != null && outputsArray.length() > 0) {
                logits = outputsArray.getJSONObject(0).optBoolean("output_is_logits", true);
            }
            outputIsLogits = logits;

            cropMarginRatio = (float) json.optDouble("crop_margin_ratio", 0.10);
            delegate = json.optString("delegate", "nnapi");
            rgbInputIndex = rgbIndex;
            irInputIndex = irIndex;
        } else {
            rgbInputIndex = json.optInt("rgbInputIndex", -1);
            irInputIndex = json.optInt("irInputIndex", -1);
            inputWidth = json.optInt("inputWidth", -1);
            inputHeight = json.optInt("inputHeight", -1);
            inputKind = json.optString("inputKind", "").toLowerCase(Locale.US);
            bgr = "BGR".equalsIgnoreCase(json.optString("channelOrder", "RGB"));
            rgbNormalization = json.optString("rgbNormalization", RGB_NORMALIZATION_IMAGENET);
            rgbMean = parseFloatArray(json, "rgbMean");
            rgbStd = parseFloatArray(json, "rgbStd");
            irMean = parseFloatArray(json, "irMean");
            irStd = parseFloatArray(json, "irStd");
            delegate = json.optString("delegate", "nnapi");
            outputIsLogits = json.getBoolean("outputIsLogits");
            cropMarginRatio = (float) json.getDouble("cropMarginRatio");
        }

        if (cropMarginRatio < 0f || cropMarginRatio > 1f) {
            throw new IllegalArgumentException("model_spec.json contains invalid cropMarginRatio");
        }
        boolean irOnly = "ir".equals(inputKind) && rgbInputIndex < 0;
        boolean rgbAndIr = inputKind.isEmpty() && rgbInputIndex >= 0 && irInputIndex >= 0
                && rgbInputIndex != irInputIndex;
        if (!irOnly && !rgbAndIr) {
            throw new IllegalArgumentException(
                    "model_spec.json must define one IR input or distinct RGB/IR input indices");
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

    static Spec parse(String json) throws Exception {
        return new Spec(new JSONObject(json));
    }

    static Spec load(Context context, String assetName) throws Exception {
        return parse(AssetLoader.readUtf8(context, assetName));
    }
}
