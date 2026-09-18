package com.unionbiometrics.vision.internal.model;

import org.junit.Test;

public class ModelSlotClassifierTest {
    @Test
    public void acceptsIrAndRgbIrModelTypes() {
        ModelSlotClassifier.validateType("single_1_input");
        ModelSlotClassifier.validateType("dual_2_input");
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsPairedOneInputModels() {
        ModelSlotClassifier.validateType("paired_1_input");
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsFiveInputModels() {
        ModelSlotClassifier.validateType("five_input");
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsNonCanonicalTypeCasing() {
        ModelSlotClassifier.validateType("SINGLE_1_INPUT");
    }
}
