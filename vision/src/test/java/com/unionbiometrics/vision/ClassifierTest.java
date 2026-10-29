package com.unionbiometrics.vision;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.tensorflow.lite.DataType;

public class ClassifierTest {
    @Test
    public void rejectsUint8ForBothInputChannels() {
        for (int channels : new int[]{1, 3}) {
            IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                    () -> Classifier.validateInput(new int[]{1, 224, 224, channels},
                            DataType.UINT8, 1f, "input", channels));
            assertTrue(error.getMessage().contains("FLOAT32/INT8"));
            assertTrue(error.getMessage().contains("actual=UINT8"));
        }
    }

    @Test
    public void acceptsFloatAndInt8ForBothInputChannels() {
        for (int channels : new int[]{1, 3}) {
            int[] shape = {1, 224, 224, channels};
            Classifier.validateInput(shape, DataType.FLOAT32, 0f, "input", channels);
            Classifier.validateInput(shape, DataType.INT8, 0.007843138f, "input", channels);
        }
    }

    @Test
    public void rejectsInvalidShapeAndInt8Scale() {
        assertThrows(IllegalArgumentException.class,
                () -> Classifier.validateInput(new int[]{1, 112, 112, 1},
                        DataType.INT8, 0.01f, "input", 1));
        assertThrows(IllegalArgumentException.class,
                () -> Classifier.validateInput(new int[]{1, 224, 224, 3},
                        DataType.INT8, 0.01f, "input", 1));
        assertThrows(IllegalArgumentException.class,
                () -> Classifier.validateInput(new int[]{1, 224, 224, 1},
                        DataType.INT8, 0f, "input", 1));
    }

    @Test
    public void irLuminanceUsesEveryColorChannel() {
        int nonGrayPixel = 0xFF0A64C8;

        assertEquals(88, Classifier.irLuminance(nonGrayPixel));
        assertEquals(54, Classifier.irLuminance(0xFFFF0000));
        assertEquals(182, Classifier.irLuminance(0xFF00FF00));
        assertEquals(18, Classifier.irLuminance(0xFF0000FF));
    }

    @Test
    public void irLuminancePreservesGrayPixel() {
        assertEquals(137, Classifier.irLuminance(0xFF898989));
    }
}
