<!-- SPDX-FileCopyrightText: 2026 SeeMoreMagnifier contributors -->
<!-- SPDX-License-Identifier: GPL-3.0-or-later -->
# SeeMoreMagnifier — Architecture and Implementation Notes

This document describes the current v1 implementation and calls out differences from a more formal layered/state-machine design. Component names and examples below are descriptive unless identified as actual source files; they are not a promise that a class with that exact name exists.

## 1. Overview

App Name: SeeMoreMagnifier  
Platform: Android  
Primary Use Case: Low-vision magnifier using the rear camera with simple, reliable gestures.

Core behaviors:

- Request camera permission and start a full-screen live rear-camera preview.
- Pinch to zoom the live preview.
- Tap the magnifier content in live mode to freeze an in-memory still; if still capture fails, the app attempts to use the latest analysis frame.
- Tap anywhere on the frozen image to resume the live preview.
- Expose semantic freeze/resume actions and camera-error retry semantics for screen-reader users.

The v1 implementation prioritizes a responsive representation of the visible preview over maximum still-image resolution. It does not persist captured images.

---

## 2. Goals and Non-Goals

### 2.1 Goals

- Extremely simple UX for low-vision users.
- Fast startup and minimal UI chrome.
- Stable, predictable gestures.
- Correct camera and activity lifecycle handling.
- Bounded frame memory and efficient camera use; battery and memory behavior require device validation.
- Useful TalkBack and large-display behavior.
- A small architecture that can support future accessibility and camera features.

### 2.2 Non-Goals (v1)

- No saving or sharing images.
- No filters, OCR, or ML.
- No front camera.
- No multi-screen navigation.
- No custom image library or gallery.
- No persistence of the frozen frame across process death.

---

## 3. Technical Stack and Project Constraints

### 3.1 Core Libraries

- Kotlin.
- Jetpack Compose for the UI.
- CameraX `Preview`, `ImageAnalysis`, and in-memory `ImageCapture`.
- AndroidX Lifecycle for camera binding and Compose lifecycle integration. The v1 implementation does not use a `ViewModel` or `Flow` for magnifier state.
- Kotlin coroutines for Compose animation and gesture-related work.

`ImageCapture` is used in v1 for in-memory freeze captures with a decode bound of 2560 pixels per dimension. It does not persist or export captured images. The camera uses latency-oriented capture mode, so this is a higher-resolution alternative to the capped analysis frame, not a promise of maximum still-image quality.

### 3.2 Platform decisions

- The app targets Android phones and tablets with a rear-facing camera.
- Minimum SDK: 26; compile SDK: 36; target SDK: 36. These values are also recorded with the pinned toolchain in [`development.md`](development.md).
- The application ID and namespace are `com.seemoremagnifier`.
- Portrait and landscape are supported unless usability testing establishes that portrait-only operation is preferable.
- The UI uses edge-to-edge layout with camera content behind system bars where appropriate.
- `CameraPreviewContent` sets `keepScreenOn` while the permission-granted camera screen is composed. This currently includes frozen mode; the screen is not explicitly allowed to sleep while frozen.
- Compose and Material components provide standard system accessibility integration. TalkBack and large-display behavior were manually checked on the connected device; a dedicated contrast mode or user-configurable accessibility settings are not implemented.
- The rear-camera preview and frozen frame are not mirrored. The frozen frame is oriented and center-cropped to the measured preview viewport aspect ratio.

### 3.3 Permission

The only runtime permission is:

- `android.permission.CAMERA`

The app does not initialize or bind CameraX before camera permission is granted.

---

## 4. High-Level Architecture

The code is organized into UI, domain-policy, and camera/frame packages, but the current implementation is not a strict presentation/domain/data architecture. `MainActivity.kt` hosts the permission flow, Compose screen, UI state, event handling, and lifecycle coordination. The camera controller and small domain policies provide focused helpers. Avoid adding forwarding-only layers; a `ViewModel` and formal state machine are possible future refactors, not current components.

### 4.1 Components

#### UI and interaction

