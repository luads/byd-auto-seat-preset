Seat Presets 0.1.11 (versionCode 12), pre-alpha.

- Hold a preset card to record the current position in P. Tap a saved card to open the guided movement test.
- Home panel now uses the home observer and reported gearMode P, without a 30-second timeout. Other window identities, screen-off and non-P/unknown gear hide it. Gear updates use settings notifications, not polling. Panel taps open the test screen.
- Left/right child-lock double taps can be assigned to saved presets. The bounded foreground test shows the mapping while in P; no automatic seat command is enabled.
- Settings is shorter. Sharing is removed; unsupported file backup no longer clutters normal settings.
- Movement testing is available from the card without Developer mode and uses one final confirmation. Native A-B-A verification remains required. Targets can span the native range observed in that session, replacing the arbitrary 1-2 unit limit. Only one verified axis moves per test.

Signed with the existing public key for in-place self-update. 80 JVM tests per variant, debug/release lint and builds passed. No physical car ADB write or seat command was run. Emulator testing remains deferred for host memory pressure. Real launcher/menu callbacks, gear cache freshness, setters and overnight behavior need car validation.

See docs/FIELD-TRIALS.md for the test sequence. Normal/automatic full-preset recall and phone identity selection remain disabled.
