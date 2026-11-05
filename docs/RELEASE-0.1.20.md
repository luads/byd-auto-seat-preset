# Seat Presets 0.1.20 · pre-alpha

- New **Minimum command pause** selector under Settings > Seat movement when Developer tools is enabled. Choose 0, 100, 200, 300, 500, 750 or 1000 ms; default 500 ms. The setting persists across restarts and is captured at the start of each load. It does not move anything when changed.
- The shared card, floating-panel and child-lock loader enforces the minimum release-to-next-command interval. Feedback-read time counts toward it. The first command and an already-matched preset have no added delay. Private logs include the selected minimum and observed gaps for the existing trusted Lab transfer.
- Waiting checks cancellation, foreground, movement opt-in, P and parking brake. An added wait refreshes seat feedback before another command; changed coordinates stop the load. Missing/unchanged feedback still stops without a blind retry. Existing pulse durations and signal/run limits remain.
- Presets continue to retain available left/right mirror X/Y readings. Mirror preset loading stays off. The existing confirmed 100 ms mirror tilt trials remain available under Developer tools > Seat trials. Their release=0 candidate still needs physical direction and stop confirmation.
- Existing captures, names, movement preference and signing key are retained. No new recording is needed for valid 0.1.18/0.1.19 captures.

The new pause is experimental. Retained 0.1.19 field logs show unchanged feedback aborting several loads before a later request matches the seat target. These logs do not prove whether commands were ignored or small movement went unreported. Spacing needs a parked owner test; software tests do not establish physical travel or stopping guarantees. No car command was used to validate this release.
