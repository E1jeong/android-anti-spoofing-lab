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
import java.util.Locale;
import java.util.Map;

public final class AntiSpoofingClassifier implements AutoCloseable {
    private static final String TAG = "AntiSpoofingClassifier";
    private static final String MODEL_NAME = "anti_spoofing_npu.tflite";
    private static final int THREAD_COUNT = 2;
    private static final int INPUT_COUNT = 5;

    private final Interpreter interpreter;
    private final String inferenceBackend;
    private final String backendStatus;
    private final ModelSpec spec;
    private final InputMapping inputMapping;
    private final InputBuffer cropRgbInput;
    private final InputBuffer cropIrInput;
    private final InputBuffer fullRgbInput;
    private final InputBuffer fullIrInput;
    private final InputBuffer heatmapInput;
    private final Object[] inputs;
    private final DataType outputDataType;
    private final Tensor.QuantizationParams outputQuantization;
    private final float[][] outputFloat = new float[1][5];
    private final byte[][] outputInt8 = new byte[1][5];
    private final Map<Integer, Object> outputs = new HashMap<>();

    public AntiSpoofingClassifier(Context context) throws Exception {
        this(context, MODEL_NAME, "model_spec.json");
    }

    public AntiSpoofingClassifier(Context context, String modelName, String specName) throws Exception {
        spec = ModelSpec.load(context, specName);
        InterpreterBundle bundle = createInterpreter(loadModel(context, modelName), spec.delegate);
        interpreter = bundle.interpreter;
        inferenceBackend = bundle.backend;
        backendStatus = bundle.status;
        if (interpreter.getInputTensorCount() != INPUT_COUNT || interpreter.getOutputTensorCount() != 1) {
            throw new IllegalArgumentException("Model must have exactly five inputs and one output");
        }

        logModelIo();
        inputMapping = resolveInputMapping();
        Tensor cropRgbTensor = interpreter.getInputTensor(inputMapping.cropRgbIndex);
        Tensor cropIrTensor = interpreter.getInputTensor(inputMapping.cropIrIndex);
        Tensor fullRgbTensor = interpreter.getInputTensor(inputMapping.fullRgbIndex);
        Tensor fullIrTensor = interpreter.getInputTensor(inputMapping.fullIrIndex);
        Tensor heatmapTensor = interpreter.getInputTensor(inputMapping.heatmapIndex);
        validateInput(cropRgbTensor, "cropRgb", 3);
        validateInput(cropIrTensor, "cropIr", 1);
        validateInput(fullRgbTensor, "fullRgb", 3);
        validateInput(fullIrTensor, "fullIr", 1);
        validateInput(heatmapTensor, "heatmap", 1);
        cropRgbInput = new InputBuffer(cropRgbTensor, InputKind.RGB);
        cropIrInput = new InputBuffer(cropIrTensor, InputKind.IR);
        fullRgbInput = new InputBuffer(fullRgbTensor, InputKind.RGB);
        fullIrInput = new InputBuffer(fullIrTensor, InputKind.IR);
        heatmapInput = new InputBuffer(heatmapTensor, InputKind.HEATMAP);
        inputs = new Object[interpreter.getInputTensorCount()];

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

        try {
            long warmupStart = SystemClock.elapsedRealtime();
            inputs[inputMapping.cropRgbIndex] = cropRgbInput.zeroFill();
            inputs[inputMapping.cropIrIndex] = cropIrInput.zeroFill();
            inputs[inputMapping.fullRgbIndex] = fullRgbInput.zeroFill();
            inputs[inputMapping.fullIrIndex] = fullIrInput.zeroFill();
            inputs[inputMapping.heatmapIndex] = heatmapInput.zeroFill();
            interpreter.runForMultipleInputsOutputs(inputs, outputs);
            long warmupDuration = SystemClock.elapsedRealtime() - warmupStart;
            Log.i(TAG, "Model warmup completed in " + warmupDuration + " ms using " + inferenceBackend);
        } catch (Exception e) {
            Log.e(TAG, "Failed to warmup model: " + e.getMessage(), e);
        }
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
        inputs[inputMapping.cropRgbIndex] = cropRgbInput.fillImage(rgb, rgbBox);
        inputs[inputMapping.cropIrIndex] = cropIrInput.fillImage(ir, irBox);
        inputs[inputMapping.fullRgbIndex] = fullRgbInput.fillImage(rgb, null);
        inputs[inputMapping.fullIrIndex] = fullIrInput.fillImage(ir, null);
        inputs[inputMapping.heatmapIndex] = heatmapInput.fillHeatmap(rgbBox, rgb.getWidth(), rgb.getHeight());
        long start = SystemClock.elapsedRealtimeNanos();
        interpreter.runForMultipleInputsOutputs(inputs, outputs);
        long inferenceMs = (SystemClock.elapsedRealtimeNanos() - start) / 1_000_000L;
        float[] modelOutput = readModelOutput();
        float[] probabilities = spec.outputIsLogits ? softmax(modelOutput) : validateProbabilities(modelOutput);

        return new ClassificationResult(probabilities, inferenceMs);
    }

