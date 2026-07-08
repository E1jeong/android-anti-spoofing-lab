package com.virditech.ac7000.capture;

import java.util.Locale;

public final class CaptureProgressText {
    public final String text;
    public final int stepCountStart;
    public final int stepCountEnd;
    public final int totalCountStart;
    public final int totalCountEnd;

    private CaptureProgressText(String text, int stepCountStart, int stepCountEnd,
                                int totalCountStart, int totalCountEnd) {
        this.text = text;
        this.stepCountStart = stepCountStart;
        this.stepCountEnd = stepCountEnd;
        this.totalCountStart = totalCountStart;
        this.totalCountEnd = totalCountEnd;
    }

    public static CaptureProgressText format(String className, CaptureStep step,
                                             int stepCount, int totalCount, int targetCount,
                                             String qualityLine) {
        StringBuilder sb = new StringBuilder();
        sb.append(className.toUpperCase(Locale.US))
                .append(" / Sector ")
                .append(step.sector)
                .append(" ")
                .append(step.name)
                .append("\n");
        int stepCountStart = sb.length();
        sb.append(stepCount)
                .append(" of ")
                .append(step.targetCount);
        int stepCountEnd = sb.length();
        sb.append(" / ");
        int totalCountStart = sb.length();
        sb.append("Total ")
                .append(totalCount)
                .append(" of ")
                .append(targetCount);
        int totalCountEnd = sb.length();
        if (qualityLine != null && !qualityLine.isEmpty()) {
            sb.append("\n").append(qualityLine);
        }
        return new CaptureProgressText(sb.toString(), stepCountStart, stepCountEnd,
                totalCountStart, totalCountEnd);
    }
}
