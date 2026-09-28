package com.unionbiometrics.vision.api;

import androidx.annotation.RestrictTo;

/**
 * Immutable model-slot metadata owned by a loaded engine.
 */
public final class EngineInfo {
    private final String label;
    private final String backend;
    private final float cropMarginRatio;

    @RestrictTo(RestrictTo.Scope.LIBRARY)
    public EngineInfo(String label, String backend, float cropMarginRatio) {
        this.label = label;
        this.backend = backend;
        this.cropMarginRatio = cropMarginRatio;
    }

    public String label() {
        return label;
    }

    public String backend() {
        return backend;
    }

    public float cropMarginRatio() {
        return cropMarginRatio;
    }
}
