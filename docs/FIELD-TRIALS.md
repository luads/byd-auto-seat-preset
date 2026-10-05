# Owner-operated parked trials, 0.1.10

These are separate foreground tests. Normal live recall, automatic driver selection and child-lock seat application remain disabled. No car ADB write is needed by the developer. New installs use generic driver labels. Existing names and presets stay local.

## Home panel and backup

0.1.9 never showed the normal live panel because its adapter always returned missing gear. Setup retries could not fix that. Setup now enables the panel preference in one step and reports overlay/home-observer readiness. P-only persistent display still waits for a validated live telemetry feed. This is not fixed by treating a cached P as fresh.

Use Settings → Home panel → Set up home panel, then Test panel · 30 seconds. The preview works with overlay access alone, returns to home and ignores gear only for this display experiment. Both tiles show a preview message; neither can move a seat. Try placement/size and confirm the preview disappears after thirty seconds. Camera/CarPlay/split-screen and actual BYD home detection remain field work.

Export presets offers Save to a file and Share backup. Sharing uses a non-exported, read-only grant provider with a unique cached JSON snapshot, only when the owner chooses a destination. Keys and private logs are excluded. The actual cause of the reported car file-picker failure remains unknown; class-only errors go to public widget diagnostics. The new route is a fallback, not proof that the BYD picker is fixed.

## Save actual driver-seat observations

From a preset, Edit → Save current position → Read current position. This explicitly acquires the OEM setting device from com.byd.data.collect using PathClassLoader. Area 1 is the logical driver route in the inspected firmware, not a guessed left/right mapping. Getters: getSeatHorization(int), getSeatbackrestPostion(int), getSeatTurnHeight(int). Declare SETTING COMMON/GET; binding failures stay unavailable.

The screen compares two reads 200 ms apart. Missing/non-finite/out-of-candidate-range data, changing reads and the all-zero adapter fallback cannot be saved. Individual zero values are retained as raw candidate values, not fabricated defaults. Values are saved only after explicit confirmation and only to the selected preset. The metadata uses an unverified coordinate format and a hash of firmware/OEM package version. A stable pair is repeatability, not proof of cache freshness, units or actual geometry. Normal recall rejects this format.

The foreground trial reports raw gear and brake separately. It never labels getter return time as the freshness of the underlying OEM cache or as unlock identity. Seat observations, raw input trials and movement records go only to the private observation log, not Logcat or automatic/public exports. Developer tools can view/clear that report. Buffered entries cannot survive process death.

## Child-lock screen trigger

Enable Developer mode in Settings. Open Child-lock input trial · screen only. Arm for ten seconds, wait for READY on both sides and do labelled singles/doubles with about 500 ms between presses. Getter: BYDAutoDoorLockDevice.getDoorLockStatus(int), left 6/right 7. Named zero-argument child getters are not used. Only valid 1/2 baselines enter the recognizer. Startup values never emit an event.

A complete exclusive-side return pair changes the screen only. Invalid values, failures, both-side changes, gaps over 250 ms and pairs over one second reset recognition. Requested cadence is 100 ms after each read; the report records actual gaps and unavailable samples. Stop/leaving the activity ends the window; one in-flight read may finish but cannot emit after cancellation. No continuous background polling, wake lock or new worker service is added. Reliability/false positives are unvalidated.

## First actual loading test, one axis only

This is an owner-triggered experiment on the owner's parked car, not normal recall. No physical command was run during development. Developer mode → Supervised seat movement trial.

1. Choose one axis. Use native controls to select A, change only that driver axis by a small amount to B, then return to A. Record A, B and A in the trial. All other observed axes must remain unchanged, and A must return exactly. The selected values must be integral and away from endpoint/fallback values. Calibration expires after five minutes and is discarded when the activity stops.
2. Save B to a separate test preset through the capture screen before this calibration session (then repeat step 1). The selected target must differ from the current position by only 1–2 candidate units. Larger full-preset movements are blocked.
3. Confirm driver-seat mapping, observed units and native interruption behavior. Confirm that you are in the driver's seat, in P, parking brake applied and with clear space. Select the test preset and review the exact axis/current/target values. Cancel if any condition is uncertain.
4. The final Move now confirmation provides fresh owner-observed physical P/brake evidence. The documented Settings.Global sys.share.gpack.agent.data gearMode field (1=P, 2=R, 3=N, 4=D) and getEPBState (3=applied) are additional vetoes. The unreliable SDK getGear route is not used. Only gearMode is extracted; the full row contains identity data and is never logged. Those cached values are not promoted to fresh telemetry. Missing/disagreeing getters or missing SET permission block the test.
5. On Move now, read/recheck the position, small delta, OEM vetoes, foreground state and two-second confirmation expiry. Consume the command once and invoke only the matching driver-area-1 setter: turnSeatHorizationPercent, turnSeatbackrestPercent or turnSeatHeightPercent. No bank recall, mirrors or second axis. No queued request survives cancellation, restart, storage failure or re-entry.
6. Check actual physical behavior yourself. The UI reports command return and getter readback, not successful completion. Use native controls if movement is wrong. The app does not claim a validated cancellation API. A setter can return without moving anything. Stop after one trial if results disagree; inspect the private report before expanding scope.

The shared automatic recall controller and parked/stale-data checks stay unchanged. This supervised tool requires fresh physical owner confirmation rather than inventing fresh telemetry from SDK caches. Enabling normal/automatic actuation still needs a verified live control/freshness contract and completion/interruption behavior.

## Software validation

78 JVM tests per variant cover storage and startup safeguards, child recognition, invalid/fallback seat observations, backup round trips, one-shot consent, small deltas, calibration/source isolation, stale/foreground/P/brake/speed vetoes and reset. Android integration includes the read-only backup provider and traversal/authority/write rejection. Emulator checks covered SDK-unavailable capture, panel preview and export/share selection before the final gear-route correction. Remaining child-trial, movement-screen and panel-expiry UI checks are deferred to avoid memory pressure. They cannot establish actual car getters, permissions, movement, cancellation or battery impact.

Reference field facts remain in the private lab handoff. Per-phone NFC identity is unproven; none of these flows infers a driver from a phone, seat value or timestamp.
