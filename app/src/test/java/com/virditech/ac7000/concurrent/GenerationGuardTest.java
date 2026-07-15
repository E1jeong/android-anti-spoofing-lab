package com.virditech.ac7000.concurrent;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class GenerationGuardTest {
    @Test public void advanceInvalidatesPreviousGeneration() {
        GenerationGuard guard = new GenerationGuard();
        int first = guard.advance();
        int second = guard.advance();

        assertFalse(guard.isCurrent(first));
        assertTrue(guard.isCurrent(second));
    }
}
