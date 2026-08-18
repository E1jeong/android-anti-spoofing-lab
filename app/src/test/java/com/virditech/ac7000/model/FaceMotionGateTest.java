package com.virditech.ac7000.model;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class FaceMotionGateTest {
    @Test public void allowsInferenceOnStableFrame() {
        FaceMotionGate gate = new FaceMotionGate();
        assertTrue(gate.evaluate(100, 100, 200, 220, 432, 768, 1_000_000_000L).allowInference);
        assertTrue(gate.evaluate(102, 100, 202, 220, 432, 768, 1_100_000_000L).allowInference);
    }

    @Test public void blocksFastMovementUntilTheFaceIsStableAgain() {
        FaceMotionGate gate = new FaceMotionGate();
        gate.evaluate(100, 100, 200, 220, 432, 768, 1_000_000_000L);
        gate.evaluate(102, 100, 202, 220, 432, 768, 1_100_000_000L);
        assertFalse(gate.evaluate(180, 100, 280, 220, 432, 768, 1_200_000_000L).allowInference);
        assertTrue(gate.evaluate(181, 100, 281, 220, 432, 768, 1_300_000_000L).allowInference);
    }

    @Test public void blocksFacesTouchingTheImageEdge() {
        FaceMotionGate gate = new FaceMotionGate();
        assertFalse(gate.evaluate(0, 100, 100, 220, 432, 768, 1_000_000_000L).allowInference);
    }
}
