# Seat Presets

Pre-alpha Android app for two favourite driving presets on BYD DiLink 5.
Dark matte cards, editable driver names, preset backups and an optional home panel.

**Real independent seat loading is enabled, experimental.** Enable **Allow seat movement**, park in P with the parking brake applied, and tap a saved card to load it. Captures made with 0.1.18 remain valid; older captures need recording once. Legacy coordinates are preserved but cannot silently become targets for the new loader. Names remain local preferences; new installs use Driver 1 and Driver 2.

Loading uses native press/release signals and typed position feedback for fore/aft, backrest and height. Coarse-to-fine loading uses nominal 100–300 ms holds, with a target reserve and conservative observed-rate limit. Each hold is released before reading feedback. Developer tools exposes a persistent **Minimum command pause** selector under Seat movement: 0/100/200/300/500/750/1000 ms, default 500 ms. It applies to the next load and sets the minimum release-to-next-command interval, including feedback read time. Waiting remains cancellable and rechecks P/brake; changed coordinates during an added wait stop the load. Unchanged feedback still stops without a blind retry. This spacing is experimental, not a proven fix for missed commands. The loader still stops between pulses; it does not provide continuous movement. A load stops on cancellation, opt-out, P/brake loss, closed control surface, stale/unchanged feedback, movement away from target, another seat axis changing, overshoot or fixed signal/time limits. There is no automatic reverse correction or persisted/replayed movement. The duration does not guarantee physical travel or a hardware stop. Keep native controls ready. The owner reports the short trials and eventual preset load worked. Retained logs show one complete three-axis match after four unchanged-feedback failures; this is not a validated travel or stopping guarantee.

The app also records available left/right mirror X/Y readings. **Preset loading does not move mirrors.** Optional confirmed mirror tilt trials are now available, separately from read-only comparisons. Their release=0 candidate still needs physical confirmation. Independent presets do not overwrite the native app's memory slots. NFC identity remains unproven; startup and updates never recall a preset.

See [the parked feedback test](docs/SEAT-FEEDBACK-PROBE.md) for the next car session.

Developer tools > **Seat feedback** records a foreground 10-second read-only comparison of fixed seat getters from both SDK sources against typed ADB cache reads and the optional public Car property API. The latter is tried without changing permissions and may be unavailable on this firmware. Select one axis, start recording in P with the parking brake applied, and use the physical seat controls. Mark physical movement/release for later log review; marker times are reports, not measured physical stop times. Values, per-call timing, cache timestamps and failures use the private Lab report. A changing/default SDK value cannot enable actuation. Continuous-control simulation is demo-only; live continuous movement stays locked until feedback and stopping behavior are physically verified. The probe has no permission grant, preset save or vehicle-command path. Stop/leave cancels acceptance; an outstanding read must return before another recording can start.

Developer tools > **Seat trials** provides one-axis seat pulses (100/200/300 ms) and eight mirror tilt choices (100 ms), with confirmation, readback and intervention markers, a typed seven-field read, and comparisons of mirror getters across both SDK sources. The old percentage and unsupported generic-float movement trials are retired. **Set up seat access** explicitly requests missing native control access; read-only setup remains read-only.

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

Hold a preset card to save the current driver's seat position while in P. Tap a saved card to load its independent seat coordinates. No native memory slots or calibration loop are used.

The live app uses a floating home panel. Standard Android widget setup and the old trial screens are removed.
The optional home panel stays visible on home while P is reported, without a preview timeout.
It hides when another window opens, the screen turns off or gear becomes non-P/unknown.
Panel taps and opt-in child-lock doubles use the same recall controller as the app. Stop is available in the app and panel while loading; another recognized child-lock double cancels an active load. File backup is a developer
tool on supported devices. Sharing was removed because the car has no usable share targets.
Presets and keys stay private on the device; exported preset backups contain personal data.

Developer tools has **Set up vehicle readings**, **Inspect saved positions**, **Seat trials** and **View private report**.
New captures retain per-axis source and sample/cache timestamps; none establishes verified ECU freshness. Legacy coordinates are preserved. SDK signatures are kept in a separate bounded private inventory and included only in explicit diagnostic transfer.
The private report records capture attempts, persisted save readback, request origins, raw P/parking-brake
checks, child-lock state changes and failure causes. It is capped at 180,000 characters and is separate
from setup logs. Early pending memory cannot survive process death. Developer tools also has **Trusted ADB Lab certificate** and **Send diagnostics to ADB Lab**.
Set the trusted Lab signing certificate SHA-256 from your trusted build first. It is blank by default.
The installed Lab certificate is checked on every transfer. The owner confirms private seat readings,
preset IDs, timing and failures before a bounded JSON snapshot is passed to Lab. Lab shows another
preview and confirmation before any Telegram upload. No preset backup or setup log is included. The log-only payload drops whole lines with sensitive identity/credential labels, VIN-shaped values, MAC addresses or key material, and reports the number omitted.
This action uses a separate process-owned export worker, so a stalled OEM inspection cannot queue the export behind it. Only one preparation runs at a time, including across Activity recreation. Leaving Settings cancels the eventual handoff. This action does not read the vehicle or change movement settings. The separate credential-protected,
non-exported diagnostic provider grants one read of one random snapshot, unlinks/revokes it after open,
and rejects expired snapshots after five minutes when next accessed. Temporary copies are also removed
when clearing the private report. Only live Seat Presets supports this transfer.
Flush failures, buffer drops and export byte truncation are warnings, not successful complete captures.
Earlier rotated history and pending memory lost on process death cannot be recovered.
The filter does not recognize arbitrary unlabelled personal text in old free-form errors; review the private preview before sending.

## Development

OpenJDK 21 and Android SDK 34:

```sh
./gradlew test assembleDemoDebug assembleLiveDebug lintDemoDebug lintLiveDebug
```

Public releases use a private stable keystore, outside the repo. See scripts/release.sh.
See [startup safeguards](docs/STARTUP.md), [home integration](docs/HOME-INTEGRATION.md),
[vehicle validation](docs/LIVE-SEAT-VALIDATION.md) and [validation notes](docs/VALIDATION.md).

See [parked trial instructions](docs/FIELD-TRIALS.md) before testing movement.

The optional single-pulse trial can save an owner-confirmed numeric direction per axis and firmware. Stale, flat, coupled or unusually large changes are not accepted as direction evidence. This changes no preset coordinates, and the loader never learns/reverses direction automatically after an unexpected step.
