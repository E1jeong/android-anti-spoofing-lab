package com.unionbiometrics.vision;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import org.junit.Test;

public class ModelSpecTest {
    private static final String CLASS_ORDER = "\"class_order\":[\"live\",\"print\",\"picture\","
            + "\"mask\",\"display\",\"pmask\",\"curved_print\",\"curved_mask\","
            + "\"curved_picture\",\"curved_pmask\",\"dental_white\",\"dental_black\"]";

    @Test(expected = IllegalArgumentException.class)
    public void rejectsLegacySidecarWithoutGeneratedInputs() throws Exception {
        ModelSpec.parse("{"
                + "\"inputKind\":\"ir\",\"irInputIndex\":0,"
                + "\"inputWidth\":224,\"inputHeight\":224,"
                + "\"rgbMean\":[0.5],\"rgbStd\":[0.5],"
                + "\"irMean\":[0.5],\"irStd\":[0.5],"
                + "\"outputIsLogits\":true,\"cropMarginRatio\":0.1"
                + "}");
    }

    @Test
    public void parsesCurrentIrSidecarContract() throws Exception {
        ModelSpec spec = ModelSpec.parse("{"
                + "\"delegate\":\"nnapi\","
                + "\"crop_margin_ratio\":0.1,"
                + "\"inputs\":[{"
                + "\"index\":0,"
                + "\"shape\":[1,224,224,1],"
                + "\"input_type\":\"ir\","
                + "\"normalization\":{\"mean\":[0.5],\"std\":[0.5]}"
                + "}],"
                + "\"outputs\":[{\"output_is_logits\":true}]," + CLASS_ORDER
                + "}");

        assertEquals("ir", spec.singleInputType);
        assertEquals(0.1f, spec.cropMarginRatio, 0.0001f);
        assertEquals(0, spec.irInputIndex);
        assertEquals(-1, spec.rgbInputIndex);
        assertEquals(0.5f, spec.irMean[0], 0.0001f);
        assertEquals(0.5f, spec.irStd[0], 0.0001f);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsOldInputKindKey() throws Exception {
        ModelSpec.parse("{\"inputs\":[{\"index\":0,"
                + "\"shape\":[1,224,224,1],\"input_kind\":\"ir\"}]," + CLASS_ORDER + "}");
    }

    @Test
    public void rejectsInvalidCropMargin() throws Exception {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> ModelSpec.parse("{"
                + "\"delegate\":\"nnapi\","
                + "\"crop_margin_ratio\":1.5,"
                + "\"inputs\":[{"
                + "\"index\":0,"
                + "\"shape\":[1,224,224,1],"
                + "\"input_type\":\"ir\","
                + "\"normalization\":{\"mean\":[0.5],\"std\":[0.5]}"
                + "}]," + CLASS_ORDER
                + "}"));
        assertEquals("model_spec.json contains invalid cropMarginRatio", error.getMessage());
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsRgbOnlySidecar() throws Exception {
        ModelSpec.parse("{"
                + "\"delegate\":\"nnapi\","
                + "\"crop_margin_ratio\":0.1,"
                + "\"inputs\":[{"
                + "\"index\":0,"
                + "\"shape\":[1,224,224,3],"
                + "\"input_type\":\"rgb\","
                + "\"normalization\":{\"mean\":[0.5,0.5,0.5],\"std\":[0.5,0.5,0.5]}"
                + "}]"
                + "}");
    }

    @Test
    public void parsesRgbIrSidecar() throws Exception {
        ModelSpec spec = ModelSpec.parse("{"
                + "\"delegate\":\"nnapi\","
                + "\"crop_margin_ratio\":0.2,"
                + "\"inputs\":["
                + "{\"index\":0,\"shape\":[1,224,224,3],\"input_type\":\"rgb\","
                + "\"normalization\":{\"mean\":[0.4,0.5,0.6],\"std\":[0.2,0.3,0.4]}},"
                + "{\"index\":1,\"shape\":[1,224,224,1],\"input_type\":\"ir\","
                + "\"normalization\":{\"mean\":[0.6],\"std\":[0.3]}}"
                + "],\"outputs\":[{\"output_is_logits\":true}]," + CLASS_ORDER
                + "}");

        assertEquals("", spec.singleInputType);
        assertEquals(0, spec.rgbInputIndex);
        assertEquals(1, spec.irInputIndex);
        assertEquals(0.4f, spec.rgbMean[0], 0.0001f);
        assertEquals(0.4f, spec.rgbStd[2], 0.0001f);
        assertEquals(0.6f, spec.irMean[0], 0.0001f);
        assertEquals(0.3f, spec.irStd[0], 0.0001f);
        assertEquals(0.2f, spec.cropMarginRatio, 0.0001f);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsCpuDelegate() throws Exception {
        ModelSpec.parse(irSpec("\"delegate\":\"cpu\",", 224, true));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsUnknownDelegate() throws Exception {
        ModelSpec.parse(irSpec("\"delegate\":\"gpu\",", 224, true));
    }

    @Test
    public void acceptsNnapiDelegateRegardlessOfCase() throws Exception {
        for (String delegate : new String[]{"nnapi", "NNAPI", "NnApi"}) {
            ModelSpec spec = ModelSpec.parse(irSpec("\"delegate\":\"" + delegate + "\",", 224, true));
            assertEquals(0, spec.irInputIndex);
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsProbabilityOutput() throws Exception {
        ModelSpec.parse(irSpec("", 224, false));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsNon224InputEvenWithSizeOverrides() throws Exception {
        ModelSpec.parse(irSpec("\"inputWidth\":192,\"inputHeight\":192,", 192, true));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsNon224ShapeWithoutSizeOverrides() throws Exception {
        ModelSpec.parse(irSpec("", 192, true));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsHeightOverrideThatConflictsWithFixedInput() throws Exception {
        ModelSpec.parse(irSpec("\"inputHeight\":192,", 224, true));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsSizeOverrideThatConflictsWithFixedInput() throws Exception {
        ModelSpec.parse(irSpec("\"inputWidth\":192,", 224, true));
    }

    @Test
    public void acceptsFixedContractWithoutOptionalSettings() throws Exception {
        ModelSpec spec = ModelSpec.parse("{\"inputs\":[{\"index\":0,"
                + "\"shape\":[1,224,224,1],\"input_type\":\"ir\"}]," + CLASS_ORDER + "}");

        assertEquals(0, spec.irInputIndex);
    }

    private static String irSpec(String settings, int size, boolean logits) {
        return "{" + settings + "\"inputs\":[{\"index\":0,\"shape\":[1,"
                + size + "," + size + ",1],\"input_type\":\"ir\"}],"
                + "\"outputs\":[{\"output_is_logits\":" + logits + "}]," + CLASS_ORDER + "}";
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsAdditionalInputs() throws Exception {
        ModelSpec.parse("{"
                + "\"delegate\":\"nnapi\","
                + "\"crop_margin_ratio\":0.1,"
                + "\"inputs\":["
                + "{\"index\":0,\"shape\":[1,224,224,3],\"input_type\":\"rgb\","
                + "\"normalization\":{\"mean\":[0.5,0.5,0.5],\"std\":[0.5,0.5,0.5]}},"
                + "{\"index\":1,\"shape\":[1,224,224,1],\"input_type\":\"ir\","
                + "\"normalization\":{\"mean\":[0.5],\"std\":[0.5]}},"
                + "{\"index\":2,\"shape\":[1,224,224,1],\"input_type\":\"heatmap\","
                + "\"normalization\":{\"mean\":[0.0],\"std\":[1.0]}}"
                + "]"
                + "}");
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsMissingClassOrder() throws Exception {
        ModelSpec.parse("{\"inputs\":[{\"index\":0,\"shape\":[1,224,224,1],"
                + "\"input_type\":\"ir\"}]}");
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsReorderedClasses() throws Exception {
        ModelSpec.parse("{\"inputs\":[{\"index\":0,\"shape\":[1,224,224,1],"
                + "\"input_type\":\"ir\"}],"
                + CLASS_ORDER.replace("\"live\",\"print\"", "\"print\",\"live\"") + "}");
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsIrSidecarWithNonzeroTensorIndex() throws Exception {
        ModelSpec.parse("{"
                + "\"delegate\":\"nnapi\","
                + "\"crop_margin_ratio\":0.1,"
                + "\"inputs\":[{"
                + "\"index\":1,"
                + "\"shape\":[1,224,224,1],"
                + "\"input_type\":\"ir\","
                + "\"normalization\":{\"mean\":[0.5],\"std\":[0.5]}"
                + "}]"
                + "}");
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsDualSidecarWithOutOfRangeTensorIndex() throws Exception {
        ModelSpec.parse("{"
                + "\"delegate\":\"nnapi\","
                + "\"crop_margin_ratio\":0.1,"
                + "\"inputs\":["
                + "{\"index\":0,\"shape\":[1,224,224,3],\"input_type\":\"rgb\","
                + "\"normalization\":{\"mean\":[0.5,0.5,0.5],\"std\":[0.5,0.5,0.5]}},"
                + "{\"index\":2,\"shape\":[1,224,224,1],\"input_type\":\"ir\","
                + "\"normalization\":{\"mean\":[0.5],\"std\":[0.5]}}"
                + "]"
                + "}");
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsSidecarChannelsThatDoNotMatchInputType() throws Exception {
        ModelSpec.parse("{"
                + "\"delegate\":\"nnapi\","
                + "\"crop_margin_ratio\":0.1,"
                + "\"inputs\":[{"
                + "\"index\":0,"
                + "\"shape\":[1,224,224,3],"
                + "\"input_type\":\"ir\","
                + "\"normalization\":{\"mean\":[0.5],\"std\":[0.5]}"
                + "}]"
                + "}");
    }
}
