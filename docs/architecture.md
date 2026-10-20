# SeeMoreMagnifier — Architecture Document (v0.2)

## 1. Overview

App Name: SeeMoreMagnifier  
Platform: Android  
Primary Use Case: Low-vision magnifier using the rear camera with simple, reliable gestures.

Core behaviors:

- Request camera permission and start a full-screen live rear-camera preview.
- Pinch to zoom the live preview.
- Tap anywhere on the content to freeze the most recently available frame.
- Tap anywhere on the frozen image to resume the live preview.
- Provide equivalent semantic actions for screen-reader users.

The v1 implementation prioritizes a responsive representation of the visible preview over maximum still-image resolution. It does not persist captured images.

---

## 2. Goals and Non-Goals

### 2.1 Goals

- Extremely simple UX for low-vision users.
- Fast startup and minimal UI chrome.
- Stable, predictable gestures.
- Correct camera and activity lifecycle handling.
- Minimal battery and memory footprint.
- Useful TalkBack and large-display behavior.
- A small architecture that can support future accessibility and camera features.

### 2.2 Non-Goals (v1)

- No saving or sharing images.
- No filters, OCR, or ML.
- No front camera or torch.
- No multi-screen navigation.
- No custom image library or gallery.
- No persistence of the frozen frame across process death.

---

## 3. Technical Stack and Project Constraints

### 3.1 Core Libraries

- Kotlin.
- Jetpack Compose for the UI.
- CameraX `Preview` and `ImageAnalysis`.
- AndroidX Lifecycle and ViewModel.
- Coroutines and Flow for asynchronous state and camera events.

`ImageCapture` is intentionally not used in v1. It is reserved for a future save/share feature that requires high-resolution still capture.

### 3.2 Platform decisions

- The app targets Android phones and tablets with a rear-facing camera.
- Minimum SDK: 26; compile SDK: 36; target SDK: 36. These values are also recorded with the pinned toolchain in [`development.md`](development.md).
- The application ID and namespace are `com.seemoremagnifier`.
- Portrait and landscape are supported unless usability testing establishes that portrait-only operation is preferable.
- The UI uses edge-to-edge layout with camera content behind system bars where appropriate.
- The app keeps the display awake while a live preview is active and does not force the display to remain awake while frozen.
- System font scale, display scaling, touch exploration, and other system accessibility settings are respected.
- The rear-camera preview and frozen frame are not mirrored. The frozen frame must preserve the preview's orientation and crop.

### 3.3 Permission

The only runtime permission is:

- `android.permission.CAMERA`

The app must not initialize or bind CameraX before permission is granted.

---

## 4. High-Level Architecture

The app uses a small presentation/domain/data separation. It should avoid introducing layers that only forward calls without adding behavior.

### 4.1 Components

#### Presentation layer

- `MagnifierScreen`: Composable screen and layout.
- `PreviewSurface`: Hosts the CameraX `PreviewView`.
- `FrozenImageSurface`: Displays and scales the copied frozen frame.
- `MagnifierOverlays`: Zoom indicator, frozen status, permission/error messages.
- `MagnifierViewModel`: Owns UI state, handles user events, and coordinates camera operations.
- `MagnifierSemantics`: Content descriptions, state descriptions, and accessibility actions.

The presentation layer detects pointer gestures and emits semantic events. It does not directly implement camera lifecycle or image conversion.

#### Domain layer

- `MagnifierUiState`: Explicit state machine described in Section 5.
- `MagnifierEvent`: User and lifecycle events.
- `ZoomPolicy`: Clamps zoom and defines zoom behavior for live and frozen content.
- `MagnifierError`: User-presentable error categories.

Use cases may be introduced when behavior grows, but v1 does not require a separate class for every state transition.

#### Data/platform layer

- `CameraController`: Owns CameraX provider, use-case binding, camera selection, zoom, and camera errors.
- `CameraPreviewController`: v1 preview-only implementation that binds the rear camera and `Preview`; it must not initialize until permission is granted.
- `CameraFrameAnalyzer`: Receives `ImageProxy` frames, converts the latest frame, and closes every proxy.
- `FrozenFrameStore`: Owns the copied frozen frame and its cleanup.
- `CameraPermissionManager`: Encapsulates permission status and opening the application settings page after permanent denial.

The v1 permission entry point currently lives in the launcher UI. It requests camera access at first launch, explains denial, and links to application settings after permanent denial. A dedicated permission manager may be extracted when permission behavior is reused.

### 4.2 Package structure

```text
com.seemoremagnifier/
  ui/
    magnifier/
      MagnifierScreen.kt
      MagnifierViewModel.kt
      MagnifierUiState.kt
      MagnifierEvent.kt
      MagnifierSemantics.kt
    components/
  domain/
    magnifier/
      ZoomPolicy.kt
      MagnifierError.kt
  data/
    camera/
      CameraController.kt
      CameraFrameAnalyzer.kt
    frame/
      FrozenFrame.kt
      FrozenFrameStore.kt
    permissions/
      CameraPermissionManager.kt
```

