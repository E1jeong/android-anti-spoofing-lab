package com.unionbiometrics.vision;

final class VisionConstants {
    static final String[] CLASS_LABELS = {
            "LIVE", "PRINT", "PICTURE", "MASK", "DISPLAY", "PMASK",
            "CURVED_PRINT", "CURVED_MASK", "CURVED_PICTURE", "CURVED_PMASK",
            "DENTAL_WHITE", "DENTAL_BLACK",
            "LIVE_OUTDOOR", "PRINT_OUTDOOR", "PICTURE_OUTDOOR", "MASK_OUTDOOR",
            "DISPLAY_OUTDOOR", "PMASK_OUTDOOR", "CURVED_PRINT_OUTDOOR", "CURVED_MASK_OUTDOOR",
            "CURVED_PICTURE_OUTDOOR", "CURVED_PMASK_OUTDOOR",
            "DENTAL_WHITE_OUTDOOR", "DENTAL_BLACK_OUTDOOR"
    };
    static final String DIRECTORY = "ubio-vision";
    static final String MANIFEST = "model_manifest.json";

    static final String SINGLE_1_INPUT = "single_1_input";
    static final String DUAL_2_INPUT = "dual_2_input";

    static final int CLASS_COUNT = CLASS_LABELS.length;
    static final int IR_INPUT_COUNT = 1;
    static final int DUAL_INPUT_COUNT = 2;

    private VisionConstants() {}
}
