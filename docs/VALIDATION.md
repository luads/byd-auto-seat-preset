# Pre-alpha validation

## 0.1.18, 2026-10-30

167 JVM tests passed in each demo/live debug/release variant. All four lint variants have zero errors; existing warnings remain. Required debug builds and stable-signed release builds passed. The parser also accepted the private seven-axis capture from the owner car, without committing its values. Tests cover typed/status-bearing zero versus fallback/sentinel, ambiguous and wrong-type feedback, legacy rejection, bounded signals, release on failure, expired requests, cancellation/P/brake/opt-out loss, late reads, wrong direction, coupling, overshoot, explicit direction profiles, motion/update exclusion and the bounded private Lab envelope.

Eight Android storage/startup/inventory cases passed on the Android 11 emulator. The actual demo trial UI passed acknowledgement gating, Cancel, one native simulated pulse/release, readback, intervention markers and rejection of intervened direction evidence, read-only mirrors, a six-pulse three-axis independent load, explicit owner direction confirmation, background invalidation and preference restoration. Wide screenshots were inspected and remain ignored/private. The harness rechecks observation visibility because its ten-second window may expire during a UI dump.

Settings regression checks also passed rename/save readback, favourite swaps, both child mappings, movement opt-in persistence, confirmation cancellation, retained scroll, Settings Back, and 1920×1080/1440×1080 recreation without geometry changes. The narrow child section was checked after correcting an automation scroll oscillation; the app needed no layout fix. Original private emulator labels were restored after QA.

The stable-signed, non-debuggable live app passed the actual trusted Lab one-use transfer test against a tokenless Lab build. Synthetic native-signal, release, feedback-frame and mirror-read records were present in the exported snapshot and imported privately by Lab. Wrong/missing trust, locked storage, shell/own-UID reads, malformed/oversize/wrong-MIME/path payloads and URI revocation were checked. No Telegram Send action or network delivery was tested.

One earlier, explicitly owner-authorized driver-backrest forward command followed by release had physical confirmation: only the backrest moved briefly and stopped without physical intervention. This is not validation of a full preset load, reverse direction, other axes, every release failure or a guaranteed travel bound. No more car calls were made after the owner started driving. Native memory slots are not overwritten and mirrors remain read-only. Full independent loading, per-axis physical direction/stop, mirror-only attribution and overnight behavior still require owner-operated car checks. The public build enables real loading behind the owner movement toggle, as requested; startup/updates/readiness never load a preset.

## 0.1.17, 2026-10-29

143 JVM tests passed in each demo/live debug/release variant. All four lint variants have zero errors; existing warnings remain. Debug and stable-signed release builds passed. New tests cover distinct single-axis/seat-batch float payloads, preserved axes, retired/collector/unknown-backend rejection, immutable permits, request expiry, opt-out, foreground and parked-state vetoes, one-use dispatch, separate write-permission setup and private-export formatting.

Eight Android storage/startup/inventory integration cases passed on the Android 11 emulator. The actual demo UI passed acknowledgement gating, Cancel, one B and one C simulation, payload/readback traces, intervention markers, retired A, background invalidation and preference restoration. The smoke harness handles the ten-second observation window expiring during automation and counts only this run's trace IDs because older log entries can rotate out.

Wide and narrow home checks passed at 1920×1080 and 960×720, density 240. Title, Settings, driver names and Edit no longer collapse their horizontal rows. Private screenshots stay ignored. The emulator was stopped before signed builds.

No physical-car command was sent. Native SDK loading, protected permission grants, actual generic geometry, float dispatch interpretation, physical travel/coupling, native interruption and overnight behavior still require an owner-operated car check. Software compatibility checks are not a verified ECU freshness or movement contract. Normal preset loading remains blocked. See BACKREST-TEST.md.

## 0.1.16, 2026-10-26

134 JVM tests passed in each demo/live debug/release variant. Debug/release lint and signed builds passed. Tests cover one-shot backrest permits, expiry, changed readings, parked-state vetoes, coordinate provenance persistence and privacy-filter-safe candidate plans. The backrest experiment is an explicit foreground owner-controlled exception to the normal recall hold. Its numeric units and physical travel remain unverified. See BACKREST-TEST.md.

Eight Android integration cases passed on the emulator, including deduplicated inventory, failed-revision retry, locked access, deferred clear and truncated inventory recovery. UI checks passed acknowledgement gating, cancel, one simulated dispatch with target/cross-axis logs, no observation replay, background invalidation and read-permission setup/cancel. No car command was sent. Screenshots remain local.

## 0.1.14, 2026-10-21

105 JVM tests passed in each of demo/live debug/release, with zero failures or errors. All four lint variants have zero errors. Both debug and signed release APKs build. The live release signing certificate matches the established public key.

Real Android 11 emulator UI checks exercised rename/save/readback, distinct favourite swaps, left/right child mappings, movement opt-in persistence, confirmation cancellation, unchanged preset coordinates, retained scroll on a preference update, Settings Back and a narrower window recreation. Generic screenshots remain local. The smoke harness now scrolls in the card gutter to avoid its gesture being consumed by the embedded native panel preview.

The live write hold is enforced in the shared path and again at OEM writer binding/invocation. Neither this validation nor readiness performs seat movement. New read-permission setup, bodywork getter candidates, Compose on DiLink and overnight behavior still need a connected-car check. See SEAT-RECALL-CONTRACT.md and RELEASE-0.1.14.md.

## Historical 0.1.8 validation

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

Historical native captures: [presets](screenshots/presets-018.png),
[settings](screenshots/settings-018.png), [home panel](screenshots/panel-018.png).
UIAutomator suppresses the accessibility observer during inspection; capture the panel directly
after a fresh HOME event, without dumping the hierarchy first.

Still pending: real BYD autostart/force-stop, overnight storage unlock and the second settings clear,
real ADB authorization timing, 12V behavior, OEM getter freshness, phone identity and live actuation.
The emulators cannot establish those firmware and physical behaviors.
No seat apply action was used in these checks.
