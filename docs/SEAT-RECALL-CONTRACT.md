# Seat recall contract investigation

Live writes are paused. A parked owner-triggered recall produced unintended travel. The retained private report includes the candidate axis targets before dispatch and later raw observations. It does not provide an acknowledgement or a time series proving which ECU operation caused each physical movement. Do not classify this as corrupted saved coordinates without comparing persisted data, command payload and independent readback.

## OverDrive reference

Reviewed upstream revision `e14a7a30b1264108d629256e76246c596fb4e0cc` (2026-10-17), MIT license. No implementation code was copied.

- [BodyworkSeatProbe.java](https://github.com/yash-srivastava/Overdrive-release/blob/e14a7a30b1264108d629256e76246c596fb4e0cc/app/src/main/java/com/overdrive/app/byd/BodyworkSeatProbe.java): absolute geometry reads and writes have separate IDs. Its `writeAxes` uses `BYDAutoBodyworkDevice.set(int[], BYDAutoEventValue)` with parallel `floatArrayValue` data. `applyFull` reads the full pose, then sends mirror/steering and seat batches. Its values and sentinel are specific to that contract and cannot be substituted into this app's percentage format.
- [VehicleActuatorService.java](https://github.com/yash-srivastava/Overdrive-release/blob/e14a7a30b1264108d629256e76246c596fb4e0cc/app/src/main/java/com/overdrive/app/services/VehicleActuatorService.java): a separate app-process bridge handles DiLink 5 requests. Rechecks and raw result codes do not establish physical completion.

The failed loader instead called three named integer `BYDAutoSettingDevice` percentage setters. Local inspection of the owner's SDK and vendor framework confirms the area/value argument order and separate integer property writes, but not independent physical behavior. A plausible cause is a different command contract or shared ECU payload. The evidence does not yet establish the cause.

## Firmware differences

The cached owner SDK exposes the generic bodywork signature, but its implementation uses a different feature-ID table and named float-valued driver-seat getters. The generic superclass accepts float arrays, but this firmware's bodywork `setForOneFeatureId` converts non-integer objects to an integer zero before its small-ID seat dispatch. Its named backrest, fore/aft and height setters reach the same integer vendor properties as the failed percentage path, not a separate verified absolute-geometry transport. The read values are floats and the write values are integers; matching numbers do not establish matching units or packing. Do not try a float-array substitution on this firmware.

Signature presence does not validate OverDrive's IDs, float-array behavior, units, sentinel, batching or mirrored controls on this firmware. Do not reuse its numeric constants as a verified mapping.

The explicit **Set up vehicle readings** action grants and verifies only the declared COMMON/GET permissions, including both GEARBOX_COMMON and GEARBOX_GET. An already-held getter permission alone does not satisfy the SDK factory permission check. The action retries each missing permission independently, reports what remains unavailable and never grants SET permissions or enables recovery policy.

Developer inspection now reads the named absolute driver-seat getters only when the owner has granted BODYWORK_COMMON and BODYWORK_GET. It reports raw values, including zero/error sentinels, as unverified candidates. It does not save them to a preset, reinterpret the old format, acquire BODYWORK_SET or send a vehicle command. No context replacement or permission bypass is included.

## Diagnostics and next parked test

The private credential-protected report retains up to 128,000 characters, and its viewer now shows that whole retained bounded report. Child-lock baseline and changed-state observations are recorded privately, together with recognized sides and mappings. A baseline does not trigger a gesture. No per-tick file logging is added.

The private credential-protected report records preset IDs, capture wall times, persisted save readback, raw/source/format values, original request uptime, initiating surface, P/EPB results and exception chains. Planned dispatches are separate from setter returns, and neither is called physical completion. Startup and public logs do not include these details.

Next, inspect the saved records and compare percentage and absolute candidate readings against small owner-operated native adjustments. Identify the native recall payload for this exact firmware before another setter test. Existing presets remain in their original unverified format. Do not convert them based only on matching ranges or repeated cached reads.

The current candidate has a hard block in the shared live path and again at OEM writer binding/invocation. All card, panel, widget, child-lock and editor requests remain unable to write. Demo behavior is unchanged. Unflushed private memory logs cannot survive process death.
