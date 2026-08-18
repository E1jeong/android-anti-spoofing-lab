package com.virditech.ac7000.recognition;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class FaceRecognitionTest {

    @Test
    public void testL2NormalizationAndCosineSimilarity() {
        float[] v1 = new float[]{3.0f, 4.0f, 0.0f};
        FaceEmbeddingModel.normalizeL2(v1);

        // Norm should be 1.0 (3/5, 4/5, 0)
        assertEquals(0.6f, v1[0], 1e-5f);
        assertEquals(0.8f, v1[1], 1e-5f);
        assertEquals(0.0f, v1[2], 1e-5f);

        // Identical vector similarity should be 1.0
        float similaritySelf = FaceEmbeddingModel.cosineSimilarity(v1, v1);
        assertEquals(1.0f, similaritySelf, 1e-5f);

        // Orthogonal vector similarity should be 0.0
        float[] v2 = new float[]{0.0f, 0.0f, 5.0f};
        FaceEmbeddingModel.normalizeL2(v2);
        float similarityOrthogonal = FaceEmbeddingModel.cosineSimilarity(v1, v2);
        assertEquals(0.0f, similarityOrthogonal, 1e-5f);
    }

    @Test
    public void testFaceTemplateCreation() {
        float[] embedding = new float[512];
        embedding[0] = 1.0f;
        FaceTemplate template = new FaceTemplate("USER_01", "User 1", embedding, 1000L, 1);

        assertEquals("USER_01", template.getId());
        assertEquals("User 1", template.getName());
        assertEquals(512, template.getEmbedding().length);
        assertEquals(1.0f, template.getEmbedding()[0], 1e-5f);
        assertEquals(1000L, template.getEnrolledAtMs());
    }

    @Test
    public void testRecognitionResultCreation() {
        float[] embedding = new float[512];
        embedding[0] = 1.0f;
        FaceTemplate template = new FaceTemplate("USER_01", "User 1", embedding, 1000L, 1);

        RecognitionResult success = RecognitionResult.success(template, 0.85f, 0.70f, 15L);
        assertTrue(success.isRecognized());
        assertEquals(template, success.matchedTemplate());
        assertEquals(0.85f, success.similarityScore(), 1e-5f);
        assertEquals(15L, success.elapsedMs());

        RecognitionResult fail = RecognitionResult.notRecognized(0.45f, 0.70f, 10L, "Score below threshold");
        assertFalse(fail.isRecognized());
        assertEquals(0.45f, fail.similarityScore(), 1e-5f);
    }
}
