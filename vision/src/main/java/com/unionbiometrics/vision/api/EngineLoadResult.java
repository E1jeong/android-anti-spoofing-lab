package com.unionbiometrics.vision.api;

import androidx.annotation.RestrictTo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public record EngineLoadResult(List<AntiSpoofingEngine> engines, List<String> errors) {
    @RestrictTo(RestrictTo.Scope.LIBRARY)
    public EngineLoadResult(List<AntiSpoofingEngine> engines, List<String> errors) {
        this.engines = Collections.unmodifiableList(new ArrayList<>(engines));
        this.errors = Collections.unmodifiableList(new ArrayList<>(errors));
    }
}
