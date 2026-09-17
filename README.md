# RGB/IR Anti-Spoofing Evaluation App

Android laboratory application for deploying, measuring, and validating RGB/IR anti-spoofing and face-recognition models on `[REDACTED_DEVICE]` hardware.

> Organization, product, package, proprietary SDK, model artifact, repository endpoint, signing, and signaling identifiers are intentionally redacted from this document. Refer to the checked-out source and private project documentation only in an authorized environment.

## Scope

This repository provides an on-device evaluation runtime rather than a production authentication product. Its main responsibilities are:

- synchronized RGB and IR Camera2 capture;
- face detection, calibration, crop generation, and preview overlays;
- TFLite anti-spoofing inference through NNAPI;
- optional standalone face-recognition evaluation and enrollment;
- atomic dataset capture with quality gating;
- lifecycle, latency, and memory diagnostics;
- an isolated WebRTC terminal proof of concept.

The UI is constructed programmatically in Java. XML layout inflation is not used.

## Runtime overview

`MainActivity` coordinates the UI and delegates mutable pipeline state to focused components:

| Area | Responsibility |
| --- | --- |
| `camera/` | RGB/IR camera ownership, frame conversion, timestamp matching, and safe teardown |
| `capture/` | 100-sample schedules, atomic save state, BMP output, and metadata |
| `concurrent/` | generation invalidation and latest-frame execution |
| `face/` | proprietary and public face-detector adapters |
| `vision/` | anti-spoofing engine library: slots, preprocessing, NNAPI inference, classification |
| `recognition/` | alignment, embeddings, template persistence, matching, and enrollment state |
| `ui/` | programmatic view hierarchy and overlays |
| `call/` | isolated WebRTC test activity |

RGB and IR frames are paired within 150 ms. Tracking, anti-spoofing inference, recognition, model initialization, and file I/O use separate background executors.

## Model contract

Model slots are configured in `vision/src/main/assets/ubio-vision/model_manifest.json`. Supported anti-spoofing layouts are:

- `single_1_input`: one RGB or IR crop input;
- `paired_1_input`: separate RGB and IR one-input models;
- `dual_2_input`: one model with RGB and IR crop inputs;
- `five_input`: RGB crop, IR crop, full RGB, full IR, and heatmap inputs.

The current evaluator requires the anti-spoofing output shape and class order to match `VisionSdk.labels()`, currently `[1,12]`. Legacy ten-class assets are rejected.

The active deployment artifacts are intentionally shown as:

- anti-spoofing model: `[REDACTED_ANTI_SPOOFING_MODEL]`;
- recognition model: `[REDACTED_RECOGNITION_MODEL]`;
- proprietary detector/runtime: `[REDACTED_FACE_SDK]`.

### NNAPI rules

- A manifest slot that fails NNAPI setup or warmup is rejected; there is no silent CPU fallback.
- NNAPI compilation caching must remain disabled because it is incompatible with `[REDACTED_DEVICE]`'s NPU driver.
- Verify tensor shape, delegate partitioning, warmup, on-device logs, and latency together. A backend label alone does not prove complete NPU execution.
- Recognition has an independent delegate policy and executor; it does not depend on an anti-spoofing result.

## Main features

### Live evaluation

- mirrored RGB and IR preview switching;
- synchronized face boxes and calibrated RGB-to-IR mapping;
- twelve-class anti-spoofing probabilities;
- preprocessing, inference, queue, end-to-end latency, and FPS diagnostics;
- optional motion, lighting, and foreground-entry experiments;
- five-frame Auth Mode verdict with a 0.85 LIVE threshold.

### Face recognition

- 112x112 five-landmark face alignment;
- isolated embedding inference and 1:N template matching;
- five-frame averaged enrollment;
- model-bound template persistence;
- separate fixed-input CPU/NNAPI diagnostic.

