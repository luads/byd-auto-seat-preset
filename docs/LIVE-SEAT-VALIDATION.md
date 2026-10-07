# Capture and recall validation plan

The emulator proves storage and controller behavior. It cannot prove that an OEM getter reports actual coordinates, that a setter affects the intended seat, or that a real movement stops safely. Normal live recall remains disabled. The owner-triggered seat observation and one-axis supervised trial are documented in FIELD-TRIALS.md. Those experimental observations do not constitute validated automatic recall.

## Read-only contract work

Before any movement test, establish the supported local SDK/binder route on this firmware. Record the exact methods, permission requirements, axis IDs, coordinate units/ranges and result semantics. Getter discovery must exclude mutating methods. Use two manually selected native seat positions and confirm repeatable reads, including across an app/process restart. A getter returning zero or a method existing in another APK is not sufficient evidence.

Validate gear and parking-brake sources, event delivery and sample timestamps separately. Determine whether missing readings, suspension and restart invalidate the cache. The home panel observes settings changes and reported P for visibility only, with no continuous ADB polling. This does not establish fresh movement eligibility; renewed P must not replay a blocked recall.

## Supervised on-car recall

Use the owner-operated car, parked in P, with the agreed parking-brake evidence and clear space around the seat. Explicitly agree the test position and validated control path before writing. Start with one known, small change and read back the result. Check refusal outside eligibility, interruption/cancellation and partial failures before enabling a full preset. Determine whether read-back proves command acceptance or actual completion; do not label a moving seat as successfully recalled merely because a setter returns success.

Capture must save source, coordinate format and capture time with the position. Confirm replacement only on the selected driver's preset. Recall must reject incompatible metadata, non-finite or unsupported coordinates, unknown/stale eligibility and an unvalidated adapter. Validated automatic app/panel recalls must use that same controller. Current live panel taps open the supervised test screen, not a background command. Startup, widget creation, resizing, rename, update and return to P must never move the seat.

## Release gates

- Two distinct captured positions persist across force-stop, app update and head-unit restart without any automatic recall.
- Names are preferences; new installs use Driver 1 and Driver 2. Favourite mappings remain distinct after renaming or changing selection.
- Home-only reported-P display works with camera, CarPlay, dialogs and split-screen transitions, and taps disable before the fade-out finishes.
- Observer disconnection, missing telemetry and screen-off remove the panel. CPU/12V behavior is measured on this firmware rather than inferred from emulator results.
- Stable signing and public-update integrity are verified before preserving real presets. Backups must retain compatibility metadata; incompatible firmware/data must not silently apply old coordinates.

No actual seat write, on-car capture or parked battery measurement has been performed by this offline iteration.
