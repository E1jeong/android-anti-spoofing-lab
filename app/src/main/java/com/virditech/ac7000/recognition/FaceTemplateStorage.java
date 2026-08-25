package com.virditech.ac7000.recognition;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

final class FaceTemplateStorage {
    private FaceTemplateStorage() {}

    static byte[] encode(float[] embedding) {
        if (embedding == null) throw new IllegalArgumentException("embedding is required");
        ByteBuffer buffer = ByteBuffer.allocate(embedding.length * Float.BYTES).order(ByteOrder.LITTLE_ENDIAN);
        for (float value : embedding) buffer.putFloat(value);
        return buffer.array();
    }

    static float[] decode(byte[] bytes) {
        if (bytes == null || bytes.length == 0 || bytes.length % Float.BYTES != 0) {
            throw new IllegalArgumentException("invalid persisted embedding");
        }
        ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        float[] embedding = new float[bytes.length / Float.BYTES];
        for (int i = 0; i < embedding.length; i++) embedding[i] = buffer.getFloat();
        return embedding;
    }
}
