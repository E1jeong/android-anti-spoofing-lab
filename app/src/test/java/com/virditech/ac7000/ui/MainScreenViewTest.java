package com.virditech.ac7000.ui;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class MainScreenViewTest {
    @Test
    public void displayLabelsPreserveClassOrderAndShortenCurvedNames() {
        String[] expected = {
                "LIVE", "PRINT", "PICTURE", "MASK", "DISPLAY", "PMASK",
                "C PRINT", "C MASK", "C PICTURE", "C PMASK", "DENTAL_WHITE", "DENTAL_BLACK"
        };
        for (int i = 0; i < expected.length; i++) {
            assertEquals(expected[i], MainScreenView.classDisplayLabel(i));
        }
    }
}
