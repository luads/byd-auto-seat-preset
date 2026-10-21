# v0.1.15

Live seat writes remain paused. Nothing in this release moves a seat, validates a setter or identifies an unlocking phone.

- Add a native A/B/A seat observation trial, with explicit owner capture steps, read-only raw readings and implementation-signature metadata. A position change uses the car’s native controls, never app commands.
- Add an explicit private diagnostic transfer to ADB Lab. Developer tools requires an owner-configured trusted Lab signing certificate and confirms seat readings, preset IDs, timing and failures before preparing the snapshot. Lab asks separately before any Telegram send.
- Keep diagnostic transfer credential protected, bounded and one use. The read-only URI is revoked and unlinked after opening. Passive expiry and Clear private report discard temporary copies. No credentials, preset backup or setup log is transferred.
- Report flush uncertainty, dropped lines, redaction and byte limits. Filter labelled personal/credential fields, VIN-shaped values, MAC addresses and PEM blocks. Unlabelled personal text in old free-form errors still requires preview review.

No new vehicle-write permission, automatic capture, background upload or movement trigger was added. Android emulator checks cover transport and UI behavior; they do not validate the OEM seat API or physical movement.
