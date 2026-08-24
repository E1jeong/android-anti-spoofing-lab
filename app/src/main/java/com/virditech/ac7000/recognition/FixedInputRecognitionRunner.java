package com.virditech.ac7000.recognition;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.Environment;
import android.os.SystemClock;
import android.util.Log;

import com.virditech.ac7000.BuildConfig;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Runs an embedding model against fixed 112x112 files without camera or face alignment. */
public final class FixedInputRecognitionRunner {
    private static final String TAG = "FixedInputRecognition";
    private static final String INPUT_DIRECTORY_NAME = "recognition-fixed-input";
    public static final int REPEAT_COUNT = 5;

    private FixedInputRecognitionRunner() {}

    public static Result run(Context context, String modelAssetPath) {
        File inputDir = resolveInputDirectory();
        if (!inputDir.isDirectory() && !inputDir.mkdirs()) {
            return new Result(false, null, "Unable to create " + inputDir.getAbsolutePath(), false);
        }
        File outputFile = new File(inputDir,
                "recognition-fixed-input-result_" + System.currentTimeMillis() + ".json");
        JSONObject report = new JSONObject();
        List<InputImage> inputs = new ArrayList<>();
        boolean nnapiActive = false;
        String message;
        boolean completed = false;
        try {
            report.put("schemaVersion", 1);
            report.put("createdAtEpochMs", System.currentTimeMillis());
            report.put("appVersionName", BuildConfig.VERSION_NAME);
            report.put("appVersionCode", BuildConfig.VERSION_CODE);
            JSONObject device = new JSONObject();
            device.put("manufacturer", Build.MANUFACTURER);
            device.put("model", Build.MODEL);
            device.put("device", Build.DEVICE);
            device.put("sdkInt", Build.VERSION.SDK_INT);
            report.put("device", device);
            report.put("modelAssetPath", modelAssetPath);
            report.put("modelSha256", sha256(context.getAssets().open(modelAssetPath)));
            report.put("inputDirectory", inputDir.getAbsolutePath());
            report.put("repeatCount", REPEAT_COUNT);
            report.put("executionOrder", new JSONArray(new String[]{"CPU", "NNAPI"}));
            inputs = loadInputs(inputDir);
            report.put("inputCount", inputs.size());

            Map<String, float[]> cpuEmbeddings = new HashMap<>();
            Map<String, float[]> nnapiEmbeddings = new HashMap<>();
            JSONArray delegates = new JSONArray();
            DelegateRun cpu = benchmarkDelegate(context, modelAssetPath,
                    FaceEmbeddingModel.DelegateType.CPU, inputs, cpuEmbeddings);
            delegates.put(cpu.json);
            report.put("delegates", delegates);
            DelegateRun nnapi = benchmarkDelegate(context, modelAssetPath,
                    FaceEmbeddingModel.DelegateType.NNAPI, inputs, nnapiEmbeddings);
            delegates.put(nnapi.json);
            nnapiActive = "NNAPI".equals(nnapi.activeDelegate);
            report.put("delegates", delegates);
            report.put("crossDelegate", crossDelegateJson(inputs, cpuEmbeddings,
                    nnapiEmbeddings, nnapiActive));
            report.put("status", "complete");
            completed = true;
            message = "Fixed-input test complete: " + inputs.size() + " input(s), NNAPI active="
                    + nnapiActive;
        } catch (Exception e) {
            message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            Log.e(TAG, "Fixed-input recognition test failed", e);
            try {
                report.put("status", "failed");
                report.put("error", message);
            } catch (Exception ignored) {}
        } finally {
            for (InputImage input : inputs) input.recycle();
        }

        try (FileOutputStream out = new FileOutputStream(outputFile)) {
            out.write(report.toString(2).getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            return new Result(false, null, "Unable to write result: " + e.getMessage(), nnapiActive);
        }
        Log.i(TAG, message + " result=" + outputFile.getAbsolutePath());
        return new Result(completed, outputFile, message, nnapiActive);
    }

    private static DelegateRun benchmarkDelegate(Context context, String modelAssetPath,
                                                 FaceEmbeddingModel.DelegateType requestedDelegate,
                                                 List<InputImage> inputs,
                                                 Map<String, float[]> firstEmbeddings) throws Exception {
        checkInterrupted();
        long loadStartNs = SystemClock.elapsedRealtimeNanos();
        try (FaceEmbeddingModel model = new FaceEmbeddingModel(context, modelAssetPath, requestedDelegate)) {
            long loadWarmupMs = elapsedMs(loadStartNs);
            JSONArray samples = new JSONArray();
            List<Long> allRunMs = new ArrayList<>();
            for (InputImage input : inputs) {
                checkInterrupted();
                JSONArray runMs = new JSONArray();
                JSONArray repeatCosines = new JSONArray();
                JSONArray embeddingNorms = new JSONArray();
                float[] first = null;
                float minRepeatCosine = 1f;
                for (int repeat = 0; repeat < REPEAT_COUNT; repeat++) {
                    checkInterrupted();
                    long startNs = SystemClock.elapsedRealtimeNanos();
                    float[] embedding = model.extractEmbedding(input.bitmap);
                    long durationMs = elapsedMs(startNs);
                    allRunMs.add(durationMs);
                    runMs.put(durationMs);
                    embeddingNorms.put(FaceEmbeddingModel.l2Norm(embedding));
                    if (first == null) {
                        first = embedding;
                    } else {
                        float cosine = FaceEmbeddingModel.cosineSimilarity(first, embedding);
                        repeatCosines.put(cosine);
                        minRepeatCosine = Math.min(minRepeatCosine, cosine);
                    }
                }
                firstEmbeddings.put(input.file.getName(), first);
                JSONObject sample = new JSONObject();
                sample.put("file", input.file.getName());
                sample.put("sha256", input.sha256);
                sample.put("runMs", runMs);
                sample.put("p50Ms", percentile(toLongArray(runMs), 50));
                sample.put("p95Ms", percentile(toLongArray(runMs), 95));
                sample.put("repeatCosinesToFirst", repeatCosines);
                sample.put("minRepeatCosine", minRepeatCosine);
                sample.put("embeddingNorms", embeddingNorms);
                sample.put("allEmbeddingsValid", true);
                samples.put(sample);
            }

            JSONObject delegate = new JSONObject();
            delegate.put("requestedDelegate", requestedDelegate.name());
            delegate.put("activeDelegate", model.getActiveDelegate());
            delegate.put("inputShape", new JSONArray(new int[]{1, FaceEmbeddingModel.INPUT_SIZE,
                    FaceEmbeddingModel.INPUT_SIZE, 3}));
            delegate.put("inputType", model.getInputDataType().name());
            delegate.put("outputShape", new JSONArray(new int[]{1, FaceEmbeddingModel.EMBEDDING_DIM}));
            delegate.put("outputType", model.getOutputDataType().name());
            delegate.put("loadAndWarmupMs", loadWarmupMs);
            long[] timings = new long[allRunMs.size()];
            for (int i = 0; i < timings.length; i++) timings[i] = allRunMs.get(i);
            delegate.put("overallP50Ms", percentile(timings, 50));
            delegate.put("overallP95Ms", percentile(timings, 95));
            delegate.put("samples", samples);
            Log.i(TAG, String.format(Locale.US,
                    "Fixed-input delegate requested=%s active=%s inputs=%d repeats=%d loadWarmup=%dms P50=%dms P95=%dms",
                    requestedDelegate, model.getActiveDelegate(), inputs.size(), REPEAT_COUNT,
                    loadWarmupMs, percentile(timings, 50), percentile(timings, 95)));
            return new DelegateRun(delegate, model.getActiveDelegate());
        }
    }

    private static JSONObject crossDelegateJson(List<InputImage> inputs,
                                                Map<String, float[]> cpuEmbeddings,
                                                Map<String, float[]> nnapiEmbeddings,
                                                boolean nnapiActive) throws Exception {
        JSONObject cross = new JSONObject();
        cross.put("applicable", nnapiActive);
        JSONArray samples = new JSONArray();
        for (InputImage input : inputs) {
            JSONObject sample = new JSONObject();
            sample.put("file", input.file.getName());
            if (nnapiActive) {
                sample.put("cpuNnapiCosine", FaceEmbeddingModel.cosineSimilarity(
                        cpuEmbeddings.get(input.file.getName()), nnapiEmbeddings.get(input.file.getName())));
            } else {
                sample.put("cpuNnapiCosine", JSONObject.NULL);
            }
            samples.put(sample);
        }
        cross.put("samples", samples);
        if (!nnapiActive) cross.put("reason", "NNAPI request did not produce an active NNAPI interpreter");
        return cross;
    }

    static long percentile(long[] values, int percentile) {
        if (values == null || values.length == 0) {
            throw new IllegalArgumentException("values must not be empty");
        }
        if (percentile < 1 || percentile > 100) {
            throw new IllegalArgumentException("percentile must be between 1 and 100");
        }
        long[] sorted = values.clone();
        Arrays.sort(sorted);
        int index = (int) Math.ceil(percentile / 100.0 * sorted.length) - 1;
        return sorted[Math.max(0, index)];
    }

    private static List<InputImage> loadInputs(File inputDir) throws Exception {
        File[] files = inputDir.listFiles(file -> file.isFile() && isImageFilename(file.getName()));
        if (files == null || files.length == 0) {
            throw new IllegalStateException("Place one or more 112x112 PNG/JPG/BMP files in "
                    + inputDir.getAbsolutePath());
        }
        Arrays.sort(files, (left, right) -> left.getName().compareToIgnoreCase(right.getName()));
        List<InputImage> inputs = new ArrayList<>();
        try {
            for (File file : files) {
                Bitmap bitmap = BitmapFactory.decodeFile(file.getAbsolutePath());
                if (bitmap == null) throw new IllegalArgumentException("Unable to decode " + file.getName());
                if (bitmap.getWidth() != FaceEmbeddingModel.INPUT_SIZE
                        || bitmap.getHeight() != FaceEmbeddingModel.INPUT_SIZE) {
                    int width = bitmap.getWidth();
                    int height = bitmap.getHeight();
                    bitmap.recycle();
                    throw new IllegalArgumentException("Fixed input must be 112x112: " + file.getName()
                            + " is " + width + "x" + height);
                }
                inputs.add(new InputImage(file, bitmap, sha256(new FileInputStream(file))));
            }
            return inputs;
        } catch (Exception e) {
            for (InputImage input : inputs) input.recycle();
            throw e;
        }
    }

    static boolean isImageFilename(String name) {
        if (name == null) return false;
        String lower = name.toLowerCase(Locale.US);
        return lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg")
                || lower.endsWith(".bmp");
    }

    public static File resolveInputDirectory() {
        return new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                INPUT_DIRECTORY_NAME);
    }

