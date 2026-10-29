# Independent seat feedback contract

Version 0.1.18 enables the explicitly requested experimental native-signal loader. Absolute percentage setters remain retired after unintended travel. Generic bodywork float writes are not used. Native ECU memory slots are neither read as the app's presets nor overwritten.

The canonical capture routes are declared Float, READ-only, area 0: fore/aft 0x21603b80, backrest 0x21603b88 and height 0x21603b84. Mirror references are 0x21603b7e/7c/7f/7d. A bounded fixed command filters the car-service cache for only those seven properties. Each accepted sample needs status 0, a single finite Float in the candidate range and a nonzero cache timestamp. Missing/ambiguous/wrong-type/bad-status/sentinel rows reject seat capture; unavailable mirrors are omitted. Typed, status-bearing zero is distinct from an old getter fallback. Samples retain provenance and unverified cache freshness.

The position format and firmware/SDK source are new. Legacy captures remain available in backups and diagnostics and require explicit re-recording for movement. The owner chose independent coordinates, not a native-bank mapping.

The driver-area-1 momentary APIs are turnSeatHorization, turnSeatbackrest and turnSeatHeight, with signals 1, 2 and release 0 as traced in the installed native app. Only one backrest forward/release operation has physical confirmation. Native source names are evidence of intent, not proof of every axis's physical polarity or stopping behavior.

The shared controller checks owner opt-in, reported P and EPB, awake/control-surface state and the original short request deadline before its first signal. Ongoing steps are limited to 30 seconds, 48 pulses total and 24 per axis. Each nominal 100 ms start is paired with a release attempt in finally, including start failure or cancellation. The next signal requires an advancing cache timestamp, reported progress toward the selected target and no different seat axis changing. Stale/no-change/away/overshoot observations abort with no reverse correction. Final matching feedback is not labelled verified physical completion. Release return is not proof of a motor stop.

Stop/cancellation invalidates process-memory ownership. P loss, opt-out, screen/surface loss, another child-lock shortcut or explicit Stop aborts further steps. There is no saved request, wake lock, wakeup alarm or new always-on service. Silent update replacement and native motion are mutually excluded. Native controls remain the owner's physical fallback.

Mirror routes can be compared read-only for both SDKs and both reviewed FID namespaces. No mirror adjustment or fold setter is available. All new logs use the separate private report and trusted Lab export; raw captures/proprietary artifacts remain outside Git. See NATIVE-SIGNAL-FINDINGS.md and VALIDATION.md.

The optional single-pulse trial can save an owner-confirmed numeric direction per axis and firmware. Stale, flat, coupled or unusually large changes are not accepted as direction evidence. This changes no preset coordinates, and the loader never learns/reverses direction automatically after an unexpected step.
