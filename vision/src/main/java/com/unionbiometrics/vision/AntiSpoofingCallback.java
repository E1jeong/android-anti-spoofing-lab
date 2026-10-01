package com.unionbiometrics.vision;

@FunctionalInterface
public interface AntiSpoofingCallback {
    void onResult(AntiSpoofingResult result);
}
