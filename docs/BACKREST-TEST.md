# Native seat trials

Real independent seat loading is available through the shared card/panel/child-lock controller when enabled in Settings. It uses typed Float feedback and native momentary commands, not absolute percentage setters or native memory slots. Legacy captures need recording again. Mirror coordinates are read-only references, not movement targets.

Developer tools > Seat trials is an optional check before loading. Choose Backrest, Fore/aft or Height. Read seat and mirror positions. Choose Forward/up or Back/down for a nominal 100 ms request, acknowledge P/brake/clearance/native controls, then Move once. Cancel sends no command. The default is Backrest. Each trial has a trace ID, complete typed cache samples, command/release results and ten seconds of read-only observation. Physical-control and native-preset markers label owner intervention without claiming its exact timing.

The installed native app's source uses signal 1 for forward/up, 2 for back/down and 0 on release. The owner confirmed one driver-backrest forward pulse and autonomous stop; the backrest cache advanced by one numeric unit while the other seat axes remained unchanged. Other axes, directions, durations, multi-axis loading, accuracy and repeated cancellation still need owner-operated validation. There is no claim of a guaranteed physical distance or hardware stop.

The optional comparison reads four mirror axes through typed VHAL feedback, named getters and both reviewed generic getter namespaces, for Native MyCar and Collector SDKs. SDK defaults and 127.5 sentinels are not saved coordinates. There is no mirror setter, fold command or automatic direction fallback.

Set up seat access explicitly grants missing reads and SETTING_SET for this app through the setup channel. It sends no seat command, enables no movement toggle and changes no recovery policy. Nothing in startup, update, readiness or Android user unlock starts a load. One process-owned motion session runs at a time; silent package replacement is excluded while motion owns that gate.

All trial and load events use the separate private report and the existing trusted Lab transfer. The retained report is bounded at 180,000 characters; the signature inventory and report are sent together within the existing 262,144-byte contract. Failed flushes, early-memory loss, rotation, sensitive-line omission and byte truncation remain explicit warnings. Pending memory cannot survive process death.

See NATIVE-SIGNAL-FINDINGS.md for evidence limits. Demo runs the same trial/coordinator/feedback engine using synthetic data with no OEM invocation. Emulator checks do not validate physical behavior.

The optional single-pulse trial can save an owner-confirmed numeric direction per axis and firmware. Stale, flat, coupled or unusually large changes are not accepted as direction evidence. This changes no preset coordinates, and the loader never learns/reverses direction automatically after an unexpected step.
