package com.virditech.ac7000.model;

import android.content.Context;

import org.json.JSONObject;
import org.json.JSONException;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

final class ModelSpec {
    final int rgbInputIndex;
    final int irInputIndex;
    final boolean bgr;
    final float rgbMean;
    final float rgbStd;
    final float irMean;
    final float irStd;
    final boolean outputIsLogits;
    final float cropMarginRatio;

    private ModelSpec(JSONObject json) throws JSONException {
        rgbInputIndex = json.getInt("rgbInputIndex");
        irInputIndex = json.getInt("irInputIndex");
        bgr = "BGR".equalsIgnoreCase(json.getString("channelOrder"));
        rgbMean = (float) json.getDouble("rgbMean");
        rgbStd = (float) json.getDouble("rgbStd");
        irMean = (float) json.getDouble("irMean");
        irStd = (float) json.getDouble("irStd");
        outputIsLogits = json.getBoolean("outputIsLogits");
        cropMarginRatio = (float) json.getDouble("cropMarginRatio");
        if (rgbInputIndex == irInputIndex || rgbStd == 0f || irStd == 0f || cropMarginRatio < 0f || cropMarginRatio > 1f) {
            throw new IllegalArgumentException("model_spec.json contains invalid values");
        }
    }

    static ModelSpec load(Context context) throws Exception {
        try (InputStream input = context.getAssets().open("model_spec.json")) {
            byte[] bytes = new byte[input.available()];
            int read = input.read(bytes);
            if (read != bytes.length) throw new IllegalStateException("Unable to read model_spec.json");
            return new ModelSpec(new JSONObject(new String(bytes, StandardCharsets.UTF_8)));
        }
    }
}
