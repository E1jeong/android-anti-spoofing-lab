package com.unionbiometrics.vision;

final class VisionConstants {
    //반드시 순서 지켜야함
    static final String[] CLASS_LABELS = {
            "LIVE", "PRINT", "PICTURE", "MASK", "DISPLAY", "PMASK",
            "CURVED_PRINT", "CURVED_MASK", "CURVED_PICTURE", "CURVED_PMASK",
            "DENTAL_WHITE", "DENTAL_BLACK"
    };
    static final String DIRECTORY = "ubio-vision";
    static final String MANIFEST = "model_manifest.json";

    static final String SINGLE_1_INPUT = "single_1_input";
    static final String DUAL_2_INPUT = "dual_2_input";
    static final String INPUT_KIND_RGB = "rgb";
    static final String INPUT_KIND_IR = "ir";
    static final String BACKEND_NNAPI = "NNAPI";

    static final int CLASS_COUNT = CLASS_LABELS.length;
    static final int INPUT_SIZE = 224;
    static final int RGB_CHANNELS = 3;
    static final int IR_CHANNELS = 1;
    static final int IR_INPUT_COUNT = 1;
    static final int DUAL_INPUT_COUNT = 2;

    private VisionConstants() {}
}
