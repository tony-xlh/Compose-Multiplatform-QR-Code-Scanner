# Compose Multiplatform QR Code Scanner

A Compose Multiplatform QR Code Scanning demo using [Dynamsoft Barcode Reader](https://www.dynamsoft.com/barcode-reader/overview/) v11 (`CaptureVisionRouter`).

Demo video:

https://github.com/user-attachments/assets/ada83322-7a1a-46fc-bd60-a2a838815806

## License

You can apply for a license [here](https://www.dynamsoft.com/customer/license/trialLicense/?product=dcv&package=cross-platform)

## Run the App from the Command Line

### Android

Connect an Android device with USB debugging enabled, then build, install and launch the app:

```bash
# Build the debug APK (use gradlew.bat on Windows)
./gradlew :composeApp:assembleDebug

# Install it on the connected device
adb install -r composeApp/build/outputs/apk/debug/composeApp-debug.apk

# Launch the app (optional)
adb shell am start -n org.example.project/.MainActivity
```

If `adb` is not in your `PATH`, it is located at `<Android SDK>/platform-tools/adb` (by default `%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe` on Windows).

### iOS (macOS only)

The iOS SDK (`DynamsoftCaptureVisionBundle.xcframework`) is vendored under `iosApp/SDK`. Install the pods and open the workspace:

```bash
cd iosApp
pod install
open iosApp.xcworkspace
```

Then run the `iosApp` scheme on a device or simulator. You can also build from the command line:

```bash
xcodebuild -workspace iosApp.xcworkspace -scheme iosApp -destination 'generic/platform=iOS' build
```

## Kotlin Multiplatform

This is a Kotlin Multiplatform project targeting Android, iOS.

* `/composeApp` is for code that will be shared across your Compose Multiplatform applications.
  It contains several subfolders:
  - `commonMain` is for code that’s common for all targets.
  - Other folders are for Kotlin code that will be compiled for only the platform indicated in the folder name.
    For example, if you want to use Apple’s CoreCrypto for the iOS part of your Kotlin app,
    `iosMain` would be the right folder for such calls.

* `/iosApp` contains iOS applications. Even if you’re sharing your UI with Compose Multiplatform, 
  you need this entry point for your iOS app. This is also where you should add SwiftUI code for your project.


Learn more about [Kotlin Multiplatform](https://www.jetbrains.com/help/kotlin-multiplatform-dev/get-started.html)…

## References

[EasyQRScan](https://github.com/kalinjul/EasyQRScan/)


