# Settings UI direction

Reviewed 2026-10-21. Keep the dark matte identity: graphite canvas, slightly raised neutral cards, silver type and restrained blue actions. Do not add gloss, ornamental gradients or animated page entrances.

## Recommendation

Settings now uses Compose Material 3. Its theme roles, switches, list items and dialogs provide a coherent baseline, while our typography, corner sizes and palette define the product. The Activity keeps persistence and privileged actions behind typed callbacks. Settings receives an immutable snapshot and emits typed actions. Recomposition keeps scroll and dialog state. The preset page and floating panel remain native Views; the panel preview uses AndroidView interop. Vehicle behavior is unchanged.

| Option | Fit for this app | Decision |
| --- | --- | --- |
| [Compose Material 3](https://developer.android.com/develop/ui/compose/designsystems/material3) | Customizable Android UI components and themes. Works independently of Android Auto templates. Actual DiLink rendering/performance must still be checked. | Implemented for Settings. |
| [Material Components for Views](https://github.com/material-components/material-components-android/blob/master/docs/getting-started.md) | Would fit the current View hierarchy, but the upstream project now states maintenance mode with no planned new feature releases. | Do not add it as an intermediate migration. |
| [AAOS Car UI library](https://source.android.com/docs/automotive/hmi/car_ui) | Components and OEM customization infrastructure, including system-image and resource-overlay integration. It is not a DiLink application skin. | Borrow layout principles, not platform integration. |
| [Android for Cars App Library](https://developer.android.com/training/cars/apps/library) | Host-rendered templates for supported app categories. | Does not replace this sideloaded app's Activity or floating panel. |
| [Kinex](https://kinex.lexwah.com/) | A relevant BYD visual reference for restraint, hierarchy and large controls. Its [public repository](https://github.com/lexwah/kinex-dist) is a release repository, not a reusable UI kit. | Study composition and spacing. Do not assume a library or copy private assets. |

[Google's automotive sizing guidance](https://developers.google.com/cars/design/automotive-os/design-system/sizing) recommends 76dp touch targets. Use this for Settings controls, with 12dp gaps between related actions, 20dp between columns and a clear distinction between section, row and secondary text. This is design guidance, not a claim that DiLink is an Android Auto host.

## Implemented Compose pass

- Drivers and child-lock mappings form the left stack. Home panel, startup and updates form the right stack on wide screens. Narrow layouts use one stack.
- Seat movement stays below Drivers. The Allow seat movement switch stays visible and persists the owner opt-in. The failed writer remains blocked independently.
- Child-lock tiles show the physical side, mapped preset and change affordance. Removing the trial UI does not remove the recognizer or stored bindings.
- Settings actions have 76dp minimum height, explicit gutters and full-width or paired layouts.
- Developer tools are grouped into Diagnostics and Connection. Setup logs open on demand instead of filling the page. File backup controls appear only when the system supplies a compatible document picker.
- Supervised movement, child-input trial and replay screens are removed. Standard Android widget setup is removed, and its receiver is disabled in the live flavor. The separate floating home panel remains. Demo builds retain the conventional widget provider for non-BYD test launchers.

## Review

| Before | After | Why |
| --- | --- | --- |
| Stacked buttons touch with no gutters | Spaced full-width or paired actions | Makes each action distinct and easier to tap |
| Long child-lock mapping button labels | Equal side-labelled mapping tiles | Physical button and assigned preset can be read separately |
| Lone wide section and scattered developer controls | Consistent two-column stacks and named tool groups | Reduces scanning and uneven empty space |
| Standard widget preview and obsolete trials dominate settings | Floating panel setup plus on-demand diagnostics | Keeps the usable path visible |

Emulator screenshots are visual checks only. They cannot validate the native DiLink home host, OEM permissions, seat units or physical actuation.

## Toolchain boundary

The first migration uses Compose BOM 2024.02.00 and Activity Compose 1.8.2 with compiler 1.5.8, matching this repository's Kotlin 1.9.22 and installed compile SDK 34. [Google's compatibility table](https://developer.android.com/jetpack/androidx/releases/compose-kotlin) lists that compiler/Kotlin pair. These are compatible pinned versions, not a claim to use the newest Compose release. A Kotlin 2/SDK upgrade is a separate dependency update. No unsupported compiler-version override is used.
