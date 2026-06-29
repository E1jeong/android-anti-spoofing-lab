# Anti-Spoofing Viewer

Minimal Android shell for RGB/IR anti-spoofing model evaluation.

## Required local inputs

1. Put the model at `app/src/main/assets/anti_spoofing.tflite`.
2. Update `app/src/main/assets/model_spec.json` to match the training preprocessing contract.
3. Supply the private Maven repository URL and platform-signing values through user-level Gradle properties or the ignored root `local.properties` file. Do not commit them.
4. Ensure `/sdcard/devlocal/CalibConfig.dat` exists on the device, or create it with the built-in calibration flow.

Required Gradle property names:

```properties
PRIVATE_MAVEN_URL=...
KEYSTORE_PATH=...
KEY_ALIAS=...
KEY_PASSWORD=...
STORE_PASSWORD=...
```

`PRIVATE_MAVEN_URL` must point to a repository containing the proprietary face detection dependency. Without repository access and the target hardware inputs, a public checkout cannot perform a complete device build or runtime test.

## Model runtime status

The app supports float and full INT8 TFLite models. It tries Android NNAPI first and falls back to CPU/XNNPACK if NNAPI preparation fails. The on-screen backend label is authoritative:

- `Backend NNAPI`: NNAPI delegate was prepared.
- `Backend CPU`: NNAPI failed and inference is running on CPU/XNNPACK.

Current NPU experiment status: the NPU-friendly Keras INT8 export still falls back to CPU on the target board with `ANEURALNETWORKS_BAD_DATA ... while adding operation`. Do not treat INT8 model size reduction as proof of NPU acceleration.

## Camera calibration

Tap the invisible upper-left hotspot five times within two seconds to open camera calibration. Place exactly one face inside the guide and press `CONFIRM`. The app detects the face in synchronized RGB and IR frames, then writes the device-specific 64-byte `CalibConfig.dat`. Use `CANCEL` to exit without changing the saved calibration.

## Build

```powershell
./gradlew.bat :app:compileDebugJavaWithJavac
```
