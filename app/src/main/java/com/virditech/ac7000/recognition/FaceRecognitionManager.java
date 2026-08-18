package com.virditech.ac7000.recognition;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.PointF;
import android.graphics.Rect;
import android.os.SystemClock;
import android.util.Log;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Manages face recognition lifecycle, template enrollment, and 1:1 / 1:N matching.
 * Decoupled from anti-spoofing classifiers.
 */
public final class FaceRecognitionManager implements AutoCloseable {
    private static final String TAG = "FaceRecognitionManager";
    public static final float DEFAULT_THRESHOLD = 0.70f;

    private final List<FaceTemplate> enrolledTemplates = new CopyOnWriteArrayList<>();
    private FaceEmbeddingModel embeddingModel;
    private float threshold = DEFAULT_THRESHOLD;
    private String initError;

    public FaceRecognitionManager(Context context) {
        this(context, FaceEmbeddingModel.DEFAULT_MODEL_PATH, FaceEmbeddingModel.DelegateType.NNAPI);
    }

    public FaceRecognitionManager(Context context, String modelPath, FaceEmbeddingModel.DelegateType delegateType) {
        try {
            embeddingModel = new FaceEmbeddingModel(context, modelPath, delegateType);
        } catch (Exception e) {
            initError = e.getMessage();
            Log.e(TAG, "Failed to initialize FaceEmbeddingModel: " + e.getMessage(), e);
        }
    }

    public synchronized boolean reloadModel(Context context, String modelPath, FaceEmbeddingModel.DelegateType delegateType) {
        if (embeddingModel != null) {
            embeddingModel.close();
            embeddingModel = null;
        }
        try {
            embeddingModel = new FaceEmbeddingModel(context, modelPath, delegateType);
            initError = null;
            Log.i(TAG, "Reloaded FaceEmbeddingModel: " + modelPath + " (" + delegateType + ")");
            return true;
        } catch (Exception e) {
            initError = e.getMessage();
            Log.e(TAG, "Failed to reload FaceEmbeddingModel: " + e.getMessage(), e);
            return false;
        }
    }

    public boolean isReady() {
        return embeddingModel != null;
    }

    public String getInitError() {
        return initError;
    }

    public String getActiveDelegate() {
        return embeddingModel != null ? embeddingModel.getActiveDelegate() : "N/A";
    }

    public String getModelAssetPath() {
        return embeddingModel != null ? embeddingModel.getModelAssetPath() : "N/A";
    }

    public long getLastInferenceMs() {
        return embeddingModel != null ? embeddingModel.getLastInferenceMs() : -1L;
    }

    public float getThreshold() {
        return threshold;
    }

    public void setThreshold(float threshold) {
        this.threshold = threshold;
    }

    public int getEnrolledCount() {
        return enrolledTemplates.size();
    }

    public List<FaceTemplate> getEnrolledTemplates() {
        return Collections.unmodifiableList(new ArrayList<>(enrolledTemplates));
    }

    public void clearTemplates() {
        enrolledTemplates.clear();
        Log.i(TAG, "All enrolled face templates cleared");
    }

    /**
     * Extracts an embedding from an RGB Bitmap with 5-point landmark alignment or fallback crop.
     */
    public float[] extractEmbedding(Bitmap rgbBitmap, Rect faceBox, PointF[] landmarks) {
        if (embeddingModel == null || rgbBitmap == null) return null;

        long alignStart = SystemClock.elapsedRealtimeNanos();
        Bitmap aligned112 = null;
        if (landmarks != null && landmarks.length >= 5) {
            aligned112 = FaceAligner.align5PointsTo112(rgbBitmap, landmarks);
        }
        if (aligned112 == null && landmarks != null && landmarks.length >= 2) {
            aligned112 = FaceAligner.alignEyesTo112(rgbBitmap, landmarks[0], landmarks[1]);
        }
        if (aligned112 == null && faceBox != null) {
            aligned112 = FaceAligner.cropTo112(rgbBitmap, faceBox, 0.15f);
        }
        long alignMs = (SystemClock.elapsedRealtimeNanos() - alignStart) / 1_000_000L;
        if (aligned112 == null) return null;

        try {
            float[] emb = embeddingModel.extractEmbedding(aligned112);
            long inferMs = embeddingModel.getLastInferenceMs();
            Log.i(TAG, String.format(java.util.Locale.US, "ExtractEmbedding [%s]: Align=%dms, ModelRun=%dms",
                    embeddingModel.getActiveDelegate(), alignMs, inferMs));
            return emb;
        } finally {
            if (!aligned112.isRecycled()) aligned112.recycle();
        }
    }

    /**
     * Backward-compatible overload without explicit landmarks.
     */
    public float[] extractEmbedding(Bitmap rgbBitmap, Rect faceBox) {
        return extractEmbedding(rgbBitmap, faceBox, null);
    }

