# Native seat observation trial

Developer tools > Native seat observation trial captures read-only evidence around an owner-operated native adjustment. It does not reinstate the failed setter, enable another setter, change a preset or prepare a future movement request.

1. Park in P with the parking brake set and clearance around the seat. Choose one axis. Read A with the seat stopped.
2. Use the car's native controls for a small change on that axis. You can leave this screen for the native settings and return. Read B after stopping the adjustment.
3. Return to A with native controls and read again. Export the private report afterwards.

Each phase records the exact named percentage getter values (without substituting fallback values), separate raw bodywork getter candidates, two successive reads, read bounds and raw P/EPB before and after. An unavailable getter remains null. Missing optional bodywork access does not hide the percentage observations. A changed pair is logged but not accepted as a stopped phase. The first phase also records available seat-related SDK method signatures without invoking candidate setters. Signature presence, stable values and successful A/B/A do not establish units, command packing, cache freshness, the effect of a setter or physical completion.

Trials last at most ten minutes and stay in memory. Leaving during a read invalidates that read. Process death starts a new trial; opening the app never resumes a read or movement. No wake lock, background sampler or vehicle write is added. Existing movement and writer-binding blocks remain in force. Permission setup remains a separate explicit action.

This screen remains read-only. The separately requested BACKREST-TEST.md experiment is an explicit owner-confirmed exception and does not validate the normal recall contract. On this owner's reviewed firmware, generic bodywork float data can become integer zero and named bodywork setters reach the failed integer property route. Validated preset loading requires a separately reviewed payload/units/stop contract for this exact firmware.

A 20-second UI deadline and Cancel read discard acceptance without interrupting OEM code. One process-wide worker permits only one outstanding read, including after this screen is reopened. A stuck getter cannot create additional worker threads or queue more reads. Wait for it to return or restart the app. Neither timeout nor cancellation sends a vehicle command.

Missing read permissions are visible before each phase, with an explicit setup action. Percentage and cached fore/aft routes are separate; cached routes retain sample/cache timestamps with unverified clock/freshness. SDK signatures are stored separately once per firmware/SDK revision, rather than repeated in each trial.