- `MainActivity.kt` contains `MainActivity`, the permission Composable, the live/frozen magnifier Composables, overlays, and their state/event wiring.
- `AndroidCameraPreview` hosts CameraX `PreviewView`.
- `MagnifierAccessibilitySemantics.kt` applies content/state descriptions and custom actions.
- `MagnifierGestureDetector.kt`, `GestureTapTracker.kt`, and `LiveGesturePolicy.kt` implement gesture recognition and its small policy helpers.
- The permission prompt and retry are handled in the permission Composable in `MainActivity.kt`; there is no standalone permission-manager class, rationale text, or direct application-settings shortcut after permanent denial.

#### Camera and frame support

- `CameraPreviewController.kt` owns CameraX provider access, rear-camera selection, `Preview`/`ImageAnalysis`/`ImageCapture` binding, zoom/torch/focus operations, and rebind/unbind/shutdown behavior. Despite its name, it is not preview-only.
- `AnalysisFrameConverter.kt` converts analysis and still `ImageProxy` data to bounded, oriented/cropped bitmaps; `CameraPreviewController` closes the proxies in its callbacks.
- `FrozenFrameStore.kt` defines `LatestFrameStore`, which owns the latest analysis candidate and releases replaced or rejected frames. The Compose UI owns a transferred frozen bitmap.
- `CameraError.kt`, `FreezeTiming.kt`, `ZoomPolicy.kt`, and `FrozenPanPolicy.kt` hold error labels, debug timing, and policy helpers.

The code does not currently contain separate `MagnifierViewModel`, `MagnifierUiState`, `MagnifierEvent`, `CameraFrameAnalyzer`, `CameraController`, `CameraPermissionManager`, or `FrozenFrame` classes. Those names in older design sketches should not be read as existing source files.

### 4.2 Package structure

```text
com.seemoremagnifier/
  MainActivity.kt                 # Activity, permission flow, Compose UI/state
  ui/magnifier/                   # Errors and gesture/accessibility helpers
  domain/magnifier/               # Zoom and frozen-pan policies
  data/camera/                    # CameraPreviewController, converter, timing
  data/frame/                     # LatestFrameStore
```

---

## 5. User-Visible Modes and State

The following mutually exclusive state model describes the intended user-visible modes; it is not a `MagnifierUiState` type implemented in v1. The current Compose implementation represents these modes through `frozenFrame`, `cameraError`, `previewBound`, `isCapturingStill`, `isRebindingAfterFreeze`, and associated state in `MainActivity.kt`. This is less formal than a sealed state machine and should be considered a refactoring opportunity if more modes or transitions are added.

### 5.1 Modes and state

The effective modes are permission/request or explanation, initializing/binding, live, capturing, frozen, and error (including a frozen frame with a rebind error). Zoom, torch, focus indicator, and gesture-help visibility are additional UI state. `LatestFrameStore<Bitmap>` owns the candidate bitmap until it is transferred to the Compose frozen-image state; the frozen bitmap is then explicitly recycled when replaced/released or when the UI is disposed.

### 5.2 Event handling

Permission results, taps, long presses, pinch/pan gestures, retry requests, display changes, and lifecycle callbacks are handled by Compose callbacks/effects and `MainActivity`; there is no centralized `MagnifierEvent` reducer.

### 5.3 Transitions

```text
Permission request/explanation -> binding  permission becomes granted
Binding -> Live                            camera is successfully bound
Live -> Capturing                          tap or long press starts still capture
Capturing -> Frozen                        still or analysis fallback is available
Capturing -> Live                          capture fails and no fallback is available
Frozen -> Rebinding                        user resumes live preview
Rebinding -> Live                          preview is bound and streaming
Rebinding -> Frozen with error             camera rebind fails; frozen image is retained
Camera error -> retry                      user selects the retry action
```

The requested zoom ratio is preserved through freeze/resume and camera rebinds, then clamped to the camera's reported minimum and maximum. A successful resume only releases the frozen image after binding/streaming succeeds; a rebind error keeps the frozen image available with retry semantics.

---

## 6. Camera Pipeline

### 6.1 CameraX integration

`CameraPreviewController` uses `ProcessCameraProvider` and binds these use cases:

- `Preview` for the visible live camera stream.
- `ImageAnalysis` for the latest frame used by freeze.

