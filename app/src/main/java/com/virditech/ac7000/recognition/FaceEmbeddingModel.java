package com.virditech.ac7000.recognition;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.util.Log;

import org.tensorflow.lite.DataType;
import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.Tensor;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;

/**
 * Executes face embedding extraction using MobileFaceNet.
 * Supports FLOAT32, FLOAT16, and Full INT8 models with CPU and NNAPI delegates.
 *
 * Isolated completely from the anti-spoofing model pipeline.
 */
public final class FaceEmbeddingModel implements AutoCloseable {
    private static final String TAG = "FaceEmbeddingModel";
    public static final String MODEL_NPU_INT8 = "models/w600k_mbf_npu_int8.tflite";
    public static final String MODEL_FLOAT16 = "models/w600k_mbf_static_float16.tflite";
    public static final String MODEL_FLOAT32 = "models/w600k_mbf_static_float32.tflite";
    public static final String DEFAULT_MODEL_PATH = MODEL_NPU_INT8;
    public static final int INPUT_SIZE = 112;
    public static final int EMBEDDING_DIM = 512;
    private static final float NORM_MEAN = 127.5f;
    private static final float NORM_STD = 128.0f;

    public enum DelegateType {
        CPU,
        NNAPI
    }

    private Interpreter interpreter;
    private final String modelAssetPath;
    private final String activeDelegate;
    private final DataType inputDataType;
    private final float inputScale;
    private final int inputZeroPoint;
    private final DataType outputDataType;
    private final float outputScale;
    private final int outputZeroPoint;
    private final ByteBuffer inputBuffer;
    private final int[] pixelBuffer = new int[INPUT_SIZE * INPUT_SIZE];
    private final float[][] outputBufferFloat;
    private final byte[][] outputBufferInt8;
    private final Object inferenceLock = new Object();
    private volatile long lastInferenceNs;

    public FaceEmbeddingModel(Context context) throws IOException {
        this(context, DEFAULT_MODEL_PATH, DelegateType.NNAPI);
    }

    public FaceEmbeddingModel(Context context, String modelAssetPath) throws IOException {
        this(context, modelAssetPath, DelegateType.NNAPI);
    }

    public FaceEmbeddingModel(Context context, String modelAssetPath, DelegateType delegateType) throws IOException {
        this.modelAssetPath = modelAssetPath;
        ByteBuffer modelBuffer = loadModelFile(context, modelAssetPath);

        Interpreter createdInterpreter = null;
        String delegateName = "CPU";

        if (delegateType == DelegateType.NNAPI) {
            try {
                // Do NOT setCacheDir / setModelToken for NNAPI on i.MX 8M Plus VSI NPU
                Interpreter.Options nnapiOptions = new Interpreter.Options().setUseNNAPI(true);
                nnapiOptions.setNumThreads(4);
                createdInterpreter = new Interpreter(modelBuffer, nnapiOptions);
                createdInterpreter.allocateTensors();
                delegateName = "NNAPI";
                Log.i(TAG, "Initialized FaceEmbeddingModel with NNAPI Delegate");
            } catch (Exception e) {
                Log.w(TAG, "NNAPI initialization failed, falling back to CPU: " + e.getMessage());
            }
        }

        if (createdInterpreter == null) {
            Interpreter.Options cpuOptions = new Interpreter.Options();
            cpuOptions.setUseXNNPACK(true);
            cpuOptions.setNumThreads(4);
            createdInterpreter = new Interpreter(modelBuffer, cpuOptions);
            createdInterpreter.allocateTensors();
            delegateName = "CPU";
        }

        this.interpreter = createdInterpreter;
        this.activeDelegate = delegateName;

        Tensor inputTensor = interpreter.getInputTensor(0);
        this.inputDataType = inputTensor.dataType();
        if (inputTensor.quantizationParams() != null) {
            this.inputScale = inputTensor.quantizationParams().getScale();
            this.inputZeroPoint = inputTensor.quantizationParams().getZeroPoint();
        } else {
            this.inputScale = 0f;
            this.inputZeroPoint = 0;
        }

        Tensor outputTensor = interpreter.getOutputTensor(0);
        this.outputDataType = outputTensor.dataType();
        if (outputTensor.quantizationParams() != null) {
            this.outputScale = outputTensor.quantizationParams().getScale();
            this.outputZeroPoint = outputTensor.quantizationParams().getZeroPoint();
        } else {
            this.outputScale = 0f;
            this.outputZeroPoint = 0;
        }

        int bytesPerInputVal = (inputDataType == DataType.FLOAT32) ? 4 : 1;
        this.inputBuffer = ByteBuffer.allocateDirect(1 * INPUT_SIZE * INPUT_SIZE * 3 * bytesPerInputVal);
        this.inputBuffer.order(ByteOrder.nativeOrder());

        if (outputDataType == DataType.FLOAT32) {
            this.outputBufferFloat = new float[1][EMBEDDING_DIM];
            this.outputBufferInt8 = null;
        } else {
            this.outputBufferFloat = null;
            this.outputBufferInt8 = new byte[1][EMBEDDING_DIM];
        }

        Log.i(TAG, String.format(java.util.Locale.US,
                "FaceEmbeddingModel loaded: %s (%s, inType=%s, outType=%s)",
                modelAssetPath, activeDelegate, inputDataType, outputDataType));
    }

