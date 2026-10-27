package com.unionbiometrics.vision;

import org.junit.Test;

import android.content.Context;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class VisionModelInfoTest {
    @Test(expected = IllegalArgumentException.class)
    public void rejectsUnsupportedInputCount() {
        new VisionModelInfo(1, 3, 0.1f);
    }

    @Test
    public void rejectsNonPositiveModelCount() {
        assertThrows(IllegalArgumentException.class, () -> new VisionModelInfo(0));
        assertThrows(IllegalArgumentException.class, () -> new VisionModelInfo(-1, 1, 0.1f));
    }

    @Test(expected = UnsupportedOperationException.class)
    public void classLabelsCannotBeChanged() {
        new VisionModelInfo(1, 1, 0.1f).classLabels.set(0, "changed");
    }

    @Test
    public void irInfoIsAvailableWithoutInferenceResult() {
        VisionModelInfo info = new VisionModelInfo(4, 1, 0.1f);

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
        VisionModelInfo ir = new VisionModelInfo(4, 1, 0.1f);
        VisionModelInfo dual = new VisionModelInfo(4, 2, 0.2f);

        assertEquals(4, dual.modelCount);
        assertEquals("dual_2_input", dual.inputType);
        assertEquals(0.2f, dual.cropMarginRatio, 0f);
        assertEquals("single_1_input", ir.inputType);
        assertEquals(0.1f, ir.cropMarginRatio, 0f);
    }

    @Test
    public void infoQueriesReturnOneObject() throws Exception {
        assertEquals(VisionModelInfo.class, VisionEngine.class.getMethod("getModelInfo").getReturnType());
        assertEquals(VisionModelInfo.class, VisionEngine.class.getMethod("getModelInfo", Context.class).getReturnType());
    }

    @Test(expected = NoSuchMethodException.class)
    public void separateModelCountIsNotPublic() throws Exception {
        VisionEngine.class.getMethod("modelCount", Context.class);
    }

    @Test
    public void resultDoesNotExposeModelLabelsOrIndexedProbability() {
        assertThrows(NoSuchMethodException.class,
                () -> VisionLivenessResult.class.getMethod("getClassLabels"));
        assertThrows(NoSuchMethodException.class,
                () -> VisionLivenessResult.class.getMethod("getProbability", int.class));
    }

    @Test(expected = IllegalArgumentException.class)
    public void infoRejectsMissingContext() {
        VisionEngine.getModelInfo(null);
    }

    @Test
    public void catalogInfoProvidesCountAndLabelsWithoutSelectingAModel() {
        VisionModelInfo info = new VisionModelInfo(4);

        assertEquals(4, info.modelCount);
        assertNull(info.inputType);
        assertEquals(new VisionModelInfo(4, 1, 0.1f).classLabels, info.classLabels);
        assertTrue(Float.isNaN(info.cropMarginRatio));
    }
}
