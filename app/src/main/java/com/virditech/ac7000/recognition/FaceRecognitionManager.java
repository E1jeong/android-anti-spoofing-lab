package com.virditech.ac7000.recognition;

import android.content.Context;
import android.graphics.Bitmap;
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
 * Totally decoupled from anti-spoofing classifiers.
 */
public final class FaceRecognitionManager implements AutoCloseable {
    private static final String TAG = "FaceRecognitionManager";
    public static final float DEFAULT_THRESHOLD = 0.70f;

    private final List<FaceTemplate> enrolledTemplates = new CopyOnWriteArrayList<>();
    private FaceEmbeddingModel embeddingModel;
    private float threshold = DEFAULT_THRESHOLD;
    private String initError;

    public FaceRecognitionManager(Context context) {
        try {
            embeddingModel = new FaceEmbeddingModel(context);
        } catch (Exception e) {
            initError = e.getMessage();
            Log.e(TAG, "Failed to initialize FaceEmbeddingModel: " + e.getMessage(), e);
        }
    }

    public boolean isReady() {
        return embeddingModel != null;
    }

    public String getInitError() {
        return initError;
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
     * Extracts an embedding from an RGB Bitmap face region.
     */
    public float[] extractEmbedding(Bitmap rgbBitmap, Rect faceBox) {
        if (embeddingModel == null || rgbBitmap == null || faceBox == null) return null;
        Bitmap cropped112 = FaceAligner.cropTo112(rgbBitmap, faceBox, 0.15f);
        if (cropped112 == null) return null;
        try {
            return embeddingModel.extractEmbedding(cropped112);
        } finally {
            if (!cropped112.isRecycled()) cropped112.recycle();
        }
    }

    /**
     * Enrolls a new face template from the given image and face bounding box.
     */
    public FaceTemplate enrollFace(String id, String name, Bitmap rgbBitmap, Rect faceBox) {
        float[] embedding = extractEmbedding(rgbBitmap, faceBox);
        if (embedding == null) return null;

        FaceTemplate template = new FaceTemplate(id, name, embedding, System.currentTimeMillis(), 1);
        enrolledTemplates.add(template);
        Log.i(TAG, "Enrolled face: ID=" + id + ", Name=" + name + ", Total=" + enrolledTemplates.size());
        return template;
    }

    /**
     * Identifies a face across all enrolled templates (1:N search).
     */
    public RecognitionResult identify(Bitmap rgbBitmap, Rect faceBox) {
        return identify(rgbBitmap, faceBox, this.threshold);
    }

    /**
     * Identifies a face across all enrolled templates with a specified threshold.
     */
    public RecognitionResult identify(Bitmap rgbBitmap, Rect faceBox, float matchThreshold) {
        long startNs = SystemClock.elapsedRealtimeNanos();
        if (embeddingModel == null) {
            return RecognitionResult.notRecognized(0f, matchThreshold, 0L, "Model not initialized");
        }
        if (enrolledTemplates.isEmpty()) {
            return RecognitionResult.notRecognized(0f, matchThreshold, 0L, "No enrolled templates");
        }

        float[] currentEmbedding = extractEmbedding(rgbBitmap, faceBox);
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