The controller uses `CameraSelector.DEFAULT_BACK_CAMERA` and binds to the screen's lifecycle owner. It reports camera initialization, binding, zoom, capture, and runtime failures as `CameraError` callbacks; these are not dispatched through a domain-event stream.

The current CameraX configuration is:

- `PreviewView.ScaleType.FILL_CENTER` is used for the live preview; this fills the available area and center-crops where aspect ratios differ.
- Preview, analysis, and still-capture target rotations are initialized from display rotation and updated in place when the display rotates. Orientation/size configuration changes do not recreate the activity, so live/frozen mode and in-memory UI state survive rotation.
- The rear-camera preview is not mirrored; no software mirror transform is applied in v1.
- CameraX `ZoomState` is the source of applied zoom and min/max bounds. UI preserves the requested ratio across camera rebinds and clamps it to the new camera bounds.
- `ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST` backpressure.
- Explicit target rotation based on the current display rotation.
- No explicit CameraX preview/analysis resolution selector is configured. The converter bounds the resulting bitmap dimensions; that is not the same as selecting a display-appropriate camera output resolution.
- The frozen frame is oriented and center-cropped to the measured preview viewport aspect ratio to approximate the preview's `FILL_CENTER` crop. This is a bitmap transform, not a shared CameraX output-resolution strategy.
- Neither the rear-camera preview nor frozen bitmap is mirrored.

Devices with no available rear camera are detected and reported as a camera error.

### 6.2 Freeze strategy

`ImageAnalysis` supplies an immediate fallback frame while `ImageCapture` produces the higher-resolution frozen still. Both are bound with `Preview`; still captures are kept in memory and never written to shared storage.

`CameraPreviewController` installs the analyzer callback on its single-thread analysis executor. The conversion and frame-store behavior is:

1. Receives an `ImageProxy` on the controller's single-thread analysis executor.
2. Converts the newest frame from YUV to a displayable representation, applying rotation metadata.
3. Applies the same rear-camera orientation and preview crop rules used by the UI; it does not mirror the frame.
4. Replaces the previous candidate frame in `LatestFrameStore`.
5. Closes every analysis `ImageProxy`, including error paths.

Analysis-frame errors currently reach an empty callback at the UI binding sites, so conversion/allocation errors in that path are not presented to the user. Successful still capture is converted off the main thread and the still `ImageProxy` is closed in the callback's `finally` block. If still capture or conversion fails, the UI attempts to freeze the latest analysis candidate; the current failure callback does not display a separate fallback notice. If no analysis candidate exists, the fallback leaves the screen live rather than showing an explicit no-frame message.

When the user freezes the live preview:

1. Keep the live preview and analysis running while capture is pending, show a white screen flash (currently 80% peak opacity fading over 450 ms), and ignore repeated taps/pinch zoom.
2. A normal tap skips point metering and starts capture using the camera's current focus state, taking advantage of autofocus already running during preview.
3. A stationary long press shows the animated focus reticle/status, requests autofocus/metering at the pressed position through `PreviewView.meteringPointFactory`, then captures on focus completion or after a 900 ms timeout. The reticle converges on the touch point, turns green on focus lock, and pulses amber if focus is not confirmed. Movement beyond touch slop or a second pointer cancels long-press recognition. Focus failure does not block freezing.
4. `ImageCapture` uses `CAPTURE_MODE_MINIMIZE_LATENCY`. Decode the still off the main thread, correct orientation, and center-crop to the preview aspect ratio. Decode sampling bounds the still to 2560 pixels per dimension. This is a higher-resolution alternative to the capped analysis fallback, while latency mode favors speed over maximum quality.
5. On successful still delivery, show that still as the frozen image and unbind camera use cases. On failure, attempt to freeze the latest analysis candidate; fallback is silent in the current UI.

Debug builds include monotonic freeze timing traces for focus, still capture, decode, and frame presentation. Filter them with `adb logcat -s FreezeTiming:D`; the timing instrumentation is disabled in release builds.

When the user unfreezes:

1. Keep the frozen frame visible and pause the candidate-frame store.
2. Rebind preview and analysis, preserving the current zoom request subject to the new camera bounds.
3. On successful binding, release the frozen frame, resume candidate analysis, and return to `Live`.
4. On rebind failure, retain the frozen frame and expose a retry action; do not claim the app is live.

