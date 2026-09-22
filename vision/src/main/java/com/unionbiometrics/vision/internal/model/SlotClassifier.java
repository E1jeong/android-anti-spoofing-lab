package com.unionbiometrics.vision.internal.model;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Rect;

import androidx.annotation.RestrictTo;

import org.json.JSONArray;
import org.json.JSONObject;

import com.unionbiometrics.vision.internal.asset.AssetLoader;

import java.util.ArrayList;
import java.util.List;

@RestrictTo(RestrictTo.Scope.LIBRARY)
public final class SlotClassifier implements AutoCloseable {
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

    public String label() {
        return label;
    }

    public float cropMarginRatio() {
        return cropMarginRatio;
    }

    public String inferenceBackend() {
        return classifier.inferenceBackend();
    }

    public SlotClassificationResult classify(Bitmap rgb, Rect rgbBox, Bitmap ir, Rect irBox) {
        return new SlotClassificationResult(classifier.classify(rgb, rgbBox, ir, irBox));
    }

    @Override public void close() {
        classifier.close();
    }

    public static LoadResult loadAll(Context context) {
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
        int inputCount = classifier.inputTensorCount();
        if ((TYPE_SINGLE_1_INPUT.equals(type) && inputCount != 1)
                || (TYPE_DUAL_2_INPUT.equals(type) && inputCount != 2)) {
            closeQuietly(classifier);
            throw new IllegalArgumentException(type + " model has " + inputCount + " inputs");
        }
        return new SlotClassifier(label, classifier);
    }

    static void validateType(String type) {
        if (!TYPE_SINGLE_1_INPUT.equals(type) && !TYPE_DUAL_2_INPUT.equals(type)) {
            throw new IllegalArgumentException("Unsupported model type: " + type);
        }
    }

    private static void closeQuietly(Classifier classifier) {
        try { classifier.close(); } catch (Exception ignored) {}
    }

    public static final class LoadResult {
        public final List<SlotClassifier> slots;
        public final List<String> errors;

        LoadResult(List<SlotClassifier> slots, List<String> errors) {
            this.slots = slots;
            this.errors = errors;
        }
    }
}
