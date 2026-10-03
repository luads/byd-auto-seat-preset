# Dark matte UI pass

Kinex's restrained hierarchy and spacing informed this pass. Colours remain charcoal, silver and subdued blue. No external UI source or assets were copied.

Preset content is centred and capped at 1280 dp. Two favourites have equal weight, with quieter Edit controls, seat artwork and a small action cue. Demo cards call the existing capture or recall path; live cards remain unavailable. Edit is a separate target. No active driver is inferred. The stock widget entry is replaced by Home panel settings because the tested BYD launcher did not display the standard widget.

Driver settings use compact name/rename rows. Section outlines are removed and secondary surfaces are quieter. Home panel tiles have softer borders, more space for names and smaller artwork; the native widget follows the same name sizing. No Recall label is added.

Validation: 60 JVM tests passed in each of four variants. Both debug APKs built and both debug lint checks passed. Installed on emulator-5580 only. Reviewed 1920×1080 and 540×960 captures, plus saved demo cards. Fixed a narrow footer weight found during the visual review. Separate Edit opens its menu. No recall was pressed. Original demo presets were restored byte for byte; emulator geometry was restored. The panel was reviewed through its normal settings preview.

Generic live captures: [presets](screenshots/presets-dark-review.png), [settings](screenshots/settings-dark-review.png), [narrow presets](screenshots/presets-dark-review-narrow.png), [narrow settings](screenshots/settings-dark-review-narrow.png).

Prepared for v0.1.9 (versionCode 10). Screenshots were captured during development with the 0.1.8 version label. Real BYD launcher placement, touchscreen readability, sunlight contrast and overlay interference still need a parked-car check. Native widget changes were built but not inspected in a launcher host during this pass.
