package com.virditech.ac7000.model;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Rect;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class ModelSlotClassifier implements AutoCloseable {
    private static final String MANIFEST_NAME = "model_manifest.json";
    private static final String TYPE_DUAL_2_INPUT = "dual_2_input";
    private static final String TYPE_PAIRED_1_INPUT = "paired_1_input";
    private static final String TYPE_SINGLE_1_INPUT = "single_1_input";
    private static final String TYPE_FIVE_INPUT = "five_input";

    private final String label;
    private final String type;
    private final AntiSpoofingClassifier classifier;
    private final AntiSpoofingClassifier rgbClassifier;
    private final AntiSpoofingClassifier irClassifier;
    private final float cropMarginRatio;

    private ModelSlotClassifier(String label, String type, AntiSpoofingClassifier classifier,
                                AntiSpoofingClassifier rgbClassifier,
                                AntiSpoofingClassifier irClassifier) {
        this.label = label;
        this.type = type;
        this.classifier = classifier;
        this.rgbClassifier = rgbClassifier;
        this.irClassifier = irClassifier;
        this.cropMarginRatio = classifier != null ? classifier.cropMarginRatio() : rgbClassifier.cropMarginRatio();
    }

    public String label() {
        return label;
    }

    public float cropMarginRatio() {
        return cropMarginRatio;
    }

    public String inferenceBackend() {
        if (classifier != null) return classifier.inferenceBackend();
        return "RGB " + rgbClassifier.inferenceBackend() + " / IR " + irClassifier.inferenceBackend();
    }

    public String backendStatus() {
        if (classifier != null) return label + ": " + classifier.backendStatus();
        return label + ": RGB " + rgbClassifier.backendStatus() + " / IR " + irClassifier.backendStatus();
    }

    public SlotClassificationResult classify(Bitmap rgb, Rect rgbBox, Bitmap ir, Rect irBox) {
        if (classifier != null) {
            return new SlotClassificationResult(classifier.classify(rgb, rgbBox, ir, irBox), null, null);
        }
        ClassificationResult rgbResult = rgbClassifier.classify(rgb, rgbBox, ir, irBox);
        ClassificationResult irResult = irClassifier.classify(rgb, rgbBox, ir, irBox);
        return new SlotClassificationResult(null, rgbResult, irResult);
    }

    @Override public void close() {
        if (classifier != null) classifier.close();
        if (rgbClassifier != null) rgbClassifier.close();
        if (irClassifier != null) irClassifier.close();
    }

    public static LoadResult loadAll(Context context) {
        ArrayList<ModelSlotClassifier> slots = new ArrayList<>();
        ArrayList<String> errors = new ArrayList<>();
        JSONArray models;
        try {
            models = loadManifest(context);
        } catch (Exception e) {
            errors.add("MODEL MANIFEST FAILED: " + e.getMessage());
            models = defaultManifest(context);
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
        if (!assetExists(context, MANIFEST_NAME)) return defaultManifest(context);
        JSONObject root = new JSONObject(readAsset(context, MANIFEST_NAME));
        return root.getJSONArray("models");
    }

    private static JSONArray defaultManifest(Context context) {
        JSONArray models = new JSONArray();
        if (assetExists(context, "anti_spoofing.tflite")) {
            JSONObject model = new JSONObject();
            try {
                model.put("label", "MODEL 1");
                model.put("type", TYPE_DUAL_2_INPUT);
                model.put("model", "anti_spoofing.tflite");
                model.put("spec", "model_spec.json");
                models.put(model);
            } catch (Exception ignored) {}
        }
        if (assetExists(context, "anti_spoofing_npu.tflite")) {
            JSONObject model = new JSONObject();
            try {
                model.put("label", "MODEL 2");
                model.put("type", TYPE_FIVE_INPUT);
                model.put("model", "anti_spoofing_npu.tflite");
                model.put("spec", "model_spec_npu.json");
                models.put(model);
            } catch (Exception ignored) {}
        }
        return models;
    }

    private static ModelSlotClassifier loadSlot(Context context, String label, JSONObject json) throws Exception {
        String type = json.getString("type").toLowerCase(Locale.US);
        if (TYPE_PAIRED_1_INPUT.equals(type)) {
            AntiSpoofingClassifier rgb = null;
            AntiSpoofingClassifier ir = null;
            try {
                rgb = new AntiSpoofingClassifier(context, json.getString("rgbModel"), json.getString("rgbSpec"));
                ir = new AntiSpoofingClassifier(context, json.getString("irModel"), json.getString("irSpec"));
            } catch (Exception e) {
                if (rgb != null) closeQuietly(rgb);
                if (ir != null) closeQuietly(ir);
                throw e;
            }
            if (rgb.inputTensorCount() != 1 || ir.inputTensorCount() != 1) {
                closeQuietly(rgb);
                closeQuietly(ir);
                throw new IllegalArgumentException("paired_1_input requires two 1-input models");
            }
            if (!"rgb".equals(rgb.singleInputKind()) || !"ir".equals(ir.singleInputKind())) {
                closeQuietly(rgb);
                closeQuietly(ir);
                throw new IllegalArgumentException("paired_1_input requires rgbSpec inputKind=rgb and irSpec inputKind=ir");
            }
            if (Math.abs(rgb.cropMarginRatio() - ir.cropMarginRatio()) > 0.0001f) {
                closeQuietly(rgb);
                closeQuietly(ir);
                throw new IllegalArgumentException("paired_1_input specs must use the same cropMarginRatio");
            }
            return new ModelSlotClassifier(label, type, null, rgb, ir);
        }
        if (!TYPE_SINGLE_1_INPUT.equals(type)
                && !TYPE_DUAL_2_INPUT.equals(type)
                && !TYPE_FIVE_INPUT.equals(type)) {
            throw new IllegalArgumentException("Unsupported model type: " + type);
        }

        AntiSpoofingClassifier classifier = new AntiSpoofingClassifier(context,
                json.getString("model"), json.getString("spec"));
        int inputCount = classifier.inputTensorCount();
        if ((TYPE_SINGLE_1_INPUT.equals(type) && inputCount != 1)
                || (TYPE_DUAL_2_INPUT.equals(type) && inputCount != 2)
                || (TYPE_FIVE_INPUT.equals(type) && inputCount != 5)) {
            closeQuietly(classifier);
            throw new IllegalArgumentException(type + " model has " + inputCount + " inputs");
        }
        return new ModelSlotClassifier(label, type, classifier, null, null);
    }

    private static boolean assetExists(Context context, String name) {
        try {
            String[] assets = context.getAssets().list("");
            if (assets == null) return false;
            for (String asset : assets) {
                if (name.equals(asset)) return true;
            }
        } catch (IOException ignored) {}
        return false;
    }

    private static String readAsset(Context context, String name) throws IOException {
        try (InputStream input = context.getAssets().open(name)) {
            byte[] bytes = new byte[input.available()];
            int read = input.read(bytes);
            if (read != bytes.length) throw new IOException("Unable to read " + name);
            return new String(bytes, StandardCharsets.UTF_8);
        }
    }

    private static void closeQuietly(AntiSpoofingClassifier classifier) {
        try { classifier.close(); } catch (Exception ignored) {}
    }

    public static final class LoadResult {
        public final List<ModelSlotClassifier> slots;
        public final List<String> errors;

        LoadResult(List<ModelSlotClassifier> slots, List<String> errors) {
            this.slots = slots;
            this.errors = errors;
        }
    }
}
