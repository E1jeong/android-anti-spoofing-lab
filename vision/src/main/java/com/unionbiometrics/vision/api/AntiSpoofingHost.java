package com.unionbiometrics.vision.api;

import android.graphics.Rect;

/**
 * Hardware-specific operations supplied by an application that embeds the Vision SDK.
 * Calls are synchronous on the thread that invokes the corresponding engine method.
 */
public interface AntiSpoofingHost {
    /** Maps an unexpanded RGB face box into the IR camera coordinate system. */
    Rect mapRgbFaceToIr(Rect rgbFaceBox, int irWidth, int irHeight);

    /** Applies the requested IR illumination state on the host device. */
    void setIrIllumination(boolean enabled);
}
