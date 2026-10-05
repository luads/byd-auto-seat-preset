Seat Presets 0.1.10 (versionCode 11), pre-alpha.

- Save experimental current driver-seat readings to a selected preset. Normal recall remains disabled.
- Clearer home setup status and a display-only 30-second panel test. Automatic P-only panel visibility still awaits validated fresh telemetry.
- Preset export now offers Share backup as an alternative to the file picker. Backup sharing excludes logs and keys.
- Developer mode adds a bounded child-lock screen-only trial and an owner-confirmed one-axis movement trial. The latter requires native A-B-A calibration, a target only 1-2 candidate units away, physical P/brake confirmation and reported-state vetoes.
- Gear checks use the documented gearMode settings field, not the unreliable SDK getGear. Recent getter calls do not prove cache freshness.

78 JVM tests per variant, debug/release lint and signed builds passed. Six Android integration tests passed before the final gear-route correction; the backup provider is unchanged. No physical seat command was run. Actual car getters, permissions, movement and interruption remain unvalidated. Further emulator trials were deferred due to host memory pressure.

Install the live APK on the car; demo is a separate simulated app. Both retain the established public signing key. Existing public 0.1.9 installs can use Settings > Check updates; download/installer and automatic installation require their existing access and permissions.

See docs/FIELD-TRIALS.md for the parked test sequence. Nothing applies a preset at startup or from an unproven phone identity.
