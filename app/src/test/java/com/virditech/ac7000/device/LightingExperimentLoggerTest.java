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
    public void csvFileReturnsValidFileObject() {
        File file = LightingExperimentLogger.getCsvFile();
        assertNotNull(file);
        assertTrue(file.getName().endsWith(".csv"));
    }

    @Test
    public void recordSnapshotWithNullResultReportsError() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean errorReported = new AtomicBoolean(false);

        LightingExperimentLogger.recordSnapshot(null, "TEST", new LightingExperimentLogger.LogCallback() {
            @Override
            public void onLogged(int sampleId, String message) {
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
