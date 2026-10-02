package com.unionbiometrics.vision;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

final class ModelLoader {
    private ModelLoader() {}

    static final class LoadedModel {
        final String label;
        final Classifier classifier;

        private LoadedModel(String label, Classifier classifier) {
            this.label = label;
            this.classifier = classifier;
        }
    }

    static int slotCount(Context context) throws Exception {
        return loadManifest(context).length();
    }

    static LoadedModel loadSelected(Context context, int index) throws Exception {
        JSONArray models = loadManifest(context);
        if (index < 0 || index >= models.length()) {
            throw new IllegalArgumentException("Model slot index is out of range: " + index);
        }
        JSONObject json = models.optJSONObject(index);
        if (json == null) {
            throw new IllegalArgumentException("Model slot is not an object: " + index);
        }
        return loadSlot(context, json.optString("label", "MODEL " + (index + 1)), json);
    }

    private static JSONArray loadManifest(Context context) throws Exception {
        JSONObject root = new JSONObject(AssetLoader.readUtf8(context, VisionConstants.MANIFEST));
        JSONArray models = root.optJSONArray("models");
        if (models == null || models.length() == 0) {
            throw new IllegalStateException(AssetLoader.path(VisionConstants.MANIFEST) + " has no models");
        }
        return models;
    }

    private static LoadedModel loadSlot(Context context, String label, JSONObject json) throws Exception {
        String type = json.getString("type");
        validateType(type);

        Classifier classifier = new Classifier(context,
                json.getString("model"), json.getString("spec"));
        boolean loaded = false;
        try {
            int inputCount = classifier.inputTensorCount();
            if ((VisionConstants.SINGLE_1_INPUT.equals(type)
                    && inputCount != VisionConstants.IR_INPUT_COUNT)
                    || (VisionConstants.DUAL_2_INPUT.equals(type)
                    && inputCount != VisionConstants.DUAL_INPUT_COUNT)) {
                throw new IllegalArgumentException(type + " model has " + inputCount + " inputs");
            }
            LoadedModel model = new LoadedModel(label, classifier);
            loaded = true;
            return model;
        } finally {
            if (!loaded) closeQuietly(classifier);
        }
    }

    static void validateType(String type) {
        if (!VisionConstants.SINGLE_1_INPUT.equals(type)
                && !VisionConstants.DUAL_2_INPUT.equals(type)) {
            throw new IllegalArgumentException("Unsupported model type: " + type);
        }
    }

    private static void closeQuietly(Classifier classifier) {
        try { classifier.close(); } catch (Exception ignored) {}
    }
}
