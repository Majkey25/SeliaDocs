# Stylus button and pending-ink verification

## Cause and change

The canvas selected temporary barrel-button erasing only at pointer-down. Changing
the button while the pen touched the page did not split the interaction. Eraser
callbacks could also run before the asynchronous native ink callback, so an erase
could miss ink written immediately before it.

The canvas now handles button-state changes during MOVE and generic button
events. It resolves the tracked pointer ID, finishes the preceding segment, and
starts the new segment at the same page position. The selected toolbar tool does
not change. An ordered queue delivers ink and editing callbacks in input order.

Back, Undo, Redo, tool changes, and page navigation wait for native ink handoff before entering the existing database
save barrier. Page disposal and activity stop retain finished real input when the
native renderer cannot hand it off. Unfinished canceled strokes are discarded.
Late native callbacks do not save a fallback-committed stroke again.

The fallback uses the existing AndroidX Ink brush and stroke constructor. Its
input capture follows the installed Ink 1.1.0-alpha07 conversion: captured page
transform, physical unit length, event-time origin, historical MOVE samples, and
device-supported optional axes. It excludes predicted input and duplicate x/y/time
samples. No new dependency, permission, or storage format is required.

## Reproduction

Huawei YAL-L21, Android 10, serial `BQLDU19927002646`, isolated debug package.
The original implementation failed all three focused tests on September 8:

- `barrelButtonMovesPreserveInkEraseInkOrder`
- `genericBarrelButtonsPreserveInkEraseInkOrder`
- `completedStrokeSurvivesImmediateCanvasDetach`

Local evidence: `.reference/tmp/device-qa-20260908-102300-309.log`.

## Verification status

- The first patched phone run passed 8 of 9 focused checks. A new immediate-Undo test exposed another bypass: Undo removed the preceding stroke instead of the pending stroke. Log: `.reference/tmp/device-qa-20260908-111334-318.log`.
- Routing Undo/Redo and keyboard page navigation through the existing action barrier fixed that failure. The input group reported `OK (58 tests)`, with three expected external-input/API skips. Log: `.reference/tmp/device-qa-20260908-111516-077.log`.
- The same 58-test group passed again on version 15. Log: `.reference/tmp/device-qa-20260908-111955-329.log`. This includes zoomed native versus retained input and bitmap parity, same-millisecond movement, both buttons, palm pointer indexing, repeated flush, unfinished-input cancellation, late highlighter handoff, immediate Back, and immediate Undo.
- Nearby editor, text, image, and page-navigation workflows reported `OK (77 tests)`. Log: `.reference/tmp/device-qa-20260908-111650-298.log`. Groups overlap; do not add their counts.
- The signed build, lint, and 98 JVM tests passed. APK signing has the expected single certificate, and 16 KB alignment passed. AAB verification passed with the existing self-signed-certificate, timestamp, POSIX-attribute, and JAR stream-order warnings.
- The signed version-15 APK installed as an update without clearing data. Existing text and ink remained visible. `ReleaseInkSmokeTest` reported `OK (1 test)` using native-window pixels after injected stylus input.
- The phone returned to Home and was released at 11:22:40 CEST. No ADB operation remained active.

Initial version-15 artifact SHA-256, not published:

```text
E86C0B49BEF641390D7C703DCEBA6F587F7D52151CF0E4668455BA7D47A48030  app-release.apk
C59C0CFDA21D4A47BF32E2DA3E5C8D1D311A881A22FFD7864A4C5F0CB393B381  app-release.aab
```

Physical active-pen pressure, tilt, latency, hover, and vendor button mappings
remain unverified. Injected stylus events on the Huawei do not certify that hardware.

## Close following Undo

Android 10 CI passed, but Android 17 CI exposed a race in `immediateUndoTargetsNewestPendingInk`: two strokes remained instead of one. `requestAction` replaced a pending Undo with Back while native handoff was still suspended. Compose idleness does not guarantee native ink handoff.

`EditorActionState` now retains one deferred Close behind pending Undo/Redo. The history action executes first; Close then enters the existing save barrier as a fresh action. Failed saves and new sessions discard deferred Close. Other pending-action behavior is unchanged.

`EditorPendingHistoryTest` failed before this fix and passed afterward. The full JVM suite contains 100 passing tests. The final bundle uses version code 16 because Google Play had already accepted version 15 into an unpublished draft. Version 15 is not a release candidate.

Before this final state fix, the Huawei broad core run reported `OK (321 tests)` with two expected opt-in skips. The signed APK passed pinch-then-stylus native-pixel verification. Broad log: `.reference/tmp/device-qa-20260908-114012-118.log`.

Version 16 passed all 37 editor tests, including immediate Undo and Back, on the Huawei. Log: `.reference/tmp/device-qa-20260908-114739-648.log`. Its signed build, lint, expected signer, and 16 KB alignment passed. AAB verification returned the same documented warnings. The signed update installed without clearing data, and the phone returned to Home at 11:49:40 CEST. Android CI remains the merge gate.

Final version-16 SHA-256:

```text
4803195FBAD111C35F0751F3C769865FC88F36E8A70F2BD66F21F051D326B7AF  app-release.apk
05F97FD3BA6B96B5A3EB772994584CD259168F190F26B95C5AB4956399599B4A  app-release.aab
```

## Previous release

Play Console on September 8 showed `14 (0.6.1-beta.1)` available in the existing
closed Alpha track and no unpublished changes. This does not include this patch.
