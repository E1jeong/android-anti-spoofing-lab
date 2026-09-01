package com.virditech.ac7000.device;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.File;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class LightingExperimentLoggerTest {

    @Test
    public void csvFileAndSnapshotsDirReturnValidFileObjects() {
        File csvFile = LightingExperimentLogger.getCsvFile();
        assertNotNull(csvFile);
        assertTrue(csvFile.getName().endsWith(".csv"));

        File snapshotsDir = LightingExperimentLogger.getSnapshotsDir();
        assertNotNull(snapshotsDir);
        assertTrue(snapshotsDir.getName().equals("lighting_snapshots"));
    }

    @Test
    public void recordSnapshotWithNullResultReportsError() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean errorReported = new AtomicBoolean(false);

        LightingExperimentLogger.recordSnapshot(null, null, null, "TEST", new LightingExperimentLogger.LogCallback() {
            @Override
            public void onLogged(int sampleId, String rgbFileName, String message) {
                latch.countDown();
            }

            @Override
            public void onError(String error) {
                errorReported.set(true);
                latch.countDown();
            }
        });

        assertTrue(latch.await(2, TimeUnit.SECONDS));
        assertTrue(errorReported.get());
    }
}
