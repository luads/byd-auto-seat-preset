# Seat Presets 0.1.18 · pre-alpha

- Real independent seat loading is enabled behind **Allow seat movement**. Re-record each favourite once using the corrected typed feedback. Old coordinates are preserved, but are not silently reinterpreted or written through the retired percentage path.
- The new loader uses short native press/release signals and checks feedback after every step. It releases after each attempt and stops on no progress, stale feedback, wrong direction, overshoot, coupling, cancellation, P/brake loss or fixed limits. No automatic reverse correction, native-bank overwrite or movement replay.
- **Seat trials** now offers a structured one-axis pulse and complete readback, with explicit confirmation and physical/native-intervention markers. The unsupported A/B/C movement choices are removed. Read-only mirror comparisons cover both SDK sources and the reviewed getter routes.
- Four mirror coordinates can be recorded as references. **No mirror movement is sent.** All card/panel/child-lock seat loads share the same controller. Stop controls are available in the app/panel, and another recognized child-lock double cancels an active load.
- Typed Float feedback fixes the old fore/aft getter's Integer/Float mismatch. Each coordinate keeps its source, read time and available cache timestamp. A read timestamp is not proof of fresh ECU data. Unsupported or missing data is never filled with zero.
- Native seat-access setup is explicit. Private trial/load logs use the existing trusted Lab integration; retention is increased to 180,000 characters within the existing export contract. Loss/truncation warnings remain visible. Silent updates cannot replace the app while it owns a motion session.

**Experimental:** one owner-operated backrest forward/release pulse has physical confirmation. Complete preset loading, reverse and other axes still need owner-operated parked checks. A 100 ms request does not guarantee a distance or a hardware stop. Keep native controls ready and only use when able to physically restore the seat. No movement is performed on startup or update, or based on phone identity.

Stable signing key and package IDs are unchanged. Update in place, keep the movement toggle as preferred, then hold each favourite to record it again. See VALIDATION.md for software checks and their limits.

The optional single-pulse trial can save an owner-confirmed numeric direction per axis and firmware. Stale, flat, coupled or unusually large changes are not accepted as direction evidence. This changes no preset coordinates, and the loader never learns/reverses direction automatically after an unexpected step.
