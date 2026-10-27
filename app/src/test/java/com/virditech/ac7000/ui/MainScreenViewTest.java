package com.virditech.ac7000.ui;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class MainScreenViewTest {
    @Test
    public void displayLabelsPreserveNamesAndShortenOnlyCurvedPrefix() {
        assertEquals("LIVE", MainScreenView.classDisplayLabel("LIVE"));
        assertEquals("DENTAL_WHITE", MainScreenView.classDisplayLabel("DENTAL_WHITE"));
        assertEquals("C PRINT", MainScreenView.classDisplayLabel("CURVED_PRINT"));
    }
}
