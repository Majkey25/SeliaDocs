# Stylus input regression checks — September 6, 2026

## Scope

- Keep a zoomed page stationary while a palm remains down after the pen or hardware eraser lifts. A new finger gesture must still pan.
- Preserve pencil direction when segment erasing interpolates samples across the zero-angle boundary, in either direction. Do not invent orientation when the input lacks it.
- Exercise cancellation on an attached ink canvas and verify that the next stroke commits. The previous cancellation test never attached its canvas, so its empty-output assertion could pass without processing input.
- Make new pen strokes respond across the full pressure range. Keep legacy strokes on their original brush family.

## Source findings

`PageCanvas` suppresses viewport updates only while a stylus is currently pressed. Its page-turn arbiter remembers pen ownership, but its pan calculation does not. A remaining palm can therefore move the page after pen-up.

`InkCodec.denseSamples` interpolates orientation as a scalar. A direction change from just below a full turn to just above zero incorrectly passes through the opposite direction. The existing normalized-orientation helper can preserve circular interpolation without a new dependency.

Android distinguishes pointer cancellation from other fingers lifting. See [stylus palm rejection](https://developer.android.com/develop/adaptive-apps/cookbook/stylus-palm-rejection). Ink's optional orientation metadata is defined by [StrokeInput](https://developer.android.com/reference/kotlin/androidx/ink/strokes/StrokeInput).

## Verification status

- Huawei `BQLDU19927002646`, Android 10: both palm tests failed before the fix, with the page center moving from x=540 to x=630. The orientation test failed at 5.849069 radians. Log: `.reference/tmp/device-qa-20260906-104050-351.log`.
- After those fixes, the codec/stylus/viewport group reported `OK (50 tests)`, with three expected API/external-input skips. Log: `.reference/tmp/device-qa-20260906-104319-083.log`.
- Ink selection and PDF-export tests reported `OK (14 tests)`. Log: `.reference/tmp/device-qa-20260906-104424-640.log`.
- The cancellation test initially dispatched input in the same callback as `setContentView`, before layout/render traversal. Posting input to the next UI turn made it pass without synthetic hover or a fixed sleep. It asserts cancellation and identifies the next committed stroke by its starting coordinate.
- The stock pressure pen rendered 20 px at pressure 0.15 and 24 px at 0.9. Runtime inspection showed its pressure input range is 0.8–1.0, with a size multiplier of 1.0–1.5. It intentionally does not vary width below 80% pressure; the exploratory >1.5 ratio assertion was not a valid contract for that legacy family.
- New `RESPONSIVE_PEN` strokes use pressure 0–1 with a size multiplier of 0.35–1.25. The stronger width-response assertion passes for this family. Real rendered-pixel checks for pressure, touch-pressure filtering, and pencil tilt/orientation after encode/decode reported `OK (3 tests)`. Log: `.reference/tmp/device-qa-20260906-104933-278.log`.
- Final version-14 integration run reported `OK (118 tests)`, with three expected API/external-input skips. This includes full-range and missing-pressure rendering, the page's active pen brush, cancellation recovery, viewport input, backup export/validation/restore, and legacy brush preservation. Log: `.reference/tmp/device-qa-20260906-111028-975.log`.
- The signed APK/AAB build and lint passed. JVM reports contain 98 tests, zero failures, and zero errors. APK verification found one expected signer, and 16 KB alignment passed. AAB verification succeeded with the existing self-signed-certificate, missing-timestamp, and JAR stream-order warnings.

`dumpsys input` reports `ExternalStylusConnected: false`. These tests exercise Android input routing and rendering, not a physical active-pen digitizer. The phone was released at 10:49:38, with no ADB operation pending.

Version 14's signed APK was installed as an update without clearing data. Native-window screenshot checks passed for stylus ink, finger ink, and stylus ink after a live pinch. Existing typed text and legacy strokes remained visible. The second phone window was released at 11:16:27.

Verified release artifact hashes:

```text
8CC868FDCB34689A8C7CFB6D5B5F9E475AFA277096BED61D468701DEEC3484ED  app-release.apk
B5D7005AD67D69A56A0481F491F7C8C7F5EC36947731C90479BD9B4045F0B076  app-release.aab
```

## Backup compatibility

`PRESSURE_PEN` remains unchanged so old handwriting does not change appearance. New strokes store `RESPONSIVE_PEN`. Exports use format 5, so released format-4 readers reject them before decoding an unknown brush. Formats 1–4 remain readable. A JVM regression failed with format 4 and passed after the version change.

## Remaining input gap

Barrel-button state is currently chosen at pen-down. Switching between ink and eraser while the tip stays down needs an ordered interaction handoff: ink completion is asynchronous, so a naive split could erase before the preceding ink is persisted. This change does not alter that behavior.
