package com.unionbiometrics.vision.api;

/** Controls the host device's IR LED for an anti-spoofing session. */
public interface IrLedController {
    /** Applies the requested IR LED state synchronously on the engine caller's thread. */
    void setEnabled(boolean enabled);
}
