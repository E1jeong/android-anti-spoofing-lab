package com.virditech.ac7000.model;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.graphics.Canvas;
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
    private static final String MODEL_NAME = "anti_spoofing.tflite";
    private static final int THREAD_COUNT = Math.min(4, Runtime.getRuntime().availableProcessors());
    private static final int ONE_INPUT_COUNT = 1;
    private static final int TWO_INPUT_COUNT = 2;
    private static final int FIVE_INPUT_COUNT = 5;

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
    private final float[][] outputFloat = new float[1][6];
    private final byte[][] outputInt8 = new byte[1][6];
    private final Map<Integer, Object> outputs = new HashMap<>();

    public AntiSpoofingClassifier(Context context) throws Exception {
        this(context, MODEL_NAME, "model_spec.json");
    }

    public AntiSpoofingClassifier(Context context, String modelName, String specName) throws Exception {
        spec = ModelSpec.load(context, specName);
        InterpreterBundle bundle = createInterpreter(loadModel(context, modelName), spec.delegate, modelName, specName);
        interpreter = bundle.interpreter;
        inferenceBackend = bundle.backend;
        backendStatus = bundle.status;
        int inputTensorCount = interpreter.getInputTensorCount();
        if ((inputTensorCount != ONE_INPUT_COUNT && inputTensorCount != TWO_INPUT_COUNT && inputTensorCount != FIVE_INPUT_COUNT)
                || interpreter.getOutputTensorCount() != 1) {
            throw new IllegalArgumentException("Model must have exactly one, two, or five inputs and one output");
        }

        logModelIo();
        inputMapping = resolveInputMapping();
        if (inputMapping.cropRgbIndex >= 0) {
            Tensor cropRgbTensor = interpreter.getInputTensor(inputMapping.cropRgbIndex);
            validateInput(cropRgbTensor, "cropRgb", 3);
            cropRgbInput = new InputBuffer(cropRgbTensor, InputKind.RGB);
        } else {
            cropRgbInput = null;
        }
        if (inputMapping.cropIrIndex >= 0) {
            Tensor cropIrTensor = interpreter.getInputTensor(inputMapping.cropIrIndex);
            validateInput(cropIrTensor, "cropIr",
                    interpreter.getInputTensorCount() == ONE_INPUT_COUNT || inputMapping.hasFiveInputs() ? 1 : -1);
            cropIrInput = new InputBuffer(cropIrTensor, InputKind.IR);
        } else {
            cropIrInput = null;
        }
        if (inputMapping.hasFiveInputs()) {
            Tensor fullRgbTensor = interpreter.getInputTensor(inputMapping.fullRgbIndex);
            Tensor fullIrTensor = interpreter.getInputTensor(inputMapping.fullIrIndex);
            Tensor heatmapTensor = interpreter.getInputTensor(inputMapping.heatmapIndex);
            validateInput(fullRgbTensor, "fullRgb", 3);
            validateInput(fullIrTensor, "fullIr", 1);
            validateInput(heatmapTensor, "heatmap", 1);
            fullRgbInput = new InputBuffer(fullRgbTensor, InputKind.RGB);
            fullIrInput = new InputBuffer(fullIrTensor, InputKind.IR);
            heatmapInput = new InputBuffer(heatmapTensor, InputKind.HEATMAP);
        } else {
            fullRgbInput = null;
            fullIrInput = null;
            heatmapInput = null;
        }
        inputs = new Object[interpreter.getInputTensorCount()];

        Tensor outputTensor = interpreter.getOutputTensor(0);
        outputDataType = outputTensor.dataType();
        outputQuantization = outputTensor.quantizationParams();
        int[] outputShape = outputTensor.shape();
        if ((outputDataType != DataType.FLOAT32 && outputDataType != DataType.INT8)
                || outputShape.length != 2 || outputShape[0] != 1 || outputShape[1] != 6) {
            throw new IllegalArgumentException("Output must be FLOAT32/INT8 [1,6], actual="
                    + outputDataType + " " + Arrays.toString(outputShape));
        }
        if (outputDataType == DataType.INT8 && outputQuantization.getScale() <= 0f) {
            throw new IllegalArgumentException("INT8 output must have a positive quantization scale");
        }
        outputs.put(0, outputDataType == DataType.FLOAT32 ? outputFloat : outputInt8);

        try {
            long warmupStart = SystemClock.elapsedRealtime();
            if (cropRgbInput != null) inputs[inputMapping.cropRgbIndex] = cropRgbInput.zeroFill();
            if (cropIrInput != null) inputs[inputMapping.cropIrIndex] = cropIrInput.zeroFill();
            if (inputMapping.hasFiveInputs()) {
                inputs[inputMapping.fullRgbIndex] = fullRgbInput.zeroFill();
                inputs[inputMapping.fullIrIndex] = fullIrInput.zeroFill();
                inputs[inputMapping.heatmapIndex] = heatmapInput.zeroFill();
            }
            interpreter.runForMultipleInputsOutputs(inputs, outputs);
            long warmupDuration = SystemClock.elapsedRealtime() - warmupStart;
            Log.i(TAG, "Model warmup completed in " + warmupDuration + " ms using " + inferenceBackend);
        } catch (Exception e) {
            Log.e(TAG, "Failed to warmup model: " + e.getMessage(), e);
            try { close(); } catch (Exception closeError) { e.addSuppressed(closeError); }
            throw new IllegalStateException("Model warmup failed for " + modelName + " with " + specName, e);
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

    public int inputTensorCount() {
        return interpreter.getInputTensorCount();
    }

    public String singleInputKind() {
        return interpreter.getInputTensorCount() == ONE_INPUT_COUNT ? spec.inputKind : "";
    }

    public ClassificationResult classify(Bitmap rgb, Rect rgbBox, Bitmap ir, Rect irBox) {
        if (cropRgbInput != null) inputs[inputMapping.cropRgbIndex] = cropRgbInput.fillImage(rgb, rgbBox);
        if (cropIrInput != null) inputs[inputMapping.cropIrIndex] = cropIrInput.fillImage(ir, irBox);
        if (inputMapping.hasFiveInputs()) {
            inputs[inputMapping.fullRgbIndex] = fullRgbInput.fillImage(rgb, null);
            inputs[inputMapping.fullIrIndex] = fullIrInput.fillImage(ir, null);
            inputs[inputMapping.heatmapIndex] = heatmapInput.fillHeatmap(rgbBox, rgb.getWidth(), rgb.getHeight());
        }
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
        if (interpreter.getInputTensorCount() == ONE_INPUT_COUNT) {
            InputMapping mapping = new InputMapping();
            if ("rgb".equals(spec.inputKind)) {
                mapping.cropRgbIndex = 0;
            } else if ("ir".equals(spec.inputKind)) {
                mapping.cropIrIndex = 0;
            } else {
                throw new IllegalArgumentException("1-input model requires inputKind=rgb or inputKind=ir in model_spec.json");
            }
            Log.i(TAG, "Resolved 1-input mapping " + spec.inputKind + "=0");
            return mapping;
        }
        if (interpreter.getInputTensorCount() == TWO_INPUT_COUNT) {
            InputMapping mapping = new InputMapping();
            if (spec.rgbInputIndex < 0 || spec.irInputIndex < 0
                    || spec.rgbInputIndex == spec.irInputIndex
                    || spec.rgbInputIndex >= TWO_INPUT_COUNT || spec.irInputIndex >= TWO_INPUT_COUNT) {
                throw new IllegalArgumentException("2-input model requires rgbInputIndex and irInputIndex in model_spec.json");
            }
            mapping.cropRgbIndex = spec.rgbInputIndex;
            mapping.cropIrIndex = spec.irInputIndex;
            Log.i(TAG, "Resolved 2-input mapping cropRgb=" + mapping.cropRgbIndex
                    + ", cropIr=" + mapping.cropIrIndex);
            return mapping;
        }
        if (spec.inputs == null) {
            throw new IllegalArgumentException("5-input model requires named inputs in model_spec.json");
        }
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
        if (!mapping.hasFiveInputs()) {
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
        boolean validSize = spec.inputWidth > 0 && spec.inputHeight > 0
                ? shape.length == 4 && shape[1] == spec.inputHeight && shape[2] == spec.inputWidth
                : shape.length == 4 && shape[1] > 0 && shape[2] > 0;
        boolean validChannels = requiredChannels > 0
                ? shape.length == 4 && shape[3] == requiredChannels
                : shape.length == 4 && (shape[3] == 1 || shape[3] == 3);
        if (shape.length != 4 || shape[0] != 1 || !validSize || !validChannels
                || (tensor.dataType() != DataType.FLOAT32 && tensor.dataType() != DataType.UINT8
                && tensor.dataType() != DataType.INT8)) {
            String channels = requiredChannels > 0 ? String.valueOf(requiredChannels) : "1 or 3";
            String size = spec.inputWidth > 0 && spec.inputHeight > 0
                    ? spec.inputHeight + "x" + spec.inputWidth
                    : "positive height/width";
            throw new IllegalArgumentException(name + " input must be NHWC "
                    + size + "x" + channels + " FLOAT32/UINT8/INT8, actual="
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
        return loadModel(context, MODEL_NAME);
    }

    private static MappedByteBuffer loadModel(Context context, String modelName) throws Exception {
        try (AssetFileDescriptor descriptor = context.getAssets().openFd(modelName);
             FileInputStream input = new FileInputStream(descriptor.getFileDescriptor())) {
            return input.getChannel().map(FileChannel.MapMode.READ_ONLY, descriptor.getStartOffset(), descriptor.getDeclaredLength());
        }
    }

    // Do NOT enable NNAPI compilation caching (NnApiDelegate.Options.setCacheDir/setModelToken)
    // here: the i.MX 8M Plus VSI NPU driver fails compilation with
    // "File ... couldn't be opened for reading" + ANEURALNETWORKS_OP_FAILED when caching is set,
    // even for models that compile fine without it.
    private static InterpreterBundle createInterpreter(MappedByteBuffer model, String delegate,
                                                       String modelName, String specName) {
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
            throw new IllegalStateException("NNAPI delegate failed for " + modelName + " with " + specName, nnapiError);
        }
    }

    @Override public void close() {
        if (cropRgbInput != null) cropRgbInput.close();
        if (cropIrInput != null) cropIrInput.close();
        if (fullRgbInput != null) fullRgbInput.close();
        if (fullIrInput != null) fullIrInput.close();
        if (heatmapInput != null) heatmapInput.close();
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

        boolean hasFiveInputs() {
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
        // Per-channel 0..255 lookup tables replace per-pixel normalization/quantization math.
        final float[][] floatLut;
        final byte[][] byteLut;
        final float[] floatScratch;
        final byte[] byteScratch;
        final byte byteZero;
        final byte byteOne;
        final Rect cachedHeatmapBox = new Rect();
        boolean heatmapCached;

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
            if (dataType == DataType.FLOAT32) {
                floatScratch = new float[width * height * channels];
                byteScratch = null;
                floatLut = kind == InputKind.HEATMAP ? null : buildFloatLut();
                byteLut = null;
                byteZero = 0;
                byteOne = 0;
            } else {
                byteScratch = new byte[width * height * channels];
                floatScratch = null;
                byteLut = kind == InputKind.HEATMAP ? null : buildByteLut();
                floatLut = null;
                if (dataType == DataType.INT8) {
                    byteZero = quantize(0f);
                    byteOne = quantize(1f);
                } else {
                    byteZero = 0;
                    byteOne = (byte) 255;
                }
            }
        }

        private float[][] buildFloatLut() {
            float[][] lut = new float[channels][256];
            for (int channel = 0; channel < channels; channel++) {
                for (int value = 0; value < 256; value++) {
                    lut[channel][value] = normalizeImage(value, channel);
                }
            }
            return lut;
        }

        private byte[][] buildByteLut() {
            byte[][] lut = new byte[channels][256];
            for (int channel = 0; channel < channels; channel++) {
                for (int value = 0; value < 256; value++) {
                    lut[channel][value] = dataType == DataType.INT8
                            ? quantize(normalizeImage(value, channel))
                            : (byte) value;
                }
            }
            return lut;
        }

        ByteBuffer zeroFill() {
            heatmapCached = false;
            buffer.clear();
            if (dataType == DataType.FLOAT32) {
                Arrays.fill(floatScratch, 0f);
                buffer.asFloatBuffer().put(floatScratch);
            } else {
                Arrays.fill(byteScratch, dataType == DataType.INT8 ? byteZero : (byte) 0);
                buffer.put(byteScratch);
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
            if (dataType == DataType.FLOAT32) {
                fillFloatPixels();
                buffer.asFloatBuffer().put(floatScratch);
            } else {
                fillBytePixels();
                buffer.put(byteScratch);
            }
            buffer.rewind();
            return buffer;
        }

        private void fillFloatPixels() {
            int index = 0;
            if (kind == InputKind.IR) {
                for (int pixel : pixels) {
                    int value = (pixel >> 16) & 0xFF;
                    for (int channel = 0; channel < channels; channel++) {
                        floatScratch[index++] = floatLut[channel][value];
                    }
                }
            } else if (channels == 1) {
                float[] lut = floatLut[0];
                for (int pixel : pixels) {
                    floatScratch[index++] = lut[(pixel >> 16) & 0xFF];
                }
            } else {
                float[] lut0 = floatLut[0];
                float[] lut1 = floatLut[1];
                float[] lut2 = floatLut[2];
                if (spec.bgr) {
                    for (int pixel : pixels) {
                        floatScratch[index++] = lut0[pixel & 0xFF];
                        floatScratch[index++] = lut1[(pixel >> 8) & 0xFF];
                        floatScratch[index++] = lut2[(pixel >> 16) & 0xFF];
                    }
                } else {
                    for (int pixel : pixels) {
                        floatScratch[index++] = lut0[(pixel >> 16) & 0xFF];
                        floatScratch[index++] = lut1[(pixel >> 8) & 0xFF];
                        floatScratch[index++] = lut2[pixel & 0xFF];
                    }
                }
            }
        }

        private void fillBytePixels() {
            int index = 0;
            if (kind == InputKind.IR) {
                for (int pixel : pixels) {
                    int value = (pixel >> 16) & 0xFF;
                    for (int channel = 0; channel < channels; channel++) {
                        byteScratch[index++] = byteLut[channel][value];
                    }
                }
            } else if (channels == 1) {
                byte[] lut = byteLut[0];
                for (int pixel : pixels) {
                    byteScratch[index++] = lut[(pixel >> 16) & 0xFF];
                }
            } else {
                byte[] lut0 = byteLut[0];
                byte[] lut1 = byteLut[1];
                byte[] lut2 = byteLut[2];
                if (spec.bgr) {
                    for (int pixel : pixels) {
                        byteScratch[index++] = lut0[pixel & 0xFF];
                        byteScratch[index++] = lut1[(pixel >> 8) & 0xFF];
                        byteScratch[index++] = lut2[(pixel >> 16) & 0xFF];
                    }
                } else {
                    for (int pixel : pixels) {
                        byteScratch[index++] = lut0[(pixel >> 16) & 0xFF];
                        byteScratch[index++] = lut1[(pixel >> 8) & 0xFF];
                        byteScratch[index++] = lut2[pixel & 0xFF];
                    }
                }
            }
        }

        ByteBuffer fillHeatmap(Rect faceBox, int sourceWidth, int sourceHeight) {
            if (sourceWidth <= 0 || sourceHeight <= 0 || faceBox == null) return zeroFill();
            int left = clamp(Math.round(faceBox.left * width / (float) sourceWidth), 0, width);
            int top = clamp(Math.round(faceBox.top * height / (float) sourceHeight), 0, height);
            int right = clamp(Math.round(faceBox.right * width / (float) sourceWidth), 0, width);
            int bottom = clamp(Math.round(faceBox.bottom * height / (float) sourceHeight), 0, height);
            if (heatmapCached && cachedHeatmapBox.left == left && cachedHeatmapBox.top == top
                    && cachedHeatmapBox.right == right && cachedHeatmapBox.bottom == bottom) {
                buffer.rewind();
                return buffer;
            }
            buffer.clear();
            if (dataType == DataType.FLOAT32) {
                Arrays.fill(floatScratch, 0f);
                for (int y = top; y < bottom; y++) {
                    Arrays.fill(floatScratch, y * width + left, y * width + right, 1f);
                }
                buffer.asFloatBuffer().put(floatScratch);
            } else {
                Arrays.fill(byteScratch, byteZero);
                for (int y = top; y < bottom; y++) {
                    Arrays.fill(byteScratch, y * width + left, y * width + right, byteOne);
                }
                buffer.put(byteScratch);
            }
            buffer.rewind();
            cachedHeatmapBox.set(left, top, right, bottom);
            heatmapCached = true;
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
