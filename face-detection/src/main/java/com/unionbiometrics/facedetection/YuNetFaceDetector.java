package com.unionbiometrics.facedetection;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.Log;

import org.json.JSONObject;
import org.tensorflow.lite.DataType;
import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.Tensor;
import org.tensorflow.lite.nnapi.NnApiDelegate;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** RGB detector with optional five-point landmarks. Background-thread calls are serialized. */
public final class YuNetFaceDetector implements AutoCloseable {
    private static final String ASSET_DIRECTORY = "ubio-face-detection/";

    public enum Mode {
        INT8_NNAPI("int8"), FLOAT_CPU("float");
        final String artifact;
        Mode(String artifact) { this.artifact = artifact; }
    }

    private final ModelContract contract;
    private final Mode mode;
    private Interpreter interpreter;
    private NnApiDelegate delegate;
    private Bitmap resized;
    private Canvas canvas;
    private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final ByteBuffer input;
    private final ByteBuffer logits;
    private final ByteBuffer boxes;
    private final ByteBuffer landmarks;
    private final Object[] inputs;
    private final Map<Integer, Object> outputs = new HashMap<>();
    private final int[] pixels;
    private final float[] decodedLogits;
    private final float[] decodedBoxes;
    private final float[] decodedLandmarks;

    public static YuNetFaceDetector create(Context applicationContext) throws Exception {
        return create(applicationContext, Mode.INT8_NNAPI);
    }

    public static YuNetFaceDetector create(Context applicationContext, Mode mode) throws Exception {
        if (applicationContext == null || mode == null) throw new IllegalArgumentException("Context/mode required");
        return new YuNetFaceDetector(applicationContext.getApplicationContext(), mode);
    }

    private YuNetFaceDetector(Context context, Mode mode) throws Exception {
        this.mode = mode;
        contract = new ModelContract(new JSONObject(new String(readAsset(context, "manifest.json"),
                StandardCharsets.UTF_8)), mode.artifact);
        byte[] model = readAsset(context, contract.file);
        byte[] hash = MessageDigest.getInstance("SHA-256").digest(model);
        StringBuilder hex = new StringBuilder();
        for (byte value : hash) hex.append(String.format(java.util.Locale.US, "%02x", value & 255));
        if (!contract.sha256.equals(hex.toString())) throw new IllegalArgumentException("YuNet model hash mismatch");
        ByteBuffer modelBuffer = ByteBuffer.allocateDirect(model.length).order(ByteOrder.nativeOrder());
        modelBuffer.put(model).rewind();
        try {
            Interpreter.Options options = new Interpreter.Options()
                    .setNumThreads(Math.min(4, Runtime.getRuntime().availableProcessors()));
            if (mode == Mode.INT8_NNAPI) {
                // No compilation caching on the VSI driver, and no automatic CPU retry.
                delegate = new NnApiDelegate(new NnApiDelegate.Options().setUseNnapiCpu(false));
                options.setUseXNNPACK(false).addDelegate(delegate);
            } else {
                options.setUseXNNPACK(true);
            }
            interpreter = new Interpreter(modelBuffer, options);
            interpreter.allocateTensors();
            int outputCount = contract.landmarks == null ? 2 : 3;
            if (interpreter.getInputTensorCount() != 1 || interpreter.getOutputTensorCount() != outputCount) {
                throw new IllegalArgumentException("YuNet tensor count disagrees with manifest version");
            }
            validateTensor(interpreter.getInputTensor(0), contract.input);
            input = buffer(interpreter.getInputTensor(0));
            ByteBuffer foundLogits = null;
            ByteBuffer foundBoxes = null;
            ByteBuffer foundLandmarks = null;
            for (int slot = 0; slot < outputCount; slot++) {
                Tensor tensor = interpreter.getOutputTensor(slot);
                // Manifest tensor indices are graph indices, not Java output-slot indices.
                if (Arrays.equals(tensor.shape(), contract.logits.shape) && foundLogits == null) {
                    validateTensor(tensor, contract.logits);
                    foundLogits = buffer(tensor);
                    outputs.put(slot, foundLogits);
                } else if (Arrays.equals(tensor.shape(), contract.boxes.shape) && foundBoxes == null) {
                    validateTensor(tensor, contract.boxes);
                    foundBoxes = buffer(tensor);
                    outputs.put(slot, foundBoxes);
                } else if (contract.landmarks != null && foundLandmarks == null
                        && Arrays.equals(tensor.shape(), contract.landmarks.shape)) {
                    validateTensor(tensor, contract.landmarks);
                    foundLandmarks = buffer(tensor);
                    outputs.put(slot, foundLandmarks);
                } else {
                    throw new IllegalArgumentException("Unexpected YuNet output tensor");
                }
            }
            if (foundLogits == null || foundBoxes == null) throw new IllegalArgumentException("Missing YuNet output tensor");
            if (contract.landmarks != null && foundLandmarks == null) {
                throw new IllegalArgumentException("Missing YuNet landmark tensor");
            }
            logits = foundLogits;
            boxes = foundBoxes;
            landmarks = foundLandmarks;
            inputs = new Object[]{input};
            pixels = new int[contract.width * contract.height];
            decodedLogits = new float[contract.postprocessor.candidateCount * 2];
            decodedBoxes = new float[contract.postprocessor.candidateCount * 4];
            decodedLandmarks = contract.landmarks == null ? null
                    : new float[contract.postprocessor.candidateCount * 10];
            resized = Bitmap.createBitmap(contract.width, contract.height, Bitmap.Config.ARGB_8888);
            canvas = new Canvas(resized);
            // Warmup with correctly encoded black RGB, not zero bytes for quantized input.
            for (int i = 0; i < pixels.length * 3; i++) putPixel(0);
            run();
            Log.i("YuNetFaceDetector", "Loaded " + mode + ": " + contract.file
                    + " sha256=" + contract.sha256 + " (hardware delegation requires device verification)");
        } catch (Exception | Error error) {
            close();
            throw error;
        }
    }

