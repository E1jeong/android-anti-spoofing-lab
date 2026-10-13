package com.virditech.ac7000.capture;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public final class CaptureConfigTest {

    @Test
    public void liveIndoorPermitsHighAndMediumOnly() {
        CaptureConfig high = CaptureConfig.create("live", "indoor", CaptureConfig.QUALITY_HIGH);
        assertEquals("live", high.className);
        assertEquals("indoor", high.environment);
        assertEquals(CaptureConfig.QUALITY_HIGH, high.qualityMode);
        assertEquals(2, high.minQualityLevel);
        assertTrue(high.isLive());
        assertTrue(high.isQualityCheckRequired());

        CaptureConfig medium = CaptureConfig.create("live", "indoor", CaptureConfig.QUALITY_MEDIUM);
        assertEquals("live", medium.className);
        assertEquals("indoor", medium.environment);
        assertEquals(CaptureConfig.QUALITY_MEDIUM, medium.qualityMode);
        assertEquals(1, medium.minQualityLevel);
        assertTrue(medium.isLive());
        assertTrue(medium.isQualityCheckRequired());

        assertInvalid(() -> CaptureConfig.create("live", "indoor", CaptureConfig.QUALITY_OFF));
        assertInvalid(() -> CaptureConfig.create("live", "indoor", null));
        assertInvalid(() -> CaptureConfig.create("live", "indoor", "invalid"));
    }

    @Test
    public void liveOutdoorForcesQualityOff() {
        CaptureConfig off = CaptureConfig.create("live", "outdoor", CaptureConfig.QUALITY_OFF);
        assertEquals("live", off.className);
        assertEquals("outdoor", off.environment);
        assertEquals(CaptureConfig.QUALITY_OFF, off.qualityMode);
        assertEquals(-1, off.minQualityLevel);
        assertTrue(off.isLive());
        assertFalse(off.isQualityCheckRequired());

        CaptureConfig nullQuality = CaptureConfig.create("live", "outdoor", null);
        assertEquals(CaptureConfig.QUALITY_OFF, nullQuality.qualityMode);
        assertEquals(-1, nullQuality.minQualityLevel);

        assertInvalid(() -> CaptureConfig.create("live", "outdoor", CaptureConfig.QUALITY_HIGH));
        assertInvalid(() -> CaptureConfig.create("live", "outdoor", CaptureConfig.QUALITY_MEDIUM));
    }

    @Test
    public void nonLiveClassesSupportIndoorAndOutdoorWithoutQuality() {
        for (String className : CaptureConfig.SUPPORTED_CLASSES) {
            if ("live".equals(className)) continue;

            CaptureConfig indoor = CaptureConfig.create(className, "indoor", null);
            assertEquals(className, indoor.className);
            assertEquals("indoor", indoor.environment);
            assertNull(indoor.qualityMode);
            assertEquals(-1, indoor.minQualityLevel);
            assertFalse(indoor.isLive());
            assertFalse(indoor.isQualityCheckRequired());

            CaptureConfig outdoor = CaptureConfig.create(className, "outdoor", null);
            assertEquals(className, outdoor.className);
            assertEquals("outdoor", outdoor.environment);
            assertNull(outdoor.qualityMode);
            assertEquals(-1, outdoor.minQualityLevel);
            assertFalse(outdoor.isLive());
            assertFalse(outdoor.isQualityCheckRequired());

            assertInvalid(() -> CaptureConfig.create(className, "indoor", CaptureConfig.QUALITY_HIGH));
            assertInvalid(() -> CaptureConfig.create(className, "indoor", CaptureConfig.QUALITY_MEDIUM));
            assertInvalid(() -> CaptureConfig.create(className, "indoor", CaptureConfig.QUALITY_OFF));
            assertInvalid(() -> CaptureConfig.create(className, "outdoor", CaptureConfig.QUALITY_HIGH));
            assertInvalid(() -> CaptureConfig.create(className, "outdoor", CaptureConfig.QUALITY_OFF));
        }
    }

    @Test
    public void rejectsInvalidClassOrEnvironment() {
        assertInvalid(() -> CaptureConfig.create(null, "indoor", "high"));
        assertInvalid(() -> CaptureConfig.create("", "indoor", "high"));
        assertInvalid(() -> CaptureConfig.create("unknown_class", "indoor", null));
        assertInvalid(() -> CaptureConfig.create("live", null, "high"));
        assertInvalid(() -> CaptureConfig.create("live", "", "high"));
        assertInvalid(() -> CaptureConfig.create("live", "space", "high"));
    }

    @Test
    public void equalsAndHashCodeAndToString() {
        CaptureConfig config1 = CaptureConfig.create("live", "indoor", "high");
        CaptureConfig config2 = CaptureConfig.create("live", "indoor", "high");
        CaptureConfig config3 = CaptureConfig.create("live", "indoor", "medium");

        assertEquals(config1, config2);
        assertEquals(config1.hashCode(), config2.hashCode());
        assertNotEquals(config1, config3);
        assertTrue(config1.toString().contains("live"));
        assertTrue(config1.toString().contains("indoor"));
        assertTrue(config1.toString().contains("high"));
    }

    @Test
    public void normalizesInputsAndSupportsToSummaryLabel() {
        CaptureConfig normalized = CaptureConfig.create(" LIVE ", " InDoor ", " HIGH ");
        assertEquals("live", normalized.className);
        assertEquals("indoor", normalized.environment);
        assertEquals("high", normalized.qualityMode);
        assertEquals("LIVE · INDOOR · HIGH", normalized.toSummaryLabel());

        CaptureConfig nonLive = CaptureConfig.create(" MASK ", " OutDoor ", null);
        assertEquals("mask", nonLive.className);
        assertEquals("outdoor", nonLive.environment);
        assertEquals("MASK · OUTDOOR", nonLive.toSummaryLabel());
    }

    @Test
    public void matchesMainScreenViewClassOptions() {
        assertEquals(CaptureConfig.SUPPORTED_CLASSES.size(),
                com.virditech.ac7000.ui.MainScreenView.CLASS_OPTIONS.length);
        for (String[] option : com.virditech.ac7000.ui.MainScreenView.CLASS_OPTIONS) {
            String slug = option[1];
            assertTrue("Supported classes must contain slug: " + slug,
                    CaptureConfig.SUPPORTED_CLASSES.contains(slug));
        }
    }

    private static void assertInvalid(Runnable action) {
        try {
            action.run();
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // Success.
        }
    }
}
