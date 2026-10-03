## Startup and storage safeguards, 0.1.7

Android user unlock is storage readiness, not a phone unlocking the car. Startup tracks process
start, successful credential preference access, TCP availability and ADB authentication separately.
The live OEM getter remains unavailable until its binding is validated. No readiness transition
selects or applies a preset. Demo observations record their actual monotonic sample time separately
from process and storage-ready times. There is no fresh unlock identity contract or automatic recall.

Credential preferences, names, presets, private observations and the existing `panel-adbkey` pair
remain in credential-protected storage. Locked or unreadable storage defers work. Key files are not
inspected while locked. Only proven absence of both files permits first-use generation during
explicit setup. An existing partial or unreadable pair fails without replacement. Startup checks
only existing keys and runs a bounded read-only authentication probe; it cannot grant permissions.

Only the explicit recovery switch is in device-protected `startup-bootstrap` preferences. Migration
runs after Android unlock and checks persistence. An existing device-protected opt-out wins over
legacy preferences. The old live default was not consent: an absent legacy switch migrates to off.
If startup previously relied on that default, explicitly enable its switch in Developer settings.
Unreadable policy, WSS alone, an unknown port state and unknown settings never authorize recovery.
Recovery checks the closed loopback port off the UI thread, writes only known-zero ADB settings,
checks the returned write and read-back, and retries after unlock for BYD's second settings clear.
It does not change development mode or ADB key expiry.

Application launch, ACTION_USER_UNLOCKED, boot/package callbacks, activity resume and natural
home-window callbacks request a serialized pass. Bursts coalesce to one further pass. A passive
5-second watch stops after 2 minutes of elapsed real time, including suspend. No wake lock, wakeup
alarm, always-on service, VPN or new Direct Boot component is added. After that window, a naturally
firing callback retries work. Successful read jobs finish once; failed read jobs remain pending
until authenticated, without retaining any movement or permission-setup request.

Widget lifecycle logs and private observation logs own separate bounded memory queues and separate
credential-protected AtomicFile sinks. Entries retain original wall-clock and monotonic timestamps.
Failed writes retain the queue, oldest lines dropped at its limits are reported on the next flush,
and each sink flushes independently. Neither content is mirrored to Logcat. Private clear discards
pending entries immediately and retries disk deletion when writable. Public widget reports read
only widget-diagnostics.log; preset backup exports contain no runtime identity observations. Preset backups themselves contain
private driver labels and seat geometry and must be treated as personal backups, never public diagnostics. There
is no Telegram delivery in this app. **Unflushed memory cannot survive process death**, including
BYD force-stop. These logs are best-effort evidence, not a persistent unlock-event journal.

The existing shared recall controller, parked/brake evidence, stale-data veto, coordinate provenance
checks and disabled live actuator remain unchanged. No gesture is armed by startup.

### Validation still needed on the car

After a separately authorized install, test BYD autostart across car-off and overnight restart,
early locked launch followed by Android unlock, the second settings clear, delayed ADB authorization,
explicit opt-out, and recovery when credential storage becomes writable. Inspect public diagnostics
without moving a seat. Confirm no port/settings writes when recovery is disabled or the port is open.
The local JVM checks simulate failure conditions; they do not establish firmware behavior or 12V
consumption. No ADB writes or seat movement are needed for this implementation's validation.

Phone identity and live gesture recognition remain unvalidated. Readiness or cached observations
never establish a driver's identity. The private research reports stay outside this public app.

Adapted from lab commit 64a58f9f9fcd77a6274af308e24024d8e1c67681 and the startup lesson in
[Trip Stats 2961bba](https://github.com/angoikon/byd-trip-stats/commit/2961bba59a12a25e4664e39f340a8e24845a859f).
No Trip Stats implementation code or lab background diagnostics are imported.