    private static String sha256(InputStream rawInput) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = new BufferedInputStream(rawInput)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) digest.update(buffer, 0, read);
        }
        StringBuilder hex = new StringBuilder(64);
        for (byte value : digest.digest()) hex.append(String.format(Locale.US, "%02x", value & 0xff));
        return hex.toString();
    }

    private static long[] toLongArray(JSONArray values) throws Exception {
        long[] result = new long[values.length()];
        for (int i = 0; i < result.length; i++) result[i] = values.getLong(i);
        return result;
    }

    private static long elapsedMs(long startNs) {
        return (SystemClock.elapsedRealtimeNanos() - startNs) / 1_000_000L;
    }

    private static void checkInterrupted() throws InterruptedException {
        if (Thread.currentThread().isInterrupted()) throw new InterruptedException("Fixed-input test cancelled");
    }

    private static final class InputImage {
        final File file;
        final Bitmap bitmap;
        final String sha256;

        InputImage(File file, Bitmap bitmap, String sha256) {
            this.file = file;
            this.bitmap = bitmap;
            this.sha256 = sha256;
        }

        void recycle() {
            if (!bitmap.isRecycled()) bitmap.recycle();
        }
    }

    private static final class DelegateRun {
        final JSONObject json;
        final String activeDelegate;

        DelegateRun(JSONObject json, String activeDelegate) {
            this.json = json;
            this.activeDelegate = activeDelegate;
        }
    }

    public static final class Result {
        public final boolean completed;
        public final File outputFile;
        public final String message;
        public final boolean nnapiActive;

        Result(boolean completed, File outputFile, String message, boolean nnapiActive) {
            this.completed = completed;
            this.outputFile = outputFile;
            this.message = message;
            this.nnapiActive = nnapiActive;
        }
    }
}
