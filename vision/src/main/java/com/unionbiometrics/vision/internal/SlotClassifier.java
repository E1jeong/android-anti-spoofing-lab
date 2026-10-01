package com.unionbiometrics.vision.internal;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Rect;

import androidx.annotation.RestrictTo;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

@RestrictTo(RestrictTo.Scope.LIBRARY)
public final class SlotClassifier {
    private static final String TYPE_DUAL_2_INPUT = "dual_2_input";
    private static final String TYPE_SINGLE_1_INPUT = "single_1_input";

    private final String label;
    private final Classifier classifier;
    private final float cropMarginRatio;

    private SlotClassifier(String label, Classifier classifier) {
        this.label = label;
        this.classifier = classifier;
        this.cropMarginRatio = classifier.cropMarginRatio();
    }

    String label() {
        return label;
    }

    float cropMarginRatio() {
        return cropMarginRatio;
    }

    String inferenceBackend() {
        return classifier.inferenceBackend();
    }

    public boolean isDualInput() {
        return classifier.inputTensorCount() == 2;
    }

    FrameResult classify(Bitmap rgb, Rect rgbBox, Bitmap ir, Rect irBox) {
        return classifier.classify(rgb, rgbBox, ir, irBox);
    }

    void close() {
        classifier.close();
    }

    static LoadResult loadAll(Context context) {
        ArrayList<SlotClassifier> slots = new ArrayList<>();
        ArrayList<String> errors = new ArrayList<>();
        JSONArray models;
        try {
            models = loadManifest(context);
        } catch (Exception e) {
            errors.add("MODEL MANIFEST FAILED: " + e.getMessage());
            return new LoadResult(slots, errors);
        }

        for (int i = 0; i < models.length(); i++) {
            JSONObject json = models.optJSONObject(i);
            if (json == null) {
                errors.add("MODEL " + (i + 1) + " FAILED: manifest entry is not an object");
                continue;
            }
            String label = json.optString("label", "MODEL " + (i + 1));
            try {
                slots.add(loadSlot(context, label, json));
            } catch (Exception e) {
                errors.add(label + " FAILED: " + e.getMessage());
            }
        }
        return new LoadResult(slots, errors);
    }

    public static SlotClassifier loadSelected(Context context, int index) throws Exception {
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
        JSONObject root = new JSONObject(AssetLoader.readUtf8(context, AssetLoader.MANIFEST));
        JSONArray models = root.optJSONArray("models");
        if (models == null || models.length() == 0) {
            throw new IllegalStateException(AssetLoader.path(AssetLoader.MANIFEST) + " has no models");
        }
        return models;
    }

    private static SlotClassifier loadSlot(Context context, String label, JSONObject json) throws Exception {
        String type = json.getString("type");
        validateType(type);

        Classifier classifier = new Classifier(context,
                json.getString("model"), json.getString("spec"));
        boolean loaded = false;
        try {
            int inputCount = classifier.inputTensorCount();
            if ((TYPE_SINGLE_1_INPUT.equals(type) && inputCount != 1)
                    || (TYPE_DUAL_2_INPUT.equals(type) && inputCount != 2)) {
                throw new IllegalArgumentException(type + " model has " + inputCount + " inputs");
            }
            SlotClassifier slot = new SlotClassifier(label, classifier);
            loaded = true;
            return slot;
        } finally {
            if (!loaded) closeQuietly(classifier);
        }
    }

    static void validateType(String type) {
        if (!TYPE_SINGLE_1_INPUT.equals(type) && !TYPE_DUAL_2_INPUT.equals(type)) {
            throw new IllegalArgumentException("Unsupported model type: " + type);
        }
    }

    private static void closeQuietly(Classifier classifier) {
        try { classifier.close(); } catch (Exception ignored) {}
    }

    static final class LoadResult {
        final List<SlotClassifier> slots;
        final List<String> errors;

        LoadResult(List<SlotClassifier> slots, List<String> errors) {
            this.slots = slots;
            this.errors = errors;
        }
    }
}
