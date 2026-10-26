# Seat Presets 0.1.16 · pre-alpha

- New **Backrest movement test** in Developer tools. It reads the current position, offers exactly ±1 numeric unit, requires an explicit physical-state acknowledgement and **Move once**, then records available axis readbacks for ten seconds. It sends at most one command, never retries or changes presets, and rejects expired, background, changed-position or unparked requests. Demo provides a simulated version.
- **Experimental:** numeric units and physical travel remain unverified. Other axes may react. There is no verified app stop command. Normal preset loading remains blocked. Review the in-app warning and BACKREST-TEST.md before any owner-operated parked test.
- Seat observations and preset backups retain per-axis source, sample time and available cache timestamp. Legacy presets retain their original coordinates with unknown evidence. Cached and direct routes are distinguished; timestamps are not promoted to verified freshness.
- Native observation trials show missing read permissions and offer explicit read-only setup. They include the separate cached fore/aft route, retaining source/timestamp details. Setup failures report their bounded private reason.
- SDK signature inventory is stored once per firmware/SDK revision in a separate bounded private file. Explicit diagnostic transfer includes it ahead of trial history so the byte cap preserves newer trial records first. Clearing private diagnostics clears both.
- Structured axis/from/target traces survive the existing privacy filter without weakening protection for personal names or credentials.

The stable release signing key and package IDs are unchanged. Update in place. No preset is applied on install, restart or update.

Local JVM, lint, signed-build and emulator checks cover software behavior. Real-car command interpretation, physical travel, coupling and interruption still require an owner-operated parked test. No car command is part of release validation.

Validation: 134 JVM tests passed in each demo/live debug/release variant; all four lint variants have zero errors. Signed demo/live builds passed. Eight Android storage/startup integration cases passed. Emulator UI checks cover acknowledgement gating, Cancel, exactly one simulated command, target/cross-axis readback logs, no replay, background invalidation and missing-permission setup/cancel. Physical car behavior is untested in this release.
