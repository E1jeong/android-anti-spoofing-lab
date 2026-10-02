package com.unionbiometrics.vision;

final class ClassLabels {
    private static final String[] VALUES = {
            "LIVE", "PRINT", "PICTURE", "MASK", "DISPLAY", "PMASK",
            "CURVED_PRINT", "CURVED_MASK", "CURVED_PICTURE", "CURVED_PMASK",
            "DENTAL_WHITE", "DENTAL_BLACK"
    };

    private ClassLabels() {}

    static int count() {
        return VALUES.length;
    }

    static String[] values() {
        return VALUES.clone();
    }

    static String displayLabel(int index) {
        String label = VALUES[index];
        return label.startsWith("CURVED_")
                ? "C " + label.substring("CURVED_".length()) : label;
    }

    static boolean isAcceptedClass(int index) {
        return index == 0 || index == 10 || index == 11;
    }
}
