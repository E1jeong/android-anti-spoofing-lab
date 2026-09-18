package com.unionbiometrics.vision.internal.model;

import androidx.annotation.RestrictTo;

@RestrictTo(RestrictTo.Scope.LIBRARY)
public final class SlotClassificationResult {
    public final ClassificationResult result;
    public final long preprocessMs;
    public final long inferenceMs;

    SlotClassificationResult(ClassificationResult result) {
        this.result = result;
        preprocessMs = result.preprocessMs;
        inferenceMs = result.inferenceMs;
    }
}
