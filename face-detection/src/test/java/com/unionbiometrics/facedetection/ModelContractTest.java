package com.unionbiometrics.facedetection;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.*;

public class ModelContractTest {
    @Test
    public void suppliedExportManifestSupportsBothExecutionModes() throws Exception {
        byte[] bytes = java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(System.getProperty("yunet.manifest")));
        JSONObject json = new JSONObject(new String(bytes, java.nio.charset.StandardCharsets.UTF_8));
        ModelContract int8 = new ModelContract(json, "int8");
        ModelContract floating = new ModelContract(json, "float");
        assertTrue(int8.input.quantized);
        assertFalse(floating.input.quantized);
        assertEquals(int8.postprocessor.candidateCount, floating.postprocessor.candidateCount);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsRgbNormalizationChange() throws Exception {
        JSONObject json = manifest();
        json.getJSONObject("input").put("pixel_range", new JSONArray(new int[]{0, 1}));
        new ModelContract(json, "int8");
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsReorderedOutputChannels() throws Exception {
        JSONObject json = manifest();
        json.getJSONObject("output").put("logits_channels",
                new JSONArray(new String[]{"objectness_logit", "class_logit"}));
        new ModelContract(json, "int8");
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsConcatenatedOutput() throws Exception {
        JSONObject json = manifest();
        artifact(json).put("outputs", new JSONArray().put(tensor(6, 0.5, 0)));
        new ModelContract(json, "int8");
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsNonpositiveQuantizationScale() throws Exception {
        JSONObject json = manifest();
        artifact(json).getJSONArray("outputs").getJSONObject(0)
                .put("quantization", new JSONArray(new double[]{0, 103}));
        new ModelContract(json, "int8");
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsArtifactPathOutsideAssetDirectory() throws Exception {
        JSONObject json = manifest();
        artifact(json).put("file", "../yunet_n_int8.tflite");
        new ModelContract(json, "int8");
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsWrongCandidateCount() throws Exception {
        JSONObject json = manifest();
        artifact(json).getJSONArray("outputs").getJSONObject(0)
                .put("shape", new JSONArray(new int[]{1, 2101, 2}));
        new ModelContract(json, "int8");
    }

    @Test
    public void int8UsesIndependentLogitAndBoxQuantization() throws Exception {
        ModelContract contract = new ModelContract(manifest(), "int8");
        assertEquals(2100, contract.postprocessor.candidateCount);
        assertEquals(0.5f, contract.logits.dequantize((byte) 104), 0);
        assertEquals(0.025f, contract.boxes.dequantize((byte) -25), 0);
        assertEquals(-128, contract.input.quantize(0));
        assertEquals(0, contract.input.quantize(128));
        assertEquals(127, contract.input.quantize(255));
        assertEquals(-128, contract.input.quantize(-100));
        assertEquals(127, contract.input.quantize(1000));
    }

    @Test
    public void identifiesOutputsByShapeWhenArrayOrderIsReversed() throws Exception {
        JSONObject json = manifest();
        JSONArray outputs = artifact(json).getJSONArray("outputs");
        artifact(json).put("outputs", new JSONArray().put(outputs.get(1)).put(outputs.get(0)));
        ModelContract contract = new ModelContract(json, "int8");
        assertEquals(2, contract.logits.shape[2]);
        assertEquals(4, contract.boxes.shape[2]);
    }

    @Test
    public void acceptsUnquantizedFloatGoldenModel() throws Exception {
        JSONObject json = manifest();
        JSONObject model = artifact(json);
        JSONObject input = model.getJSONObject("input");
        input.put("dtype", "float32").put("quantization", new JSONArray(new int[]{0, 0}));
        JSONArray outputs = model.getJSONArray("outputs");
        for (int i = 0; i < outputs.length(); i++) {
            outputs.getJSONObject(i).put("dtype", "float32")
                    .put("quantization", new JSONArray(new int[]{0, 0}));
        }
        json.getJSONObject("artifacts").put("float", model);
        ModelContract contract = new ModelContract(json, "float");
        assertFalse(contract.input.quantized);
        assertFalse(contract.logits.quantized);
    }

    private static JSONObject artifact(JSONObject manifest) throws Exception {
        return manifest.getJSONObject("artifacts").getJSONObject("int8");
    }

    private static JSONObject tensor(int channels, double scale, int zeroPoint) throws Exception {
        return new JSONObject().put("shape", new JSONArray(new int[]{1, 2100, channels}))
                .put("dtype", "int8").put("quantization", new JSONArray(new double[]{scale, zeroPoint}));
    }

    private static JSONObject manifest() throws Exception {
        JSONObject input = new JSONObject().put("shape", new JSONArray(new int[]{1, 320, 320, 3}))
                .put("layout", "NHWC").put("color", "RGB")
                .put("resize", "bilinear centered letterbox, black padding")
                .put("pixel_range", new JSONArray(new int[]{0, 255}));
        JSONObject output = new JSONObject().put("layout", "[batch, candidates, channels]")
                .put("strides", new JSONArray(new int[]{8, 16, 32}))
                .put("order", "stride ascending, then row-major y/x")
                .put("logits_channels", new JSONArray(new String[]{"class_logit", "objectness_logit"}))
                .put("box_channels", new JSONArray(new String[]{"dx", "dy", "log_width", "log_height"}));
        JSONObject post = new JSONObject().put("score", "sigmoid(class_logit) * sigmoid(objectness_logit)")
                .put("center", "(grid_xy + delta_xy) * stride")
                .put("size", "exp(clip(log_wh, -16, 16)) * stride")
                .put("restore", "subtract padding, divide by letterbox scale, clip")
                .put("score_threshold", 0.5).put("nms_threshold", 0.45).put("max_detections", 100);
        JSONObject modelInput = new JSONObject().put("shape", input.getJSONArray("shape"))
                .put("dtype", "int8").put("quantization", new JSONArray(new int[]{1, -128}));
        JSONObject artifact = new JSONObject().put("file", "yunet_n_int8.tflite")
                .put("sha256", "0000000000000000000000000000000000000000000000000000000000000000")
                .put("input", modelInput).put("outputs", new JSONArray()
                        .put(tensor(2, 0.5, 103)).put(tensor(4, 0.025, -26)));
        return new JSONObject().put("format_version", 1).put("input", input).put("output", output)
                .put("postprocess", post).put("artifacts", new JSONObject().put("int8", artifact));
    }
}
