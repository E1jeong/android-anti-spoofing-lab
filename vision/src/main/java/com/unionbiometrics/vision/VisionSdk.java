package com.unionbiometrics.vision;

import android.content.Context;
import android.graphics.Rect;

import com.unionbiometrics.vision.api.AntiSpoofingEngine;
import com.unionbiometrics.vision.api.VisionLoadResult;
import com.unionbiometrics.vision.api.VisionOptions;
import com.unionbiometrics.vision.api.IrLedController;
import com.unionbiometrics.vision.internal.image.FaceCrop;
import com.unionbiometrics.vision.internal.model.ClassificationResult;
import com.unionbiometrics.vision.internal.model.ModelSlotClassifier;
import com.unionbiometrics.vision.internal.session.DefaultAntiSpoofingEngine;

import java.util.ArrayList;

public final class VisionSdk {
    private VisionSdk() {}

    /** Returns the output label order used by every probability vector. */
    public static String[] labels() {
        return ClassificationResult.LABELS.clone();
    }

    public static String displayLabel(int index) {
        return ClassificationResult.displayLabel(index);
    }

    public static boolean isAcceptedClass(int index) {
        return ClassificationResult.isAcceptedClass(index);
    }

    public static Rect expandFaceBox(Rect faceBox, float marginRatio,
                                     int imageWidth, int imageHeight) {
        return FaceCrop.expand(faceBox, marginRatio, imageWidth, imageHeight);
    }

    public static VisionLoadResult loadAll(Context context, IrLedController irLedController) {
        return loadAll(context, irLedController, VisionOptions.defaults());
    }

    public static VisionLoadResult loadAll(Context context, IrLedController irLedController,
                                           VisionOptions options) {
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
            engines.add(new DefaultAntiSpoofingEngine(slot, irLedController, options));
        }
        return new VisionLoadResult(engines, loaded.errors);
    }
}
