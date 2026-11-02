# Seat Presets 0.1.19 · pre-alpha

- Seat loading uses coarse-to-fine 100–300 ms holds and removes the extra fixed settling pause. Longer holds are limited by remaining distance, a target reserve and conservative observed-rate estimates. Short steps remain near the target. This should reduce choppiness; it still uses bounded steps, not continuous native-memory recall.
- P/parking-brake, opt-in, control-surface, cancellation, no-progress, wrong-direction, coupling, overshoot and total limits remain. Feedback is read after release, with no blind retry when it does not advance. Planned/actual timing and read latency/change details are logged privately.
- Seat trials can compare 100, 200 and 300 ms. New mirror trials offer all eight left/right/up/down choices, one confirmed 100 ms action at a time, with complete before/after readings and intervention markers. Mirror stop=0 is a trial candidate and has no physical confirmation yet. Keep native controls ready.
- Mirrors remain excluded from preset loading. Mirror confirmation records evidence only. Fold/unfold, native memory-bank overwrite, absolute-coordinate writes and automatic movement remain unavailable.
- Existing 0.1.18 captures, driver names, movement preference and signing key are retained. No new recording is required for those captures. New trial and timing logs use the existing trusted Lab integration.

The owner reports the earlier seat trials and eventual loading worked. Retained logs show four no-change aborts before one complete three-axis feedback match. New timing and mirror behavior still require parked owner tests; software tests do not establish physical travel or stopping guarantees.
