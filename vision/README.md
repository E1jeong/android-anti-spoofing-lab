# Vision SDK Host Contract

The loading façade is `com.unionbiometrics.vision.VisionSdk`; stable host contracts are
isolated under `com.unionbiometrics.vision.api`. A host implements `AntiSpoofingHost`
for its device-specific RGB-to-IR mapping and IR illumination control, then uses only
the `AntiSpoofingEngine` interface during authentication.

```java
AntiSpoofingHost host = new AntiSpoofingHost() {
    @Override public Rect mapRgbFaceToIr(Rect rgbFaceBox, int irWidth, int irHeight) {
        return calibration.rgbToIr(rgbFaceBox, irWidth, irHeight);
    }

    @Override public void setIrIllumination(boolean enabled) {
        hardware.setIrLed(enabled);
    }
};

VisionLoadResult loaded = VisionSdk.loadAll(applicationContext, host);
AntiSpoofingEngine engine = loaded.engines().get(0);
engine.startSession();

VisionResult result = engine.process(new VisionFrame(
        rgbBitmap, rgbFaceBox, rgbTimestampNs,
        irBitmap, null, irTimestampNs));
```

The default session requests IR illumination, waits 400 ms, and averages three
probability vectors before returning `LIVE` or `SPOOF`. Calls made earlier return
`SETTLING` or `COLLECTING`; failures return `ERROR`. Call `reset()` at authentication
completion or cancellation and `close()` at host teardown.

The SDK borrows frame bitmaps only for the synchronous `process()` call and never
recycles them. Host interface methods run synchronously on the engine caller's thread.
Loading and inference must run off the Android main thread. A manifest
slot that fails NNAPI setup or warmup is rejected without CPU fallback.

Probability vectors follow the defensive label array returned by `VisionSdk.labels()`;
hosts do not need to import `ClassificationResult`.

The Lab app uses `AntiSpoofingEngine.infer(VisionFrame)` for raw per-frame diagnostics.
Product hosts use `startSession()` plus `process()`. Implementation is grouped by
responsibility under `com.unionbiometrics.vision.internal.asset`, `.image`, `.model`,
and `.session`; those packages are unsupported and are not part of the AAR's host API.
Public declarations required for cross-package SDK wiring are marked library-only for
consumer lint. Result construction is likewise library-only; hosts consume results
returned by `VisionSdk` and `AntiSpoofingEngine` rather than manufacturing them.
