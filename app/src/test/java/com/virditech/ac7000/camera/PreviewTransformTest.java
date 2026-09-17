package com.virditech.ac7000.camera;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class PreviewTransformTest {
    @Test public void rawTextureScaleIsDefinedPerCamera() {
        assertEquals(1f, PreviewTransform.RGB.textureScaleX(), 0f);
        assertEquals(-1f, PreviewTransform.IR.textureScaleX(), 0f);
    }

    @Test public void analysisOverlayAndCropUseTheSameMirrorPolicy() {
        assertTrue(PreviewTransform.RGB.mirrorAnalysisCoordinates());
        assertTrue(PreviewTransform.IR.mirrorAnalysisCoordinates());
        assertEquals(-1f, PreviewTransform.RGB.analysisBitmapScaleX(), 0f);
        assertEquals(-1f, PreviewTransform.IR.analysisBitmapScaleX(), 0f);
    }

    @Test public void cameraFlagSelectsTheMatchingTransform() {
        assertEquals(PreviewTransform.RGB, PreviewTransform.forColorCamera(true));
        assertEquals(PreviewTransform.IR, PreviewTransform.forColorCamera(false));
    }
}
