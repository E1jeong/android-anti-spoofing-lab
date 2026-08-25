package com.virditech.ac7000.recognition;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class FaceTemplateStorageTest {
    @Test
    public void entityRoundTripPreservesEmbeddingAndMetadata() {
        FaceTemplate template = new FaceTemplate("template-1", "Alice",
                new float[]{0.25f, -0.5f, 1.0f}, 1234L, 3);

        FaceTemplateEntity entity = FaceTemplateEntity.from("model_int8.tflite", "abc123", template);
        FaceTemplate restored = entity.toFaceTemplate();

        assertEquals("template-1", restored.getId());
        assertEquals("Alice", restored.getName());
        assertEquals(1234L, restored.getEnrolledAtMs());
        assertEquals(3, restored.getSampleCount());
        assertEquals("abc123", entity.modelChecksum);
        assertArrayEquals(template.getEmbedding(), restored.getEmbedding(), 0f);
    }
}
