package com.unionbiometrics.vision.internal.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SpecTest {
    @Test
    public void parsesCurrentIrSidecarContract() throws Exception {
        Spec spec = Spec.parse("{"
                + "\"delegate\":\"nnapi\","
                + "\"crop_margin_ratio\":0.1,"
                + "\"inputs\":[{"
                + "\"index\":0,"
                + "\"shape\":[1,224,224,1],"
                + "\"input_kind\":\"ir\","
                + "\"normalization\":{\"mean\":[0.5],\"std\":[0.5]}"
                + "}],"
                + "\"outputs\":[{\"output_is_logits\":true}]"
                + "}");

        assertEquals("ir", spec.inputKind);
        assertEquals("nnapi", spec.delegate);
        assertEquals(0.1f, spec.cropMarginRatio, 0.0001f);
        assertEquals(0, spec.irInputIndex);
        assertEquals(-1, spec.rgbInputIndex);
        assertEquals(0.5f, spec.irMean[0], 0.0001f);
        assertEquals(0.5f, spec.irStd[0], 0.0001f);
        assertTrue(spec.outputIsLogits);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsInvalidCropMargin() throws Exception {
        Spec.parse("{"
                + "\"delegate\":\"nnapi\","
                + "\"crop_margin_ratio\":1.5,"
                + "\"inputs\":[{"
                + "\"index\":0,"
                + "\"shape\":[1,224,224,1],"
                + "\"input_kind\":\"ir\","
                + "\"normalization\":{\"mean\":[0.5],\"std\":[0.5]}"
                + "}]"
                + "}");
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsRgbOnlySidecar() throws Exception {
        Spec.parse("{"
                + "\"delegate\":\"nnapi\","
                + "\"crop_margin_ratio\":0.1,"
                + "\"inputs\":[{"
                + "\"index\":0,"
                + "\"shape\":[1,224,224,3],"
                + "\"input_kind\":\"rgb\","
                + "\"normalization\":{\"mean\":[0.5,0.5,0.5],\"std\":[0.5,0.5,0.5]}"
                + "}]"
                + "}");
    }

    @Test
    public void parsesRgbIrSidecar() throws Exception {
        Spec spec = Spec.parse("{"
                + "\"delegate\":\"nnapi\","
                + "\"crop_margin_ratio\":0.1,"
                + "\"inputs\":["
                + "{\"index\":0,\"shape\":[1,224,224,3],\"input_kind\":\"rgb\","
                + "\"normalization\":{\"mean\":[0.5,0.5,0.5],\"std\":[0.5,0.5,0.5]}},"
                + "{\"index\":1,\"shape\":[1,224,224,1],\"input_kind\":\"ir\","
                + "\"normalization\":{\"mean\":[0.5],\"std\":[0.5]}}"
                + "]"
                + "}");

        assertEquals(0, spec.rgbInputIndex);
        assertEquals(1, spec.irInputIndex);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsAdditionalInputs() throws Exception {
        Spec.parse("{"
                + "\"delegate\":\"nnapi\","
                + "\"crop_margin_ratio\":0.1,"
                + "\"inputs\":["
                + "{\"index\":0,\"shape\":[1,224,224,3],\"input_kind\":\"rgb\","
                + "\"normalization\":{\"mean\":[0.5,0.5,0.5],\"std\":[0.5,0.5,0.5]}},"
                + "{\"index\":1,\"shape\":[1,224,224,1],\"input_kind\":\"ir\","
                + "\"normalization\":{\"mean\":[0.5],\"std\":[0.5]}},"
                + "{\"index\":2,\"shape\":[1,224,224,1],\"input_kind\":\"heatmap\","
                + "\"normalization\":{\"mean\":[0.0],\"std\":[1.0]}}"
                + "]"
                + "}");
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsIrSidecarWithNonzeroTensorIndex() throws Exception {
        Spec.parse("{"
                + "\"delegate\":\"nnapi\","
                + "\"crop_margin_ratio\":0.1,"
                + "\"inputs\":[{"
                + "\"index\":1,"
                + "\"shape\":[1,224,224,1],"
                + "\"input_kind\":\"ir\","
                + "\"normalization\":{\"mean\":[0.5],\"std\":[0.5]}"
                + "}]"
                + "}");
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsDualSidecarWithOutOfRangeTensorIndex() throws Exception {
        Spec.parse("{"
                + "\"delegate\":\"nnapi\","
                + "\"crop_margin_ratio\":0.1,"
                + "\"inputs\":["
                + "{\"index\":0,\"shape\":[1,224,224,3],\"input_kind\":\"rgb\","
                + "\"normalization\":{\"mean\":[0.5,0.5,0.5],\"std\":[0.5,0.5,0.5]}},"
                + "{\"index\":2,\"shape\":[1,224,224,1],\"input_kind\":\"ir\","
                + "\"normalization\":{\"mean\":[0.5],\"std\":[0.5]}}"
                + "]"
                + "}");
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsSidecarChannelsThatDoNotMatchInputKind() throws Exception {
        Spec.parse("{"
                + "\"delegate\":\"nnapi\","
                + "\"crop_margin_ratio\":0.1,"
                + "\"inputs\":[{"
                + "\"index\":0,"
                + "\"shape\":[1,224,224,3],"
                + "\"input_kind\":\"ir\","
                + "\"normalization\":{\"mean\":[0.5],\"std\":[0.5]}"
                + "}]"
                + "}");
    }
}
