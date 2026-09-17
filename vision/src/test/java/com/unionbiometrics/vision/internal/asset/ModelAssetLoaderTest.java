package com.unionbiometrics.vision.internal.asset;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class ModelAssetLoaderTest {
    @Test
    public void prefixesLibraryDirectory() {
        assertEquals("ubio-vision/model_manifest.json", ModelAssetLoader.path("model_manifest.json"));
        assertEquals("ubio-vision/best_crop_ir_fixed_npu_int8.tflite",
                ModelAssetLoader.path("best_crop_ir_fixed_npu_int8.tflite"));
    }

    @Test
    public void doesNotDoublePrefix() {
        assertEquals("ubio-vision/model_manifest.json",
                ModelAssetLoader.path("ubio-vision/model_manifest.json"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsAbsoluteAssetName() {
        ModelAssetLoader.path("/model_manifest.json");
    }
}
