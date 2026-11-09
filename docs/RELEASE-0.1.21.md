# Seat Presets 0.1.21 · diagnostic pre-alpha

- Developer tools > **Seat feedback** adds a foreground 10-second read-only comparison of fixed seat getters from both SDK sources, typed local ADB cache readings and an optional typed public Car-property route. Select one axis, record while using its physical control, and add movement/release report markers. Missing/default/error readings stay diagnostic-only. No permission grant, preset save or vehicle command is performed by this screen.
- Private logs retain call/sample times, raw values, cache times, failures and per-route latency/change summaries for the existing trusted Lab transfer. Stop/backgrounding invalidates sample acceptance; an outstanding call must return before another recording starts. No background polling, startup action, wake lock or alarm is added.
- Existing pulse trials add post-release change timing. Normal recall distinguishes overshoot from a direction error and reports delayed final drift without correction. Pulse timing, movement opt-in and interlocks remain.
- A bounded one-start/one-release continuous framework is available in demo simulation only. **Live continuous movement stays locked.** Faster native feedback, physical directions and stopping behavior still need field validation. Existing presets, mirror captures and normal pulse-based seat loading remain available; mirror preset loading stays off.

201 JVM tests passed per variant, debug/release builds and lint passed, and demo/signed-live emulator checks passed. The actual SDK/Car-property routes and physical travel/stop behavior have not been tested on the car. See SEAT-FEEDBACK-PROBE.md.

The owner reports no visible movement in a brief mirror trial. Its logs have not been reviewed, so command delivery, failure cause and mirror release behavior remain unconfirmed. Mirror preset loading stays off.
