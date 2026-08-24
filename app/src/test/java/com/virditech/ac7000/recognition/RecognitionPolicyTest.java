package com.virditech.ac7000.recognition;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class RecognitionPolicyTest {

    @Test
    public void rejectsNegativeTemplateCount() {
        try {
            RecognitionPolicy.shouldSchedule(false, true, true, -1);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // Expected.
        }
    }

    @Test
    public void enrollmentDependsOnlyOnRecognitionModelReadiness() {
        assertFalse(RecognitionPolicy.shouldSchedule(true, false, false, 0));
        assertTrue(RecognitionPolicy.shouldSchedule(true, false, true, 0));
    }

    @Test
    public void identificationNeedsModeAndTemplate() {
        assertFalse(RecognitionPolicy.shouldSchedule(false, false, true, 1));
        assertFalse(RecognitionPolicy.shouldSchedule(false, true, true, 0));
        assertTrue(RecognitionPolicy.shouldSchedule(false, true, true, 1));
    }
}
