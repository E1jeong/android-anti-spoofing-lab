package com.virditech.ac7000.capture;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public final class CaptureProgressTextTest {
    @Test public void formatsProgressAndReturnsHighlightRanges() {
        CaptureStep step = new CaptureStep(5, "CENTER", 20);
        CaptureConfig config = CaptureConfig.create("live", "indoor", "high");
        CaptureProgressText text = CaptureProgressText.format(
                config, step, 3, 11, 100, "HIGH 0.900");

        assertEquals("LIVE · INDOOR · HIGH\n3 of 20 / Total 11 of 100\nHIGH 0.900", text.text);
        assertEquals("3 of 20", text.text.substring(text.stepCountStart, text.stepCountEnd));
        assertEquals("Total 11 of 100", text.text.substring(text.totalCountStart, text.totalCountEnd));
    }

    @Test public void formatsNoFaceProgressWithSceneAndCountdown() {
        CaptureStep step = new CaptureStep(0, "NO FACE", 20);
        CaptureConfig config = CaptureConfig.createNoFace("indoor", "hand");
        CaptureProgressText text = CaptureProgressText.format(
                config, step, 4, 4, 20, "START IN 3");

        assertEquals("NO FACE · HAND · INDOOR\n4 of 20 / Total 4 of 20\nSTART IN 3", text.text);
        assertEquals("4 of 20", text.text.substring(text.stepCountStart, text.stepCountEnd));
        assertEquals("Total 4 of 20", text.text.substring(text.totalCountStart, text.totalCountEnd));
    }

    @Test public void formatsProgressWithCaptureConfigForLiveAndNonLive() {
        CaptureStep step = new CaptureStep(5, "CENTER", 20);
        CaptureConfig liveConfig = CaptureConfig.create("live", "indoor", "high");
        CaptureProgressText liveText = CaptureProgressText.format(
                liveConfig, step, 3, 11, 100, "HIGH 0.900");
        assertEquals("LIVE · INDOOR · HIGH\n3 of 20 / Total 11 of 100\nHIGH 0.900", liveText.text);
        assertEquals("3 of 20", liveText.text.substring(liveText.stepCountStart, liveText.stepCountEnd));
        assertEquals("Total 11 of 100", liveText.text.substring(liveText.totalCountStart, liveText.totalCountEnd));

        CaptureConfig maskConfig = CaptureConfig.create("mask", "outdoor", null);
        CaptureProgressText maskText = CaptureProgressText.format(
                maskConfig, step, 5, 25, 100, null);
        assertEquals("MASK · OUTDOOR\n5 of 20 / Total 25 of 100", maskText.text);
        assertEquals("5 of 20", maskText.text.substring(maskText.stepCountStart, maskText.stepCountEnd));
        assertEquals("Total 25 of 100", maskText.text.substring(maskText.totalCountStart, maskText.totalCountEnd));
    }
}
