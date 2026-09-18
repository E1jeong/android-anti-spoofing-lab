package com.unionbiometrics.vision.api;

import androidx.annotation.RestrictTo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public record EngineLoadResult(List<AntiSpoofingEngine> engines, List<EngineInfo> engineInfos,
                               List<String> errors) {
    @RestrictTo(RestrictTo.Scope.LIBRARY)
    public EngineLoadResult(List<AntiSpoofingEngine> engines,
                            List<EngineInfo> engineInfos,
                            List<String> errors) {
        if (engines.size() != engineInfos.size()) {
            throw new IllegalArgumentException("engines and engineInfos must have the same size");
        }
        this.engines = Collections.unmodifiableList(new ArrayList<>(engines));
        this.engineInfos = Collections.unmodifiableList(new ArrayList<>(engineInfos));
        this.errors = Collections.unmodifiableList(new ArrayList<>(errors));
    }

    /**
     * Returns metadata in the same order as {@link #engines()}.
     */
    @Override
    public List<EngineInfo> engineInfos() {
        return engineInfos;
    }
}
