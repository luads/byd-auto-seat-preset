# Owner-confirmed backrest experiment

This is a separately requested experimental write path, not validated preset recall. Normal card, panel, child-lock and widget loading remains blocked. It does not convert or change saved presets.

In Developer tools, open **Backrest movement test**. **Allow seat movement** must be on. Read the current position, choose **Backrest −1 unit** or **Backrest +1 unit**, review the current/target readings, confirm that P, parking brake, clearance and native controls are available, then press **Move once**. Cancel sends no command. No foot-brake input is required.

The number is one OEM numeric unit, not a verified physical distance. Units, payload meaning and coupling remain unverified. Unexpected or larger travel is possible. The app has no verified seat-stop command. End observation only ends the readback window. Only run this owner-operated experiment when able to inspect and physically restore the position.

The path binds only `BYDAutoSettingDevice.turnSeatbackrestPercent(int, int)`, driver area 1. It accepts a stable integral backrest baseline from 3 through 97 and a delta of exactly -1 or +1. It checks the signature and already-held SET permission, then rechecks reported P/parking brake, screen/foreground state, opt-in, unchanged backrest and the original two-second request deadline before consuming a single-use permit. No file I/O occurs between the final check and the invocation. It does not grant SET permission, batch axes, load a preset or retry the setter.

Private logs record the explicit confirmation, method arguments, pre-command observations, whether dispatch was attempted, setter return/failure and available readbacks for ten seconds. They report other routes that changed and whether the backrest target was reported. A matching readback or successful setter return is not physical completion. Unknown/cached data stays labelled unverified. No raw seat data goes into public logs or automatic delivery.

Leaving the screen invalidates pending dispatch and ends observation. It cannot undo a command already in flight. UI deadlines discard late acceptance without interrupting OEM code. A stuck call occupies the one process-wide test worker; it cannot create a command backlog. Process restart resumes neither a command nor observation.

Demo has the same confirmation/permit/readback flow with a simulated backrest value and no OEM invocation. Emulator checks validate this flow and refusal paths, not the car's setter meaning or physical behavior.
