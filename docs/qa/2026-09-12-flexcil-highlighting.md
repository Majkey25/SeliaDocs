# Flexcil reference and highlighting regressions

The replacement `Notes_260911_155212 (1).pdf` opens as a 23-page PDF. The supplied screenshot identifies Flexcil Notes & PDF Reader. The document describes workflows and proposes an architecture; it is not evidence of Flexcil's implementation. Its framework, renderer, storage-format, and latency claims are not used as verified facts. No proprietary code or assets were copied.

## Verified reference behavior

| Workflow | Flexcil documentation | SeliaSheets status in this change |
| --- | --- | --- |
| Pen/highlighter size, color, opacity | [Pen toolbar](https://support.flexcil.com/hc/en-us/articles/8164459936537-Pen-toolbar) | Existing width/color controls gain opacity; changing color preserves opacity. |
| Separate drawing and content-selection gestures | [Pen and gesture modes](https://support.flexcil.com/hc/en-us/articles/8131456080025-Pen-mode-and-Gesture-mode) | Pen, eraser, and lasso exist. Semantic PDF text-selection gestures are not implemented. |
| Select words/paragraphs, highlight or copy selected text | [Large-circle selection](https://support.flexcil.com/hc/en-us/articles/6716975907609--Gesture-Select-text-using-large-circle-gesture) | Freehand ink is not a text-linked annotation. The PDF sandbox currently renders pages but supplies no glyph bounds. |
| Pop-up document and resizable split view | [Android split view](https://support.flexcil.com/hc/en-us/articles/33109748826521-Using-Split-View-on-Android) | Not implemented. Requires separate editor sessions and independent input ownership. |
| Captured passage linked to its source | [Reference links](https://support.flexcil.com/hc/en-us/articles/8427582550553-What-is-a-Reference-Link) | Not implemented. Source-page/rectangle metadata must survive backup, duplication, and source deletion. |
| Audio linked to annotation timing | [Audio recording and syncing](https://support.flexcil.com/hc/en-us/articles/8006910155801-Audio-recording-and-syncing) | Not implemented. Requires explicit microphone consent and recording lifecycle tests. |

The requested feature set remains broader than this change. Text geometry and selection are prerequisites for reliable text-aligned highlights and linked excerpts. Full Flexcil parity is not claimed.

## Reproduced bug

The earlier viewport fix does not address every highlighter failure. Holding `LibraryMutationGate`, drawing over typed text, and changing a brush setting reproduced another failure on Huawei Android 10. The finished ink appeared, then disappeared after the settings update while its database write remained pending.

`FinishedInkView.setStrokes` replaced newly handed-off ink with an unchanged database snapshot during `AndroidView.update`. A redraw is not a storage acknowledgement.

Evidence:

- `device-qa-20260912-162106-046.log`: delayed-save visibility test failed; three opacity UI tests passed.
- `device-qa-20260912-162754-783.log`: first fix passed the delayed-save and saved-highlight checks plus three opacity tests.
- `device-qa-20260912-163201-913.log`: forced SQLite insert failure, queued Undo, and delayed-save refresh checks passed.

The first fix also exposed a multi-stroke case: completing one save must not hide another pending highlight. `savingFirstHighlightDoesNotHideSecondPendingHighlight` failed against the saved intermediate APK in `device-qa-20260912-165526-726.log`.

The final renderer tracks explicit save begin/completion events. It retains both pending ink and the latest supplied database snapshot. Once the pending group finishes, it reconciles the displayed strokes with that snapshot. Failed saves remove only their original stroke. This avoids dropping a later highlight or retaining ink removed by an intervening eraser action.

`device-qa-20260912-165632-894.log` reports eight passing focused tests, covering staggered saves, ink/erase/ink, settings refresh, forced SQLite failure, queued Undo, and opacity controls. The alpha validation also passed JVM boundary checks from 0 through 255 while preserving RGB and unrelated settings. Full-suite and publication results belong with the merged PR and release.

The tests inspect rendered pixels, not only database rows. Injected stylus events do not certify a physical pen's pressure, tilt, palm rejection, or vendor buttons.