### 6.3 Zoom

In `Live`, pinch input is converted to a clamped zoom ratio and applied through CameraX `CameraControl.setZoomRatio`.

Zoom uses multiplicative gesture scale (`newRatio = currentRatio × gestureScale`), clamped to `ZoomState.minZoomRatio..maxZoomRatio`. The indicator displays the latest CameraX-reported `zoomRatio`, formatted to one decimal place. Pinch gestures may issue a newer zoom command before an earlier `setZoomRatio` future completes; CameraX cancellation of a superseded request is expected and must not be surfaced as a camera error or interrupt preview.

In `Frozen`, the same logical zoom ratio is applied through a clipped scale transform to the frozen frame, relative to the zoom ratio at capture. Frozen zoom is clamped to the capture ratio and the camera's maximum, so scaling cannot reveal empty regions beyond the captured image. A one-finger drag pans the frame while keeping its edges within the viewport; panning is constrained independently on each axis and has no effect where the fitted image has no overflow. A quick single-finger pan continues as a bounded exponential-decay fling (minimum velocity 100 px/s, at most 700 ms); hitting an image edge ends the fling without overscroll. New touch input, pinch zoom, or leaving frozen mode cancels the active fling. Pinch zoom preserves the current pan where possible and clamps it to the new image bounds. The requested ratio is saved when live pinch zoom changes and at freeze start; camera zoom-state updates while capturing or frozen must not reset it. Resume restores this ratio to CameraX. The frozen image must not be re-read from the camera. Camera torch is available only in live preview; an upward swipe of at least 72 dp with predominantly vertical movement toggles it. If enabled when freeze is requested, the torch remains on through still exposure, switches off when camera use cases are unbound, and a successful resume restores it after the preview is streaming. The current UI gives brief visual feedback for successful torch changes; unsupported hardware, toggle failure, or restoration failure does not currently produce the documented unavailable/failure notice.

Orientation and screen-size changes are handled without activity recreation so a frozen frame remains in memory and the current live/frozen mode, zoom, and torch preference are not discarded. In frozen mode, the image keeps its captured orientation and apparent on-screen size as the viewport changes; the viewport-fit scale at capture is compensated for against the new fit scale, while the displayed logical zoom ratio remains unchanged. Pan is reclamped to the new bounds, allowing immediate panning if the image now extends beyond the viewport, and uncovered areas are black. While live, the existing preview surface is retained and CameraX preview, analysis, and capture target rotations are updated to match the display.

For a stationary long press, freeze uses a best-effort autofocus/metering request at the pressed location, followed by an in-memory `ImageCapture` still on focus completion or after a 900 ms timeout. A normal tap skips point metering. The still is decoded at up to 2560 pixels per dimension, compared with the analysis fallback's 1280×720 maximum. Capture/conversion errors attempt to freeze the latest analysis candidate; if none is available, the screen remains live without an explicit no-frame message.

The shared gesture detector cancels tap eligibility when movement exceeds Compose touch slop, a second pointer is added, or the pointer sequence is cancelled; a pinch or cancelled gesture therefore does not trigger freeze/resume.

---

## 7. Gesture and Event Handling

The UI uses Compose pointer input to recognize gestures and invokes callbacks directly; the descriptions below are behaviors, not event classes dispatched to a reducer:

- A tap toggles freeze or resume.
- A pinch changes zoom by the gesture scale.
- Live upward swipe (at least 72 dp and predominantly vertical) toggles the camera torch; the gesture is disabled for frozen content.
- Live pinch scales the requested camera zoom; a one-pointer pan is ignored. Frozen one-pointer drags pan the image, and pinch gestures zoom it.

Tap and pinch recognition share one pointer pipeline in both live and frozen modes. A second pointer, movement beyond Compose touch slop, or a cancelled pointer sequence permanently cancels tap eligibility for that gesture. Pinch zoom is updated from the change in average pointer span, and a tap fires only on a clean release. Independent overlapping `clickable` and transform detectors must not cause a pinch to freeze the image.

Gesture behavior:

