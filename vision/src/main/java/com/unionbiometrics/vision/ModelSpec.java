package com.unionbiometrics.vision;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

final class ModelSpec {
    final int rgbInputIndex;
    final int irInputIndex;
    final String inputKind;
    final float[] rgbMean;
    final float[] rgbStd;
    final float[] irMean;
    final float[] irStd;
    final float cropMarginRatio;

    private ModelSpec(JSONObject json) throws JSONException {
        JSONArray inputsArray = json.optJSONArray("inputs");
        if (inputsArray == null) {
            throw new IllegalArgumentException("model_spec.json must contain generated inputs");
        }
        if (json.optInt("inputWidth", VisionConstants.INPUT_SIZE) != VisionConstants.INPUT_SIZE
                || json.optInt("inputHeight", VisionConstants.INPUT_SIZE) != VisionConstants.INPUT_SIZE) {
            throw new IllegalArgumentException("model_spec.json input size must be " + VisionConstants.INPUT_SIZE);
        }

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
            if (inputsArray.length() == VisionConstants.IR_INPUT_COUNT) {
                kind = itemKind;
            }
            if (VisionConstants.INPUT_KIND_RGB.equals(itemKind)) {
                rgbIndex = inputTensor.optInt("index", i);
            } else if (VisionConstants.INPUT_KIND_IR.equals(itemKind)) {
                irIndex = inputTensor.optInt("index", i);
            }
            JSONArray shape = inputTensor.optJSONArray("shape");
            if (shape == null || shape.length() != 4 || shape.optInt(0, -1) != 1
                    || shape.optInt(1, -1) != VisionConstants.INPUT_SIZE
                    || shape.optInt(2, -1) != VisionConstants.INPUT_SIZE) {
                throw new IllegalArgumentException(
                        "model_spec.json input shape must be [1," + VisionConstants.INPUT_SIZE + ","
                                + VisionConstants.INPUT_SIZE + ",channels]");
            }
            int channels = shape.optInt(3, -1);
            if ((VisionConstants.INPUT_KIND_RGB.equals(itemKind) && channels != VisionConstants.RGB_CHANNELS)
                    || (VisionConstants.INPUT_KIND_IR.equals(itemKind) && channels != VisionConstants.IR_CHANNELS)) {
                throw new IllegalArgumentException(
                        "model_spec.json input channels do not match input_kind=" + itemKind);
            }

            JSONObject normObj = inputTensor.optJSONObject("normalization");
            if (normObj != null) {
                float[] mean = parseFloatArray(normObj, "mean");
                float[] std = parseFloatArray(normObj, "std");
                if (VisionConstants.INPUT_KIND_RGB.equals(itemKind)) {
                    rgbM = mean;
                    rgbS = std;
                } else if (VisionConstants.INPUT_KIND_IR.equals(itemKind)) {
                    irM = mean;
                    irS = std;
                }
            }
        }
        boolean validIrOnly = inputsArray.length() == VisionConstants.IR_INPUT_COUNT
                && VisionConstants.INPUT_KIND_IR.equals(kind) && irIndex == 0;
        boolean validRgbAndIr = inputsArray.length() == VisionConstants.DUAL_INPUT_COUNT
                && rgbIndex >= 0 && irIndex >= 0
                && rgbIndex < VisionConstants.DUAL_INPUT_COUNT
                && irIndex < VisionConstants.DUAL_INPUT_COUNT && rgbIndex != irIndex;
        if (!validIrOnly && !validRgbAndIr) {
            throw new IllegalArgumentException(
                    "model_spec.json must define one IR input or one RGB and one IR input");
        }
        JSONArray classOrder = json.optJSONArray("class_order");
        if (classOrder == null || classOrder.length() != VisionConstants.CLASS_COUNT) {
            throw new IllegalArgumentException("model_spec.json class_order must match Vision class labels");
        }
        for (int i = 0; i < VisionConstants.CLASS_COUNT; i++) {
            if (!VisionConstants.CLASS_LABELS[i].equalsIgnoreCase(classOrder.optString(i))) {
                throw new IllegalArgumentException("model_spec.json class_order differs at index " + i);
            }
        }
        inputKind = kind;
        rgbMean = rgbM;
        rgbStd = rgbS;
        irMean = irM;
        irStd = irS;
        JSONArray outputsArray = json.optJSONArray("outputs");
        if (outputsArray != null && outputsArray.length() > 0
                && !outputsArray.getJSONObject(0).optBoolean("output_is_logits", true)) {
            throw new IllegalArgumentException("model_spec.json output must be logits");
        }
        cropMarginRatio = (float) json.optDouble("crop_margin_ratio", 0.10);
        rgbInputIndex = rgbIndex;
        irInputIndex = irIndex;

        if (cropMarginRatio < 0f || cropMarginRatio > 1f) {
            throw new IllegalArgumentException("model_spec.json contains invalid cropMarginRatio");
        }
        if (!VisionConstants.BACKEND_NNAPI.equalsIgnoreCase(json.optString("delegate", VisionConstants.BACKEND_NNAPI))) {
            throw new IllegalArgumentException("model_spec.json delegate must be nnapi");
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

    static ModelSpec parse(String json) throws Exception {
        return new ModelSpec(new JSONObject(json));
    }

    static ModelSpec load(Context context, String assetName) throws Exception {
        return parse(AssetLoader.readUtf8(context, assetName));
    }
}
