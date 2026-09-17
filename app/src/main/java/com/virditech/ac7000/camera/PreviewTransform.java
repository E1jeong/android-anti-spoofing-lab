package com.virditech.ac7000.camera;

public enum PreviewTransform {
    RGB(1f, true),
    IR(-1f, true);

    private final float textureScaleX;
    private final boolean mirrorAnalysisCoordinates;

    PreviewTransform(float textureScaleX, boolean mirrorAnalysisCoordinates) {
        this.textureScaleX = textureScaleX;
        this.mirrorAnalysisCoordinates = mirrorAnalysisCoordinates;
    }

    public static PreviewTransform forColorCamera(boolean color) {
        return color ? RGB : IR;
    }

    public float textureScaleX() {
        return textureScaleX;
    }

    public boolean mirrorAnalysisCoordinates() {
        return mirrorAnalysisCoordinates;
    }

    public float analysisBitmapScaleX() {
        return mirrorAnalysisCoordinates ? -1f : 1f;
    }
}
