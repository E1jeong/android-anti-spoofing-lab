package com.unionbiometrics.vision;

import android.content.Context;

import com.unionbiometrics.vision.api.AntiSpoofingEngine;
import com.unionbiometrics.vision.api.IrLedController;
import com.unionbiometrics.vision.api.EngineLoadResult;
import com.unionbiometrics.vision.api.AntiSpoofingOptions;
import com.unionbiometrics.vision.internal.model.ModelSlotClassifier;
import com.unionbiometrics.vision.internal.engine.AntiSpoofingEngineImpl;

import java.util.ArrayList;

public final class VisionSdk {
    private VisionSdk() {}

    public static EngineLoadResult loadAll(Context context, IrLedController irLedController,
                                           AntiSpoofingOptions options) {
        if (context == null) throw new IllegalArgumentException("context must not be null");
        if (irLedController == null) {
            throw new IllegalArgumentException("irLedController must not be null");
        }
        if (options == null) throw new IllegalArgumentException("options must not be null");

        Context applicationContext = context.getApplicationContext();
        ModelSlotClassifier.LoadResult loaded = ModelSlotClassifier.loadAll(
                applicationContext == null ? context : applicationContext);
        ArrayList<AntiSpoofingEngine> engines = new ArrayList<>();
        for (ModelSlotClassifier slot : loaded.slots) {
            engines.add(new AntiSpoofingEngineImpl(slot, irLedController, options));
        }
        return new EngineLoadResult(engines, loaded.errors);
    }
}
