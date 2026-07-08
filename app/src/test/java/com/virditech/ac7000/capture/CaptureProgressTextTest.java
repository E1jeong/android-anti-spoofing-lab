package com.virditech.ac7000.capture;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public final class CaptureProgressTextTest {
    @Test public void formatsProgressAndReturnsHighlightRanges() {
        CaptureStep step = new CaptureStep(5, "CENTER", 20);
        CaptureProgressText text = CaptureProgressText.format(
                "live", step, 3, 11, 100, "HIGH 0.900");

        assertEquals("LIVE / Sector 5 CENTER\n3 of 20 / Total 11 of 100\nHIGH 0.900", text.text);
        assertEquals("3 of 20", text.text.substring(text.stepCountStart, text.stepCountEnd));
        assertEquals("Total 11 of 100", text.text.substring(text.totalCountStart, text.totalCountEnd));
    }
}
