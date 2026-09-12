# PDF study tools verification

This work extends the [Flexcil workflow comparison](2026-09-12-flexcil-highlighting.md). It is not a full parity release.

## Implemented paths

- The isolated PDF service returns selected text and normalized rectangles on Android 15+. Older devices and scanned pages use local word OCR when image OCR is enabled.
- Lasso selection offers copy, highlight, underline, strikeout, and linked excerpts. Annotations retain their text, color, and rectangles through editing, Undo, export, and backup.
- Text and cropped PNG excerpts retain source page/rectangle metadata. Captures use the same PDF, text, element, and ink rendering as export.
- Room migration 4→5 adds four nullable element fields. Backup v6 reads older formats and remaps source links without resolving missing sources to unrelated imported pages.

## Checks completed

- JVM suite: 117 tests passed. Command: `./gradlew :app:testDebugUnitTest --console=plain`.
- Debug app/test APKs build. Android lint completes with existing dependency-update and platform warnings.
- Huawei Android 10, `device-qa-20260912-182345-120.log`: 19 focused backend/data tests passed, with two expected API-35 skips.
- Huawei, `device-qa-20260912-183742-644.log`: PDF highlight visibility, Undo/Redo, reopening, nearby typing, capture rendering, and data checks passed. Two excerpt UI scenarios failed and remained under investigation.
- Huawei, `device-qa-20260912-182306-508.log`: exported highlight, underline, and strikeout pixel checks passed after correcting the blend mode.
- Huawei, `device-qa-20260912-185625-626.log`: all five PDF study UI flows and two fit/zoom/pan finger-selection flows passed. The new checkerboard crop-detail test failed before its fix.
- Huawei, `device-qa-20260912-190013-471.log`: 66 input, capture, and export tests passed, with three expected hardware/emulator skips. The checkerboard test passed after removing crop-canvas dimensions from the image decode target.
- Android 17 CI passed the backend increment in [run 34705329886](https://github.com/Majkey25/SeliaSheets/actions/runs/34705329886). Its Android 10 job exposed the deletion-test synchronization race described below.
- A clean version-19 build passed all JVM/lint tasks and produced signed APK/AAB outputs. APK verification confirms one expected signer, package `com.majkeylab.seliadocs`, version `0.7.0-beta.1`, minSdk 29, targetSdk 37, and 16 KB alignment. AAB verification reports the existing self-signed-certificate, timestamp, POSIX-attribute, and JAR stream-order warnings.
- Huawei version 19, `device-qa-20260912-191714-168.log`: all 28 final PDF study, capture, export, legacy-ID, migration, and backup tests passed.

## Bugs and test defects found

- Legacy `PorterDuff.Mode.MULTIPLY` also multiplies alpha. Using it for export made black source text translucent. `BlendMode.MULTIPLY` preserves destination opacity. The strict black-text pixel assertion failed before the change and passed afterward. See the [Android blend equations](https://developer.android.com/reference/android/graphics/BlendMode).
- Element transforms used a 24-point minimum. Small text marks now use their own minimum, preserving glyph-sized bounds during movement and duplication.
- Finger drawing disabled also left single taps without a selection action. Lasso-mode taps now use page coordinates adjusted for zoom and pan.
- The first markup visibility selector used two test tags on one modifier. Tests use the existing element tag instead.
- The initial yellow-pixel detector counted gray RGB values 201–209. Screenshot inspection identified all 52 false positives. The corrected detector requires color separation as well as brightness; visibility and black-text thresholds remain unchanged.
- The destination test matched both the dialog notebook and the background page-location button. Exact-title selectors are now scoped to the dialog.
- Source references accept the same bounded opaque identifiers as legacy pages, including Unicode and punctuation. They are resolved through Room, not filesystem paths or URLs.
- Captures use 2048-pixel page rasters and a 4MP inserted-image decode budget. Ordinary PDF export retains its 4096-pixel/16MP limits. The capture bitmap-buffer calculation is at most 64 MiB; this is not a measured heap peak.
- Android 10 CI exposed an existing deletion-test race: Room emitted the deleted stroke list before selection/history controls updated. The test now awaits the complete state and retains its kept-stroke and Undo assertions.

## Remaining acceptance

Verify the final version-19 signed artifacts and re-run final compatibility/allocation checks. Full CI for the integrated UI changes remains pending. Split/pop-up workspaces, audio-linked notes, study masking, annotation-preserving PDF export, and other parity items remain open in the [implementation checklist](../superpowers/specs/2026-09-12-flexcil-parity.md).

Injected stylus events exercise Android input routing. They do not certify a physical active pen's pressure, tilt, palm rejection, or vendor buttons. The shared Huawei has no attached active pen.
