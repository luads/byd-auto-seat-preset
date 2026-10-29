# Owner-confirmed seat experiments

Normal card, panel, child-lock and widget loading stays blocked. Saved presets are unchanged. The former integer backrest experiment caused coupled movement. The owner stopped it using the physical control and restored a native preset. Later observations cannot be attributed solely to the app.

Open **Seat command experiments** in Developer tools. Enable **Allow seat movement** separately. Choose a method and SDK, use **Set up experiment access** if needed, then **Read current position**. Choose −1 or +1, review the exact method, source and numeric target, acknowledge P/parking brake/clearance/native controls, then press **Move once**. Cancel sends nothing. No foot-brake input is required.

| Method | Payload | Availability |
| --- | --- | --- |
| A · Integer setter | Former named percentage setter | Retired after coupled movement. Aliases are not independent methods. |
| B · Bodywork float · one axis | One backrest identifier and floatArrayValue | Experimental Native MyCar SDK route only. |
| C · Bodywork float · seat batch | Seven seat identifiers, preserving six current values | Requires all seven current values. No mirror, steering, zero fill or sentinel fill. |

The collector SDK is available for read comparison only. Each choice needs a new read. No automatic source/method fallback runs. Experiment access explicitly grants BODYWORK_SET separately from read-only setup. Access alone does not enable movement, startup recovery or normal recalls. Protected permission failures remain visible; the app does not replace permission contexts or inject a shared classloader.

The owned loader uses the selected installed system APK and declared libraries. The generic getter must return unambiguous numeric geometry. Zero, missing, unsupported, sentinel or out-of-range readings reject preparation. Known TS integer-dispatch backends are quarantined. Passing these checks is compatibility evidence only, not validated physical behavior.

Numeric units are unverified. A ±1 target does not guarantee small physical travel. Unexpected travel or coupling remains possible. The app has no verified stop command. **End observation** only ends readback. Use native controls to stop movement. Only test when able to physically inspect and restore the seat.

A single-use permit freezes method, source, baseline and payload. Dispatch rechecks P/parking brake, foreground, opt-in, unchanged payload-axis readings and the original two-second request deadline. One setter invocation at most, no retry. Leaving the screen invalidates pending dispatch. Process restart never resumes a request. One process-wide worker rejects concurrent work rather than queueing commands.

Private logs retain selected method/source, exact identifiers/float payload, pre-command samples, dispatch attempt, raw SDK result/failure and ten seconds of available readbacks. Result zero or matching readings do not mean physical completion. The **I used the physical control** and **I restored the native preset** markers label owner intervention; their tap times do not establish when intervention actually happened. Earlier samples may already include manual movement. Logging and observation remain bounded and private; pending memory cannot survive process death.

Demo runs the confirmation, payload and readback flow using simulated values, without OEM calls. Emulator success verifies software flow only. Firmware interpretation, actual travel, cross-axis behavior and native interruption still need a parked owner trial. See [source comparison](SEAT-COMMAND-SOURCES.md).
