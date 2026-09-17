package com.unionbiometrics.vision.api;

import androidx.annotation.RestrictTo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class VisionLoadResult {
    private final List<AntiSpoofingEngine> engines;
    private final List<String> errors;

    @RestrictTo(RestrictTo.Scope.LIBRARY)
    public VisionLoadResult(List<AntiSpoofingEngine> engines, List<String> errors) {
        this.engines = Collections.unmodifiableList(new ArrayList<>(engines));
        this.errors = Collections.unmodifiableList(new ArrayList<>(errors));
    }

    public List<AntiSpoofingEngine> engines() {
        return engines;
    }

    public List<String> errors() {
        return errors;
    }
}
