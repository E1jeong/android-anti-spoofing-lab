package com.unionbiometrics.vision;

import org.junit.Test;

public class ModelLoaderTest {
    @Test
    public void acceptsIrAndRgbIrModelTypes() {
        ModelLoader.validateType("single_1_input");
        ModelLoader.validateType("dual_2_input");
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsPairedOneInputModels() {
        ModelLoader.validateType("paired_1_input");
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsFiveInputModels() {
        ModelLoader.validateType("five_input");
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsNonCanonicalTypeCasing() {
        ModelLoader.validateType("SINGLE_1_INPUT");
    }
}
