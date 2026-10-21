Seat Presets 0.1.13 (versionCode 14), local diagnostic build, not published.

Restores app access after an unintended live recall. Live seat writes are blocked in both the shared recall path and the OEM writer binding. Existing presets and preferences are preserved. Recording remains read only.

Settings uses two columns. Seat movement sits below Drivers. Android Back returns from Settings to Presets.

Signed live build and live-release JVM tests passed. Installed in place and re-enabled on the owner car. Opening the app and Settings Back were checked on the head unit. The existing private report was captured for offline analysis. The live setter contract remains unverified; no further seat command was sent during this investigation.
