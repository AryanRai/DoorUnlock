# Door 1.5 family dry run

This is the same Android application ID as the BLE test, with a Material 3 interface and Android 12+ wallpaper colors. It upgrades the BLE test without clearing enrollment or calibration. Dad's existing App Inventor application is a different package and has not been modified.

The four cards open floating sheets with native vector animations. Android 12+ background blur is requested when supported, with ordinary dimming as the fallback. Motion respects Android's disabled-animation setting. Face retains a smiling face and check on preview completion; BLE retains its phone and ripples; code has a rotating timer; the button shows an opening padlock.

## Implemented behavior

- BLE enrollment, calibration, start/stop, three-second checks and household slot sharing remain functional. Only an authenticated `WOULD UNLOCK` result produces the actual BLE test success state. Background notification/vibration, optional unlocked-screen floating cards, widgets, launcher shortcuts, Quick Settings tiles and supported Device controls are described in [BACKGROUND_TEST.md](BACKGROUND_TEST.md). Screen-off feedback uses notifications; overlays never cover the keyguard.
- TOTP accepts manually entered Google Authenticator codes and can generate six-digit, 30-second HMAC-SHA1 codes from an optional setup key in Android Keystore. It preserves leading zeros. Its key is separate from BLE enrollment and can be removed from the code settings.
- The request formatter matches the existing server's unquoted `Action: Test, TOTP:` format. This build does not send that request. Entering six digits previews the flow; it is not authentication success.
- Button and face are explicitly labelled animation previews. The camera service is not connected to this app yet.

## Existing app integration

The shared `DoorLockApp_Working-1.apk` uses MIT App Inventor. No `.aia` project was supplied. The shared ESP8266 source receives MQTT over TLS on port 8883, subscribes to `Home/Request`, validates a six-digit TOTP with a plus/minus one-step window, and publishes responses on `Home/Response`. `Action: Unlock` calls the door's `/unlock`; `Action: Test` checks `/update`. Door 1.5 uses INTERNET permission only for optional GitHub app updates; it has no MQTT transport or live-unlock path. It therefore does not yet replace Dad's working internet unlock function. Its purpose is to establish the combined native app and verify both code entry paths while preserving the dry-run constraint.

The existing 1.1 `Shared/PreparedForDad/DoorBLE-dryrun.apk` and Dad test ZIP remain the stable BLE test package. The new interface is packaged separately as `Shared/Preview/Door-preview.apk`; it is a newer version of the same BLE app, not an additional installed package. `prepare.py` routes preview APKs to the preview folder, and `deploy.py` continues to distribute the stable Dad test APK.

## Validation

Android APK build and lint pass. Host tests cover six RFC 6238 reference vectors, Base32 decoding, leading zeros, malformed codes and exact legacy Test payload formatting. USB instrumentation renders all four sheets and BLE setup on the Pixel 7, checks Android Keystore signing against a public reference key, and removes that test key. The redesigned app completed four authenticated BLE sessions with 12 responses in 30 seconds before the final visual refinements. Full household arrival and other phone manufacturers still require physical testing.
