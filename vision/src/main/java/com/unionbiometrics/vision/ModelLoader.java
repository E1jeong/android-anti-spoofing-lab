package com.unionbiometrics.vision;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

final class ModelLoader {
    private ModelLoader() {}

    static int modelCount(Context context) throws Exception {
        return loadManifest(context).length();
    }

    static Classifier loadModel(Context context, int index) throws Exception {
        JSONArray models = loadManifest(context);
        if (index < 0 || index >= models.length()) {
            throw new IllegalArgumentException("Model slot index is out of range: " + index);
        }
        JSONObject json = models.optJSONObject(index);
        if (json == null) {
            throw new IllegalArgumentException("Model slot is not an object: " + index);
        }
        return loadClassifier(context, json);
    }

    private static JSONArray loadManifest(Context context) throws Exception {
        JSONObject root = new JSONObject(AssetLoader.readUtf8(context, Constants.MODEL_MANIFEST));
        JSONArray models = root.optJSONArray("models");
        if (models == null || models.length() == 0) {
            throw new IllegalStateException(AssetLoader.getAssetPath(Constants.MODEL_MANIFEST) + " has no models");
        }
        return models;
    }

    private static Classifier loadClassifier(Context context, JSONObject json) throws Exception {
        String type = json.getString("type");
        validateType(type);

        Classifier classifier = new Classifier(context,
                json.getString("model"), json.getString("spec"));
        boolean loaded = false;
        try {
            int inputCount = classifier.inputTensorCount();
            if ((Constants.SINGLE_1_INPUT.equals(type)
                    && inputCount != Constants.IR_INPUT_COUNT)
                    || (Constants.DUAL_2_INPUT.equals(type)
                    && inputCount != Constants.DUAL_INPUT_COUNT)) {
                throw new IllegalArgumentException(type + " model has " + inputCount + " inputs");
            }
            loaded = true;
            return classifier;
        } finally {
            if (!loaded) closeQuietly(classifier);
        }
    }

    static void validateType(String type) {
        if (!Constants.SINGLE_1_INPUT.equals(type)
                && !Constants.DUAL_2_INPUT.equals(type)) {
            throw new IllegalArgumentException("Unsupported model type: " + type);
        }
    }

    private static void closeQuietly(Classifier classifier) {
        try { classifier.close(); } catch (Exception ignored) {}
    }
}
