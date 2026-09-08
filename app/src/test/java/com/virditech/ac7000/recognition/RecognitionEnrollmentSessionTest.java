package com.virditech.ac7000.recognition;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public final class RecognitionEnrollmentSessionTest {
    @Test public void rejectsInvalidPreparation() {
        RecognitionEnrollmentSession session = new RecognitionEnrollmentSession();
        assertInvalid(() -> session.prepare("", "name"));
        assertInvalid(() -> session.prepare("id", "  "));
    }

    @Test public void requiresPreparedUiBeforeStart() {
        RecognitionEnrollmentSession session = new RecognitionEnrollmentSession();
        assertFalse(session.start());
        assertFalse(session.isRequested());
    }

    @Test public void completionIsFinalizedOnlyAfterPersistenceAttemptReturns() {
        RecognitionEnrollmentSession session = new RecognitionEnrollmentSession();
        session.prepare("id", "Alice");
        assertTrue(session.start());

        for (int i = 1; i <= 5; i++) {
            RecognitionEnrollmentSession.Progress progress = session.add(new float[]{i}, 5);
            assertTrue(progress.accepted);
            assertEquals(i, progress.collectedCount);
            assertEquals(i == 5, progress.isComplete());
        }
        assertTrue(session.isRequested());

        RecognitionEnrollmentSession.Progress retry = session.add(new float[]{6f}, 5);
        assertTrue(retry.isComplete());
        assertEquals(6, retry.collectedCount);

        session.finishCompletedAttempt();
        assertFalse(session.isRequested());
        assertFalse(session.add(new float[]{7f}, 5).accepted);
    }

    @Test public void cancelInvalidatesRequestAndClearsIdentity() {
        RecognitionEnrollmentSession session = new RecognitionEnrollmentSession();
        session.prepare("id", "Alice");
        session.start();
        session.add(new float[]{1f}, 5);

        assertTrue(session.cancel());
        assertFalse(session.isRequested());
        assertFalse(session.isUiActive());
        assertNull(session.getEnrollmentId());
        assertNull(session.getEnrollmentName());
        assertFalse(session.add(new float[]{2f}, 5).accepted);
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
