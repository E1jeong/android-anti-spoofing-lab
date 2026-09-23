package com.unionbiometrics.vision;

import android.content.Context;

import com.unionbiometrics.vision.api.AntiSpoofingEngine;
import com.unionbiometrics.vision.api.EngineLoadResult;
import com.unionbiometrics.vision.api.AntiSpoofingOptions;
import com.unionbiometrics.vision.internal.classification.SlotClassifier;
import com.unionbiometrics.vision.internal.engine.AntiSpoofingEngineImpl;

import java.util.ArrayList;

public final class VisionSdk {
    private VisionSdk() {}

    public static EngineLoadResult loadAll(Context context, AntiSpoofingOptions options) {
        if (context == null) throw new IllegalArgumentException("context must not be null");
        if (options == null) throw new IllegalArgumentException("options must not be null");

        Context applicationContext = context.getApplicationContext();
        SlotClassifier.LoadResult loaded = SlotClassifier.loadAll(
                applicationContext == null ? context : applicationContext);
        ArrayList<AntiSpoofingEngine> engines = new ArrayList<>();
        for (SlotClassifier slot : loaded.slots) {
            engines.add(new AntiSpoofingEngineImpl(slot, options));
        }
        return new EngineLoadResult(engines, loaded.errors);
    }
}
