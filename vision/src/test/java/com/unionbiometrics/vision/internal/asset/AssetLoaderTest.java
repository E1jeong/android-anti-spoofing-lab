package com.unionbiometrics.vision.internal.asset;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class AssetLoaderTest {
    @Test
    public void prefixesLibraryDirectory() {
        assertEquals("ubio-vision/model_manifest.json", AssetLoader.path("model_manifest.json"));
        assertEquals("ubio-vision/best_crop_ir_fixed_npu_int8.tflite",
                AssetLoader.path("best_crop_ir_fixed_npu_int8.tflite"));
    }

    @Test
    public void doesNotDoublePrefix() {
        assertEquals("ubio-vision/model_manifest.json",
                AssetLoader.path("ubio-vision/model_manifest.json"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsAbsoluteAssetName() {
        AssetLoader.path("/model_manifest.json");
    }
}
