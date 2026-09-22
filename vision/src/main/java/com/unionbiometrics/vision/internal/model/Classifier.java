package com.unionbiometrics.vision.internal.model;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.SystemClock;
import android.util.Log;

import com.unionbiometrics.vision.internal.asset.AssetLoader;

import org.tensorflow.lite.DataType;
import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.Tensor;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

final class Classifier implements AutoCloseable {
    private static final String TAG = "AntiSpoofingClassifier";
    private static final int THREAD_COUNT = Math.min(4, Runtime.getRuntime().availableProcessors());
    private static final int ONE_INPUT_COUNT = 1;
    private static final int TWO_INPUT_COUNT = 2;
    // Match the host's former ColorMatrix.setSaturation(0) conversion.
    private static final float LUMINANCE_RED = 0.213f;
    private static final float LUMINANCE_GREEN = 0.715f;
    private static final float LUMINANCE_BLUE = 0.072f;

    private final Interpreter interpreter;
    private final String inferenceBackend;
    private final Spec spec;
    private final InputMapping inputMapping;
    private final InputBuffer cropRgbInput;
    private final InputBuffer cropIrInput;
    private final Object[] inputs;
    private final DataType outputDataType;
    private final Tensor.QuantizationParams outputQuantization;
    private final float[][] outputFloat = new float[1][ClassificationResult.LABELS.length];
    private final byte[][] outputInt8 = new byte[1][ClassificationResult.LABELS.length];
    private final Map<Integer, Object> outputs = new HashMap<>();

    Classifier(Context context, String modelName, String specName) throws Exception {
        spec = Spec.load(context, specName);
        InterpreterBundle bundle = createInterpreter(loadModel(context, modelName), spec.delegate, modelName, specName);
        interpreter = bundle.interpreter;
        inferenceBackend = bundle.backend;
        InputMapping mapping = null;
        InputBuffer cropRgb = null;
        InputBuffer cropIr = null;
        Object[] liveInputs;
        DataType liveOutputType;
        Tensor.QuantizationParams liveOutputQuantization;
        try {
            int inputTensorCount = interpreter.getInputTensorCount();
            if ((inputTensorCount != ONE_INPUT_COUNT && inputTensorCount != TWO_INPUT_COUNT)
                    || interpreter.getOutputTensorCount() != 1) {
                throw new IllegalArgumentException("Model must have exactly one or two inputs and one output");
            }

            logModelIo();
            mapping = resolveInputMapping();
            if (mapping.cropRgbIndex >= 0) {
                Tensor cropRgbTensor = interpreter.getInputTensor(mapping.cropRgbIndex);
                validateInput(cropRgbTensor, "cropRgb", 3);
                cropRgb = new InputBuffer(cropRgbTensor, InputKind.RGB);
            }
            if (mapping.cropIrIndex >= 0) {
                Tensor cropIrTensor = interpreter.getInputTensor(mapping.cropIrIndex);
                validateInput(cropIrTensor, "cropIr", 1);
                cropIr = new InputBuffer(cropIrTensor, InputKind.IR);
            }
            liveInputs = new Object[interpreter.getInputTensorCount()];

            Tensor outputTensor = interpreter.getOutputTensor(0);
            liveOutputType = outputTensor.dataType();
            liveOutputQuantization = outputTensor.quantizationParams();
            int[] outputShape = outputTensor.shape();
            if ((liveOutputType != DataType.FLOAT32 && liveOutputType != DataType.INT8)
                    || outputShape.length != 2 || outputShape[0] != 1
                    || outputShape[1] != ClassificationResult.LABELS.length) {
                throw new IllegalArgumentException("Output must be FLOAT32/INT8 [1,"
                        + ClassificationResult.LABELS.length + "], actual="
                        + liveOutputType + " " + Arrays.toString(outputShape));
            }
            if (liveOutputType == DataType.INT8 && liveOutputQuantization.getScale() <= 0f) {
                throw new IllegalArgumentException("INT8 output must have a positive quantization scale");
            }
            outputs.put(0, liveOutputType == DataType.FLOAT32 ? outputFloat : outputInt8);

            long warmupStart = SystemClock.elapsedRealtime();
            if (cropRgb != null) liveInputs[mapping.cropRgbIndex] = cropRgb.zeroFill();
            if (cropIr != null) liveInputs[mapping.cropIrIndex] = cropIr.zeroFill();
            interpreter.runForMultipleInputsOutputs(liveInputs, outputs);
            Log.i(TAG, "Model warmup completed in "
                    + (SystemClock.elapsedRealtime() - warmupStart)
                    + " ms using " + inferenceBackend);
        } catch (IllegalArgumentException e) {
            releasePartial(interpreter, cropRgb, cropIr);
            throw e;
        } catch (Exception e) {
            Log.e(TAG, "Failed to warmup model: " + e.getMessage(), e);
            releasePartial(interpreter, cropRgb, cropIr);
            throw new IllegalStateException("Model warmup failed for " + modelName + " with " + specName, e);
        }
        inputMapping = mapping;
        cropRgbInput = cropRgb;
        cropIrInput = cropIr;
        inputs = liveInputs;
        outputDataType = liveOutputType;
        outputQuantization = liveOutputQuantization;
    }

    public float cropMarginRatio() {
        return spec.cropMarginRatio;
    }

    public String inferenceBackend() {
        return inferenceBackend;
    }

    public int inputTensorCount() {
        return interpreter.getInputTensorCount();
    }

