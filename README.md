# Job Bangla Control - Android app

Android app for the Job Bangla admin panel (a Capacitor shell around the live
panel). For authorised operators only; signing in needs a Job Bangla admin account.

## Install
1. On your phone, open the **Releases** page of this repository and download `JobBangla-Control.apk`.
2. Open the file. If Android asks, allow "Install unknown apps" for your browser/files app.
3. Open **Job Bangla Control** and sign in.

## Build (developers)
JDK 17 + Android SDK, then `npm install`, `npx cap sync android`, and
`cd android && gradlew.bat assembleRelease`. Signing needs a local, uncommitted
`android/keystore.properties` + keystore.