- The magnifier content area is the tap target; help controls and permission/error actions are separate controls.
- A tap with movement below Compose touch slop toggles freeze or resume.
- A multi-pointer gesture is never treated as a tap.
- Pinch is available in both live and frozen modes.
- One-finger drags pan the frozen image without revealing empty space or triggering resume.
- Zoom changes are clamped at the camera-reported bounds. No separate accessibility announcement is emitted when a bound is reached.
- Accessibility services can invoke freeze/resume without pointer gestures through semantic custom actions.

---

## 8. Accessibility

### 8.1 Visual accessibility

- Status and controls use theme surfaces over camera content; there is no dedicated high-contrast overlay mode or user-configurable contrast setting.
- Large, readable zoom indicator.
- Frozen mode has a persistent white border; “Frozen” is exposed in the accessibility state description rather than in a visible text banner.
- State descriptions identify live/frozen state; the focus reticle also uses text/status cues when focus is not confirmed.

### 8.2 Interaction accessibility

The magnifier content exposes:

- A screen description such as “Live camera view” or “Frozen image”.
- A state description containing live/frozen status and zoom level.
- A custom action labeled “Freeze image” or “Resume live preview”.
- An accessible retry action for camera errors.

The live and frozen nodes expose state descriptions but are not configured as live regions. Camera-error semantics opt into polite live-region behavior. Explicit announcements on freeze/unfreeze completion and zoom-limit feedback are not implemented.

The app uses standard Compose/Material layouts and semantics and was manually checked for TalkBack and large-display behavior on the connected SM-A566B, as recorded in the Feature 07 validation checklist. This is device validation, not a guarantee for every font scale, display, theme, or accessibility service. The code does not define a persisted `AccessibilityConfig`; no custom accessibility preferences are provided.

---

## 9. Lifecycle, Memory, and Privacy

### 9.1 Lifecycle

- Camera use cases are bound to the Activity's lifecycle owner only after permission is granted. CameraX lifecycle binding controls active camera streaming as the owner starts/stops; the app does not have a separate `ON_STOP`/`ON_START` reducer or ViewModel.
- Camera use cases are explicitly unbound when the user freezes a frame. They are also unbound on controller shutdown when the Compose camera content is disposed.
- Background/foreground behavior was manually checked on the connected SM-A566B (see the Feature 08 record). While the Activity remains composed in the background, CameraX lifecycle binding stops active camera work, but the app does not explicitly close the candidate store on every `ON_STOP`. Candidate and frozen bitmap cleanup is tied to replacement, ownership transfer/release, or Compose disposal.
- The frozen bitmap is not saved in `SavedStateHandle` or other saved-instance state. Activity configuration changes for orientation/screen sizing are handled without recreation. After process death, in-memory frozen content is not restored; startup follows the permission/camera initialization flow.

### 9.2 Memory

At steady state, the frame store owns at most one candidate bitmap and the UI holds at most one frozen bitmap. Conversion also allocates transient byte arrays, JPEG buffers, and bitmaps; it does not reuse conversion buffers. The implementation:

- Downsample frames to a display-appropriate maximum size.
- Avoid retaining full-resolution camera frames unnecessarily.
- Releases the candidate frame when it is replaced, transferred, rejected, or the store is closed. The store is closed when camera content is disposed; release on every stop/error path is not explicit.
- Bounds decoded analysis and still bitmap dimensions and recycles intermediate bitmaps when cropping/rotation creates replacements.
- Catches allocation errors in conversion/capture paths; analysis errors are not currently surfaced by the UI, and still-capture failure attempts the silent analysis fallback described in Section 6.2.

`LatestFrameStore` owns candidate cleanup and transfers ownership to the UI on freeze. The UI then owns frozen-bitmap release. This separates candidate cleanup from frozen display ownership.

### 9.3 Privacy

- The camera is not accessed before permission is granted.
- Camera use cases are explicitly unbound while frozen. CameraX lifecycle binding suspends active camera use while its lifecycle owner is stopped; the app does not explicitly unbind the use cases from an `ON_STOP` handler.
- Frozen images remain in memory only and are never written to shared storage in v1.
- The permission flow requests camera permission and, after denial, shows a camera-access title and retry action. The current screen does not show the longer rationale text or provide a direct application-settings shortcut after permanent denial.
- Android's system camera privacy indicators apply while CameraX is active.

