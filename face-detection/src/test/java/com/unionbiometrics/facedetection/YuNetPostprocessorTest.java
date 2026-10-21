package com.unionbiometrics.facedetection;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

public class YuNetPostprocessorTest {
    private final YuNetPostprocessor decoder = new YuNetPostprocessor(320, 320, 0.5f, 0.45f, 100);

    @Test(expected = IllegalArgumentException.class)
    public void rejectsWrongOutputLength() {
        decoder.decode(new float[2], new float[4], new Letterbox(432, 768, 320, 320));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsInvalidThreshold() {
        new YuNetPostprocessor(320, 320, Float.NaN, 0.45f, 100);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsEmptySource() {
        new Letterbox(0, 768, 320, 320);
    }

    @Test
    public void backgroundProducesNoFaces() {
        assertTrue(decode(logits(), boxes()).isEmpty());
    }

    @Test
    public void restoresPortraitCoordinatesWithoutAddingCropMargin() {
        float[] logits = logits();
        float[] boxes = boxes();
        // stride 8, row 10, column 15 -> center (120,80), size (40,80).
        face(logits, boxes, 10 * 40 + 15, 8, 0, 0, 40, 80, 10);
        FaceDetection face = decode(logits, boxes).get(0);
        assertEquals(72f, face.left, 0.001f);
        assertEquals(96f, face.top, 0.001f);
        assertEquals(168f, face.right, 0.001f);
        assertEquals(288f, face.bottom, 0.001f);
        assertTrue(face.confidence > 0.99f);
    }

    @Test
    public void nmsSuppressesDuplicateAcrossStridesAndKeepsSeparateFace() {
        float[] logits = logits();
        float[] boxes = boxes();
        face(logits, boxes, 10 * 40 + 15, 8, 0, 0, 40, 80, 10);
        // stride 16, row 5, column 7, delta x=.5 -> same box.
        face(logits, boxes, 1600 + 5 * 20 + 7, 16, 0.5f, 0, 40, 80, 8);
        face(logits, boxes, 2000 + 7 * 10 + 5, 32, 0, 0, 32, 32, 9);
        List<FaceDetection> faces = decode(logits, boxes);
        assertEquals(2, faces.size());
        assertTrue(faces.get(0).confidence > faces.get(1).confidence);
    }

    @Test
    public void clipsToSourceAndDropsBoxesEntirelyInPadding() {
        float[] logits = logits();
        float[] boxes = boxes();
        face(logits, boxes, 10 * 40, 8, 0, 0, 8, 8, 10);
        face(logits, boxes, 20 * 40 + 10, 8, 0, 0, 80, 400, 9);
        List<FaceDetection> faces = decode(logits, boxes);
        assertEquals(1, faces.size());
        assertEquals(0f, faces.get(0).left, 0);
        assertEquals(0f, faces.get(0).top, 0);
        assertEquals(768f, faces.get(0).bottom, 0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void nonFinitePredictionFailsExplicitly() {
        float[] logits = logits();
        logits[0] = Float.NaN;
        decode(logits, boxes());
    }

    @Test
    public void letterboxUsesTrainingRoundToEvenAndIntegerPadding() {
        Letterbox geometry = new Letterbox(65, 256, 320, 320);
        assertEquals(81, geometry.resizedWidth);
        assertEquals(119, geometry.padLeft);
        Letterbox tie = new Letterbox(66, 256, 320, 320);
        assertEquals(82, tie.resizedWidth);
    }

    @Test
    public void capsDetectionCount() {
        float[] logits = logits();
        float[] boxes = boxes();
        face(logits, boxes, 10 * 40 + 15, 8, 0, 0, 40, 80, 10);
        face(logits, boxes, 2000 + 7 * 10 + 5, 32, 0, 0, 32, 32, 9);
        assertEquals(1, new YuNetPostprocessor(320, 320, 0.5f, 0.45f, 1)
                .decode(logits, boxes, new Letterbox(432, 768, 320, 320)).size());
    }

    private List<FaceDetection> decode(float[] logits, float[] boxes) {
        return decoder.decode(logits, boxes, new Letterbox(432, 768, 320, 320));
    }

    private static float[] logits() {
        float[] values = new float[2100 * 2];
        Arrays.fill(values, -20f);
        return values;
    }

    private static float[] boxes() { return new float[2100 * 4]; }

    private static void face(float[] logits, float[] boxes, int index, int stride,
                             float dx, float dy, float width, float height, float logit) {
        logits[index * 2] = logit;
        logits[index * 2 + 1] = logit;
        boxes[index * 4] = dx;
        boxes[index * 4 + 1] = dy;
        boxes[index * 4 + 2] = (float) Math.log(width / stride);
        boxes[index * 4 + 3] = (float) Math.log(height / stride);
    }
}
