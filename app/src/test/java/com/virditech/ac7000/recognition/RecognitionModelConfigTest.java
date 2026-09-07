package com.virditech.ac7000.recognition;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class RecognitionModelConfigTest {

    @Test
    public void testParseJsonWithRecognitionSection() throws Exception {
        String json = "{\n" +
                "  \"models\": [],\n" +
                "  \"recognition\": [\n" +
                "    {\n" +
                "      \"label\": \"MobileNet Emore INT8\",\n" +
                "      \"model\": \"models/mobilenet_emore_npu_int8.tflite\",\n" +
                "      \"delegate\": \"NNAPI\"\n" +
                "    },\n" +
                "    {\n" +
                "      \"label\": \"Experimental Model\",\n" +
                "      \"model\": \"models/exp_model.tflite\",\n" +
                "      \"delegate\": \"CPU\"\n" +
                "    }\n" +
                "  ]\n" +
                "}";

        List<RecognitionModelConfig> configs = RecognitionModelConfig.parseJson(json);
        assertEquals(2, configs.size());

        RecognitionModelConfig config1 = configs.get(0);
        assertEquals("MobileNet Emore INT8", config1.getLabel());
        assertEquals("models/mobilenet_emore_npu_int8.tflite", config1.getModelPath());
        assertEquals(FaceEmbeddingModel.DelegateType.NNAPI, config1.getDelegateType());

        RecognitionModelConfig config2 = configs.get(1);
        assertEquals("Experimental Model", config2.getLabel());
        assertEquals("models/exp_model.tflite", config2.getModelPath());
        assertEquals(FaceEmbeddingModel.DelegateType.CPU, config2.getDelegateType());
    }

    @Test
    public void testParseJsonWithoutRecognitionSection() throws Exception {
        String json = "{\"models\": []}";
        List<RecognitionModelConfig> configs = RecognitionModelConfig.parseJson(json);
        assertTrue(configs.isEmpty());
    }

    @Test
    public void testParseJsonWithEmptyOrNullInput() throws Exception {
        assertTrue(RecognitionModelConfig.parseJson(null).isEmpty());
        assertTrue(RecognitionModelConfig.parseJson("").isEmpty());
        assertTrue(RecognitionModelConfig.parseJson("   ").isEmpty());
    }

    @Test
    public void testParseJsonSkipsMissingModel() throws Exception {
        String json = "{\n" +
                "  \"recognition\": [\n" +
                "    {\"label\": \"No Model Path\"},\n" +
                "    {\"label\": \"Valid\", \"model\": \"models/valid.tflite\"}\n" +
                "  ]\n" +
                "}";
        List<RecognitionModelConfig> configs = RecognitionModelConfig.parseJson(json);
        assertEquals(1, configs.size());
        assertEquals("Valid", configs.get(0).getLabel());
        assertEquals("models/valid.tflite", configs.get(0).getModelPath());
        assertEquals(FaceEmbeddingModel.DelegateType.NNAPI, configs.get(0).getDelegateType());
    }

    @Test
    public void testCreateDefault() {
        RecognitionModelConfig defaultConfig = RecognitionModelConfig.createDefault();
        assertNotNull(defaultConfig);
        assertEquals("MobileNet Emore INT8", defaultConfig.getLabel());
        assertEquals(FaceEmbeddingModel.DEFAULT_MODEL_PATH, defaultConfig.getModelPath());
        assertEquals(FaceEmbeddingModel.DEFAULT_DELEGATE, defaultConfig.getDelegateType());
    }

    @Test
    public void testFindByModelPath() {
        RecognitionModelConfig c1 = new RecognitionModelConfig("M1", "path/1.tflite", FaceEmbeddingModel.DelegateType.NNAPI);
        RecognitionModelConfig c2 = new RecognitionModelConfig("M2", "path/2.tflite", FaceEmbeddingModel.DelegateType.CPU);
        List<RecognitionModelConfig> list = java.util.Arrays.asList(c1, c2);

        assertEquals(c1, RecognitionModelConfig.findByModelPath(list, "path/1.tflite"));
        assertEquals(c2, RecognitionModelConfig.findByModelPath(list, "path/2.tflite"));
        assertNull(RecognitionModelConfig.findByModelPath(list, "path/nonexistent.tflite"));
        assertNull(RecognitionModelConfig.findByModelPath(null, "path/1.tflite"));
        assertNull(RecognitionModelConfig.findByModelPath(list, null));
    }
}
