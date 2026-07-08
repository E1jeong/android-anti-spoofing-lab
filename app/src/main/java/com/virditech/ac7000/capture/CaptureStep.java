package com.virditech.ac7000.capture;

public final class CaptureStep {
    public final int sector;
    public final String name;
    public final int targetCount;

    public CaptureStep(int sector, String name, int targetCount) {
        this.sector = sector;
        this.name = name;
        this.targetCount = targetCount;
    }
}
