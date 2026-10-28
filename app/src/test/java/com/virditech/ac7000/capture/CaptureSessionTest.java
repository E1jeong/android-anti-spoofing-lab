package com.virditech.ac7000.capture;

import org.junit.Test;

import java.io.File;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public final class CaptureSessionTest {
    private static final File ROOT = new File("capture-test-root");

    @Test public void rejectsInvalidStartArguments() {
        CaptureSession session = new CaptureSession();
        assertInvalid(() -> session.start((CaptureConfig) null, 1, ROOT, 0L));
        CaptureConfig validConfig = CaptureConfig.create("live", "indoor", "high");
        assertInvalid(() -> session.start(validConfig, 0, ROOT, 0L));
        assertInvalid(() -> session.start(validConfig, -1, ROOT, 0L));
        assertInvalid(() -> session.start(validConfig, 1, null, 0L));
    }

    @Test public void startWithCaptureConfigPreservesConfigurationAndImmutability() {
        CaptureSession session = new CaptureSession();
        CaptureConfig config = CaptureConfig.create("dental_white", "outdoor", null);
        session.start(config, 5, ROOT, 1_000L);

        assertEquals("dental_white", session.getClassName());
        assertEquals("outdoor", session.getEnvironment());
        assertNull(session.getQualityMode());
        assertEquals(config, session.getConfig());

        CaptureSession.SaveCandidate candidate = session.snapshotForSave();
        assertNotNull(candidate);
        assertEquals(config, candidate.config);
        assertEquals("dental_white", candidate.className);
        assertEquals("outdoor", candidate.environment);

        CaptureSession.SavePermit permit = session.beginSave(candidate);
        assertNotNull(permit);
        assertEquals(config, permit.config);
        assertEquals("dental_white", permit.className);
        assertEquals("outdoor", permit.environment);
        assertEquals(1, permit.sampleIndex);

        CaptureSession.CancelledSession cancelled = session.cancel();
        assertNotNull(cancelled);
        assertEquals(config, cancelled.config);
        assertEquals("dental_white", cancelled.className);
        assertEquals("outdoor", cancelled.environment);
        assertEquals("dental_white_5", cancelled.subjectDirName);
    }

    @Test public void startAndPausePreserveCountdown() {
        CaptureSession session = startedSession();
        assertEquals(4, session.countdownSeconds(1_000L));

        assertTrue(session.togglePaused(2_500L));
        assertEquals(3, session.countdownSeconds(20_000L));
        assertFalse(session.togglePaused(20_000L));
        assertEquals(3, session.countdownSeconds(20_000L));
        assertEquals(0, session.countdownSeconds(22_500L));
    }

    @Test public void failedSaveDoesNotAdvanceAndOnlyOneSaveMayBeInFlight() {
        CaptureSession session = startedSession();
        CaptureSession.SavePermit permit = beginSave(session);
        assertNotNull(permit);
        assertNull(beginSave(session));

        session.releaseSave(permit);
        assertEquals(0, session.getCount());
        assertNotNull(beginSave(session));
    }

    @Test public void successfulSavesAdvanceScheduleAndCompleteAtOneHundred() {
        CaptureSession session = startedSession();
        int[] stepEnds = {10, 20, 30, 40, 60, 70, 80, 90, 100};
        int[] nextSectors = {4, 7, 2, 5, 8, 3, 6, 9, 9};
        int completedSteps = 0;
        for (int index = 1; index <= CaptureSchedule.TARGET_COUNT; index++) {
            CaptureSession.SavePermit permit = beginSave(session);
            assertNotNull(permit);
            CaptureSession.SaveCommit commit = session.commitSave(permit, index * 10L);
            session.releaseSave(permit);
            assertTrue(commit.committed);
            boolean atStepEnd = index == stepEnds[completedSteps];
            assertEquals(atStepEnd, commit.sectorCompleted);
            if (atStepEnd) {
                assertEquals(nextSectors[completedSteps], session.currentStep().sector);
                completedSteps++;
            }
            if (index == CaptureSchedule.TARGET_COUNT) assertTrue(commit.collectionCompleted);
        }
        assertEquals(CaptureSchedule.TARGET_COUNT, session.getCount());
        assertNull(beginSave(session));
    }

    @Test public void noFaceCompletesAtTwentyWithSingleStepAndPauseableCountdown() {
        CaptureSession session = new CaptureSession();
        session.start(CaptureConfig.createNoFace("indoor", "hand"), 3, ROOT, 1_000L);

        assertEquals(3, session.countdownSeconds(1_000L));
        assertEquals(20, session.getTargetCount());
        assertEquals(0, session.currentStep().sector);
        assertEquals(20, session.currentStep().targetCount);
        assertTrue(session.togglePaused(2_000L));
        assertEquals(2, session.countdownSeconds(20_000L));
        assertFalse(session.togglePaused(20_000L));
        assertEquals(0, session.countdownSeconds(22_000L));

        for (int index = 1; index <= 20; index++) {
            CaptureSession.SavePermit permit = beginSave(session);
            assertNotNull(permit);
            CaptureSession.SaveCommit commit = session.commitSave(permit, 22_000L + index);
            session.releaseSave(permit);
            assertTrue(commit.committed);
            assertEquals(index == 20, commit.sectorCompleted);
            assertEquals(index == 20, commit.collectionCompleted);
        }
        assertEquals(20, session.getCount());
        assertEquals(20, session.getStepCount());
        assertNull(beginSave(session));
    }

    @Test public void noFaceCancelInvalidatesLateSaveFromPreviousSession() {
        CaptureSession session = new CaptureSession();
        session.start(CaptureConfig.createNoFace("outdoor", "clock"), 4, ROOT, 1_000L);
        CaptureSession.SavePermit stale = beginSave(session);
        CaptureSession.CancelledSession cancelled = session.cancel();
        assertNotNull(cancelled);
        assertEquals("no_face_4", cancelled.subjectDirName);

        session.start(CaptureConfig.createNoFace("indoor", "background"), 5, ROOT, 10_000L);
        assertFalse(session.commitSave(stale, 11_000L).committed);
        session.releaseSave(stale);
        assertEquals(0, session.getCount());
        assertFalse(session.isIoBusy());
    }

    @Test public void cancelInvalidatesLateSaveFromPreviousSession() {
        CaptureSession session = startedSession();
        CaptureSession.SavePermit stale = beginSave(session);
        CaptureSession.CancelledSession cancelled = session.cancel();
        assertNotNull(cancelled);
        assertEquals("live_7", cancelled.subjectDirName);

        session.start(CaptureConfig.create("mask", "indoor", null), 8, ROOT, 10_000L);
        CaptureSession.SaveCommit lateCommit = session.commitSave(stale, 11_000L);
        session.releaseSave(stale);
        assertFalse(lateCommit.committed);
        assertEquals(0, session.getCount());
        assertFalse(session.isIoBusy());
    }

    @Test public void candidateFromPreviousSessionCannotStartSaveInReplacementSession() {
        CaptureSession session = startedSession();
        CaptureSession.SaveCandidate stale = session.snapshotForSave();

        session.cancel();
        session.start(CaptureConfig.create("print", "outdoor", null), 8, ROOT, 100L);

        assertNull(session.beginSave(stale));
        assertEquals(0, session.getCount());
        assertNotNull(beginSave(session));
    }

    private static CaptureSession startedSession() {
        CaptureSession session = new CaptureSession();
        session.start(CaptureConfig.create("live", "indoor", CaptureStorage.QUALITY_MEDIUM), 7, ROOT, 1_000L);
        return session;
    }

    private static CaptureSession.SavePermit beginSave(CaptureSession session) {
        return session.beginSave(session.snapshotForSave());
    }

    private static void assertInvalid(Runnable action) {
        try {
            action.run();
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // Expected.
        }
    }
}
