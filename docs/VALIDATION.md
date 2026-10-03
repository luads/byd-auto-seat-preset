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
Published-update checks are pending.
No seat apply action was used in these checks.
