package com.virditech.ac7000.recognition;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

@Database(entities = FaceTemplateEntity.class, version = 2, exportSchema = false)
public abstract class FaceTemplateDatabase extends RoomDatabase {
    private static volatile FaceTemplateDatabase instance;
    private static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE face_templates ADD COLUMN model_checksum TEXT NOT NULL DEFAULT ''");
            database.execSQL("CREATE INDEX IF NOT EXISTS index_face_templates_model_asset_path_model_checksum "
                    + "ON face_templates(model_asset_path, model_checksum)");
        }
    };

    public abstract FaceTemplateDao faceTemplateDao();

    public static FaceTemplateDatabase getInstance(Context context) {
        if (instance == null) {
            synchronized (FaceTemplateDatabase.class) {
                if (instance == null) {
                    instance = Room.databaseBuilder(context.getApplicationContext(),
                            FaceTemplateDatabase.class, "face_templates.db")
                            .addMigrations(MIGRATION_1_2)
                            .build();
                }
            }
        }
        return instance;
    }
}
