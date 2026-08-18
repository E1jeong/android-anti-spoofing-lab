package com.virditech.ac7000.recognition;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.util.Log;

import org.tensorflow.lite.Interpreter;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;

/**
 * Executes face embedding extraction using MobileFaceNet (w600k_mbf_static_float32.tflite).
 * Input: [1, 112, 112, 3] Float32, normalized to [-1.0, 1.0].
 * Output: [1, 512] Float32, L2-normalized face identity embedding.
 *
 * Isolated completely from the anti-spoofing model pipeline.
 */
public final class FaceEmbeddingModel implements AutoCloseable {
    private static final String TAG = "FaceEmbeddingModel";
    public static final String DEFAULT_MODEL_PATH = "models/w600k_mbf_static_float32.tflite";
    public static final int INPUT_SIZE = 112;
    public static final int EMBEDDING_DIM = 512;
    private static final float NORM_MEAN = 127.5f;
    private static final float NORM_STD = 128.0f;

    private Interpreter interpreter;
    private final ByteBuffer inputBuffer;
    private final int[] pixelBuffer = new int[INPUT_SIZE * INPUT_SIZE];
    private final float[][] outputBuffer = new float[1][EMBEDDING_DIM];
    private final Object inferenceLock = new Object();

    public FaceEmbeddingModel(Context context) throws IOException {
        this(context, DEFAULT_MODEL_PATH);
    }

    public FaceEmbeddingModel(Context context, String modelAssetPath) throws IOException {
        ByteBuffer modelBuffer = loadModelFile(context, modelAssetPath);
        Interpreter.Options options = new Interpreter.Options();
        options.setNumThreads(Math.min(4, Math.max(1, Runtime.getRuntime().availableProcessors() - 1)));
        interpreter = new Interpreter(modelBuffer, options);

        inputBuffer = ByteBuffer.allocateDirect(1 * INPUT_SIZE * INPUT_SIZE * 3 * 4);
        inputBuffer.order(ByteOrder.nativeOrder());
        Log.i(TAG, "FaceEmbeddingModel initialized successfully from " + modelAssetPath);
    }

    /**
     * Extracts an L2-normalized 512-dimensional embedding from a 112x112 RGB Bitmap.
     *
     * @param alignedFace 112x112 ARGB_8888 or RGB Bitmap.
     * @return 512-dim normalized float array, or null on failure.
     */
    public float[] extractEmbedding(Bitmap alignedFace) {
        if (alignedFace == null || interpreter == null) return null;
        if (alignedFace.getWidth() != INPUT_SIZE || alignedFace.getHeight() != INPUT_SIZE) {
            throw new IllegalArgumentException("Input bitmap must be 112x112, got "
                    + alignedFace.getWidth() + "x" + alignedFace.getHeight());
        }

        synchronized (inferenceLock) {
            if (interpreter == null) return null;
            preprocess(alignedFace);
            interpreter.run(inputBuffer, outputBuffer);
            float[] embedding = outputBuffer[0].clone();
            normalizeL2(embedding);
            return embedding;
        }
    }

    private void preprocess(Bitmap bitmap) {
        inputBuffer.rewind();
        bitmap.getPixels(pixelBuffer, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE);
        for (int i = 0; i < pixelBuffer.length; i++) {
            int pixel = pixelBuffer[i];
            float r = (((pixel >> 16) & 0xFF) - NORM_MEAN) / NORM_STD;
            float g = (((pixel >> 8) & 0xFF) - NORM_MEAN) / NORM_STD;
            float b = ((pixel & 0xFF) - NORM_MEAN) / NORM_STD;
            inputBuffer.putFloat(r);
            inputBuffer.putFloat(g);
            inputBuffer.putFloat(b);
        }
    }

    public static void normalizeL2(float[] vector) {
        if (vector == null || vector.length == 0) return;
        float sumSq = 0f;
        for (float v : vector) {
            sumSq += v * v;
        }
        float norm = (float) Math.sqrt(sumSq);
        if (norm > 1e-10f) {
            for (int i = 0; i < vector.length; i++) {
                vector[i] /= norm;
            }
        }
    }

    /**
     * Computes Cosine Similarity (dot product of L2-normalized vectors).
     */
    public static float cosineSimilarity(float[] v1, float[] v2) {
        if (v1 == null || v2 == null || v1.length != v2.length) return 0f;
        float dot = 0f;
        for (int i = 0; i < v1.length; i++) {
            dot += v1[i] * v2[i];
        }
        return dot;
    }

    private static ByteBuffer loadModelFile(Context context, String assetPath) throws IOException {
        try (AssetFileDescriptor afd = context.getAssets().openFd(assetPath);
             FileInputStream inputStream = new FileInputStream(afd.getFileDescriptor())) {
            FileChannel fileChannel = inputStream.getChannel();
            long startOffset = afd.getStartOffset();
            long declaredLength = afd.getDeclaredLength();
            return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
        }
    }

    @Override
    public void close() {
        synchronized (inferenceLock) {
            if (interpreter != null) {
                interpreter.close();
                interpreter = null;
            }
        }
    }
}
