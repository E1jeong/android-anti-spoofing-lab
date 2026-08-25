package com.virditech.ac7000.recognition;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface FaceTemplateDao {
    @Query("SELECT * FROM face_templates WHERE model_asset_path = :modelAssetPath AND model_checksum = :modelChecksum ORDER BY enrolled_at_ms ASC")
    List<FaceTemplateEntity> findByModel(String modelAssetPath, String modelChecksum);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(FaceTemplateEntity template);

    @Query("DELETE FROM face_templates WHERE id = :id")
    void deleteById(String id);

    @Query("DELETE FROM face_templates WHERE model_asset_path = :modelAssetPath AND model_checksum = :modelChecksum")
    void deleteByModel(String modelAssetPath, String modelChecksum);
}