---

## 5. State Model

Independent booleans are not used to represent the main screen state. They can describe contradictory conditions such as a frozen screen with no frame. Instead, the UI exposes one mutually exclusive state.

### 5.1 State

```kotlin
sealed interface MagnifierUiState {
    data object Initializing : MagnifierUiState
    data object PermissionRequired : MagnifierUiState
    data class Live(
        val zoomRatio: Float,
        val minZoomRatio: Float,
        val maxZoomRatio: Float
    ) : MagnifierUiState
    data class Frozen(
        val frame: FrozenFrame,
        val zoomRatio: Float,
        val minZoomRatio: Float,
        val maxZoomRatio: Float
    ) : MagnifierUiState
    data class Error(val error: MagnifierError) : MagnifierUiState
}
```

`FrozenFrame` is a domain model with explicit ownership and cleanup rather than an unbounded raw `Bitmap` field exposed throughout the UI.

### 5.2 Events

```text
ScreenStarted
ScreenStopped
PermissionResult(granted)
ToggleFreeze
ZoomGestureChanged(scale)
RetryCamera
```

### 5.3 Transitions

```text
Initializing -> PermissionRequired   permission is not granted
Initializing -> Live                  camera is successfully bound
PermissionRequired -> Initializing    permission is granted
Live -> Frozen                        latest frame is available and copied
Live -> Error                         camera or freeze operation fails
Frozen -> Live                        frame is released and camera is rebound
Frozen -> Error                       camera rebind fails
Error -> Initializing                 user selects retry
```

The current zoom ratio is preserved when transitioning between `Live` and `Frozen`. Zoom is clamped to the camera's reported minimum and maximum values.

---

## 6. Camera Pipeline

### 6.1 CameraX integration

`CameraController` uses `ProcessCameraProvider` and binds these use cases:

- `Preview` for the visible live camera stream.
- `ImageAnalysis` for the latest frame used by freeze.

The controller uses `CameraSelector.DEFAULT_BACK_CAMERA` and binds to the screen's lifecycle owner. It reports camera initialization, binding, zoom, and runtime failures as domain events.

The implementation must define and test the following CameraX configuration:

- `PreviewView.ScaleType.FILL_CENTER` is used for the live preview; this fills the available area and center-crops where aspect ratios differ.
- Preview target rotation is initialized from the `PreviewView` display rotation. Activity recreation on orientation change rebinds with the new display rotation.
- The rear-camera preview is not mirrored; no software mirror transform is applied in v1.
- `ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST` backpressure.
- Explicit target rotation based on the current display rotation.
- A preview and analysis resolution strategy appropriate for the display, rather than always retaining the camera's maximum resolution.
- A `PreviewView` scale type whose crop behavior is documented and shared by the frozen-frame transformation.
- Consistent rear-camera orientation and crop between live and frozen display; do not mirror either representation.

The implementation must handle devices with no available rear camera as a camera error.

### 6.2 Freeze strategy

V1 uses `ImageAnalysis`, not `ImageCapture`, because the required behavior is to freeze a recent view of what the user is seeing.

`CameraFrameAnalyzer`:

1. Receives an `ImageProxy` on a background executor.
2. Converts the newest frame from YUV to a displayable representation, applying rotation metadata.
3. Applies the same rear-camera orientation and preview crop rules used by the UI; it does not mirror the frame.
4. Replaces the previous candidate frame in `FrozenFrameStore`.
5. Closes every `ImageProxy`, including error paths.

When the user taps freeze:

1. The ViewModel requests the latest candidate frame.
2. If no frame is available, it remains live and presents a transient freeze-failure message.
3. If a frame is available, ownership transfers to the frozen state.
4. Camera analysis and preview use cases are unbound to reduce battery use and stop camera access while frozen.
5. The frozen frame is displayed with UI scaling/cropping.

When the user unfreezes:

1. The frozen frame is released.
2. The camera use cases are rebound.
3. The app returns to `Live` only after binding succeeds.
4. A rebind failure produces `Error` with a retry action.

### 6.3 Zoom

In `Live`, pinch input is converted to a clamped zoom ratio and applied through CameraX `CameraControl.setZoomRatio`.

In `Frozen`, the same logical zoom ratio is applied through a crop/scale transform to the frozen frame. The frozen image must not be re-read from the camera.

Pinch input must not generate a tap. A gesture detector must cancel a pending tap when movement exceeds the tap threshold or multiple pointers are detected.

---

## 7. Gesture and Event Handling

The UI uses Compose pointer input to recognize semantic gestures:

- `Tap` becomes `ToggleFreeze`.
- `Pinch` becomes `ZoomGestureChanged(scale)`.

Tap and pinch recognition are coordinated in one gesture pipeline or through explicit cancellation rules. Independent overlapping `clickable` and transform detectors must not cause a pinch to freeze the image.