Enrollment and calibration are exclusive UI modes. Screen taps cannot enter clean mode while either mode is active. Anti-spoofing and ordinary identity inference are suspended in both modes; enrollment embedding inference runs only after an explicit enrollment start.

### Dataset capture

A session advances only after all five sample files are written successfully:

```text
RGB.bmp
cropRGB.bmp
IR.bmp
cropIR.bmp
meta.json
```

The default target is 100 valid samples. Capture supports pause, resume, cancel, countdown-based pose sectors, and stale-session invalidation.

- `live` capture applies the configured HIGH or MEDIUM quality gate.
- Presentation-attack classes bypass the live quality gate.
- Cancel invalidates queued work and removes the current subject directory.
- Output is stored beneath `/sdcard/Pictures/raw/`; captured biometric data must not be committed.

### Calibration

The hidden calibration entry opens a synchronized RGB/IR face guide. Confirmation requires valid faces from both streams and writes a device-specific 64-byte calibration file. Cancel exits without replacing the saved calibration.

External and internal calibration paths are device-specific and intentionally documented as `[REDACTED_CALIBRATION_PATH]` outside authorized environments.

### WebRTC test

The hidden test menu can open an isolated WebRTC activity. Entering it fully releases the main RGB/IR cameras before the call capturer starts; returning reopens the evaluation pipeline.

The signaling endpoint and credentials are `[REDACTED_SIGNALING_CONFIGURATION]`. The scaffold covers offer/answer, ICE exchange, local and remote media, microphone mute, speaker routing, reconnect behavior, hangup, and resource cleanup. It is not a production calling implementation.

## Private local configuration

The build requires private values supplied through user-level Gradle properties or the ignored root `local.properties` file:

```properties
[REDACTED_PRIVATE_MAVEN_URL_PROPERTY]=...
[REDACTED_SDK_LICENSE_PROPERTY]=...
[REDACTED_KEYSTORE_PATH_PROPERTY]=...
[REDACTED_KEY_ALIAS_PROPERTY]=...
[REDACTED_KEY_PASSWORD_PROPERTY]=...
[REDACTED_STORE_PASSWORD_PROPERTY]=...
```

Never commit actual repository URLs, license keys, keystores, passwords, signaling credentials, biometric fixtures, or device-specific calibration files. A checkout without the private dependency repository, SDK license, model assets, optional signing material, calibration data, and target hardware cannot complete device validation.

## Build and verification

Use JDK 21 on the validated development environment.

```powershell
# Compile
./gradlew.bat :vision:compileDebugJavaWithJavac
./gradlew.bat :app:compileDebugJavaWithJavac

# JVM unit tests
./gradlew.bat :vision:testDebugUnitTest
./gradlew.bat :app:testDebugUnitTest

# Android lint
./gradlew.bat :app:lintDebug

# Debug APK
./gradlew.bat :app:assembleDebug
```

Useful device logs:

```powershell
adb logcat -s AntiSpoofingClassifier:I MainActivity:I
adb logcat -s MainActivity:E CameraStream:E *:S
```

Hardware-dependent validation should cover:

1. RGB/IR startup, preview switching, frame pairing, and twelve-class output;
2. calibration save/cancel and alignment after restart;
3. 100-sample capture, pause/resume/cancel, and five-file atomicity;
4. enrollment, recognition, exclusive-mode tap behavior, and inference resumption;
5. repeated pause/resume and camera teardown without `SIGSEGV` or resource warnings;
6. WebRTC entry/exit, media, signaling, and main-camera recovery when that scope changes.

Detailed version-bound checks are maintained in `docs/`.

## Safety constraints

- Never close an `ImageReader` or preview `Surface` before `CameraDevice.StateCallback.onClosed()`.
- Never enable NNAPI compilation caching on `[REDACTED_DEVICE]`.
- Never add silent CPU fallback for an NNAPI anti-spoofing slot.
- Never count a partial capture sample as successful.
- Never commit private configuration or biometric data.