    public String getActiveDelegate() {
        return activeDelegate;
    }

    public String getModelAssetPath() {
        return modelAssetPath;
    }

    public long getLastInferenceMs() {
        return lastInferenceNs / 1_000_000L;
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
            long startNs = android.os.SystemClock.elapsedRealtimeNanos();
            preprocess(alignedFace);
            float[] embedding = new float[EMBEDDING_DIM];
            if (outputDataType == DataType.FLOAT32) {
                interpreter.run(inputBuffer, outputBufferFloat);
                System.arraycopy(outputBufferFloat[0], 0, embedding, 0, EMBEDDING_DIM);
            } else {
                interpreter.run(inputBuffer, outputBufferInt8);
                float scale = outputScale > 0f ? outputScale : 1f;
                for (int i = 0; i < EMBEDDING_DIM; i++) {
                    embedding[i] = (outputBufferInt8[0][i] - outputZeroPoint) * scale;
                }
            }
            lastInferenceNs = android.os.SystemClock.elapsedRealtimeNanos() - startNs;
            normalizeL2(embedding);
            return embedding;
        }
    }

    private void preprocess(Bitmap bitmap) {
        inputBuffer.rewind();
        bitmap.getPixels(pixelBuffer, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE);
        if (inputDataType == DataType.FLOAT32) {
            for (int i = 0; i < pixelBuffer.length; i++) {
                int pixel = pixelBuffer[i];
                float r = (((pixel >> 16) & 0xFF) - NORM_MEAN) / NORM_STD;
                float g = (((pixel >> 8) & 0xFF) - NORM_MEAN) / NORM_STD;
                float b = ((pixel & 0xFF) - NORM_MEAN) / NORM_STD;
                inputBuffer.putFloat(r);
                inputBuffer.putFloat(g);
                inputBuffer.putFloat(b);
            }
        } else {
            float scale = inputScale > 0f ? inputScale : (1f / 127.5f);
            for (int i = 0; i < pixelBuffer.length; i++) {
                int pixel = pixelBuffer[i];
                float r = (((pixel >> 16) & 0xFF) - NORM_MEAN) / NORM_STD;
                float g = (((pixel >> 8) & 0xFF) - NORM_MEAN) / NORM_STD;
                float b = ((pixel & 0xFF) - NORM_MEAN) / NORM_STD;
                int qr = Math.max(-128, Math.min(127, Math.round(r / scale) + inputZeroPoint));
                int qg = Math.max(-128, Math.min(127, Math.round(g / scale) + inputZeroPoint));
                int qb = Math.max(-128, Math.min(127, Math.round(b / scale) + inputZeroPoint));
                inputBuffer.put((byte) qr);
                inputBuffer.put((byte) qg);
                inputBuffer.put((byte) qb);
            }
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
