# Native signal findings, 2026-10-29

One owner-authorized driver-backrest forward trial used the installed app's UID and existing permissions. The SDK call was `BYDAutoSettingDevice.turnSeatbackrest(1, 1)`, followed after a nominal 100 ms by `turnSeatbackrest(1, 0)` in finally. Reported P, EPB, movement opt-in and own Activity foreground were checked first. The owner confirmed only the driver's backrest moved forward briefly and stopped without physical intervention. The backrest cached property advanced by one numeric unit and its timestamp advanced. Horizontal and height feedback did not change during this trial.

This validates that one operation on the owner car, not every direction, duration, axis, arbitrary target or cancellation failure. The native UI source also uses signal 1 for forward, 2 for backward, and 0 on release. A duration is a request interval, not a guaranteed physical travel limit. Normal percentage setters remain retired. Raw captures, proprietary APKs/libraries and the temporary signed instrumentation harness stay outside Git.

Both installed SDK packages route generic bodywork get/set through the TS compatibility manager. The large geometry FIDs used by the former B/C trials returned default sentinel fields, with no new movement dispatch. The installed bodywork manager's scalar float/double set path substitutes integer zero. Do not adapt those sentinels or revive those float methods as percentage targets.

The lower SDK requests an Integer for the declared Float fore/aft feedback property. This type mismatch is a source-based explanation for zero reads; the direct Float cache route reports nonzero values. Independent coordinate capture must use typed property feedback for all seat axes, retain status/area/source/cache timestamp, and distinguish sample time from ECU/cache freshness. Valid zero needs explicit typed/status evidence, not a default getter result.

Native memory banks are a separate route, but the owner chose independent coordinate presets. Do not remap favourites to native banks or overwrite those slots.

Four primary mirror Float read properties exist: left X/Y 0x21603b7e/7c and right X/Y 0x21603b7f/7d. They had plausible cached values but neither values nor timestamps changed through the retained snapshots. The alternate NOT_L3 routes returned 127.5. A later unlabelled driving-phase snapshot showed all four primary values and timestamps changed. This supports live feedback candidates, but does not assign those changes to a labelled mirror-only/native-recall event or validate an actuator. Native preset mirroring is owner-reported. Mirror fold commands must not be confused with position commands.

A later read-only settings sample reported gearMode 4 while the owner drove, compared with gearMode 1 in P. No further movement was authorized or sent during driving. No phone identity conclusion follows from any of this evidence.
