> Current diagnostic builds block all live seat writes. The old supervised movement and child-lock trial screens are removed. Use Developer tools > Inspect saved positions for read-only investigation. The historical procedure below is not runnable in this build. See [the recall contract investigation](SEAT-RECALL-CONTRACT.md) before any future actuation test.

# Owner-operated saved presets, 0.1.12

## Use

1. In P, set the desired driver-seat position with native controls. Hold a favourite card and Save. It replaces only that preset's coordinates.
2. Enable Settings > Seat movement > Load saved presets only when native controls are within reach and movement can be physically undone. This choice is off by default and does not itself move anything.
3. Tap a saved card or panel favourite to load its usable saved axes. No A-B-A recording or calibration loop remains. To change a stored target explicitly, use Edit > Adjust saved target, then Save target. Saving alone never moves the seat.
4. Assign left/right doubles under Child-lock shortcuts, then opt into Use child-lock shortcuts in P. Recognized doubles use the same recall controller. The input reader runs in the existing home accessibility service only while awake, enabled and reporting P. No wake locks/alarms, boot movement or request replay.
5. Turn Seat movement off to block all entry points. This stops future axis commands, not an already-running motor; native interruption is not yet validated.

## Eligibility and evidence

Gear is Settings.Global sys.share.gpack.agent.data -> gearMode (1=P); brake is getEPBState()==3. The full settings row contains identity data and is never logged. Getter-return sample time is recorded with a monotonic clock but does not prove underlying cache freshness. This remains an owner-enabled experimental control route, not a certified physical interlock.

Saved metadata must match this firmware/OEM source. Missing, non-finite, fractional or endpoint coordinates do not become fabricated setpoints. A zero axis is skipped because it can be a fallback. The field report showed horizontal=0 with nonzero backrest/height; the old calibration defaulted to horizontal and never became ready. Only usable axes can be loaded. Void setter returns are not called physical completion. Private command/seat observations remain private and are not automatically exported.

## Home fix

The car emits com.android.launcher3.home.MainActivity followed by com.android.systemui/android.widget.RelativeLayout for its dashboard cards. Preserve the known dashboard overlay only after a real Home event. MoreAppActivity and other activities hide the panel. Own non-focusable overlay events are ignored. Verify home/menu/home, other apps, screen-off and P/non-P transitions. The 30-second bypass service remains removed.

## Validation

Host tests and lint cover contracts and guards, not actual vehicle response. Install the stable-signed APK in place, verify the switch starts off, test no-motion UI/setup/home paths first, then let the owner enable and initiate the first command. No developer-operated seat command is part of validation. Confirm actual movement and native interruption before relying on shortcuts. Overnight cache/CPU/12V behavior remains a field check.
