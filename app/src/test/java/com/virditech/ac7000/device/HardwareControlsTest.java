package com.virditech.ac7000.device;

import org.junit.Before;
import org.junit.Test;

public class HardwareControlsTest {

    @Before
    public void setUp() {
        HardwareControls.resetCacheForTest();
    }

    @Test
    public void setIrLedDoesNotThrowOnConsecutiveCalls() {
        HardwareControls.setIrLed(true);
        HardwareControls.setIrLed(true);
        HardwareControls.setIrLed(false);
        HardwareControls.setIrLed(false);
    }
}
