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
        assertInvalid(() -> session.start("", null, 1, ROOT, 1, 0L));
        assertInvalid(() -> session.start("live", null, 0, ROOT, 1, 0L));
        assertInvalid(() -> session.start("live", null, 1, null, 1, 0L));
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
        for (int index = 1; index <= CaptureSchedule.TARGET_COUNT; index++) {
            CaptureSession.SavePermit permit = beginSave(session);
            assertNotNull(permit);
            CaptureSession.SaveCommit commit = session.commitSave(permit, index * 10L);
            session.releaseSave(permit);
            assertTrue(commit.committed);
            if (index == 20) {
                assertTrue(commit.sectorCompleted);
                assertEquals("LEFT TOP", session.currentStep().name);
            }
            if (index == CaptureSchedule.TARGET_COUNT) assertTrue(commit.collectionCompleted);
        }
        assertEquals(CaptureSchedule.TARGET_COUNT, session.getCount());
        assertNull(beginSave(session));
    }

    @Test public void cancelInvalidatesLateSaveFromPreviousSession() {
        CaptureSession session = startedSession();
        CaptureSession.SavePermit stale = beginSave(session);
        CaptureSession.CancelledSession cancelled = session.cancel();
        assertNotNull(cancelled);
        assertEquals("live_7", cancelled.subjectDirName);

        session.start("mask", null, 8, ROOT, -1, 10_000L);
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
        session.start("print", "all", 8, ROOT, 0, 100L);

        assertNull(session.beginSave(stale));
        assertEquals(0, session.getCount());
        assertNotNull(beginSave(session));
    }

    private static CaptureSession startedSession() {
        CaptureSession session = new CaptureSession();
        session.start("live", CaptureStorage.QUALITY_MEDIUM, 7, ROOT, 1, 1_000L);
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
