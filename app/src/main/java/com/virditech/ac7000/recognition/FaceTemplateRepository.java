package com.virditech.ac7000.recognition;

import android.content.Context;

import java.util.ArrayList;
import java.util.List;

/** Blocking DAO facade. Call only from an I/O executor, never the UI thread. */
public final class FaceTemplateRepository {
    private final FaceTemplateDao dao;

    public FaceTemplateRepository(Context context) {
        dao = FaceTemplateDatabase.getInstance(context).faceTemplateDao();
    }

    public List<FaceTemplate> loadForModel(String modelAssetPath, String modelChecksum) {
        List<FaceTemplate> templates = new ArrayList<>();
        for (FaceTemplateEntity entity : dao.findByModel(modelAssetPath, modelChecksum)) {
            FaceTemplate template = entity.toFaceTemplate();
            if (FaceEmbeddingModel.isValidEmbedding(template.getEmbedding())) templates.add(template);
        }
        return templates;
    }

    public void save(String modelAssetPath, String modelChecksum, FaceTemplate template) {
        dao.insert(FaceTemplateEntity.from(modelAssetPath, modelChecksum, template));
    }

    public void delete(String id) {
        dao.deleteById(id);
    }

    public void deleteAllForModel(String modelAssetPath, String modelChecksum) {
        dao.deleteByModel(modelAssetPath, modelChecksum);
    }
}