    /**
     * Enrolls a new face template from the given image, face bounding box, and optional landmarks.
     */
    public FaceTemplate enrollFace(String id, String name, Bitmap rgbBitmap, Rect faceBox, PointF[] landmarks) {
        float[] embedding = extractEmbedding(rgbBitmap, faceBox, landmarks);
        if (embedding == null) return null;

        FaceTemplate template = new FaceTemplate(id, name, embedding, System.currentTimeMillis(), 1);
        enrolledTemplates.add(template);
        Log.i(TAG, "Enrolled face: ID=" + id + ", Name=" + name + ", Total=" + enrolledTemplates.size());
        return template;
    }

    public FaceTemplate enrollFace(String id, String name, Bitmap rgbBitmap, Rect faceBox) {
        return enrollFace(id, name, rgbBitmap, faceBox, null);
    }

    /**
     * Enrolls a new face template by averaging multiple 512-dim embedding vectors and re-normalizing L2.
     */
    public FaceTemplate enrollFaceAverage(String id, String name, List<float[]> embeddings) {
        if (embeddings == null || embeddings.isEmpty()) return null;

        float[] avgEmbedding = new float[FaceEmbeddingModel.EMBEDDING_DIM];
        int validCount = 0;
        for (float[] emb : embeddings) {
            if (emb != null && emb.length == FaceEmbeddingModel.EMBEDDING_DIM) {
                for (int i = 0; i < FaceEmbeddingModel.EMBEDDING_DIM; i++) {
                    avgEmbedding[i] += emb[i];
                }
                validCount++;
            }
        }
        if (validCount == 0) return null;

        for (int i = 0; i < FaceEmbeddingModel.EMBEDDING_DIM; i++) {
            avgEmbedding[i] /= (float) validCount;
        }
        FaceEmbeddingModel.normalizeL2(avgEmbedding);

        FaceTemplate template = new FaceTemplate(id, name, avgEmbedding, System.currentTimeMillis(), validCount);
        enrolledTemplates.add(template);
        Log.i(TAG, "Enrolled face (Multi-frame " + validCount + " frames averaged): ID=" + id
                + ", Name=" + name + ", Total=" + enrolledTemplates.size());
        return template;
    }

    /**
     * Identifies a face across all enrolled templates (1:N search) with landmarks.
     */
    public RecognitionResult identify(Bitmap rgbBitmap, Rect faceBox, PointF[] landmarks) {
        return identify(rgbBitmap, faceBox, landmarks, this.threshold);
    }

    public RecognitionResult identify(Bitmap rgbBitmap, Rect faceBox) {
        return identify(rgbBitmap, faceBox, null, this.threshold);
    }

    public RecognitionResult identify(Bitmap rgbBitmap, Rect faceBox, float matchThreshold) {
        return identify(rgbBitmap, faceBox, null, matchThreshold);
    }

    /**
     * Identifies a face across all enrolled templates with landmarks and specified threshold.
     */
    public RecognitionResult identify(Bitmap rgbBitmap, Rect faceBox, PointF[] landmarks, float matchThreshold) {
        long startNs = SystemClock.elapsedRealtimeNanos();
        if (embeddingModel == null) {
            return RecognitionResult.notRecognized(0f, matchThreshold, 0L, "Model not initialized");
        }
        if (enrolledTemplates.isEmpty()) {
            return RecognitionResult.notRecognized(0f, matchThreshold, 0L, "No enrolled templates");
        }

        float[] currentEmbedding = extractEmbedding(rgbBitmap, faceBox, landmarks);
        long elapsedMs = (SystemClock.elapsedRealtimeNanos() - startNs) / 1_000_000L;
        if (currentEmbedding == null) {
            return RecognitionResult.notRecognized(0f, matchThreshold, elapsedMs, "Face extraction failed");
        }

        return matchEmbedding(currentEmbedding, matchThreshold, elapsedMs);
    }

    /**
     * Matches a pre-extracted embedding against all enrolled templates.
     */
    public RecognitionResult matchEmbedding(float[] embedding, float matchThreshold, long elapsedMs) {
        if (embedding == null || enrolledTemplates.isEmpty()) {
            return RecognitionResult.notRecognized(0f, matchThreshold, elapsedMs, "No templates to match");
        }

        FaceTemplate bestTemplate = null;
        float bestScore = -1.0f;

        for (FaceTemplate template : enrolledTemplates) {
            float score = FaceEmbeddingModel.cosineSimilarity(embedding, template.getEmbedding());
            if (score > bestScore) {
                bestScore = score;
                bestTemplate = template;
            }
        }

        if (bestScore >= matchThreshold && bestTemplate != null) {
            return RecognitionResult.success(bestTemplate, bestScore, matchThreshold, elapsedMs);
        } else {
            return RecognitionResult.notRecognized(bestScore, matchThreshold, elapsedMs,
                    bestTemplate != null ? "Score below threshold (" + String.format("%.2f", bestScore) + ")" : "No match");
        }
    }

    @Override
    public void close() {
        if (embeddingModel != null) {
            embeddingModel.close();
            embeddingModel = null;
        }
        enrolledTemplates.clear();
    }
}
