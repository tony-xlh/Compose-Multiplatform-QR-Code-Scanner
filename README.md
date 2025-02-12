# Compose Multiplatform QR Code Scanner

A Compose Multiplatform QR Code Scanning demo using [Dynamsoft Barcode Reader](https://www.dynamsoft.com/barcode-reader/overview/).

Demo video:

https://github.com/user-attachments/assets/ada83322-7a1a-46fc-bd60-a2a838815806

## License

You can apply for a license [here](https://www.dynamsoft.com/customer/license/trialLicense/?product=dcv&package=cross-platform)

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
