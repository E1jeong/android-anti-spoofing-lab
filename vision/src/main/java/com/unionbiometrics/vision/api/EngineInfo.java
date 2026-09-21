package com.unionbiometrics.vision.api;

import androidx.annotation.RestrictTo;

/**
 * Immutable model-slot metadata owned by a loaded engine.
 */
public record EngineInfo(String label, String backend, float cropMarginRatio) {
    @RestrictTo(RestrictTo.Scope.LIBRARY)
    public EngineInfo {
    }
}
