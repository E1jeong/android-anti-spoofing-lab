package com.unionbiometrics.vision.api;

public final class ClassLabels {
    private static final String[] VALUES = {
            "LIVE", "PRINT", "PICTURE", "MASK", "DISPLAY", "PMASK",
            "CURVED_PRINT", "CURVED_MASK", "CURVED_PICTURE", "CURVED_PMASK",
            "DENTAL_WHITE", "DENTAL_BLACK"
    };

    private ClassLabels() {}

    public static int count() {
        return VALUES.length;
    }

    public static String[] values() {
        return VALUES.clone();
    }

    public static String label(int index) {
        return VALUES[index];
    }

    public static String displayLabel(int index) {
        String label = VALUES[index];
        return label.startsWith("CURVED_")
                ? "C " + label.substring("CURVED_".length()) : label;
    }

    public static boolean isAcceptedClass(int index) {
        return index == 0 || index == 10 || index == 11;
    }
}
