# Parked tests, 0.1.11

## Record a preset

Park in P. Set the driver's seat with the native controls. Hold the preset card, then Save. This replaces only that preset's stored coordinates and keeps its favourite assignment. The app checks reported P and compares two reads 200 ms apart. Missing, changing, non-finite, all-zero or out-of-candidate-range data cannot be saved. No movement is sent. Units and cache freshness remain unverified; recordings are not phone identity.

## Home panel

Settings → Home panel → Set up, if access is missing. Enable Show panel on home in P. Return to the actual launcher home screen. The panel has no 30-second timeout. It hides on other window identities, screen off, disabled access or non-P/unknown gear. Gear comes from Settings.Global sys.share.gpack.agent.data → gearMode; only the field is extracted, never the VIN-containing row. A ContentObserver updates visibility on changes, with no polling or wake locks. Reported P authorizes display only, not movement. Cached P after sleep is still a field limitation.

Check home → global menu → home, app switching, reverse camera, screen-off/wake and P → R/N/D → P. If the launcher does not emit distinct window events for its menu, that case needs further launcher evidence. Panel tiles open the selected saved-position test in the app; they do not send background commands.

## Child-lock shortcuts

Settings → Child-lock shortcuts. Assign Left double tap and Right double tap to presets, or Off. Test shortcuts arms a 10-second foreground window. Wait for READY in P. The exact getter is getDoorLockStatus(int), areas 6 left and 7 right, valid raw states 1/2. Startup samples, unknown gear, invalid values and sampling gaps do not emit gestures. Recognized gestures display the mapped preset on screen only. Mapping does not yet enable automatic seat application or continuous input polling. Bindings are local credential-protected preferences and persistence failures are reported.

## Test a saved axis

Tap a saved card. No Developer mode is needed.

1. Choose one axis. Record current A.
2. Use native controls to change only that axis to the saved position B. Record B. Check driver-seat mapping and that native controls interrupt movement.
3. Return to A and record. All other axes must match, A must return exactly, and selected values must be integral within the candidate percentage range.
4. Tap Move saved axis. The target must be within the observed native A/B range, not an extrapolation. Confirm physical P, parking brake, clear space and remaining at the native controls. The Settings gearMode and getEPBState()==3 are additional vetoes, not fresh telemetry claims.
5. Move now sends one driver-area-1 percentage setter only. Confirmation expires after two seconds; source mismatch, storage failure, leaving the screen, invalid P/brake and stale data block the command. Calibration expires after five minutes and never survives leaving the activity. No movement request is persisted or replayed.

The earlier arbitrary 1–2 unit restriction is replaced by the range you verified through native controls. Full multi-axis recall, completion/interruption semantics and automatic child-lock actuation still need actual setter evidence. The app reports command return/readback, not proven movement completion. No developer-operated physical car write is used for validation.

## Settings and private data

Normal Settings contains drivers, home panel, child shortcuts, startup and updates. Sharing is removed because the car has no usable share targets. File backup is kept in Developer mode for supported hosts; neither unsupported file export nor sharing is presented as a normal setup step. Private trial reports remain viewable/clearable locally. Keys, raw identity/VIN and private reports are not sent automatically. Unflushed in-memory logs cannot survive process death.

## Software checks

Tests cover startup/storage, preserved keys, recovery policy, deferred/public/private logs, gear parsing, home/gear/screen gating, input recognition, preset storage and one-shot native-range movement consent. Actual BYD window callbacks, getter freshness, OEM permission/setter behavior and overnight visibility still require car tests. The emulator stays off for host memory reasons.