    public Mode mode() { return mode; }

    /** Returns boxes and available landmarks in unmirrored source pixels. Does not recycle the image. */
    public synchronized List<FaceDetection> detect(Bitmap rgb) {
        if (interpreter == null) throw new IllegalStateException("YuNet detector is closed");
        if (rgb == null || rgb.isRecycled()) throw new IllegalArgumentException("Valid RGB bitmap required");
        Letterbox geometry = new Letterbox(rgb.getWidth(), rgb.getHeight(), contract.width, contract.height);
        canvas.drawColor(Color.BLACK);
        canvas.drawBitmap(rgb, null, new Rect(geometry.padLeft, geometry.padTop,
                geometry.padLeft + geometry.resizedWidth, geometry.padTop + geometry.resizedHeight), paint);
        resized.getPixels(pixels, 0, contract.width, 0, 0, contract.width, contract.height);
        input.rewind();
        for (int pixel : pixels) {
            putPixel((pixel >> 16) & 255);
            putPixel((pixel >> 8) & 255);
            putPixel(pixel & 255);
        }
        run();
        return contract.postprocessor.decode(decodedLogits, decodedBoxes, decodedLandmarks, geometry);
    }

    private void putPixel(int value) {
        if (contract.input.quantized) input.put(contract.input.quantize(value));
        else input.putFloat(value);
    }

    private void run() {
        input.rewind();
        logits.rewind();
        boxes.rewind();
        if (landmarks != null) landmarks.rewind();
        interpreter.runForMultipleInputsOutputs(inputs, outputs);
        unpack(logits, decodedLogits, contract.logits);
        unpack(boxes, decodedBoxes, contract.boxes);
        if (landmarks != null) unpack(landmarks, decodedLandmarks, contract.landmarks);
    }

    private static void unpack(ByteBuffer buffer, float[] values, ModelContract.TensorSpec spec) {
        buffer.rewind();
        for (int i = 0; i < values.length; i++) {
            values[i] = spec.quantized ? spec.dequantize(buffer.get()) : buffer.getFloat();
        }
    }

    private static ByteBuffer buffer(Tensor tensor) {
        return ByteBuffer.allocateDirect(tensor.numBytes()).order(ByteOrder.nativeOrder());
    }

    private static void validateTensor(Tensor tensor, ModelContract.TensorSpec spec) {
        DataType expectedType = spec.quantized ? DataType.INT8 : DataType.FLOAT32;
        if (!Arrays.equals(tensor.shape(), spec.shape) || tensor.dataType() != expectedType
                || Float.compare(tensor.quantizationParams().getScale(), spec.scale) != 0
                || tensor.quantizationParams().getZeroPoint() != spec.zeroPoint) {
            throw new IllegalArgumentException("YuNet tensor disagrees with manifest: " + tensor.name());
        }
    }

    private static byte[] readAsset(Context context, String name) throws Exception {
        try (InputStream stream = context.getAssets().open(ASSET_DIRECTORY + name);
             ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
            byte[] chunk = new byte[8192];
            int count;
            while ((count = stream.read(chunk)) != -1) bytes.write(chunk, 0, count);
            return bytes.toByteArray();
        }
    }

    @Override public synchronized void close() {
        try {
            if (interpreter != null) interpreter.close();
        } finally {
            interpreter = null;
            try {
                if (delegate != null) delegate.close();
            } finally {
                delegate = null;
                canvas = null;
                if (resized != null) resized.recycle();
                resized = null;
            }
        }
    }
}
