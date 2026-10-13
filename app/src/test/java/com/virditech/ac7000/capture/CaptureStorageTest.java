package com.virditech.ac7000.capture;

import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class CaptureStorageTest {
    @Test public void offQualitySamplesUseSeparateLiveDirectory() {
        File sampleDir = CaptureStorage.sampleDir(new File("raw"), "live",
                CaptureStorage.QUALITY_OFF, "live_1", 1);
        assertEquals(new File("raw/live/off/live_1/1"), sampleDir);
    }

    @Test public void liveCapturePathsMatchDatasetStandard() {
        File root = new File("raw");
        CaptureConfig high = CaptureConfig.create("live", "indoor", "high");
        assertEquals(new File("raw/live/high/live_1/1"),
                CaptureStorage.sampleDir(root, high, "live_1", 1));

        CaptureConfig medium = CaptureConfig.create("live", "indoor", "medium");
        assertEquals(new File("raw/live/medium/live_2/5"),
                CaptureStorage.sampleDir(root, medium, "live_2", 5));

        CaptureConfig off = CaptureConfig.create("live", "outdoor", "off");
        assertEquals(new File("raw/live/off/live_3/10"),
                CaptureStorage.sampleDir(root, off, "live_3", 10));
    }

    @Test public void nonLiveCapturePathsUseEnvironmentSubfolders() {
        File root = new File("raw");
        CaptureConfig maskIndoor = CaptureConfig.create("mask", "indoor", null);
        assertEquals(new File("raw/mask/indoor/mask_1/1"),
                CaptureStorage.sampleDir(root, maskIndoor, "mask_1", 1));

        CaptureConfig maskOutdoor = CaptureConfig.create("mask", "outdoor", null);
        assertEquals(new File("raw/mask/outdoor/mask_2/1"),
                CaptureStorage.sampleDir(root, maskOutdoor, "mask_2", 1));

        CaptureConfig dentalWhite = CaptureConfig.create("dental_white", "indoor", null);
        assertEquals(new File("raw/dental_white/indoor/dental_white_5/20"),
                CaptureStorage.sampleDir(root, dentalWhite, "dental_white_5", 20));

        CaptureConfig curvedPrint = CaptureConfig.create("curved_print", "outdoor", null);
        assertEquals(new File("raw/curved_print/outdoor/curved_print_7/100"),
                CaptureStorage.sampleDir(root, curvedPrint, "curved_print_7", 100));
    }

    @Test public void independentSubjectNumberingPerEnvironment() throws IOException {
        File tempRoot = File.createTempFile("capture-test-root", "");
        assertTrue(tempRoot.delete());
        assertTrue(tempRoot.mkdirs());

        try {
            CaptureConfig indoorMask = CaptureConfig.create("mask", "indoor", null);
            CaptureConfig outdoorMask = CaptureConfig.create("mask", "outdoor", null);

            assertEquals(1, CaptureStorage.getNextSubjectNumber(tempRoot, indoorMask));
            assertEquals(1, CaptureStorage.getNextSubjectNumber(tempRoot, outdoorMask));

            File indoorSub1 = new File(CaptureStorage.collectionBaseDir(tempRoot, indoorMask), "mask_1");
            assertTrue(indoorSub1.mkdirs());
            File indoorSub2 = new File(CaptureStorage.collectionBaseDir(tempRoot, indoorMask), "mask_2");
            assertTrue(indoorSub2.mkdirs());

            assertEquals(3, CaptureStorage.getNextSubjectNumber(tempRoot, indoorMask));
            // Outdoor should still be 1 because indoor folders are in mask/indoor
            assertEquals(1, CaptureStorage.getNextSubjectNumber(tempRoot, outdoorMask));

            File outdoorSub1 = new File(CaptureStorage.collectionBaseDir(tempRoot, outdoorMask), "mask_1");
            assertTrue(outdoorSub1.mkdirs());
            assertEquals(2, CaptureStorage.getNextSubjectNumber(tempRoot, outdoorMask));

            // Test deletion on cancel
            assertTrue(CaptureStorage.deleteSubject(tempRoot, indoorMask, "mask_2"));
            assertFalse(indoorSub2.exists());
            assertTrue(indoorSub1.exists());
            assertTrue(outdoorSub1.exists());
        } finally {
            deleteRecursively(tempRoot);
        }
    }

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

    @Test public void allTwentyFiveValidConfigsMatchBaseDirAndSampleDir() {
        File root = new File("raw");
        List<CaptureConfig> validConfigs = new ArrayList<>();
        validConfigs.add(CaptureConfig.create("live", "indoor", "high"));
        validConfigs.add(CaptureConfig.create("live", "indoor", "medium"));
        validConfigs.add(CaptureConfig.create("live", "outdoor", "off"));

        for (String className : CaptureConfig.SUPPORTED_CLASSES) {
            if ("live".equals(className)) continue;
            validConfigs.add(CaptureConfig.create(className, "indoor", null));
            validConfigs.add(CaptureConfig.create(className, "outdoor", null));
        }

        assertEquals(25, validConfigs.size());

        for (CaptureConfig config : validConfigs) {
            File baseDir = CaptureStorage.collectionBaseDir(root, config);
            File expectedBase;
            if (config.isLive()) {
                expectedBase = new File(root, "live/" + config.qualityMode);
            } else {
                expectedBase = new File(root, config.className + "/" + config.environment);
            }
            assertEquals("BaseDir mismatch for " + config, expectedBase, baseDir);

            File sample = CaptureStorage.sampleDir(root, config, config.className + "_1", 1);
            File expectedSample = new File(expectedBase, config.className + "_1/1");
            assertEquals("SampleDir mismatch for " + config, expectedSample, sample);
        }
    }

    @Test public void allTwentyFiveValidConfigsSupportSubjectNumberingAndDeletion() throws IOException {
        File tempRoot = File.createTempFile("capture-all-configs-test", "");
        assertTrue(tempRoot.delete());
        assertTrue(tempRoot.mkdirs());

        try {
            List<CaptureConfig> validConfigs = new ArrayList<>();
            validConfigs.add(CaptureConfig.create("live", "indoor", "high"));
            validConfigs.add(CaptureConfig.create("live", "indoor", "medium"));
            validConfigs.add(CaptureConfig.create("live", "outdoor", "off"));

            for (String className : CaptureConfig.SUPPORTED_CLASSES) {
                if ("live".equals(className)) continue;
                validConfigs.add(CaptureConfig.create(className, "indoor", null));
                validConfigs.add(CaptureConfig.create(className, "outdoor", null));
            }

            for (CaptureConfig config : validConfigs) {
                assertEquals(1, CaptureStorage.getNextSubjectNumber(tempRoot, config));

                String subject1 = config.className + "_1";
                String subject2 = config.className + "_2";
                File sub1Dir = new File(CaptureStorage.collectionBaseDir(tempRoot, config), subject1);
                File sub2Dir = new File(CaptureStorage.collectionBaseDir(tempRoot, config), subject2);
                assertTrue(sub1Dir.mkdirs());
                assertTrue(sub2Dir.mkdirs());

                assertEquals(3, CaptureStorage.getNextSubjectNumber(tempRoot, config));

                // Deletion test
                assertTrue(CaptureStorage.deleteSubject(tempRoot, config, subject2));
                assertFalse(sub2Dir.exists());
                assertTrue(sub1Dir.exists());
            }
        } finally {
            deleteRecursively(tempRoot);
        }
    }

    @Test public void rejectsNullCaptureConfigInStorageOperations() {
        File root = new File("raw");
        assertInvalidStorage(() -> CaptureStorage.getNextSubjectNumber(root, (CaptureConfig) null));
        assertInvalidStorage(() -> CaptureStorage.deleteSubject(root, (CaptureConfig) null, "sub_1"));
        assertInvalidStorage(() -> CaptureStorage.sampleDir(root, (CaptureConfig) null, "sub_1", 1));
        assertInvalidStorage(() -> CaptureStorage.collectionBaseDir(root, (CaptureConfig) null));
    }

    private static void assertInvalidStorage(Runnable action) {
        try {
            action.run();
            org.junit.Assert.fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // Success.
        }
    }

    private static void deleteRecursively(File file) {
        if (!file.exists()) return;
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) {
                deleteRecursively(child);
            }
        }
        file.delete();
    }
}