    private void logModelIo() {
        Log.i(TAG, "Input tensor count=" + interpreter.getInputTensorCount());
        for (int i = 0; i < interpreter.getInputTensorCount(); i++) {
            Tensor tensor = interpreter.getInputTensor(i);
            Tensor.QuantizationParams quantization = tensor.quantizationParams();
            Log.i(TAG, String.format(Locale.US,
                    "Input[%d] name=%s shape=%s dtype=%s quantization(scale=%f, zeroPoint=%d)",
                    i, tensor.name(), Arrays.toString(tensor.shape()), tensor.dataType(),
                    quantization.getScale(), quantization.getZeroPoint()));
        }
        Tensor output = interpreter.getOutputTensor(0);
        Log.i(TAG, "Output[0] shape=" + Arrays.toString(output.shape()) + " dtype=" + output.dataType());
    }

    private InputMapping resolveInputMapping() {
        InputMapping mapping = new InputMapping();
        String cropRgbTarget = spec.inputs.cropRgb.toLowerCase(Locale.US);
        String cropIrTarget = spec.inputs.cropIr.toLowerCase(Locale.US);
        String fullRgbTarget = spec.inputs.fullRgb.toLowerCase(Locale.US);
        String fullIrTarget = spec.inputs.fullIr.toLowerCase(Locale.US);
        String heatmapTarget = spec.inputs.heatmap.toLowerCase(Locale.US);

        for (int i = 0; i < interpreter.getInputTensorCount(); i++) {
            String name = interpreter.getInputTensor(i).name().toLowerCase(Locale.US);
            if (name.contains(cropRgbTarget)) {
                mapping.cropRgbIndex = assignUnique(mapping.cropRgbIndex, i, "cropRgb");
            } else if (name.contains(cropIrTarget)) {
                mapping.cropIrIndex = assignUnique(mapping.cropIrIndex, i, "cropIr");
            } else if (name.contains(fullRgbTarget)) {
                mapping.fullRgbIndex = assignUnique(mapping.fullRgbIndex, i, "fullRgb");
            } else if (name.contains(fullIrTarget)) {
                mapping.fullIrIndex = assignUnique(mapping.fullIrIndex, i, "fullIr");
            } else if (name.contains(heatmapTarget)) {
                mapping.heatmapIndex = assignUnique(mapping.heatmapIndex, i, "heatmap");
            }
        }
        if (!mapping.isComplete()) {
            throw new IllegalArgumentException("Unable to map all model inputs by tensor name. Expected "
                    + spec.inputs.cropRgb + ", " + spec.inputs.cropIr + ", " + spec.inputs.fullRgb + ", "
                    + spec.inputs.fullIr + ", " + spec.inputs.heatmap);
        }
        Log.i(TAG, "Resolved input mapping cropRgb=" + mapping.cropRgbIndex
                + ", cropIr=" + mapping.cropIrIndex
                + ", fullRgb=" + mapping.fullRgbIndex
                + ", fullIr=" + mapping.fullIrIndex
                + ", heatmap=" + mapping.heatmapIndex);
        return mapping;
    }

