# Flexcil workflow parity

## Target

Implement the documented Android study workflows in SeliaSheets while keeping notebooks, chapters, paper pages, and existing data. This is behavioral parity, not copied proprietary code, branding, or assets. Existing validated ink fixes remain the baseline.

## Constraints

- Android 10+, native Kotlin/Compose/Ink, English default.
- PDF parsing stays in the isolated service. Raster OCR runs locally with the existing bundled model.
- No destructive migration, silent partial backup, hidden network transmission, or invented hardware certification.
- Shared Huawei test windows remain short and coordinated. No local emulator storage is added.

## Execution and acceptance

1. **Text-aware PDF selection and annotations.** Native text selection where available, OCR fallback on older/scanned PDFs, visible selected bounds, copy/highlight/underline/strikeout, edit/delete, Undo/Redo, persistence, export, and backup v6. Verify native and OCR fixtures, malformed input, zoom alignment, cancellation, migration, and round trips.
2. **Linked excerpts.** Extract text or a cropped region to another notebook with source page/rectangle metadata. Return to the source; preserve links through duplication/restore and keep excerpts readable when a source disappears.
3. **Document workspace.** Separate primary/secondary editor sessions, resizable split/pop-up views, independent pages/zoom, safe close/rotation, and navigation history. Verify simultaneous editing without input or save leakage.
4. **Study and navigation tools.** Search PDF content and annotations, page slider/outline/bookmarks, annotation filtering, pen presets, straight/round/square highlighting, gesture selection/capture/erase, and optional tool auto-deselect. Preserve normal one-finger reading and pen/palm ownership.
5. **Audio-linked notes.** Explicit permission, foreground recording lifecycle, bounded recordings, playback/speed/seek, stroke timestamps, export/share, and backup. Verify interruption/recreation and clearly separate synthetic audio tests from microphone hardware evidence.
6. **Review/export/transfer.** Masking study content, sticky notes, presentation pointer, annotation-only/flattened export, linked-document transfer, and opt-in cloud-provider workflows. External services require normal user-directed sign-in/share flows; no secret extraction or automatic document disclosure.

Each slice must pass focused tests and live checks before integration. A release passing tests is not a claim that the remaining checklist is complete. Native active-pen hardware validation remains separate from injected events.

Reference: [Flexcil workflow/source map](../../qa/2026-09-12-flexcil-highlighting.md).
