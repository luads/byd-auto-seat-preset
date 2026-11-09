# Seat feedback diagnostic build

This test gathers evidence for a future continuous controller. It sends no vehicle commands, grants no permissions and saves no preset. Existing preset loading still uses released pulses. Continuous movement is locked in the live app; probe results and owner markers cannot enable it.

## Parked test

1. Install the signed live 0.1.21/code 22 APK with replacement, preserving app data. Enable Developer tools in Settings and open **Seat feedback**.
2. Stay in P with the parking brake applied. Select Backrest, Fore/aft or Height. Tap **Record feedback · 10 seconds**.
3. During that window, use the selected axis's physical seat control briefly and release it. Keep the app visible. The two marker buttons record when movement/release was reported; their times are not exact physical-action or motor-stop times.
4. Let the recording finish. Repeat for the other axes if convenient. Leaving the screen stops acceptance. A slow in-flight call must return before another recording can start; there is no queued retry.
5. Use **Send diagnostics to ADB Lab** and the existing Lab preview/Telegram path. The probe and post-release samples are in the private report. Recording is bounded, so transfer soon after testing if other diagnostics will rotate the log.

No Mac ADB connection is required during recording. The app's existing authenticated local channel must work for the typed ADB comparison. Missing/denied SDK or public Car APIs are recorded without fallback values or permission changes.

## Comparison routes

- Fixed setting and bodywork getters for the selected axis from both installed system SDK sources. Each source shares its own classloader. Initial binding and call latency, declared return type, raw values and errors are logged. Default/sentinel values are not movement targets.
- The existing fixed typed car-service cache query through local ADB. It retains cache timestamps and observation time independently.
- The optional public Car property API, on a separate worker, for the same fixed Float property/area/status contract. Wrong property, area, status, type, non-finite/range values or missing timestamps reject the sample. Class/service/permission failures can make this route unavailable on this firmware.

Readers run only during the owner-started foreground window, with no wake lock, alarm or startup hook. Calls are serialized within each reader and never queued once the preceding call is outstanding. The public API can block, so Stop cancels acceptance rather than claiming to terminate a blocked binder call. Repeated unchanged samples are logged at most twice per second per route; all accepted samples still contribute to bounded summary counts and latency medians. Value/error changes are logged immediately. All records use the existing bounded private log and explicit Lab transfer, with no Logcat/public export or automatic Telegram delivery.

The API shape was checked against Android's [CarPropertyManager reference](https://developer.android.com/reference/android/car/hardware/property/CarPropertyManager) and [Car lifecycle reference](https://developer.android.com/reference/android/car/Car). No listener or setter is registered. Faster reads, matching values, advancing timestamps and markers are evidence to review, not ECU freshness or measured stopping guarantees.

## Continuous framework and stop diagnostics

The demo-only framework holds one simulated direction while consuming non-blocking latest samples. It bounds the hold, rejects stale/future/backwards feedback, wrong direction, no progress and overshoot, checks eligibility, and attempts one release in finally even after an uncertain start. There is no native adapter and no live unlock preference. Field evidence must establish a usable feedback route and stopping behavior before a later build can wire one.

Existing native pulse trials now record explicit post-release timing and later coordinate changes for the selected axis. Release-return time is captured before logging overhead. Normal recall now identifies a target crossing as overshoot before testing progress, and logs final drift without automatic correction. These diagnostic fixes do not change pulse timing, limits or movement guards.
