package com.virditech.ac7000.recognition;

import android.content.Context;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Model configuration for face recognition, parsed from model_manifest.json.
 */
public final class RecognitionModelConfig {
    private static final String TAG = "RecognitionModelConfig";
    public static final String MANIFEST_NAME = "model_manifest.json";

    private final String label;
    private final String modelPath;
    private final FaceEmbeddingModel.DelegateType delegateType;

    public RecognitionModelConfig(String label, String modelPath, FaceEmbeddingModel.DelegateType delegateType) {
        this.label = (label != null && !label.trim().isEmpty()) ? label.trim() : "Recognition Model";
        this.modelPath = Objects.requireNonNull(modelPath, "modelPath cannot be null").trim();
        this.delegateType = delegateType != null ? delegateType : FaceEmbeddingModel.DEFAULT_DELEGATE;
    }

    public String getLabel() {
        return label;
    }

    public String getModelPath() {
        return modelPath;
    }

    public FaceEmbeddingModel.DelegateType getDelegateType() {
        return delegateType;
    }

    public static List<RecognitionModelConfig> loadAll(Context context) {
        if (context == null) {
            return Collections.singletonList(createDefault());
        }
        try {
            if (!assetExists(context, MANIFEST_NAME)) {
                return Collections.singletonList(createDefault());
            }
            String json = readAsset(context, MANIFEST_NAME);
            List<RecognitionModelConfig> configs = parseJson(json);
            return configs.isEmpty() ? Collections.singletonList(createDefault()) : configs;
        } catch (Exception e) {
            Log.w(TAG, "Failed to load recognition models from " + MANIFEST_NAME + ": " + e.getMessage(), e);
            return Collections.singletonList(createDefault());
        }
    }

    public static List<RecognitionModelConfig> parseJson(String jsonString) throws Exception {
        if (jsonString == null || jsonString.trim().isEmpty()) {
            return Collections.emptyList();
        }
        JSONObject root = new JSONObject(jsonString);
        if (!root.has("recognition")) {
            return Collections.emptyList();
        }
        JSONArray array = root.getJSONArray("recognition");
        List<RecognitionModelConfig> list = new ArrayList<>(array.length());
        for (int i = 0; i < array.length(); i++) {
            JSONObject obj = array.optJSONObject(i);
            if (obj == null) continue;
            String model = obj.optString("model", "").trim();
            if (model.isEmpty()) continue;
            String label = obj.optString("label", "MODEL " + (i + 1));
            String delegateStr = obj.optString("delegate", FaceEmbeddingModel.DEFAULT_DELEGATE.name());
            FaceEmbeddingModel.DelegateType delegate = FaceEmbeddingModel.DelegateType.NNAPI.name().equalsIgnoreCase(delegateStr)
                    ? FaceEmbeddingModel.DelegateType.NNAPI
                    : FaceEmbeddingModel.DelegateType.CPU;
            list.add(new RecognitionModelConfig(label, model, delegate));
        }
        return Collections.unmodifiableList(list);
    }

    public static RecognitionModelConfig createDefault() {
        return new RecognitionModelConfig("MobileNet Emore INT8",
                FaceEmbeddingModel.DEFAULT_MODEL_PATH,
                FaceEmbeddingModel.DEFAULT_DELEGATE);
    }

    public static RecognitionModelConfig findByModelPath(List<RecognitionModelConfig> configs, String modelPath) {
        if (configs == null || modelPath == null) return null;
        for (RecognitionModelConfig config : configs) {
            if (modelPath.equals(config.getModelPath())) {
                return config;
            }
        }
        return null;
    }

    private static boolean assetExists(Context context, String filename) {
        try (InputStream ignored = context.getAssets().open(filename)) {
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private static String readAsset(Context context, String filename) throws IOException {
        try (InputStream is = context.getAssets().open(filename);
             ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            byte[] buf = new byte[4096];
            int n;
            while ((n = is.read(buf)) != -1) {
                bos.write(buf, 0, n);
            }
            return bos.toString(StandardCharsets.UTF_8.name());
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RecognitionModelConfig)) return false;
        RecognitionModelConfig that = (RecognitionModelConfig) o;
        return Objects.equals(label, that.label) &&
                Objects.equals(modelPath, that.modelPath) &&
                delegateType == that.delegateType;
    }

    @Override
    public int hashCode() {
        return Objects.hash(label, modelPath, delegateType);
    }

    @Override
    public String toString() {
        return "RecognitionModelConfig{" +
                "label='" + label + '\'' +
                ", modelPath='" + modelPath + '\'' +
                ", delegateType=" + delegateType +
                '}';
    }
}
