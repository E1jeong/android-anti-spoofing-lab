package com.virditech.ac7000.evaluation;

import org.junit.Test;

import static org.junit.Assert.assertThrows;

public class EvaluationConditionsTest {
    @Test public void rejectsInvalidConditions() {
        assertThrows(IllegalArgumentException.class, () -> conditions(-1, "S1", "", 1, 30, "indoor"));
        assertThrows(IllegalArgumentException.class, () -> conditions(12, "S1", "", 1, 30, "indoor"));
        assertThrows(IllegalArgumentException.class, () -> conditions(0, " ", "", 1, 30, "indoor"));
        assertThrows(IllegalArgumentException.class, () -> conditions(1, "S1", "", 1, 30, "indoor"));
        assertThrows(IllegalArgumentException.class, () -> conditions(0, "S1", "", 0, 30, "indoor"));
        assertThrows(IllegalArgumentException.class, () -> conditions(0, "S1", "", 10, 30, "indoor"));
        assertThrows(IllegalArgumentException.class, () -> conditions(0, "S1", "", 1, Double.NaN, "indoor"));
        assertThrows(IllegalArgumentException.class, () -> conditions(0, "S1", "", 1, 0, "indoor"));
        assertThrows(IllegalArgumentException.class, () -> conditions(0, "S1", "", 1, 30, ""));
        assertThrows(IllegalArgumentException.class, () -> conditions(0, "S\n1", "", 1, 30, "indoor"));
    }

    private static EvaluationConditions conditions(int label, String subject, String medium,
                                                   int sector, double distance, String lighting) {
        return new EvaluationConditions(label, subject, medium, sector, distance, lighting);
    }
}
