# Seat Presets 0.1.17 · pre-alpha

- Fix the large empty gap on the preset home screen. Settings and Edit now use their own width, leaving the title and driver names visible.
- Replace the failed integer backrest experiment with **Seat command experiments**. A remains labelled retired. B compares a one-axis float payload with C's seven-seat-axis float payload, preserving all other readable seat values. Mirrors and steering are excluded. The source review is linked in BACKREST-TEST.md.
- Select the Native MyCar system SDK explicitly. Collector reads remain available for comparison; its writes and known TS integer-dispatch backends are quarantined. Unknown dispatcher, missing access, zero/sentinel/ambiguous geometry and incomplete batch values reject the experiment. There is no automatic fallback.
- Experiment permission setup is explicit and separate from read-only setup. Each command still requires the movement toggle, fresh reported P/parking-brake checks, unchanged written-axis values, foreground state, physical acknowledgement and **Move once**. One attempt, no retry or persisted request.
- Private traces include selected method/source, numeric payload identifiers, raw SDK result and bounded readback. Mark physical-control use and native-preset restoration so observations are not presented as app-only effects. The marker time does not establish the exact intervention time.

**Experimental:** numeric units, actual travel and cross-axis effects remain unverified. A ±1 numeric target does not guarantee small physical movement. There is no verified app stop command. Keep native controls ready and only test when able to inspect and physically restore the seat. Normal card/panel/child-lock/widget loading stays blocked. No seat command runs on startup, update or release validation.

Stable signing key and package IDs are unchanged. Update in place. Software validation is recorded in VALIDATION.md. Firmware support and physical behavior still need an owner-operated parked test.
