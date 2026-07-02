# Anti-Spoofing Viewer

Minimal Android shell for RGB/IR anti-spoofing model evaluation.

## Required local inputs

1. Put the standard model at `app/src/main/assets/anti_spoofing.tflite` (five-input contract: `cropRgb`, `cropIr`, `fullRgb`, `fullIr`, `heatmap`). Optionally put an NPU-friendly model at `app/src/main/assets/anti_spoofing_npu.tflite`; if it is absent, the NPU slot reuses the standard model file.
2. Update `app/src/main/assets/model_spec.json` (standard) and `app/src/main/assets/model_spec_npu.json` (NPU) to match the training preprocessing contract.
3. Supply the private Maven repository URL, Face SDK license, and platform-signing values through user-level Gradle properties or the ignored root `local.properties` file. Do not commit them.
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

The app supports float and full INT8 TFLite models. The standard spec runs CPU/XNNPACK directly because the current standard full-INT8 export is known to fail NNAPI preparation on the target board. The NPU spec tries Android NNAPI first and falls back to CPU/XNNPACK if NNAPI preparation fails. The on-screen backend label is authoritative:

- `Backend NNAPI`: NNAPI delegate was prepared.
- `Backend CPU`: inference is running on CPU/XNNPACK, either by spec request or NNAPI fallback.

Both the standard and NPU models are loaded and warmed up in parallel at startup. The loading spinner stays visible until both warmups finish (NNAPI compilation of the NPU model occupies the NPU driver that face detection also uses). The `MODEL: Standard / NPU` button is enabled once the NPU model finishes loading and switches the active model instantly.

Current NPU experiment status: the NPU-friendly INT8 export compiles on NNAPI on the target board, but vendor compilation takes about 165 seconds at startup (performed in the background). The standard full-INT8 export is kept on CPU to avoid the known `ANEURALNETWORKS_BAD_DATA ... while adding operation` delegate failure. NNAPI compilation caching must stay disabled because the board's VSI NPU driver fails compilation when caching is enabled.

## Camera calibration

Tap the invisible upper-left hotspot five times within two seconds to open camera calibration. Place exactly one face inside the guide and press `CONFIRM`. The app detects the face in synchronized RGB and IR frames, then writes the device-specific 64-byte `CalibConfig.dat`. Use `CANCEL` to exit without changing the saved calibration.

## Build

```powershell
./gradlew.bat :app:compileDebugJavaWithJavac
```
