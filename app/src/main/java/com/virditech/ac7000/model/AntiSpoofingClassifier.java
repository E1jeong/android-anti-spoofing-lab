package com.virditech.ac7000.model;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
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
    private final InputBuffer rgbInput;
    private final InputBuffer irInput;
    private final Object[] inputs = new Object[2];
    private final float[][] output = new float[1][5];
    private final Map<Integer, Object> outputs = new HashMap<>();

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
        rgbInput = new InputBuffer(rgbTensor);
        irInput = new InputBuffer(irTensor);
        outputs.put(0, output);
        int[] outputShape = interpreter.getOutputTensor(0).shape();
        if (interpreter.getOutputTensor(0).dataType() != DataType.FLOAT32
                || outputShape.length != 2 || outputShape[0] != 1 || outputShape[1] != 5) {
            throw new IllegalArgumentException("Output must be FLOAT32 [1,5]");
        }
    }

    public float cropMarginRatio() {
        return spec.cropMarginRatio;
    }

    public ClassificationResult classify(Bitmap rgb, Rect rgbBox, Bitmap ir, Rect irBox) {
        inputs[spec.rgbInputIndex] = rgbInput.fill(rgb, rgbBox, false);
        inputs[spec.irInputIndex] = irInput.fill(ir, irBox, true);
        long start = SystemClock.elapsedRealtimeNanos();
        interpreter.runForMultipleInputsOutputs(inputs, outputs);
        long inferenceMs = (SystemClock.elapsedRealtimeNanos() - start) / 1_000_000L;
        float[] probabilities = spec.outputIsLogits ? softmax(output[0]) : validateProbabilities(output[0]);

        return new ClassificationResult(probabilities, inferenceMs);
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
        rgbInput.close();
        irInput.close();
        interpreter.close();
    }

    private final class InputBuffer {
        final int width;
        final int height;
        final int channels;
        final DataType dataType;
        final Bitmap scaled;
        final Canvas canvas;
        final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
        final Rect sourceRect = new Rect();
        final Rect targetRect;
        final int[] pixels;
        final ByteBuffer buffer;

        InputBuffer(Tensor tensor) {
            int[] shape = tensor.shape();
            height = shape[1];
            width = shape[2];
            channels = shape[3];
            dataType = tensor.dataType();
            int bytesPerValue = dataType == DataType.FLOAT32 ? 4 : 1;
            scaled = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            canvas = new Canvas(scaled);
            targetRect = new Rect(0, 0, width, height);
            pixels = new int[width * height];
            buffer = ByteBuffer.allocateDirect(width * height * channels * bytesPerValue).order(ByteOrder.nativeOrder());
        }

        ByteBuffer fill(Bitmap source, Rect box, boolean infrared) {
            int left = Math.max(0, box.left);
            int top = Math.max(0, box.top);
            int right = Math.min(source.getWidth(), box.right);
            int bottom = Math.min(source.getHeight(), box.bottom);
            buffer.clear();
            if (right <= left || bottom <= top) {
                while (buffer.hasRemaining()) buffer.put((byte) 0);
                buffer.rewind();
                return buffer;
            }

            sourceRect.set(left, top, right, bottom);
            canvas.drawBitmap(source, sourceRect, targetRect, paint);
            scaled.getPixels(pixels, 0, width, 0, 0, width, height);
            for (int pixel : pixels) {
                int r = Color.red(pixel);
                int g = Color.green(pixel);
                int b = Color.blue(pixel);
                for (int channel = 0; channel < channels; channel++) {
                    int value;
                    if (infrared || channels == 1) value = r;
                    else if (spec.bgr) value = channel == 0 ? b : channel == 1 ? g : r;
                    else value = channel == 0 ? r : channel == 1 ? g : b;
                    if (dataType == DataType.FLOAT32) {
                        float[] meanArr = infrared ? spec.irMean : spec.rgbMean;
                        float[] stdArr = infrared ? spec.irStd : spec.rgbStd;
                        float mean = meanArr.length == 1 ? meanArr[0] : meanArr[channel];
                        float std = stdArr.length == 1 ? stdArr[0] : stdArr[channel];
                        buffer.putFloat(((value / 255.0f) - mean) / std);
                    } else {
                        buffer.put((byte) value);
                    }
                }
            }
            buffer.rewind();
            return buffer;
        }

        void close() {
            scaled.recycle();
        }
    }
}
