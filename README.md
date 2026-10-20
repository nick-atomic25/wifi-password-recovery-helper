# Wi-Fi Recovery Helper (Android) — v1.2

Native Java Android app for authorized Wi-Fi recovery assistance.

## What's in this version

- Nearby Wi-Fi network scan and selection, including latest scan results when Android permits. Scan refresh is asynchronous; Android can throttle scans.
- Displays the selected network's SSID, signal level, and security information when Android exposes it.
- Best-effort brand estimate based on SSID text (for example PLDT, Globe, Huawei, ZTE, TP-Link, ASUS, D-Link, Tenda or MikroTik). SSID text is not reliable proof of router manufacturer; unknown is a valid result.
- Candidate generator chooses a random WPA-Personal candidate length automatically between 8 and 63 characters. The user does not enter a length. Character sets and an optional known prefix can be selected.
- Generated candidate is copied into the verification field for convenience.
- One user-initiated WPA2-Personal candidate verification through Android 10+ `WifiNetworkSpecifier`; Android may require system approval. A successful connection callback triggers encrypted local saving of the candidate. A failure is not necessarily proof that the password is wrong.
- Cancel/release control for a pending connection request.
- AES-GCM encrypted local credential vault backed by Android Keystore; save, reveal and delete controls.
- Flat-style blue controls, clear section labels and lightweight icons.
- GitHub Actions workflow installs Gradle, builds the debug APK, verifies the APK exists, and uploads it as an artifact.

## Important limits

This app does **not** run an unattended or unlimited password-guessing loop. Generating a candidate locally does not test it. To verify a generated candidate, select the network, generate a candidate, then tap **Verify candidate with Android** and respond to any Android system prompt. This feature is intended for WPA2-Personal passphrases (8–63 characters) and Android 10+; support can vary by Android version and router configuration. WPA3, enterprise Wi-Fi, WEP, captive portal flows and older Android versions are not supported by this verification method. Network scan visibility depends on Android permissions, location settings and scan throttling.

## Build the APK using GitHub Actions

1. Download and extract this ZIP.
2. Open the repository: `https://github.com/nick-atomic25/wifi-password-recovery-helper`.
3. Upload/replace the project files at the repository root, including `app/`, `build.gradle`, `settings.gradle`, `gradle.properties`, `README.md`, and `.github/workflows/build-apk.yml`. Keep `.github` as a hidden folder at the repository root; do not add an extra nested project directory.
4. Open **Actions** → **Build Android APK** → **Run workflow**.
5. When the run succeeds, download the `WiFi-Recovery-Helper-debug-apk` artifact.
6. Extract the artifact ZIP on Android and install `app-debug.apk`. You may need to allow installs from your browser or file manager.

The project ZIP is source code, not a compiled APK. This environment does not have the Android SDK/Gradle installation needed to compile and run an Android build locally, so a successful GitHub Actions build is still required.
