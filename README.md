# Seat Presets

Pre-alpha Android app for two favourite driving presets on BYD DiLink 5.
Dark matte cards, editable driver names, preset backups and an optional home panel.

**Diagnostic build: live seat writes are paused.** A parked recall produced unintended travel.
The app can open and record positions, but card, panel, child-lock and editor requests cannot
send seat commands. Existing presets remain observations with unverified units, not validated
movement targets. Developer mode includes a private read-only saved-position inspection and an explicit vehicle-read permission setup, including the parking-brake factory permission.
Nothing recalls a preset on startup or update. NFC identity remains unproven.
New installs use generic Driver 1 and Driver 2 labels; names are local preferences.

## Install

Download the **live** APK from [Releases](https://github.com/luads/byd-auto-seat-preset/releases)
and install it through the car's file manager or ADB. The **demo** APK is for emulator/UI exploration.
These are separate apps. Existing debug builds use a different signing key: export presets before
uninstalling once and installing the public build. Import the backup afterwards. Later public
updates keep the same signing key and preserve data.

## Setup and updates

Rename drivers in Settings. Enable the app in BYD's autostart manager.
After home access setup, optionally enable Restore setup connection after restart.
The optional home panel requires one-time home access setup. ADB must already be enabled through
your trusted setup route; approve Android's debugging prompt if shown.

Check updates in Settings. APKs are checked for checksum, package, newer version and matching
signature. **Install updates automatically** is off by default and uses the app's local ADB access.
Without that access, use the Android installer option. Checks run while the app is naturally alive;
there is no always-on update worker, VPN, wake lock or wakeup alarm.

Hold a preset card to save the current driver's seat position while in P. Live loading remains paused until the setter contract is verified. No calibration loop.

The live app uses a floating home panel. Standard Android widget setup and the old trial screens are removed.
The optional home panel stays visible on home while P is reported, without a preview timeout.
It hides when another window opens, the screen turns off or gear becomes non-P/unknown.
Panel taps and opt-in child-lock doubles use the same recall controller as the app. File backup is a developer
tool on supported devices. Sharing was removed because the car has no usable share targets.
Presets and keys stay private on the device; exported preset backups contain personal data.

Developer tools has **Set up vehicle readings**, **Inspect saved positions** and **View private report**.
The private report records capture attempts, persisted save readback, request origins, raw P/parking-brake
checks, child-lock state changes and failure causes. It is capped at 128,000 characters and is separate
from setup logs. Early pending memory cannot survive process death. Telegram transfer is not yet
implemented; a separate ADB Lab integration handoff describes an explicit owner-approved log-only path.

## Development

OpenJDK 21 and Android SDK 34:

```sh
./gradlew test assembleDemoDebug assembleLiveDebug lintDemoDebug lintLiveDebug
```

Public releases use a private stable keystore, outside the repo. See scripts/release.sh.
See [startup safeguards](docs/STARTUP.md), [home integration](docs/HOME-INTEGRATION.md),
[vehicle validation](docs/LIVE-SEAT-VALIDATION.md) and [validation notes](docs/VALIDATION.md).

See [parked trial instructions](docs/FIELD-TRIALS.md) before testing movement.
