# Pre-alpha validation

The live adapter remains disabled. No physical seat movement or phone-based selection is tested.

JVM tests cover preset storage, eligibility, startup storage/key safeguards, log isolation,
update consent/timing and installer result checks. Android instrumentation checks real credential
storage guards, partial/corrupt key retention, failed-file-write retry, private clear and independent
startup retries with injected failures. An injected failure does not reproduce an OEM overnight boot.

Android 11 emulator: all five instrumentation cases passed. Locked/unavailable service and disk
failures are injected, not a reproduction of BYD firmware boot ordering. Both driver captures were
saved without a recall. Existing emulator data was preserved; the signed update baseline uses a
fresh isolated AVD. The emulator's adbd listens on a different guest port, so a guest loopback proxy
to that emulator's own host ADB endpoint is used for update testing. Its insecure ADB configuration
cannot validate the car's key-approval dialog or delayed authentication behavior.

Wide UI checks cover generic driver labels, disabled live controls, driver editing, home-panel
preview and separate startup/update cards. Narrow settings at 540×960 also passed; the layout stacks cards and actions without clipping.
Published v0.1.8 (code 9) updated a private older signed baseline (code 8) automatically after an
Android restart. The app fetched the actual GitHub release metadata and APK, verified digest,
package/version and signing certificate, installed through its loopback channel, and recorded
replacement confirmation. The preset document and both ADB keys were byte-for-byte unchanged.
The pending version marker cleared, and the successful check was recorded for the new Android boot.
No preset recall was requested. The entered driver-name edit also survived restart; generic test
labels were restored after the check.

Public sources and screenshots use generic labels. Historical personal screenshots remain local.
The README is deliberately short. All four JVM variants passed 60 tests each; five Android
instrumentation tests passed. Debug and signed release builds succeeded; all four lint runs had
zero errors (35 debug warnings, 36 release warnings).

Current native captures: [presets](screenshots/presets-018.png),
[settings](screenshots/settings-018.png), [home panel](screenshots/panel-018.png).
UIAutomator suppresses the accessibility observer during inspection; capture the panel directly
after a fresh HOME event, without dumping the hierarchy first.

Still pending: real BYD autostart/force-stop, overnight storage unlock and the second settings clear,
real ADB authorization timing, 12V behavior, OEM getter freshness, phone identity and live actuation.
The emulators cannot establish those firmware and physical behaviors.
No seat apply action was used in these checks.
