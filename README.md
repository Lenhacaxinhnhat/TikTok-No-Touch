# HandSwipe — fixed build project

Android app that uses the front camera to detect vertical hand movement and sends global swipe gestures through AccessibilityService.

## What was fixed
- Removed the nonexistent `16.0.0-beta1` ML Kit dependency and use `com.google.mlkit:hand-detection:16.0.0`.
- Removed the missing `gradlew` requirement from GitHub Actions.
- GitHub Actions installs Gradle 8.13 explicitly.
- Android Gradle Plugin 8.13.2 + JDK 17.
- Workflow installs Android SDK 35/build-tools 35.0.0 and verifies the APK exists.
- Accessibility service is exported for Android system binding.
- Camera foreground service declarations are included for Android 14+.
- Camera processing is serialized to avoid piling up frames.

## GitHub
Upload the contents of this folder to the root of the repository. The repository must contain:

`.github/workflows/build-apk.yml`

Then open Actions → Build HandSwipe APK → Run workflow.

Artifact: `HandSwipe-debug-apk` → `app-debug.apk`.

## ADB

```bash
adb devices
adb install -r app-debug.apk
```

On the phone: grant Camera permission, enable HandSwipe in Accessibility, then return to the app and press Start.

For Xiaomi/HyperOS, if the service is stopped in the background, set HandSwipe battery usage to Unrestricted and allow Auto-start when that option exists.
