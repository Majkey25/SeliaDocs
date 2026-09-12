# Highlighter visibility at zoom

## Reproduction

On the Huawei YAL-L21 running Android 10, an unzoomed highlighter appeared while drawing and after saving. At 2x zoom, the live stroke was displaced from the pen.

The failure reproduced through both direct View dispatch and real OS input injection. The OS-injected stroke ran from screen `(188, 975)` to `(892, 975)`. The center pixel's blue channel stayed at 250 instead of changing to the highlighter color. Native event logging confirmed the correct local end coordinate `(1232, 996)` in the page-sized view. Its live SurfaceView occupied 1760 x 2490 pixels at screen `(-340, -21)`.

The failure was in live presentation, not the incoming coordinate conversion. Earlier saved-stroke tests did not check these screen pixels while the pen was down.

Local evidence:

- `.reference/tmp/device-qa-20260912-103330-155.log`: unzoomed native view passed.
- `.reference/tmp/device-qa-20260912-103551-862.log`: complete editor saved-highlighter check passed.
- `.reference/tmp/device-qa-20260912-103714-135.log`: zoomed pixel check failed, other two checks passed.
- `.reference/tmp/device-qa-20260912-104944-564.log`: OS-injected zoomed case failed before the fix.
- `.reference/tmp/highlighter-zoom-2-before.png`: generated blank test page with displaced live ink.

## Fix

`InkCanvasView` keeps its page-sized input and completed-stroke layer. `InProgressStrokesView` uses the viewport dimensions, independent of zoom. `motionEventToViewTransform` translates incoming page-view coordinates into that live surface. Four rectangular masks hide the area outside the paper. Stored brush inputs, eraser/lasso coordinates, and backup formats are unchanged.

The first candidate sized the surface to the page/viewport intersection. The original three pixel checks and both pan-direction checks passed. Logs: `.reference/tmp/device-qa-20260912-105641-012.log` and `.reference/tmp/device-qa-20260912-105934-830.log`. Temporary production coordinate logging was removed.

That first version 17 candidate passed 100 JVM tests, lint, the signed build, the expected signer check, and 16 KB APK alignment. AAB verification returned the existing self-signed, timestamp, POSIX, and JAR stream-order warnings. Huawei reported `OK (322 tests)` for the core suite and `OK (57 tests)` for the input suite, with two and three expected opt-in/API skips respectively. Logs: `.reference/tmp/device-qa-20260912-111713-000.log` and `.reference/tmp/device-qa-20260912-112111-260.log`. The signed update preserved existing text/ink and passed the pinch-before-stylus pixel smoke test.

Android 10 CI passed on `cb2afcc`. Android 17 passed its native pressure/pinch stage but found an existing synchronous-selection assumption in `recreationRetainsDraftAndSystemBackWaitsForFlush`. The helper now waits for the asynchronous tool selection, retaining the disabled-input, draft-retention, and saved-text assertions. Final CI and publication results are recorded in [PR #27](https://github.com/Majkey25/SeliaSheets/pull/27) and the release notes.

The next Android 17 run, [34686500521](https://github.com/Majkey25/SeliaSheets/actions/runs/34686500521), passed the native stylus and core stages but crashed while pinching. AndroidX Ink's V33 renderer replaced its viewport thread during a size change while an old callback was still running. The callback then checked against the replacement thread. The V33 source is identical in Ink alpha07 and alpha08; changing dependencies would not address this path.

The viewport-sized surface avoids buffer-size changes during pinch. A regression test failed on the intersection candidate: the live dimensions changed from 880 x 1245 to 936 x 2088. A first even-odd mask candidate hid the highlighter on Huawei. Removing that mask restored live ink but exposed drawing outside the paper. Four rectangular masks passed the fit, zoom, and paper-edge checks in `.reference/tmp/device-qa-20260912-123011-179.log`. The final candidate still requires the complete device/CI gates and a new signed build before publication.

These are injected stylus tests on a physical phone, not physical active-pen pressure, tilt, or vendor-button certification.

## Reference document

The supplied `Notes_260911_155212.pdf` contains 4,544,074 zero bytes. Poppler cannot read a PDF header or trailer, and a binary scan finds no nonzero content. Its application, screenshots, and feature list cannot be recovered from this copy. No claim of reference-app parity or download is made.
