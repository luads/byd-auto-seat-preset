# Preset home review

The horizontal header reused a full-width vertical action layout. Settings consumed its row and the weighted title wrapped into a tall column. The same constraint hid card names beside Edit.

| Before | After |
| --- | --- |
| Settings requested the entire horizontal row. | Settings wraps its content. |
| Title could collapse to zero width and make the header very tall. | Title keeps the remaining width, stays on one line and ellipsizes if necessary. |
| Preset-card Edit requested full row width. | Edit wraps its content, leaving the driver name visible. |

The change preserves two directly tappable favourites and the existing dark matte design. Screenshots and hierarchy dumps are local ignored QA artifacts because device preferences may contain private labels.

Emulator checks passed at 1920×1080 and 960×720, density 240. On the wide screen the title spans 1673 pixels, Settings uses 151 pixels, and the subtitle begins directly below the 84-pixel header. Both driver names and Edit actions are visible. Narrow title/action bounds also pass without the former tall gap.
