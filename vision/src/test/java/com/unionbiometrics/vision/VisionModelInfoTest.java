package com.unionbiometrics.vision;

import org.junit.Test;

import android.content.Context;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class ModelInfoTest {
    @Test(expected = IllegalArgumentException.class)
    public void rejectsUnsupportedInputCount() {
        new ModelInfo(1, 3, 0.1f);
    }

    @Test
    public void rejectsNonPositiveModelCount() {
        assertThrows(IllegalArgumentException.class, () -> new ModelInfo(0));
        assertThrows(IllegalArgumentException.class, () -> new ModelInfo(-1, 1, 0.1f));
    }

    @Test(expected = UnsupportedOperationException.class)
    public void classLabelsCannotBeChanged() {
        new ModelInfo(1, 1, 0.1f).classLabels.set(0, "changed");
    }

    @Test
    public void irInfoIsAvailableWithoutInferenceResult() {
        ModelInfo info = new ModelInfo(4, 1, 0.1f);

        assertEquals(4, info.modelCount);
        assertEquals("single_1_input", info.inputType);
        assertEquals(0.1f, info.cropMarginRatio, 0f);
        assertEquals(Arrays.asList(
                "LIVE", "PRINT", "PICTURE", "MASK", "DISPLAY", "PMASK",
                "CURVED_PRINT", "CURVED_MASK", "CURVED_PICTURE", "CURVED_PMASK",
                "DENTAL_WHITE", "DENTAL_BLACK"), info.classLabels);
    }

    @Test
    public void dualInfoKeepsItsOwnInputTypeAndMargin() {
        ModelInfo ir = new ModelInfo(4, 1, 0.1f);
        ModelInfo dual = new ModelInfo(4, 2, 0.2f);

        assertEquals(4, dual.modelCount);
        assertEquals("dual_2_input", dual.inputType);
        assertEquals(0.2f, dual.cropMarginRatio, 0f);
        assertEquals("single_1_input", ir.inputType);
        assertEquals(0.1f, ir.cropMarginRatio, 0f);
    }

    @Test
    public void infoQueriesReturnOneObject() throws Exception {
        assertEquals(ModelInfo.class, AntiSpoofingEngine.class.getMethod("getModelInfo").getReturnType());
        assertEquals(ModelInfo.class, AntiSpoofingEngine.class.getMethod("getModelInfo", Context.class).getReturnType());
    }

    @Test(expected = NoSuchMethodException.class)
    public void separateModelCountIsNotPublic() throws Exception {
        AntiSpoofingEngine.class.getMethod("modelCount", Context.class);
    }

    @Test
    public void resultDoesNotExposeModelLabelsOrIndexedProbability() {
        assertThrows(NoSuchMethodException.class,
                () -> AntiSpoofingResult.class.getMethod("getClassLabels"));
        assertThrows(NoSuchMethodException.class,
                () -> AntiSpoofingResult.class.getMethod("getProbability", int.class));
    }

    @Test(expected = IllegalArgumentException.class)
    public void infoRejectsMissingContext() {
        AntiSpoofingEngine.getModelInfo(null);
    }

    @Test
    public void catalogInfoProvidesCountAndLabelsWithoutSelectingAModel() {
        ModelInfo info = new ModelInfo(4);

        assertEquals(4, info.modelCount);
        assertNull(info.inputType);
        assertEquals(new ModelInfo(4, 1, 0.1f).classLabels, info.classLabels);
        assertTrue(Float.isNaN(info.cropMarginRatio));
    }
}
