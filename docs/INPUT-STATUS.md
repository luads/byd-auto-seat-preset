# Manual input and identity status

The lab handoff, REPORT-child-lock-agent-handoff.md (3 October 2026), establishes read-only child-lock state capture on the owner’s Sealion 7. It does not establish a reliable gesture recognizer or authorize seat movement.

Use BYDAutoDoorLockDevice.getDoorLockStatus(int), arguments 6 and 7, through the tested OEM classloader. The named left/right child getters stayed constant and are not the input. Values 1/2 are the demonstrated valid pair; 0 and exceptions are unavailable. Required COMMON and GET permissions were normal on the tested head unit. No SET permission is needed.

Implemented: an in-memory exclusive-side recognizer for start → other → start, resetting on invalid/unavailable values, long sampling gaps, timeout and restart. Replay measured gaps of 100, 302, 303, 300 and 605 ms. A 1-second timeout is a proposal. Unit tests cover the recorded 100/302/303/300/605 ms gaps, repeated pairs, simultaneous/opposite-side changes, invalid values, timeout, restart and 250 ms sampling-gap resets. Developer mode has a non-actuating replay screen. The foreground Child-lock input trial now binds these exact SDK getters, samples only during an owner-armed ten-second interval, and changes the screen after recognition. It does not move a seat. Next, measure misses and false positives during short owner-armed parked trials. No continuous parked polling or seat action is needed for this stage.

NFC unlock identity has no conclusion. Tests remain pending. Do not display a detected driver or connect either input to live recall yet.

Source: /Users/luadesouza/Sites/byd-adb-lab/REPORT-child-lock-agent-handoff.md. Raw vehicle evidence stays private in the lab.

## 0.1.11 mapping UI

The owner reported usable child-lock mapping in the foreground trial. Settings now assigns left/right doubles to preset IDs, default Off. The short foreground test displays the mapped name only while gearMode reports P. Missing/invalid data resets the recognizer. No initial sample or return to P dispatches an action. Continuous recognition and automatic movement are not enabled by saving a mapping.
