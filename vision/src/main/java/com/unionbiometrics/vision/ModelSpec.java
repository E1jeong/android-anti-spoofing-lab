package com.unionbiometrics.vision;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

final class ModelSpec {
    final int rgbInputIndex;
    final int irInputIndex;
    final String singleInputType;
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
        if (json.optInt("inputWidth", Constants.INPUT_SIZE) != Constants.INPUT_SIZE
                || json.optInt("inputHeight", Constants.INPUT_SIZE) != Constants.INPUT_SIZE) {
            throw new IllegalArgumentException("model_spec.json input size must be " + Constants.INPUT_SIZE);
        }

        String type = "";
        int rgbIndex = -1;
        int irIndex = -1;
        float[] rgbMean = new float[]{0.5f, 0.5f, 0.5f};
        float[] rgbStd = new float[]{0.5f, 0.5f, 0.5f};
        float[] irMean = new float[]{0.5f};
        float[] irStd = new float[]{0.5f};

        for (int i = 0; i < inputsArray.length(); i++) {
            JSONObject inputTensor = inputsArray.getJSONObject(i);
            String itemType = inputTensor.optString("input_type", "unknown");
            if (inputsArray.length() == Constants.IR_INPUT_COUNT) {
                type = itemType;
            }
            if (Constants.INPUT_TYPE_RGB.equals(itemType)) {
                rgbIndex = inputTensor.optInt("index", i);
            } else if (Constants.INPUT_TYPE_IR.equals(itemType)) {
                irIndex = inputTensor.optInt("index", i);
            }
            JSONArray shape = inputTensor.optJSONArray("shape");
            if (shape == null || shape.length() != 4 || shape.optInt(0, -1) != 1
                    || shape.optInt(1, -1) != Constants.INPUT_SIZE
                    || shape.optInt(2, -1) != Constants.INPUT_SIZE) {
                throw new IllegalArgumentException(
                        "model_spec.json input shape must be [1," + Constants.INPUT_SIZE + ","
                                + Constants.INPUT_SIZE + ",channels]");
            }
            int channels = shape.optInt(3, -1);
            if ((Constants.INPUT_TYPE_RGB.equals(itemType) && channels != Constants.RGB_CHANNELS)
                    || (Constants.INPUT_TYPE_IR.equals(itemType) && channels != Constants.IR_CHANNELS)) {
                throw new IllegalArgumentException(
                        "model_spec.json input channels do not match input_type=" + itemType);
            }

            JSONObject normObj = inputTensor.optJSONObject("normalization");
            if (normObj != null) {
                float[] mean = parseFloatArray(normObj, "mean");
                float[] std = parseFloatArray(normObj, "std");
                if (Constants.INPUT_TYPE_RGB.equals(itemType)) {
                    rgbMean = mean;
                    rgbStd = std;
                } else if (Constants.INPUT_TYPE_IR.equals(itemType)) {
                    irMean = mean;
                    irStd = std;
                }
            }
        }
        boolean validIrOnly = inputsArray.length() == Constants.IR_INPUT_COUNT
                && Constants.INPUT_TYPE_IR.equals(type) && irIndex == 0;
        boolean validRgbAndIr = inputsArray.length() == Constants.DUAL_INPUT_COUNT
                && rgbIndex >= 0 && irIndex >= 0
                && rgbIndex < Constants.DUAL_INPUT_COUNT
                && irIndex < Constants.DUAL_INPUT_COUNT && rgbIndex != irIndex;
        if (!validIrOnly && !validRgbAndIr) {
            throw new IllegalArgumentException(
                    "model_spec.json must define one IR input or one RGB and one IR input");
        }
        JSONArray classOrder = json.optJSONArray("class_order");
        if (classOrder == null || classOrder.length() != Constants.CLASS_COUNT) {
            throw new IllegalArgumentException("model_spec.json class_order must match Vision class labels");
        }
        for (int i = 0; i < Constants.CLASS_COUNT; i++) {
            if (!Constants.CLASS_LABELS[i].equalsIgnoreCase(classOrder.optString(i))) {
                throw new IllegalArgumentException("model_spec.json class_order differs at index " + i);
            }
        }
        singleInputType = type;
        this.rgbMean = rgbMean;
        this.rgbStd = rgbStd;
        this.irMean = irMean;
        this.irStd = irStd;
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
        if (!Constants.BACKEND_NNAPI.equalsIgnoreCase(json.optString("delegate", Constants.BACKEND_NNAPI))) {
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
