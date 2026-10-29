# Capture and recall validation plan

Current status: 0.1.18 enables the owner-requested native-signal feedback loader, with explicit movement opt-in and reported P/EPB checks. The old percentage route remains retired. One driver-backrest forward/release pulse has physical confirmation; complete loading, reverse and other axes remain experimental. Independent captures need the new typed format. Mirror feedback can be recorded and compared read-only; no mirror movement is sent. See SEAT-RECALL-CONTRACT.md and BACKREST-TEST.md.

The emulator checks storage and controller behavior. It cannot prove that an OEM getter reports actual coordinates, that a setter affects the intended seat, or that a real movement stops safely.

## Read-only contract work

Before any movement test, establish the supported local SDK/binder route on this firmware. Record the exact methods, permission requirements, axis IDs, coordinate units/ranges and result semantics. Getter discovery must exclude mutating methods. Use two manually selected native seat positions and confirm repeatable reads, including across an app/process restart. A getter returning zero or a method existing in another APK is not sufficient evidence.

Validate gear and parking-brake sources, event delivery and sample timestamps separately. Determine whether missing readings, suspension and restart invalidate the cache. The home panel observes settings changes and reported P for visibility only, with no continuous ADB polling. This does not establish fresh movement eligibility; renewed P must not replay a blocked recall.

## Supervised on-car recall

Use the owner-operated car, parked in P, with the agreed parking-brake evidence and clear space around the seat. Explicitly agree the test position and validated control path before writing. Start with one known, small change and read back the result. Check refusal outside eligibility, interruption/cancellation and partial failures before enabling a full preset. Determine whether read-back proves command acceptance or actual completion; do not label a moving seat as successfully recalled merely because a setter returns success.

Capture must save source, coordinate format and capture time with the position. Confirm replacement only on the selected driver's preset. Recall must reject incompatible metadata, non-finite or unsupported coordinates, unknown/stale eligibility and an unvalidated adapter. Validated automatic app/panel recalls must use that same controller. Current app, panel and child-lock requests share the controller, with native feedback loading enabled behind the movement preference; retired absolute writers remain blocked. Startup, widget creation, resizing, rename, update and return to P must never move the seat.

## Release gates

- Two distinct captured positions persist across force-stop, app update and head-unit restart without any automatic recall.
- Names are preferences; new installs use Driver 1 and Driver 2. Favourite mappings remain distinct after renaming or changing selection.
- Home-only reported-P display works with camera, CarPlay, dialogs and split-screen transitions, and taps disable before the fade-out finishes.
- Observer disconnection, missing telemetry and screen-off remove the panel. CPU/12V behavior is measured on this firmware rather than inferred from emulator results.
- Stable signing and public-update integrity are verified before preserving real presets. Backups must retain compatibility metadata; incompatible firmware/data must not silently apply old coordinates.

Version 0.1.14 validation is offline/emulator only. The earlier owner-operated recall is incident evidence, not a successful control-contract validation. No parked battery measurement has been established.
