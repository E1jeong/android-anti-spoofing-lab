package com.unionbiometrics.facedetection;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Arrays;

final class ModelContract {
    final int width;
    final int height;
    final String file;
    final String sha256;
    final TensorSpec input;
    final TensorSpec logits;
    final TensorSpec boxes;
    final YuNetPostprocessor postprocessor;

    ModelContract(JSONObject manifest, String artifact) throws JSONException {
        if (manifest.getInt("format_version") != 1) {
            throw new IllegalArgumentException("Unsupported YuNet manifest version");
        }
        JSONObject image = manifest.getJSONObject("input");
        require(image, "layout", "NHWC");
        require(image, "color", "RGB");
        require(image, "resize", "bilinear centered letterbox, black padding");
        if (!Arrays.equals(ints(image.getJSONArray("pixel_range")), new int[]{0, 255})) {
            throw new IllegalArgumentException("YuNet pixels must be RGB [0,255]");
        }
        int[] shape = ints(image.getJSONArray("shape"));
        if (shape.length != 4 || shape[0] != 1 || shape[3] != 3) {
            throw new IllegalArgumentException("YuNet input must be [1,H,W,3]");
        }
        height = shape[1];
        width = shape[2];
        JSONObject output = manifest.getJSONObject("output");
        require(output, "layout", "[batch, candidates, channels]");
        require(output, "order", "stride ascending, then row-major y/x");
        if (!Arrays.equals(ints(output.getJSONArray("strides")), new int[]{8, 16, 32})
                || !"[\"class_logit\",\"objectness_logit\"]".equals(output.getJSONArray("logits_channels").toString())
                || !"[\"dx\",\"dy\",\"log_width\",\"log_height\"]".equals(output.getJSONArray("box_channels").toString())) {
            throw new IllegalArgumentException("Unsupported YuNet strides/channel order");
        }
        JSONObject post = manifest.getJSONObject("postprocess");
        require(post, "score", "sigmoid(class_logit) * sigmoid(objectness_logit)");
        require(post, "center", "(grid_xy + delta_xy) * stride");
        require(post, "size", "exp(clip(log_wh, -16, 16)) * stride");
        require(post, "restore", "subtract padding, divide by letterbox scale, clip");
        postprocessor = new YuNetPostprocessor(width, height,
                (float) post.getDouble("score_threshold"), (float) post.getDouble("nms_threshold"),
                post.getInt("max_detections"));
        JSONObject model = manifest.getJSONObject("artifacts").getJSONObject(artifact);
        file = model.getString("file");
        sha256 = model.getString("sha256");
        if (!file.matches("[A-Za-z0-9_-]+\\.tflite") || !sha256.matches("[a-f0-9]{64}")) {
            throw new IllegalArgumentException("Invalid YuNet artifact filename/hash");
        }
        String type = "int8".equals(artifact) ? "int8" : "float32";
        input = new TensorSpec(model.getJSONObject("input"), shape, type);
        JSONArray outputs = model.getJSONArray("outputs");
        if (outputs.length() != 2) throw new IllegalArgumentException("YuNet requires two separate outputs");
        TensorSpec foundLogits = null;
        TensorSpec foundBoxes = null;
        for (int i = 0; i < outputs.length(); i++) {
            JSONObject tensor = outputs.getJSONObject(i);
            int[] tensorShape = ints(tensor.getJSONArray("shape"));
            if (Arrays.equals(tensorShape, new int[]{1, postprocessor.candidateCount, 2}) && foundLogits == null) {
                foundLogits = new TensorSpec(tensor, tensorShape, type);
            } else if (Arrays.equals(tensorShape, new int[]{1, postprocessor.candidateCount, 4}) && foundBoxes == null) {
                foundBoxes = new TensorSpec(tensor, tensorShape, type);
            } else {
                throw new IllegalArgumentException("YuNet requires [1,N,2] logits and [1,N,4] boxes");
            }
        }
        if (foundLogits == null || foundBoxes == null) throw new IllegalArgumentException("Missing YuNet output");
        logits = foundLogits;
        boxes = foundBoxes;
    }

    private static void require(JSONObject object, String key, String expected) throws JSONException {
        if (!expected.equals(object.getString(key))) {
            throw new IllegalArgumentException("Unsupported YuNet " + key);
        }
    }

    private static int[] ints(JSONArray array) throws JSONException {
        int[] values = new int[array.length()];
        for (int i = 0; i < values.length; i++) values[i] = array.getInt(i);
        return values;
    }

    static final class TensorSpec {
        final int[] shape;
        final boolean quantized;
        final float scale;
        final int zeroPoint;

        TensorSpec(JSONObject json, int[] expectedShape, String type) throws JSONException {
            shape = ints(json.getJSONArray("shape"));
            if (!Arrays.equals(shape, expectedShape) || !type.equals(json.getString("dtype"))) {
                throw new IllegalArgumentException("YuNet tensor shape/type disagrees with manifest");
            }
            quantized = "int8".equals(type);
            JSONArray quantization = json.getJSONArray("quantization");
            if (quantization.length() != 2) throw new IllegalArgumentException("Invalid tensor quantization");
            scale = (float) quantization.getDouble(0);
            zeroPoint = quantization.getInt(1);
            if (!Float.isFinite(scale) || (quantized && (scale <= 0 || zeroPoint < -128 || zeroPoint > 127))
                    || (!quantized && (scale != 0 || zeroPoint != 0))) {
                throw new IllegalArgumentException("Invalid YuNet tensor scale/zero point");
            }
        }

        byte quantize(float value) {
            return (byte) Math.max(-128, Math.min(127, Math.round(value / scale) + zeroPoint));
        }

        float dequantize(byte value) { return (value - zeroPoint) * scale; }
    }
}
