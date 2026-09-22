package com.unionbiometrics.vision.internal.classification;

import org.junit.Test;

public class SlotClassifierTest {
    @Test
    public void acceptsIrAndRgbIrModelTypes() {
        SlotClassifier.validateType("single_1_input");
        SlotClassifier.validateType("dual_2_input");
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsPairedOneInputModels() {
        SlotClassifier.validateType("paired_1_input");
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsFiveInputModels() {
        SlotClassifier.validateType("five_input");
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsNonCanonicalTypeCasing() {
        SlotClassifier.validateType("SINGLE_1_INPUT");
    }
}
