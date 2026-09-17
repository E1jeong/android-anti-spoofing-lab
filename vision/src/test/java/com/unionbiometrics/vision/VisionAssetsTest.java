package com.unionbiometrics.vision;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class VisionAssetsTest {
    @Test
    public void prefixesLibraryDirectory() {
        assertEquals("ubio-vision/model_manifest.json", VisionAssets.path("model_manifest.json"));
        assertEquals("ubio-vision/best_crop_ir_fixed_npu_int8.tflite",
                VisionAssets.path("best_crop_ir_fixed_npu_int8.tflite"));
    }

    @Test
    public void doesNotDoublePrefix() {
        assertEquals("ubio-vision/model_manifest.json",
                VisionAssets.path("ubio-vision/model_manifest.json"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsAbsoluteAssetName() {
        VisionAssets.path("/model_manifest.json");
    }
}
