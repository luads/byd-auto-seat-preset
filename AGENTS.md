# Seat Presets

Read README.md before editing. This repository is the new preset app, not the private ADB lab.

- Keep demo and live package IDs separate. Live vehicle readings remain null and actuation stays disabled until a local control contract is validated.
- Both app and widget recalls use the same controller. Two distinct favourites must remain directly tappable.
- Require fresh P and parking-brake evidence for recall. Speed is a veto, never proof of stillness. These are proposed conditions, not a validated physical interlock.
- Never persist or replay a movement request after restart, blocked eligibility, or an update.
- Startup uses BYD's autostart manager and the owner-granted WRITE_SECURE_SETTINGS path. No VPN, wake locks, or wakeup alarms.
- Startup must never recall a preset. Opening the app, adding a widget, and replacing the package must never recall one either.
- Keep credentials, APKs, signing keys, vehicle recordings, VIN, and identity data out of git. Public updates need no API token.
- Check licences before copying external code. The current implementation borrows ideas, not OverDrive/Kinex/BYDMate source.
- Run ./gradlew test assembleDemoDebug assembleLiveDebug lintDemoDebug lintLiveDebug before calling the bootstrap done.
