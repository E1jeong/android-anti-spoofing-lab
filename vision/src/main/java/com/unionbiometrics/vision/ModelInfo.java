package com.unionbiometrics.vision;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Immutable model information. Engine-independent queries contain the registered count only. */
public final class ModelInfo {
    /** Total registered manifest entries, including slots that may fail to load. */
    public final int modelCount;
    /** Loaded input layout, or null for an engine-independent count query. */
    public final String inputType;
    /** Class names in probability-vector order. This list cannot be modified. */
    public final List<String> classLabels;
    /** Loaded crop margin, or NaN for an engine-independent count query. */
    public final float cropMarginRatio;

    ModelInfo(int modelCount, int inputTensorCount, float cropMarginRatio) {
        if (modelCount <= 0) throw new IllegalArgumentException("modelCount must be > 0");
        if (inputTensorCount == Constants.IR_INPUT_COUNT) {
            inputType = Constants.SINGLE_1_INPUT;
        } else if (inputTensorCount == Constants.DUAL_INPUT_COUNT) {
            inputType = Constants.DUAL_2_INPUT;
        } else {
            throw new IllegalArgumentException("Model must have exactly one or two inputs");
        }
        classLabels = Collections.unmodifiableList(Arrays.asList(Constants.CLASS_LABELS.clone()));
        this.modelCount = modelCount;
        this.cropMarginRatio = cropMarginRatio;
    }

    ModelInfo(int modelCount) {
        if (modelCount <= 0) throw new IllegalArgumentException("modelCount must be > 0");
        this.modelCount = modelCount;
        inputType = null;
        classLabels = Collections.emptyList();
        cropMarginRatio = Float.NaN;
    }
}
