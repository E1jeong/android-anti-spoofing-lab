package com.virditech.ac7000.capture;

import org.junit.Test;

import java.io.File;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class CaptureStorageTest {
    @Test public void saveCompleteSampleFailsWhenDirectoryCannotBeCreated() throws Exception {
        File blocker = File.createTempFile("capture-storage", ".tmp");
        File sampleDir = new File(blocker, "1");
        try {
            CaptureStorage.SaveResult result = CaptureStorage.saveCompleteSample(
                    null, null, null, null, "{}", sampleDir);
            assertFalse(result.saved);
            assertTrue(result.errorMessage.contains("unable to create"));
            assertFalse(new File(sampleDir, "RGB.bmp").exists());
            assertFalse(new File(sampleDir, "cropRGB.bmp").exists());
            assertFalse(new File(sampleDir, "IR.bmp").exists());
            assertFalse(new File(sampleDir, "cropIR.bmp").exists());
            assertFalse(new File(sampleDir, "meta.json").exists());
        } finally {
            assertTrue(blocker.delete() || !blocker.exists());
        }
    }
}