---

## 10. Current Error Handling

The current error type is `CameraError` (`CameraError.kt`), which defines:

- `PermissionMissing` and `RearCameraUnavailable`.
- `InitializationFailed` and `BindingFailed`.
- `ZoomFailed` and `CaptureFailed`.
- `NoFrameAvailable`.
- `FrameConversionFailed`.
- `FrameAllocationFailed`.

`NoFrameAvailable`, `FrameConversionFailed`, and `FrameAllocationFailed` are enum values, but they are not all surfaced by the UI. `NoFrameAvailable` is not emitted by the freeze UI, analyzer errors are passed to no-op callbacks, and `ZoomFailed` is filtered from the screen-level camera error state.

Current user-visible behavior:

- Permission denied: a permission screen with a camera-access title and retry action; the longer rationale string and direct settings shortcut after permanent denial are not currently shown.
- Camera initialization/binding failure: prominent error text with a retry action.
- Still capture failure: attempt to freeze the analysis candidate; the fallback is silent, and if there is no candidate the app stays live without a surfaced `NoFrameAvailable` message.
- Rebind failure after unfreeze: retain the frozen image and expose camera-error semantics with a retry action; do not claim that the app is live until binding succeeds.
- Internal camera failures are mapped to user-facing strings where the relevant error state is shown; analyzer and zoom failures noted above are exceptions.

Analysis `ImageProxy` objects are closed in `finally`; successfully delivered still `ImageProxy` objects are likewise closed in `finally`. CameraX's still-capture error callback does not deliver an image proxy to close. Debug freeze timing is logged, but general internal camera failures are not comprehensively logged. Image contents are not logged.

---

## 11. Performance and Bounds

Implemented safeguards and configuration:

- Camera use cases are lifecycle-bound, analysis uses `STRATEGY_KEEP_ONLY_LATEST`, and use cases are explicitly unbound while frozen.
- Frame conversion runs on the controller's single-thread executor, not the main thread.
- `LatestFrameStore` retains at most one candidate frame; the UI holds at most one frozen frame. Conversion still allocates intermediate byte arrays, JPEG buffers, and bitmaps; reusable conversion buffers are not implemented.
- The analysis bitmap is capped at 1280×720 and the still bitmap at 2560×2560. These are decode/output bounds, not CameraX stream-resolution bounds; no explicit preview/analysis resolution selector is configured.
- Debug builds provide monotonic freeze timing traces (`FreezeTiming`). This instrumentation does not measure cold startup, battery, or sustained memory use.

Feature 08 records the user's confirmation that broader performance validation is complete. Quantitative measurements and device-by-device results are not preserved in this architecture document, so no numeric startup, battery, or memory claim is made here.

---

## 12. Future Extensions

- Front camera.
- Feedback for unavailable torch hardware and torch toggle/restoration failures; the upward-swipe torch control is implemented.
- Brightness/contrast sliders.
- Edge enhancement filters.
- Saving/sharing frozen frames to shared storage or an export pipeline.
- Preset zoom levels.
- A settings screen for custom accessibility preferences.

Future features must preserve the camera-lifecycle and frame-ownership behavior established in v1. A formal ViewModel/state-machine architecture remains a possible refactor, not an established v1 guarantee.

---

## 13. Summary

The v1 implementation is a Compose-centered magnifier hosted by `MainActivity`, with CameraX work delegated to `CameraPreviewController` and frame/gesture behavior supported by small policy and store classes. It binds `Preview`, `ImageAnalysis`, and in-memory `ImageCapture`; transfers bounded candidate/frozen bitmaps; and unbinds camera use cases while frozen. It does not currently use a ViewModel or Flow-driven state machine, reuse conversion buffers, announce live/frozen or zoom-limit changes through live regions, explicitly unbind camera use cases from an app-level `ON_STOP` handler, or provide quantified performance benchmarks in this document. Feature 08 records the user's confirmation that broader validation is complete, but detailed measurements are not preserved here. Torch control is implemented; failure/unavailable feedback is not. Implementation limitations are described in the relevant sections above.

Build and deployment workflow is defined in [`development.md`](development.md), and test strategy and acceptance criteria are defined in [`testing.md`](testing.md).
