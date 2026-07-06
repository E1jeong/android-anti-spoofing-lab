package com.virditech.ac7000.device;

import android.util.Log;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;

public final class IrCameraExposureController {
    private static final String TAG = "IrCameraExposure";
    private static final String IR_CAMERA_REGISTER =
            "/sys/bus/i2c/drivers/pi6008k-ir/1-001a/pi6008k_regctrl";

    private IrCameraExposureController() {}

    public static void applyFullAutoExposure() {
        writeRegister("0100B338", 0x00008000);
        writeRegister("0100B364", 0xFFFFFFFF);
        writeRegister("0100B368", 0xFFFFFFFF);
        writeRegister("0100B308", 0x01010203);

        writeRegister("F0601810", 0x00000090);
        writeRegister("F06012A4", 0x0000007F);
        writeRegister("0100A0EC", 0x25712200);
        writeRegister("0100A0F0", 0x25FF25FF);
        writeRegister("0100A0F4", 0x25FF25FF);
        writeRegister("0100A0F8", 0x010108FF);

        writeRegister("0100B300", 0x01020300);
        writeRegister("0100B180", 0x00000000);
    }

    private static void writeRegister(String address, int value) {
        String command = String.format("4,%s,%08X", address, value);
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(IR_CAMERA_REGISTER))) {
            writer.write(command);
            writer.newLine();
            writer.flush();
        } catch (IOException e) {
            Log.e(TAG, "Unable to write IR camera register " + command, e);
        }
    }
}
