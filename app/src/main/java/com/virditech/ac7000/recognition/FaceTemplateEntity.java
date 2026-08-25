package com.virditech.ac7000.recognition;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;
import androidx.annotation.NonNull;

/** Persistent representation of a face embedding. It intentionally contains no image data. */
@Entity(tableName = "face_templates", indices = @Index({"model_asset_path", "model_checksum"}))
public final class FaceTemplateEntity {
    @PrimaryKey
    @NonNull
    @ColumnInfo(name = "id")
    public final String id;
    @ColumnInfo(name = "name")
    public final String name;
    @ColumnInfo(name = "model_asset_path")
    public final String modelAssetPath;
    @NonNull
    @ColumnInfo(name = "model_checksum")
    public final String modelChecksum;
    @ColumnInfo(name = "embedding")
    public final byte[] embedding;
    @ColumnInfo(name = "enrolled_at_ms")
    public final long enrolledAtMs;
    @ColumnInfo(name = "sample_count")
    public final int sampleCount;

    public FaceTemplateEntity(String id, String name, String modelAssetPath, String modelChecksum, byte[] embedding,
                              long enrolledAtMs, int sampleCount) {
        this.id = id;
        this.name = name;
        this.modelAssetPath = modelAssetPath;
        this.modelChecksum = modelChecksum;
        this.embedding = embedding;
        this.enrolledAtMs = enrolledAtMs;
        this.sampleCount = sampleCount;
    }

    public static FaceTemplateEntity from(String modelAssetPath, String modelChecksum, FaceTemplate template) {
        return new FaceTemplateEntity(template.getId(), template.getName(), modelAssetPath, modelChecksum,
                FaceTemplateStorage.encode(template.getEmbedding()), template.getEnrolledAtMs(),
                template.getSampleCount());
    }

    public FaceTemplate toFaceTemplate() {
        return new FaceTemplate(id, name, FaceTemplateStorage.decode(embedding), enrolledAtMs, sampleCount);
    }
}
