package com.virditech.ac7000.model;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.SystemClock;
import android.util.Log;

import org.tensorflow.lite.DataType;
import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.Tensor;

import java.io.FileInputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public final class AntiSpoofingClassifier implements AutoCloseable {
    private static final String TAG = "AntiSpoofingClassifier";
    private static final String MODEL_NAME = "anti_spoofing.tflite";
    private static final int THREAD_COUNT = 2;
    private final Interpreter interpreter;
    private final String inferenceBackend;
    private final String backendStatus;
    private final ModelSpec spec;
    private final Tensor rgbTensor;
    private final Tensor irTensor;
    private final InputBuffer rgbInput;
    private final InputBuffer irInput;
    private final Object[] inputs = new Object[2];
    private final DataType outputDataType;
    private final Tensor.QuantizationParams outputQuantization;
    private final float[][] outputFloat = new float[1][5];
    private final byte[][] outputInt8 = new byte[1][5];
    private final Map<Integer, Object> outputs = new HashMap<>();

    public AntiSpoofingClassifier(Context context) throws Exception {
        spec = ModelSpec.load(context);
        InterpreterBundle bundle = createInterpreter(loadModel(context));
        interpreter = bundle.interpreter;
        inferenceBackend = bundle.backend;
        backendStatus = bundle.status;
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
        Tensor outputTensor = interpreter.getOutputTensor(0);
        outputDataType = outputTensor.dataType();
        outputQuantization = outputTensor.quantizationParams();
        int[] outputShape = outputTensor.shape();
        if ((outputDataType != DataType.FLOAT32 && outputDataType != DataType.INT8)
                || outputShape.length != 2 || outputShape[0] != 1 || outputShape[1] != 5) {
            throw new IllegalArgumentException("Output must be FLOAT32/INT8 [1,5], actual="
                    + outputDataType + " " + Arrays.toString(outputShape));
        }
        if (outputDataType == DataType.INT8 && outputQuantization.getScale() <= 0f) {
            throw new IllegalArgumentException("INT8 output must have a positive quantization scale");
        }
        outputs.put(0, outputDataType == DataType.FLOAT32 ? outputFloat : outputInt8);
    }

    public float cropMarginRatio() {
        return spec.cropMarginRatio;
    }

    public String inferenceBackend() {
        return inferenceBackend;
    }

    public String backendStatus() {
        return backendStatus;
    }

    public ClassificationResult classify(Bitmap rgb, Rect rgbBox, Bitmap ir, Rect irBox) {
        inputs[spec.rgbInputIndex] = rgbInput.fill(rgb, rgbBox, false);
        inputs[spec.irInputIndex] = irInput.fill(ir, irBox, true);
        long start = SystemClock.elapsedRealtimeNanos();
        interpreter.runForMultipleInputsOutputs(inputs, outputs);
        long inferenceMs = (SystemClock.elapsedRealtimeNanos() - start) / 1_000_000L;
        float[] modelOutput = readModelOutput();
        float[] probabilities = spec.outputIsLogits ? softmax(modelOutput) : validateProbabilities(modelOutput);

        return new ClassificationResult(probabilities, inferenceMs);
    }

    private static void validateInput(Tensor tensor, String name, int requiredChannels) {
        int[] shape = tensor.shape();
        if (shape.length != 4 || shape[0] != 1 || shape[1] <= 0 || shape[2] <= 0
                || (requiredChannels > 0 && shape[3] != requiredChannels)
                || (requiredChannels < 0 && shape[3] != 1 && shape[3] != 3)
                || (tensor.dataType() != DataType.FLOAT32 && tensor.dataType() != DataType.UINT8
                && tensor.dataType() != DataType.INT8)) {
            throw new IllegalArgumentException(name + " input must be NHWC FLOAT32/UINT8/INT8 with a supported channel count, actual="
                    + tensor.dataType() + " " + Arrays.toString(shape));
        }
        if (tensor.dataType() == DataType.INT8 && tensor.quantizationParams().getScale() <= 0f) {
            throw new IllegalArgumentException(name + " INT8 input must have a positive quantization scale");
        }
    }

    private float[] readModelOutput() {
        if (outputDataType == DataType.FLOAT32) return outputFloat[0].clone();
        float scale = outputQuantization.getScale();
        int zeroPoint = outputQuantization.getZeroPoint();
        float[] dequantized = new float[outputInt8[0].length];
        for (int i = 0; i < outputInt8[0].length; i++) {
            dequantized[i] = (outputInt8[0][i] - zeroPoint) * scale;
        }
        return dequantized;
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

    private static InterpreterBundle createInterpreter(MappedByteBuffer model) {
        try {
            Interpreter.Options nnapiOptions = new Interpreter.Options()
                    .setNumThreads(THREAD_COUNT)
                    .setUseNNAPI(true);
            Interpreter nnapiInterpreter = new Interpreter(model, nnapiOptions);
            nnapiInterpreter.allocateTensors();
            return new InterpreterBundle(nnapiInterpreter, "NNAPI", "Ready");
        } catch (RuntimeException nnapiError) {
            Log.w(TAG, "NNAPI delegate failed. Falling back to CPU/XNNPACK.", nnapiError);
            Interpreter.Options cpuOptions = new Interpreter.Options()
                    .setNumThreads(THREAD_COUNT)
                    .setUseXNNPACK(true);
            Interpreter cpuInterpreter = new Interpreter(model, cpuOptions);
            cpuInterpreter.allocateTensors();
            return new InterpreterBundle(cpuInterpreter, "CPU", "Ready - CPU fallback");
        }
    }

    @Override public void close() {
        rgbInput.close();
        irInput.close();
        interpreter.close();
    }

    private static final class InterpreterBundle {
        final Interpreter interpreter;
        final String backend;
        final String status;

        InterpreterBundle(Interpreter interpreter, String backend, String status) {
            this.interpreter = interpreter;
            this.backend = backend;
            this.status = status;
        }
    }

    private final class InputBuffer {
        final int width;
        final int height;
        final int channels;
        final DataType dataType;
        final Tensor.QuantizationParams quantization;
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
            quantization = tensor.quantizationParams();
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
                if (dataType == DataType.FLOAT32) {
                    while (buffer.hasRemaining()) buffer.putFloat(0f);
                } else {
                    byte zero = dataType == DataType.INT8 ? quantize(0f) : 0;
                    while (buffer.hasRemaining()) buffer.put(zero);
                }
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
                        buffer.putFloat(normalize(value, channel, infrared));
                    } else if (dataType == DataType.INT8) {
                        buffer.put(quantize(normalize(value, channel, infrared)));
                    } else {
                        buffer.put((byte) value);
                    }
                }
            }
            buffer.rewind();
            return buffer;
        }

        private float normalize(int value, int channel, boolean infrared) {
            float[] meanArr = infrared ? spec.irMean : spec.rgbMean;
            float[] stdArr = infrared ? spec.irStd : spec.rgbStd;
            float mean = meanArr.length == 1 ? meanArr[0] : meanArr[channel];
            float std = stdArr.length == 1 ? stdArr[0] : stdArr[channel];
            return ((value / 255.0f) - mean) / std;
        }

        private byte quantize(float value) {
            int quantized = Math.round(value / quantization.getScale()) + quantization.getZeroPoint();
            return (byte) Math.max(Byte.MIN_VALUE, Math.min(Byte.MAX_VALUE, quantized));
        }

        void close() {
            scaled.recycle();
        }
    }
}
