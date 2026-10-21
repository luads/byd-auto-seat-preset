# Seat Presets 0.1.14 · pre-alpha

Upgrade in place with the live APK. The public release signing key and package ID are unchanged; saved presets, driver names and preferences are retained. The demo APK is a separate app for emulator use.

## Settings and home panel

- Settings now uses Compose Material 3 with the dark matte palette, larger touch rows, spaced actions and responsive one/two-column layout. Seat movement stays below Drivers. Back returns to Presets.
- Two favourite drivers, renaming and child-lock mapping remain direct. The movement opt-in remains visible and persistent.
- Old supervised, child-input and replay trial screens are removed. Standard Android widget setup is removed and the live widget receiver is disabled. The separate floating home panel remains.
- Developer tools group Diagnostics and Connection. Backup controls appear only when a compatible file picker exists. Setup logs open on demand.

## Readings and private diagnostics

- New explicit **Set up vehicle readings** action grants and verifies only this app's COMMON/GET permissions. This includes the GEARBOX_COMMON factory permission needed alongside GEARBOX_GET for the parking-brake getter. Missing permissions are reported independently. No seat-write permission is granted.
- **Inspect saved positions** compares stored data, current observations and candidate plans without sending a command. Separate bodywork getter candidates retain their raw values and unverified units.
- Private traces record capture attempts, persisted readback, request origin and timing, raw P/parking-brake results, failure chains and child-lock baseline/changed states. Planned dispatch, setter return and physical completion remain distinct. The private viewer shows the complete retained report, capped at 128,000 characters.
- Seat data stays out of setup logs and public/automatic delivery. Pending memory cannot survive process death. Telegram integration is not included in this APK; a separate Lab handoff covers owner-approved log-only delivery.

## Seat movement status

**Live seat commands remain blocked.** A parked recall produced unintended travel. Inspection of the owner's SDK shows that merely changing to the named bodywork setters reaches the same integer command properties. OverDrive's float-array geometry contract is not validated on this firmware and is not substituted here. Existing saved observations are retained without conversion.

The Allow seat movement switch saves the owner opt-in but does not override this hold. No startup, update, panel, widget or child-lock event can bypass it. No automatic phone-identity application is enabled.

## Validation

105 JVM tests passed in each demo/live debug/release variant, with zero failures or errors. All four lint variants passed with zero errors, and signed demo/live release builds passed. Emulator checks cover real Android persistence, driver renaming, favourite swaps, child mappings, movement opt-in, confirmation dialogs, scroll retention, Settings Back and narrow-window recreation. Emulator checks do not validate DiLink home-window behavior or OEM getters.

Still needs a car test: read-permission setup, private inspection and captures compared against native adjustments, Compose rendering on DiLink, and overnight startup. A verified control payload and owner-operated parked validation are still required before live recall can be enabled.
