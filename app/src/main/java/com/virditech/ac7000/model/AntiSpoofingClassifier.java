package com.virditech.ac7000.model;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Rect;
import android.os.SystemClock;

import org.tensorflow.lite.DataType;
import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.Tensor;

import java.io.FileInputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.HashMap;
import java.util.Map;

public final class AntiSpoofingClassifier implements AutoCloseable {
    private static final String MODEL_NAME = "anti_spoofing.tflite";
    private final Interpreter interpreter;
    private final ModelSpec spec;
    private final Tensor rgbTensor;
    private final Tensor irTensor;

    public AntiSpoofingClassifier(Context context) throws Exception {
        spec = ModelSpec.load(context);
        interpreter = new Interpreter(loadModel(context), new Interpreter.Options().setNumThreads(2));
        if (interpreter.getInputTensorCount() != 2 || interpreter.getOutputTensorCount() != 1) {
            throw new IllegalArgumentException("Model must have exactly two inputs and one output");
        }
        if (spec.rgbInputIndex < 0 || spec.rgbInputIndex > 1 || spec.irInputIndex < 0 || spec.irInputIndex > 1) {
            throw new IllegalArgumentException("Input indexes must be 0 and 1");
        }
        rgbTensor = interpreter.getInputTensor(spec.rgbInputIndex);
        irTensor = interpreter.getInputTensor(spec.irInputIndex);
        validateInput(rgbTensor, "RGB", 3);
        validateInput(irTensor, "IR", -1);
        int[] outputShape = interpreter.getOutputTensor(0).shape();
        if (interpreter.getOutputTensor(0).dataType() != DataType.FLOAT32
                || outputShape.length != 2 || outputShape[0] != 1 || outputShape[1] != 4) {
            throw new IllegalArgumentException("Output must be FLOAT32 [1,4]");
        }
    }

    public float cropMarginRatio() {
        return spec.cropMarginRatio;
    }

    public ClassificationResult classify(Bitmap rgb, Rect rgbBox, Bitmap ir, Rect irBox) {
        Object[] inputs = new Object[2];
        inputs[spec.rgbInputIndex] = makeInput(rgb, rgbBox, rgbTensor, false);
        inputs[spec.irInputIndex] = makeInput(ir, irBox, irTensor, true);
        float[][] output = new float[1][4];
        Map<Integer, Object> outputs = new HashMap<>();
        outputs.put(0, output);
        long start = SystemClock.elapsedRealtimeNanos();
        interpreter.runForMultipleInputsOutputs(inputs, outputs);
        long inferenceMs = (SystemClock.elapsedRealtimeNanos() - start) / 1_000_000L;
        float[] probabilities = spec.outputIsLogits ? softmax(output[0]) : validateProbabilities(output[0]);
        return new ClassificationResult(probabilities, inferenceMs);
    }

    private ByteBuffer makeInput(Bitmap source, Rect box, Tensor tensor, boolean infrared) {
        int[] shape = tensor.shape();
        int height = shape[1];
        int width = shape[2];
        int channels = shape[3];
        Bitmap crop = Bitmap.createBitmap(source, box.left, box.top, box.width(), box.height());
        Bitmap scaled = Bitmap.createScaledBitmap(crop, width, height, true);
        if (scaled != crop) crop.recycle();
        int bytesPerValue = tensor.dataType() == DataType.FLOAT32 ? 4 : 1;
        ByteBuffer buffer = ByteBuffer.allocateDirect(width * height * channels * bytesPerValue).order(ByteOrder.nativeOrder());
        int[] pixels = new int[width * height];
        scaled.getPixels(pixels, 0, width, 0, 0, width, height);
        scaled.recycle();
        for (int pixel : pixels) {
            int r = Color.red(pixel);
            int g = Color.green(pixel);
            int b = Color.blue(pixel);
            for (int channel = 0; channel < channels; channel++) {
                int value;
                if (infrared || channels == 1) value = r;
                else if (spec.bgr) value = channel == 0 ? b : channel == 1 ? g : r;
                else value = channel == 0 ? r : channel == 1 ? g : b;
                if (tensor.dataType() == DataType.FLOAT32) {
                    float mean = infrared ? spec.irMean : spec.rgbMean;
                    float std = infrared ? spec.irStd : spec.rgbStd;
                    buffer.putFloat((value - mean) / std);
                } else {
                    buffer.put((byte) value);
                }
            }
        }
        buffer.rewind();
        return buffer;
    }

    private static void validateInput(Tensor tensor, String name, int requiredChannels) {
        int[] shape = tensor.shape();
        if (shape.length != 4 || shape[0] != 1 || shape[1] <= 0 || shape[2] <= 0
                || (requiredChannels > 0 && shape[3] != requiredChannels)
                || (requiredChannels < 0 && shape[3] != 1 && shape[3] != 3)
                || (tensor.dataType() != DataType.FLOAT32 && tensor.dataType() != DataType.UINT8)) {
            throw new IllegalArgumentException(name + " input must be NHWC FLOAT32/UINT8 with a supported channel count");
        }
    }

    private static float[] softmax(float[] logits) {
        float max = logits[0];
        for (float value : logits) max = Math.max(max, value);
        float sum = 0f;
        float[] output = new float[logits.length];
        for (int i = 0; i < logits.length; i++) {
            output[i] = (float) Math.exp(logits[i] - max);
            sum += output[i];
        }
        for (int i = 0; i < output.length; i++) output[i] /= sum;
        return output;
    }

    private static float[] validateProbabilities(float[] values) {
        float sum = 0f;
        for (float value : values) {
            if (!Float.isFinite(value) || value < 0f || value > 1f) {
                throw new IllegalStateException("Model probability is outside [0,1]");
            }
            sum += value;
        }
        if (Math.abs(sum - 1f) > 0.02f) throw new IllegalStateException("Model probabilities do not sum to 1");
        return values.clone();
    }

    private static MappedByteBuffer loadModel(Context context) throws Exception {
        try (AssetFileDescriptor descriptor = context.getAssets().openFd(MODEL_NAME);
             FileInputStream input = new FileInputStream(descriptor.getFileDescriptor())) {
            return input.getChannel().map(FileChannel.MapMode.READ_ONLY, descriptor.getStartOffset(), descriptor.getDeclaredLength());
        }
    }

    @Override public void close() {
        interpreter.close();
    }
}
