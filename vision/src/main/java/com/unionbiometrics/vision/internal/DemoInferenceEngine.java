package com.unionbiometrics.vision.internal;

import android.content.Context;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** Lab-only raw-frame entry. Product hosts use the public AntiSpoofingEngine facade. */
public final class DemoInferenceEngine {
    private DemoInferenceEngine() {}

    public static FrameResult infer(LabEngine engine, FrameInput frame) {
        Objects.requireNonNull(engine, "engine");
        return engine.impl.inferFrame(frame);
    }

    public static LoadResult loadAll(Context context) {
        if (context == null) throw new IllegalArgumentException("context must not be null");
        Context application = context.getApplicationContext();
        SlotClassifier.LoadResult loaded = SlotClassifier.loadAll(
                application == null ? context : application);
        List<LabEngine> engines = new ArrayList<>();
        for (SlotClassifier slot : loaded.slots) {
            engines.add(new LabEngine(slot));
        }
        return new LoadResult(engines, loaded.errors);
    }

    public static final class LabEngine implements AutoCloseable {
        private final AntiSpoofingEngineImpl impl;

        private LabEngine(SlotClassifier slot) {
            impl = new AntiSpoofingEngineImpl(slot, 10, 3);
        }

        public String label() { return impl.label(); }
        public String backend() { return impl.backend(); }
        public float cropMarginRatio() { return impl.cropMarginRatio(); }
        @Override public void close() { impl.close(); }
    }

    public static final class LoadResult {
        public final List<LabEngine> engines;
        public final List<String> errors;

        private LoadResult(List<LabEngine> engines, List<String> errors) {
            this.engines = Collections.unmodifiableList(new ArrayList<>(engines));
            this.errors = Collections.unmodifiableList(new ArrayList<>(errors));
        }
    }
}
