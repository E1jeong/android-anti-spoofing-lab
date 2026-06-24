# UBio Anti-Spoofing Viewer

Minimal UBio-N Face Pro Android shell for RGB/IR anti-spoofing model evaluation.

## Required local inputs

1. Put the model at `app/src/main/assets/anti_spoofing.tflite`.
2. Update `app/src/main/assets/model_spec.json` to match the training preprocessing contract.
3. Supply the private Maven repository URL, FaceMe license, and platform-signing values through user-level Gradle properties or the ignored root `local.properties` file. Do not commit them.
4. Ensure `/sdcard/devlocal/CalibConfig.dat` exists on the device.

Required Gradle property names:

```properties
UBIO_MAVEN_URL=...
FACEME_LICENSE_KEY=...
UBIO_KEYSTORE_PATH=...
UBIO_KEY_ALIAS=...
UBIO_KEY_PASSWORD=...
UBIO_STORE_PASSWORD=...
```

`UBIO_MAVEN_URL` must point to a repository containing the proprietary FaceMe dependency. Without repository access, the FaceMe license, and the target hardware inputs, a public checkout cannot perform a complete device build or runtime test.

## Build

```powershell
./gradlew.bat :app:compileDebugJavaWithJavac
```

This project intentionally reuses `com.virditech.ac7000` and cannot coexist with the production application.