    public ClassificationResult classify(Bitmap rgb, Rect rgbBox, Bitmap ir, Rect irBox) {
        long preprocessStart = SystemClock.elapsedRealtimeNanos();
        if (cropRgbInput != null)
            inputs[inputMapping.cropRgbIndex] = cropRgbInput.fillImage(rgb, rgbBox);
        if (cropIrInput != null)
            inputs[inputMapping.cropIrIndex] = cropIrInput.fillImage(ir, irBox);
        long preprocessMs = (SystemClock.elapsedRealtimeNanos() - preprocessStart) / 1_000_000L;
        long start = SystemClock.elapsedRealtimeNanos();
        interpreter.runForMultipleInputsOutputs(inputs, outputs);
        long inferenceMs = (SystemClock.elapsedRealtimeNanos() - start) / 1_000_000L;
        float[] modelOutput = readModelOutput();
        float[] probabilities = spec.outputIsLogits ? softmax(modelOutput) : validateProbabilities(modelOutput);

        return new ClassificationResult(probabilities, preprocessMs, inferenceMs);
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
            if (!"ir".equals(spec.inputKind)) {
                throw new IllegalArgumentException("1-input model requires inputKind=ir in model_spec.json");
            }
            mapping.cropIrIndex = 0;
            Log.i(TAG, "Resolved 1-input mapping ir=0");
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
        throw new IllegalArgumentException("Unsupported model input count");
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
        if (Math.abs(sum - 1f) > 0.02f)
            throw new IllegalStateException("Model probabilities do not sum to 1");
        return values.clone();
    }

    private static MappedByteBuffer loadModel(Context context, String modelName) throws Exception {
        return AssetLoader.mapModel(context, modelName);
    }

    private static void releasePartial(Interpreter interpreter, InputBuffer cropRgb, InputBuffer cropIr) {
        if (cropRgb != null) cropRgb.close();
        if (cropIr != null) cropIr.close();
        try {
            interpreter.close();
        } catch (Exception ignored) {
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
            return createAllocatedInterpreter(model, cpuOptions, "CPU");
        }
        try {
            Interpreter.Options nnapiOptions = new Interpreter.Options()
                    .setNumThreads(THREAD_COUNT)
                    .setUseNNAPI(true);
            return createAllocatedInterpreter(model, nnapiOptions, "NNAPI");
        } catch (RuntimeException nnapiError) {
            throw new IllegalStateException("NNAPI delegate failed for " + modelName + " with " + specName, nnapiError);
        }
    }

    private static InterpreterBundle createAllocatedInterpreter(MappedByteBuffer model,
                                                                Interpreter.Options options,
                                                                String backend) {
        Interpreter interpreter = new Interpreter(model, options);
        try {
            interpreter.allocateTensors();
            return new InterpreterBundle(interpreter, backend);
        } catch (RuntimeException e) {
            try {
                interpreter.close();
            } catch (Exception closeError) {
                e.addSuppressed(closeError);
            }
            throw e;
        }
    }

    @Override
    public void close() {
        if (cropRgbInput != null) cropRgbInput.close();
        if (cropIrInput != null) cropIrInput.close();
        interpreter.close();
    }

    private enum InputKind {
        RGB,
        IR
    }

    private static final class InputMapping {
        int cropRgbIndex = -1;
        int cropIrIndex = -1;
    }

    private static final class InterpreterBundle {
        final Interpreter interpreter;
        final String backend;

        InterpreterBundle(Interpreter interpreter, String backend) {
            this.interpreter = interpreter;
            this.backend = backend;
        }
    }

    static int irLuminance(int pixel) {
        int red = (pixel >> 16) & 0xFF;
        int green = (pixel >> 8) & 0xFF;
        int blue = pixel & 0xFF;
        return Math.round(LUMINANCE_RED * red + LUMINANCE_GREEN * green + LUMINANCE_BLUE * blue);
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

        InputBuffer(Tensor tensor, InputKind kind) {
            int[] shape = tensor.shape();
            height = shape[1];
            width = shape[2];
            channels = shape[3];
            dataType = tensor.dataType();
            quantization = tensor.quantizationParams();
            this.kind = kind;
            int bytesPerValue = dataType == DataType.FLOAT32 ? 4 : 1;
            scaled = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            canvas = new Canvas(scaled);
            targetRect = new Rect(0, 0, width, height);
            pixels = new int[width * height];
            buffer = ByteBuffer.allocateDirect(width * height * channels * bytesPerValue).order(ByteOrder.nativeOrder());
            if (dataType == DataType.FLOAT32) {
                floatScratch = new float[width * height * channels];
                byteScratch = null;
                floatLut = buildFloatLut();
                byteLut = null;
            } else {
                byteScratch = new byte[width * height * channels];
                floatScratch = null;
                byteLut = buildByteLut();
                floatLut = null;
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
            buffer.clear();
            if (dataType == DataType.FLOAT32) {
                Arrays.fill(floatScratch, 0f);
                buffer.asFloatBuffer().put(floatScratch);
            } else {
                Arrays.fill(byteScratch, dataType == DataType.INT8 ? quantize(0f) : (byte) 0);
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
                    int value = irLuminance(pixel);
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
                    int value = irLuminance(pixel);
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

        private float normalizeImage(int value, int channel) {
            if (kind == InputKind.IR) {
                return normalizeWithMeanStd(value / 255.0f, spec.irMean, spec.irStd, channel);
            }
            if (Spec.RGB_NORMALIZATION_MINUS_ONE_TO_ONE.equals(spec.rgbNormalization)) {
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
            scaled.recycle();
        }
    }

}
