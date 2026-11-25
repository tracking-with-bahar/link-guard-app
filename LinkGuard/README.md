# LinkGuard (Android / Kotlin)

A personal Android link-interception starter project. It registers as an HTTP/HTTPS handler so Android can offer it as the default browser. When a supported external link is routed to LinkGuard, it displays a confirmation screen with **Open in Chrome** and **Cancel**.

## Build

1. Install Android Studio (current stable) and Android SDK Platform 35.
2. Open this `LinkGuard` folder in Android Studio.
3. Allow Gradle sync.
4. Build > Build Bundle(s) / APK(s) > Build APK(s).
5. Install the resulting debug APK on your Realme phone.

## Activate

On the phone: Settings > Apps > Default apps > Browser app > LinkGuard. The exact menu names vary by Realme UI version.

You can still open Chrome directly from its app icon. LinkGuard only receives links that Android routes to it as the selected browser.

## Current safety scope

- Local heuristics only: HTTPS, URL user-info, IP-host, punycode, known URL shorteners, unusual ports, suspicious lure words and very long URLs.
- No network reputation lookup is included in this starter version. A clean result is **not** a safety guarantee.
- Links opened inside an app's own embedded browser may bypass the Android default-browser handler.
- The app does not request Internet, contacts, SMS, accessibility, VPN or device-admin permissions.
- History is stored locally in app preferences (up to 30 entries); it can be cleared from the home screen.

## Next production steps

Add an opt-in reputation provider through a privacy-preserving backend (do not embed a private API key in the APK), test Android App Links and Realme-specific intent handling, and run device tests across WhatsApp, Messenger, SMS and other apps. This is source code, not a signed production APK.

## Build APK in GitHub Actions (no Android Studio on your PC)

1. Create a GitHub repository and upload the contents of this project folder to it (including `.github/workflows/android.yml`).
2. Open the repository's **Actions** tab.
3. If GitHub asks, enable Actions for the repository.
4. Select **Build LinkGuard APK** from the workflow list and press **Run workflow**. (A push to `main` or `master` also triggers a build.)
5. When the workflow finishes successfully, open the run, scroll to **Artifacts**, and download **LinkGuard-debug-apk**.
6. Extract the downloaded artifact ZIP and install the APK on your Realme phone. If Android prompts, allow installing from that source.

This workflow builds a debug APK. It does not publish to the Play Store. The build is performed on GitHub's hosted runner, so Android Studio/SDK need not be installed locally.

## Mandatory PIN verification (v1.1)

- First launch: create and confirm a 6-digit LinkGuard PIN.
- Every intercepted HTTP/HTTPS link requires the PIN, regardless of the local URL risk result.
- A correct PIN is required before LinkGuard launches Chrome.
- Five incorrect attempts trigger a 30-second temporary lockout.
- PINs are stored as salted PBKDF2-HMAC-SHA256 hashes, not plaintext.
- The PIN is separate from banking, payment, OTP, and device-unlock credentials.

This is a local PIN gate, not a guarantee that every app's internal browser can be intercepted. It also does not replace Android's device lock or a live phishing reputation service.
