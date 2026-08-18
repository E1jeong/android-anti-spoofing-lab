package com.virditech.ac7000.recognition;

/**
 * Immutable enrolled face template representation.
 */
public final class FaceTemplate {
    private final String id;
    private final String name;
    private final float[] embedding;
    private final long enrolledAtMs;
    private final int sampleCount;

    public FaceTemplate(String id, String name, float[] embedding, long enrolledAtMs, int sampleCount) {
        this.id = id;
        this.name = name;
        this.embedding = embedding != null ? embedding.clone() : new float[0];
        this.enrolledAtMs = enrolledAtMs;
        this.sampleCount = sampleCount;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public float[] getEmbedding() {
        return embedding;
    }

    public long getEnrolledAtMs() {
        return enrolledAtMs;
    }

    public int getSampleCount() {
        return sampleCount;
    }
}
