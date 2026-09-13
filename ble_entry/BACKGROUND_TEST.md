# Background BLE dry run, version 1.5

This build is for daily-use testing. Door operation and TOTP/MQTT delivery remain disconnected. Face and button entry points retain their animation previews. Internet access is used only for optional GitHub app updates; there is no actuator implementation. See UPDATES.md for the update flow.

## Start once

1. Keep the existing phone enrollment and calibration. Open **Background, shortcuts & debug**.
2. Enable notifications. Turn on **Floating cards over other apps**, then use **Allow floating cards** to grant Android's overlay permission.
3. Use **Allow reliable background use** to request exemption from battery optimization. This is optional but matters when testing with the screen off or in Doze.
4. Tap **Start BLE test**. It stays enabled until **Stop**; the old four-hour stop has been removed. Stop also disables resuming after reboot and app updates.

On an unlocked phone with another app open, an authenticated near-signal observation starts the verifying card and an approach notification. Three consecutive near probes still precede the signed entry request. A signed `WOULD_UNLOCK` response produces the success card and result vibration. Interrupted/unstable checks show an entry-held result. A disconnect never rearms the phone. Move away to rearm, and retain the 15-second acceptance cooldown.

With the screen off or keyguard locked, the app uses a lock-screen notification and vibration. Android controls whether the display lights up and how that notification is presented. It does not use alarm/call full-screen intents, dismiss the keyguard, or expose TOTP codes on the lock screen.

On an unlocked screen, the floating card slides up from the bottom with space above system navigation, keeping the top notification banner separate.

## Proximity cards

- **Close by:** appears after stable authenticated samples enter the nearby zone. The **−** button shrinks it to a bottom-corner chip; tap that chip to expand it. Repeated readings do not reopen it. **×** dismisses that state until a new transition.
- **Out of range:** appears after leaving the nearby zone, or after 25 seconds without an authenticated probe following nearby presence. It expands for four seconds, minimizes, then disappears at ten seconds. The subtitle distinguishes weak signal from signal loss; these are proximity estimates, not measured distances.
- **Verifying → Would unlock:** still requires the existing proximity/authentication checks and signed board result. Success stays expanded for four seconds, then minimizes and disappears at ten seconds. It never reports a real unlock in this build.

The display-only nearby zone starts 14 dBm below the calibrated unlock threshold and ends 20 dBm below it, with multiple observations and hysteresis. Routine connection-slot gaps do not immediately produce departures. This changes neither the unlock threshold nor the separate three-second away/rearm requirement. Nearby/departure notifications are quiet; verification and result alerts retain their vibration settings. No overlay appears while locked.

## Access points

- The home-screen widget opens button, code, nearby and face panels.
- Four Quick Settings tiles open the corresponding panels. Add them from the in-app chooser or Quick Settings edit screen.
- Four launcher shortcuts are available by long-pressing Door; the in-app chooser can request a pinned shortcut.
- Android 11+ Device controls exposes the four panels where the phone supports that lock-screen entry point. They are app-opening controls, not operational lock toggles. Android/OEM software determines which shortcuts can be assigned to the lock screen.

## Background behavior

The opted-in foreground service returns START_STICKY, resumes after BOOT_COMPLETED (normal first unlock) and package replacement, and waits for Bluetooth to be re-enabled. Permission removal requires granting it again. Android force-stop always suspends an app until the user opens it again.

Normal sessions keep the three-second sampling budget and one-to-three-second stagger. CPU wake locks are bounded to 25 seconds and renewed during active checking. When the board cannot be found, a service-filtered PendingIntent scan replaces active polling and releases the CPU wake lock. Continuous presence within radio range still costs battery; overnight drain, Doze and vendor-specific battery behavior must be measured before treating this as a finished production release.

Enrollment, NEAR/AWAY collection, calibration, Stop, Android settings, history and a key-free copyable debug report remain available. Phone armed/cooldown state is retained across service restarts. Feedback rate limiting and 15-second freshness prevent repeated or stale approach cards.

## Verified on the local Pixel 7 and ESP32-S3, 13 September 2026

Version 1.4 additionally passed host tests for stable proximity transitions, hysteresis, stale samples, ordinary session gaps and signal loss. Phone instrumentation checked manual minimize/expand, repeated-state suppression, success auto-minimize, departure auto-hide and a new arrival reopening the card. Those UI checks used labelled fixtures and preserved enrollment. The 1.4 screen-off regression recorded five authenticated responses and passed passive rediscovery and locked-overlay suppression. Physical distance tuning for the new nearby zone remains an approach-test item.

- Build and Android lint passed (zero errors; existing resource/localization warnings remain).
- Background run recorded nine authenticated board responses; screen-off run recorded five. Both also rediscovered and authenticated through a filtered PendingIntent low-power scan.
- Notifications posted while backgrounded and screen off. Optional verifying/result overlays rendered and dismissed on the unlocked phone, and were absent with the screen off. The presentation checks used explicitly labelled UI fixtures, not simulated BLE acceptance.
- Four PendingIntent method destinations, launcher shortcut registration, widget layout inflation and Device controls entries passed on the phone. Actual launcher/Quick Settings placement and lock-screen OEM presentation remain user setup checks.
- Existing enrollment and proximity threshold were preserved. The test harness does not relax the threshold to manufacture successful approaches.
- Stop stayed disabled through explicit resume and simulated boot-receiver callbacks; an explicit Start restored checking. This checks the opt-in behavior, not a physical phone reboot.
- Battery exemption was not enabled during these brief tests. Overnight Doze, battery drain, a physical phone reboot, repeated physical approaches and six-to-eight-phone contention have not been validated in this build.

## Your next physical test

If an approach produces no card, open **Background, shortcuts & debug → Test background popup**. This puts Door in the background and posts a labelled presentation test; it does not request entry or reset the away requirement. The debug report records the feedback destination and overlay errors. History now records **Away confirmed** when the phone rearms. With the current calibration, rearming requires three seconds of authenticated signal below the close threshold minus 8 dBm within a session; disconnecting alone does not rearm.

With the S3 powered and the phone enrolled, move away, leave Door in the background and approach. Check verifying feedback followed by `WOULD UNLOCK`. Repeat with screen off, after Bluetooth off/on, after reopening the app, and after a phone reboot/first unlock. Holding the phone near the board should not repeatedly accept. Record misses, false accepts, latency, notification delivery and battery use. Test the actual mounted door and inside/outside positions separately before connecting an actuator.