    private static int assignUnique(int current, int next, String label) {
        if (current >= 0) throw new IllegalArgumentException("Duplicate tensor mapping for " + label);
        return next;
    }

    private void validateInput(Tensor tensor, String name, int requiredChannels) {
        int[] shape = tensor.shape();
        if (shape.length != 4 || shape[0] != 1 || shape[1] != spec.inputHeight || shape[2] != spec.inputWidth
                || shape[3] != requiredChannels
                || (tensor.dataType() != DataType.FLOAT32 && tensor.dataType() != DataType.UINT8
                && tensor.dataType() != DataType.INT8)) {
            throw new IllegalArgumentException(name + " input must be NHWC "
                    + spec.inputHeight + "x" + spec.inputWidth + "x" + requiredChannels
                    + " FLOAT32/UINT8/INT8, actual=" + tensor.dataType() + " " + Arrays.toString(shape));
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
        return loadModel(context, MODEL_NAME);
    }

    private static MappedByteBuffer loadModel(Context context, String modelName) throws Exception {
        try (AssetFileDescriptor descriptor = context.getAssets().openFd(modelName);
             FileInputStream input = new FileInputStream(descriptor.getFileDescriptor())) {
            return input.getChannel().map(FileChannel.MapMode.READ_ONLY, descriptor.getStartOffset(), descriptor.getDeclaredLength());
        }
    }

    private static InterpreterBundle createInterpreter(MappedByteBuffer model, String delegate) {
        if ("cpu".equals(delegate)) {
            Interpreter.Options cpuOptions = new Interpreter.Options()
                    .setNumThreads(THREAD_COUNT)
                    .setUseXNNPACK(true);
            Interpreter cpuInterpreter = new Interpreter(model, cpuOptions);
            cpuInterpreter.allocateTensors();
            return new InterpreterBundle(cpuInterpreter, "CPU", "Ready - CPU requested");
        }
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
        cropRgbInput.close();
        cropIrInput.close();
        fullRgbInput.close();
        fullIrInput.close();
        heatmapInput.close();
        interpreter.close();
    }

    private enum InputKind {
        RGB,
        IR,
        HEATMAP
    }

    private static final class InputMapping {
        int cropRgbIndex = -1;
        int cropIrIndex = -1;
        int fullRgbIndex = -1;
        int fullIrIndex = -1;
        int heatmapIndex = -1;

        boolean isComplete() {
            return cropRgbIndex >= 0 && cropIrIndex >= 0 && fullRgbIndex >= 0
                    && fullIrIndex >= 0 && heatmapIndex >= 0;
        }
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
        final InputKind kind;
        final Bitmap scaled;
        final Canvas canvas;
        final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
        final Rect sourceRect = new Rect();
        final Rect targetRect;
        final int[] pixels;
        final ByteBuffer buffer;

        InputBuffer(Tensor tensor, InputKind kind) {
            int[] shape = tensor.shape();
            height = shape[1];
            width = shape[2];
            channels = shape[3];
            dataType = tensor.dataType();
            quantization = tensor.quantizationParams();
            this.kind = kind;
            int bytesPerValue = dataType == DataType.FLOAT32 ? 4 : 1;
            scaled = kind == InputKind.HEATMAP ? null : Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            canvas = scaled == null ? null : new Canvas(scaled);
            targetRect = new Rect(0, 0, width, height);
            pixels = kind == InputKind.HEATMAP ? null : new int[width * height];
            buffer = ByteBuffer.allocateDirect(width * height * channels * bytesPerValue).order(ByteOrder.nativeOrder());
        }

        ByteBuffer zeroFill() {
            buffer.clear();
            if (dataType == DataType.FLOAT32) {
                while (buffer.hasRemaining()) buffer.putFloat(0f);
            } else if (dataType == DataType.INT8) {
                byte zero = quantize(0f);
                while (buffer.hasRemaining()) buffer.put(zero);
            } else {
                while (buffer.hasRemaining()) buffer.put((byte) 0);
            }
            buffer.rewind();
            return buffer;
        }

        ByteBuffer fillImage(Bitmap source, Rect box) {
            if (source == null) return zeroFill();
            int left = 0;
            int top = 0;
            int right = source.getWidth();
            int bottom = source.getHeight();
            if (box != null) {
                left = Math.max(0, box.left);
                top = Math.max(0, box.top);
                right = Math.min(source.getWidth(), box.right);
                bottom = Math.min(source.getHeight(), box.bottom);
            }
            if (right <= left || bottom <= top) return zeroFill();

            sourceRect.set(left, top, right, bottom);
            canvas.drawBitmap(source, sourceRect, targetRect, paint);
            scaled.getPixels(pixels, 0, width, 0, 0, width, height);
            buffer.clear();
            for (int pixel : pixels) {
                int r = Color.red(pixel);
                int g = Color.green(pixel);
                int b = Color.blue(pixel);
                for (int channel = 0; channel < channels; channel++) {
                    int value;
                    if (kind == InputKind.IR || channels == 1) value = r;
                    else if (spec.bgr) value = channel == 0 ? b : channel == 1 ? g : r;
                    else value = channel == 0 ? r : channel == 1 ? g : b;
                    putPixelValue(value, channel);
                }
            }
            buffer.rewind();
            return buffer;
        }

        ByteBuffer fillHeatmap(Rect faceBox, int sourceWidth, int sourceHeight) {
            if (sourceWidth <= 0 || sourceHeight <= 0 || faceBox == null) return zeroFill();
            int left = clamp(Math.round(faceBox.left * width / (float) sourceWidth), 0, width);
            int top = clamp(Math.round(faceBox.top * height / (float) sourceHeight), 0, height);
            int right = clamp(Math.round(faceBox.right * width / (float) sourceWidth), 0, width);
            int bottom = clamp(Math.round(faceBox.bottom * height / (float) sourceHeight), 0, height);
            buffer.clear();
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    putHeatmapValue(x >= left && x < right && y >= top && y < bottom ? 1f : 0f);
                }
            }
            buffer.rewind();
            return buffer;
        }