Gesture behavior:

- The full content area is a tap target.
- A tap with movement below the configured slop threshold toggles freeze.
- A multi-pointer gesture is never treated as a tap.
- Pinch is available in both live and frozen modes.
- Zoom changes are clamped and do not generate errors when a boundary is reached.
- Accessibility services can invoke freeze/unfreeze without pointer gestures.

---

## 8. Accessibility Architecture

### 8.1 Visual accessibility

- High-contrast overlays with sufficient contrast against the camera image.
- Large, readable zoom indicator.
- Clear and persistent “Frozen” status while frozen.
- Optional haptic feedback for freeze/unfreeze, disabled when the user has disabled haptics or the device cannot provide them.
- No essential state communicated by color alone.

### 8.2 Interaction accessibility

The main content exposes semantics that include:

- A screen description such as “Live camera view” or “Frozen image”.
- A state description containing live/frozen status and zoom level.
- A custom action labeled “Freeze image” or “Resume live preview”.
- An accessible retry action for camera errors.
- Announcements when freeze/unfreeze completes and when zoom reaches a limit, without excessive repeated announcements.

The app respects system font scale, display scaling, TalkBack/touch exploration, and reduced-motion preferences. `AccessibilityConfig` is not a required persisted settings object in v1; custom accessibility preferences can be added with the future settings feature.

---

## 9. Lifecycle, Memory, and Privacy

### 9.1 Lifecycle

- Camera use cases are bound only while permission is granted and the screen is started.
- Camera use cases are unbound while frozen.
- On `ON_STOP`, camera use cases are stopped and transient camera resources are released.
- On `ON_START`, the ViewModel re-establishes the appropriate live or initialization state.
- A frozen frame is retained only for the current screen/process instance. It is not placed in `SavedStateHandle`.
- On process recreation, the app returns to initialization or live mode; it does not attempt to restore a bitmap.

### 9.2 Memory

Only one candidate frame and one frozen frame may be retained. The implementation must:

- Downsample frames to a display-appropriate maximum size.
- Avoid retaining full-resolution camera frames unnecessarily.
- Reuse conversion buffers where practical.
- Release the candidate frame on replacement, unfreeze, stop, and error.
- Handle bitmap allocation failure as a user-visible freeze error rather than crashing.

`FrozenFrameStore` owns cleanup so that UI code does not need to manage bitmap lifetime directly.

### 9.3 Privacy

- The camera is not accessed before permission is granted.
- Camera use cases are stopped while frozen and when the app is not visible.
- Frozen images remain in memory only and are never written to shared storage in v1.
- The app provides a clear permission rationale and follows Android camera privacy indicators.

---

## 10. Error Handling

`MagnifierError` should distinguish at least:

- `PermissionDenied`.
- `CameraUnavailable`.
- `CameraBindFailed`.
- `CameraRebindFailed`.
- `NoFrameAvailable`.
- `FrameConversionFailed`.
- `FrameAllocationFailed`.

Behavior:

- Permission denied: full-screen explanation, retry action, and settings shortcut after permanent denial.
- Camera unavailable or bind failure: full-screen or prominent retry message.
- Freeze failure: transient error message while remaining in live mode.
- Rebind failure after unfreeze: error state with retry; do not claim that the app is live until binding succeeds.

All `ImageProxy` instances must be closed in success and failure paths. Internal failures should be logged without logging image contents.

---

## 11. Performance

- Use lifecycle-bound CameraX use cases.
- Use `STRATEGY_KEEP_ONLY_LATEST` for analysis.
- Perform conversion off the main thread.
- Avoid allocating a new large bitmap for every analysis frame.
- Stop analysis and preview while frozen.
- Store at most one candidate frame and one frozen frame.
- Apply zoom updates without unnecessary recomposition or camera rebinding.
- Measure startup, freeze latency, memory use, and battery behavior on representative low-end hardware.

---

## 12. Future Extensions

- Front camera and torch.
- Brightness/contrast sliders.
- Edge enhancement filters.
- Saving/sharing frozen frames using `ImageCapture` or an export pipeline.
- Preset zoom levels.
- A settings screen for custom accessibility preferences.

Future features must preserve the state-machine and camera-lifecycle guarantees established in v1.

---

## 13. Summary

This architecture supports a small, reliable Android magnifier while making the important implementation decisions explicit. CameraX `Preview` plus `ImageAnalysis` provides the live view and recent-frame freeze behavior. A sealed UI state prevents contradictory states, the ViewModel coordinates lifecycle-aware operations, and the UI owns gesture and accessibility semantics. Camera resources are released while frozen or backgrounded, image memory is bounded, and the design leaves a clear path for future capture, accessibility, and camera features.

Build and deployment workflow is defined in [`development.md`](development.md), and test strategy and acceptance criteria are defined in [`testing.md`](testing.md).