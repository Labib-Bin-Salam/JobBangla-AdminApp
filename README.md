# Job Bangla Control - Android app (Capacitor)

Standalone Capacitor 7 project: a native Android shell around the live admin
panel `https://admin.jobbangla.bd` (`server.url` in `capacitor.config.json`).
There is no web bundle to rebuild - shipping a panel update (`Website/scripts/deploy-frontend.sh`)
updates the app instantly. App id `bd.jobbangla.admin` (separate from the public
app `bd.jobbangla.mobile` in `../android/`).

Navigation is locked to `admin.jobbangla.bd`; other links open outside the app.
Permissions: internet, microphone + camera (team calls), notifications.

## Prerequisites
JDK 17, Android SDK (platform 35+, set `android/local.properties` -> `sdk.dir`), Node 20+.

## Commands (from this folder)
| Command | Does |
| --- | --- |
| `npm install` | install deps |
| `npm run sync` | copy `www/` + config into `android/` |
| `npm run open` | open `android/` in Android Studio |
| `npm run apk:debug` | `android/app/build/outputs/apk/debug/app-debug.apk` |
| `npm run bundle:release` | `.aab` for Play (create `android/keystore.properties` + a keystore first) |

## Push notifications
Team chat messages and team calls are pushed to the phone through Firebase Cloud Messaging.
1. Firebase console -> create/select a project -> add an Android app with package `bd.jobbangla.admin`
   -> download `google-services.json` into `android/app/` (without it the build still works, push just stays off).
2. Firebase console -> Project settings -> Service accounts -> Generate new private key, then set the server env var
   `FCM_SERVICE_ACCOUNT_FILE` (absolute path to that JSON on the host) or `FCM_SERVICE_ACCOUNT_JSON` (its contents)
   on the `jobbangla-api` Node.js app. Never commit that key.
3. `npm run sync`, rebuild the APK. On first sign in the app asks for the notification permission and registers
   its token (`POST /admin/push/register`); signing out removes it. Server code: `Website/backend/src/lib/push.js`.

## Quick unlock (fingerprint / face / phone PIN)
After a normal sign in the app offers to remember the phone. From then on, opening the app (or coming back after the
idle timeout) shows the phone's own prompt instead of the password + authenticator code. A deliberate **Log out**
forgets the phone; so do suspending the account, resetting its 2FA, or revoking its session in Security Center.
The device token is stored in the Android Keystore (`@aparajita/capacitor-secure-storage`) and released by
`@aparajita/capacitor-biometric-auth`. Server: `Website/backend/src/routes/admin-device.routes.js`
(table `admin_trusted_devices`, 60 days sliding, max 5 phones per operator). Needs a phone with a screen lock set.

## Notes
- Launcher icons are copied from the public app; replace `assets/` and the
  `mipmap-*` folders to give the admin app its own icon.
- Do not commit `node_modules/`, `android/build/`, `android/.gradle/`,
  `android/local.properties`, `*.jks`, `keystore.properties`.