        private float normalizeImage(int value, int channel) {
            if (kind == InputKind.IR) {
                return normalizeWithMeanStd(value / 255.0f, spec.irMean, spec.irStd, channel);
            }
            if (ModelSpec.RGB_NORMALIZATION_MINUS_ONE_TO_ONE.equals(spec.rgbNormalization)) {
                return value / 127.5f - 1.0f;
            }
            return normalizeWithMeanStd(value / 255.0f, spec.rgbMean, spec.rgbStd, channel);
        }

        private float normalizeWithMeanStd(float value, float[] meanArr, float[] stdArr, int channel) {
            float mean = meanArr.length == 1 ? meanArr[0] : meanArr[channel];
            float std = stdArr.length == 1 ? stdArr[0] : stdArr[channel];
            return (value - mean) / std;
        }

        private void putPixelValue(int rawValue, int channel) {
            if (dataType == DataType.FLOAT32) {
                buffer.putFloat(normalizeImage(rawValue, channel));
            } else if (dataType == DataType.INT8) {
                buffer.put(quantize(normalizeImage(rawValue, channel)));
            } else {
                buffer.put((byte) Math.max(0, Math.min(255, rawValue)));
            }
        }

        private void putHeatmapValue(float value) {
            if (dataType == DataType.FLOAT32) {
                buffer.putFloat(value);
            } else if (dataType == DataType.INT8) {
                buffer.put(quantize(value));
            } else {
                int raw = Math.round(value * 255f);
                buffer.put((byte) Math.max(0, Math.min(255, raw)));
            }
        }

        private byte quantize(float value) {
            int quantized = Math.round(value / quantization.getScale()) + quantization.getZeroPoint();
            return (byte) Math.max(Byte.MIN_VALUE, Math.min(Byte.MAX_VALUE, quantized));
        }

        void close() {
            if (scaled != null) scaled.recycle();
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
