# Stock BYD home integration investigation

Investigated 2026-10-03 on the owner's Australian Sealion 7. All device operations were read-only. This is evidence for this firmware, not a claim about every DiLink launcher.

## Result

The standard pin flow creates our Android widget, but it binds to Launcher3's inactive host. The native dashboard card host is inside SystemUI. Changing our RemoteViews size, configuration activity or pin callback cannot move the widget into that host.

No app-accessible route for adding an arbitrary third-party widget to the native dashboard was found in the inspected card paths. Keep the standard AppWidget for conventional launchers and emulator tests. Explore an opt-in home-only floating panel for this car.

## Live evidence

The app log records pin support, acceptance, confirmation, bound ID 9, and update-sent events. `dumpsys appwidget` records cached RemoteViews for that ID.

| Host | ID | Callback | Providers |
| --- | --- | --- | --- |
| `com.android.launcher3` | 1024 | null | Seat Presets, widget 9 |
| `com.android.systemui` | 2048 | active | Bluetooth, My Car, music and navigation |

The resolved HOME activity is `com.android.launcher3/.home.MainActivity`. Thus the default home package alone does not identify the visible dashboard widget host. A null callback is a point-in-time observation, not proof that the host can never listen.

Raw dumps, app log, screenshot and OEM APK copies remain outside the repository in `/private/tmp/seat-widget-car-20261003/`. A later screenshot shows CarPlay in the foreground and is not a dashboard-layout reference. No OEM assets or decompiled source are copied into this app.

## Static evidence

These paths refer to private local decompilation, not files distributed in this repository.

- `com/byd/card/floatcard/CardAdapter.java`, constructor: SystemUI creates AppWidgetHost 2048 and starts listening. Its binding helpers can bind providers in its own card list.
- `com/byd/card/utils/AppUtils.java`, `initCardPackages`: the initial widget list contains specific navigation, music, My Car and Bluetooth components.
- `com/byd/card/floatcard/DragCardView.java`, `initCardData`: saved widget-card entries can resolve providers by package. This is an internal persisted list, not an app registration API.
- `DragCardView.addWidgetItem`: a generic package fallback exists. Its caller is SystemUI's internal ADD_NAVI event handler in `FloatCardService`.
- `com/byd/card/view/broadcast/AppInstallBroadcastReceiver.java`: the readable package-update branch emits ADD_NAVI for specific stock/navigation packages. A third-party package is not included. The first-install anonymous branch has a decompilation error, so it must not be treated as completely recovered.
- `FloatCardService.registerCardBroadcast` and `CardReceiver.onReceive`: inspected external actions control card visibility and app/settings UI; no arbitrary-widget registration action was found there.
- `com/byd/card/model/AddCardModel.java`, `queryData`: the Add Cards picker loads app records and shortcut records, not arbitrary AppWidget providers.
- `com/byd/card/provider/ShortcutProvider.java`, `checkPermission`: OEM shortcut access is restricted to `com.gpack.agent` and `com.byd.browser`, including reads. Seat Presets is not eligible.

JADX 1.5.6 produced 14 errors for SystemUI and 19 for Launcher. The result above combines successfully recovered methods with live host evidence; it is not an exhaustive proof that no other integration exists. A fallback decode of the receiver parent succeeded but did not resolve the anonymous first-install branch.

APK SHA-256:

- SystemUI: `798e84011d9c3f6582ae15919494c06f206bfcb0440844059cd69e4e59d0f8db`
- Launcher: `502834b032be8d2372a59f85b0c32d17c8be6579d085f934f3e6e0b0e6e03fb3`

## Next prototype

[BYDMate's documented floating widget](https://github.com/AndyShaman/BYDMate/blob/main/README.en.md#floating-widget) is an overlay, not a native dashboard card. Its published behavior is visible across apps. Our proposed panel is narrower in scope and remains untested.

1. Use the matte identity and two equal favourite targets. Start with a compact panel around 360 × 180 dp, then measure it against the actual home layout. Names and tap areas take priority over artwork. Keep configuration in the app.
2. Make overlay setup explicit and off by default. First test visibility and dismissal with no vehicle actuation. Do not grant permissions or install via ADB without owner authorization.
3. Validate a reliable home-visibility signal before enabling automatic placement. Prefer event-based foreground information. Hide on uncertain foreground state, CarPlay, camera, navigation, settings and app screens. Do not add a broad accessibility service or continual ADB polling just to guess foreground state.
4. Keep the window non-focusable, limit its touchable area to the panel, and provide an immediate hide/off action. Capture screenshots on home and after leaving home. Verify that system bars and stock controls remain usable.
5. Reuse the same recall controller. Opening, dragging, resizing, startup and foreground changes never recall a preset. Live recall remains disabled until the physical control contract and fresh eligibility readings are validated.
6. Verify permission recovery and BYD autostart independently. No wake locks, wakeup alarms or VPN. Do not call the one-tap goal achieved until home placement and actual recall are both validated.

Do not edit SystemUI preferences, replace stock packages or spoof an allowed shortcut-provider package to obtain placement.

## Implemented follow-up, 0.1.4

The opt-in panel now has saved dragging and sizing plus a limited window-identity accessibility observer. The owner explicitly approved its persistent access and in-app local-ADB grants after reviewing the scope. This observer requests no screen-content or key capability. Exact home events show the panel; other window events hide it. Native emulator transitions pass; BYD event coverage remains pending owner validation. See README for setup and docs/VALIDATION.md for measured checks.

## 0.1.11 field iteration

The owner confirmed the 30-second overlay rendered, but it also stayed over the global menu. That preview bypassed the home observer. The bypass service is removed. The persistent panel now uses exact resolved-home window identity plus reported gearMode P, and listens to gear-setting notifications. It hides on another window or screen off and has no timeout. This is display eligibility, not verified telemetry freshness or movement permission. Panel taps open the selected saved-position test. Real menu/camera/CarPlay window callbacks need another field check.
