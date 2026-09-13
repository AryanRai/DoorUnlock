# Optional Android updates from GitHub

The public feed is `https://github.com/AryanRai/DoorUnlock/releases/latest/download/door-update.json`. Only **published app releases** with this asset should be marked latest. Firmware releases should use a different repository or be marked not latest. Drafts are invisible to phones; publishing a Git commit by itself does not offer an APK.

Door 1.5 checks this feed on foreground resume, no more than once per six hours. The prompt has Update, Later (one day), and Skip this version. **App updates** supports a manual check, disabling automatic checks, viewing releases and installing a previously downloaded update. Failed automatic checks stay quiet; manual checks show errors. There is no mandatory update, background forced installation, or login/token on the phone.

The manifest has schema, packageName, versionCode, versionName, minSdk, apkUrl, sha256, bytes and notes. Downloads start only after Update is selected. HTTPS redirects are limited to GitHub's release hosts, sizes are bounded, and the APK's hash, package, strictly newer version and signing identity are checked before it is shared with Android's installer through a narrow FileProvider. The installer validates it again and asks the user to install. If Android asks, allow installs from Door and return to the app. A cancelled update leaves BLE setup intact. A download interrupted by process termination can be retried; downloaded verified updates can be installed later while offline.

Internet permission is now present for these update checks/downloads. The app still contains no network transport to operate the door or send TOTP. Bluetooth operation does not require internet.

## Signing identity

The family release key is the **same certificate** as the existing locally installed test APKs, copied into an encrypted private PKCS12 store. Certificate SHA-256:

`3fa6d69470a1db86e4bf140294ba57d6a3c04cf6b3921e7d6f82ef72a0429275`

`Shared/Signing/door-family.p12` and `ble_entry/android/signing.properties` are ignored. Back up both privately together. Do not publish them, put them in release ZIPs, or regenerate the signing identity. Release builds are non-debuggable and keep the same application ID `home.doorble`, so Android upgrades preserve Keystore keys and app data. A mismatched signing key cannot update existing phones without removing their setup.

On another build machine, obtain the private key and create ignored `android/signing.properties` with `storeFile`, `storePassword`, `keyAlias`, and `keyPassword`. Use a forward-slash absolute path for storeFile. Missing signing properties fail the release build.

## Publish the next app update

1. Change the default versionCode and versionName in `android/app/build.gradle`. Increment versionCode on every public APK; use a numeric version name such as 1.6. Add release notes under `release-notes`.
2. Build/test the change. Commit the app, publisher and release notes, then push the source commit to GitHub. Never add the signing files or Shared directory.
3. From the repository root, with JDK 17+ and Android SDK 36 configured, run:

   `pwsh -File ble_entry/publish_app.ps1 -NotesFile ble_entry/release-notes/1.6.md -Publish`

   The script locates installed Gradle 9.3.1, or accepts `-GradlePath`. It builds/lints a release, verifies its certificate, prepares the APK/manifest under ignored Shared/AppReleases, creates a draft release against HEAD and publishes it as latest. Without `-Publish`, it only prepares local artifacts. Existing tags/assets are never overwritten.
4. Open an older updater-enabled app and use App updates → Check for updates now. Select Update and confirm installation in Android. Verify enrollment remains.

The initial 1.5 installation must be shared manually with phones running 1.4 or older; those builds have no updater. Keep Dad's existing TOTP application until real internet entry is integrated.
