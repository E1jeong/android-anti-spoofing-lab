package com.virditech.ac7000.device;

import android.util.Log;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;

public final class HardwareControls {
    private static final String TAG = "HardwareControls";
    private static final String IR_LED_BRIGHTNESS = "/sys/class/backlight/irled_backlight/brightness";
    private static final String LCD_BRIGHTNESS = "/sys/class/backlight/dsi_backlight/brightness";

    private HardwareControls() {}

    public static void setIrLed(boolean enabled) {
        write(IR_LED_BRIGHTNESS, enabled ? 100 : 0);
    }

    public static void setLcdBrightness(int brightness) {
        write(LCD_BRIGHTNESS, clamp(brightness));
    }

    private static void write(String path, int value) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(path))) {
            writer.write(Integer.toString(value));
            writer.newLine();
            writer.flush();
        } catch (IOException e) {
            Log.e(TAG, "Unable to write " + path, e);
        }
    }

    private static int clamp(int value) {
        return Math.max(0, Math.min(100, value));
    }
}
