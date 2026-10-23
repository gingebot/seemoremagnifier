package com.seemoremagnifier

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Surface
import android.hardware.display.DisplayManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import android.view.View
import com.seemoremagnifier.data.camera.CameraPreviewController
import com.seemoremagnifier.data.camera.FreezeTiming
import com.seemoremagnifier.data.frame.LatestFrameStore
import com.seemoremagnifier.domain.magnifier.ZoomPolicy
import com.seemoremagnifier.domain.magnifier.FrozenPanPolicy
import com.seemoremagnifier.ui.magnifier.CameraError
import com.seemoremagnifier.ui.magnifier.LiveGesturePolicy
import com.seemoremagnifier.ui.magnifier.magnifierAccessibilitySemantics
import com.seemoremagnifier.ui.magnifier.detectMagnifierGestures
import androidx.camera.core.ZoomState
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job

class MainActivity : ComponentActivity() {
    private var displayRotation by mutableIntStateOf(Surface.ROTATION_0)
    private val displayManager by lazy { getSystemService(DisplayManager::class.java) }
    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = Unit

        override fun onDisplayRemoved(displayId: Int) = Unit

        override fun onDisplayChanged(displayId: Int) {
            updateDisplayRotation(displayId)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        updateDisplayRotation()
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    MagnifierPermissionScreen(targetRotation = displayRotation)
                }
            }
        }
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        updateDisplayRotation()
    }

    override fun onStart() {
        super.onStart()
        displayManager.registerDisplayListener(displayListener, Handler(Looper.getMainLooper()))
        updateDisplayRotation()
    }

    override fun onStop() {
        displayManager.unregisterDisplayListener(displayListener)
        super.onStop()
    }

    private fun updateDisplayRotation(displayId: Int? = null) {
        val display = window.decorView.display ?: return
        if (displayId == null || display.displayId == displayId) {
            displayRotation = display.rotation
        }
    }
}

@Composable
internal fun MagnifierPermissionScreen(targetRotation: Int = Surface.ROTATION_0) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val permissionPreferences = remember(context) {
        context.getSharedPreferences("camera_permission", Context.MODE_PRIVATE)
    }

    var permissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    var hasRequestedPermission by remember {
        mutableStateOf(permissionPreferences.getBoolean("requested", false))
    }
    var showPermissionExplanation by remember(permissionGranted) {
        mutableStateOf(!permissionGranted && permissionPreferences.getBoolean("requested", false))
    }
    var cameraError by remember { mutableStateOf<CameraError?>(null) }
    var retryCameraTrigger by remember { mutableIntStateOf(0) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasRequestedPermission = true
        permissionGranted = granted
        showPermissionExplanation = !granted
        cameraError = null
    }

    // Re-check permission when returning from the system settings screen.
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                val isGranted = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.CAMERA,
                ) == PackageManager.PERMISSION_GRANTED
                permissionGranted = isGranted
                if (isGranted) {
                    cameraError = null
                } else if (hasRequestedPermission) {
                    showPermissionExplanation = true
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(permissionGranted, showPermissionExplanation) {
        if (permissionGranted) cameraError = null
    }

    LaunchedEffect(hasRequestedPermission, permissionGranted) {
        if (!permissionGranted && !hasRequestedPermission) {
            permissionPreferences.edit().putBoolean("requested", true).apply()
            hasRequestedPermission = true
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    when {
        permissionGranted -> {
            CameraPreviewContent(
                targetRotation = targetRotation,
                onCameraError = { cameraError = it },
                onCameraReady = { cameraError = null },
                cameraError = cameraError,
                retryCameraTrigger = retryCameraTrigger,
                onRetryCamera = { retryCameraTrigger++ },
            )
        }

        showPermissionExplanation -> {
            PermissionMessage(
                title = stringResource(R.string.camera_permission_title),
                actionLabel = stringResource(R.string.retry_permission),
                onAction = {
                    permissionPreferences.edit().putBoolean("requested", true).apply()
                    permissionLauncher.launch(Manifest.permission.CAMERA)
                },
            )
        }

        else -> Box(modifier = Modifier.fillMaxSize())
    }
}

@Composable
private fun CameraPreviewContent(
    targetRotation: Int,
    cameraError: CameraError?,
    onCameraError: (CameraError) -> Unit,
    onCameraReady: () -> Unit,
    retryCameraTrigger: Int,
    onRetryCamera: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val hostView = LocalView.current
    DisposableEffect(hostView) {
        val wasKeepingScreenOn = hostView.keepScreenOn
        hostView.keepScreenOn = true
        onDispose { hostView.keepScreenOn = wasKeepingScreenOn }
    }
    val gestureHelpPreferences = remember(context) {
        context.getSharedPreferences("gesture_help", Context.MODE_PRIVATE)
    }
    var hasDismissedGestureGuide by remember {
        mutableStateOf(gestureHelpPreferences.getBoolean("dismissed", false))
    }
    var showGestureGuide by remember { mutableStateOf(false) }
    val previewController = remember(context) { CameraPreviewController(context) }
    val frameStore = remember { LatestFrameStore<Bitmap> { bitmap -> bitmap.recycle() } }
    var previewView by remember { mutableStateOf<androidx.camera.view.PreviewView?>(null) }
    var previewBound by remember { mutableStateOf(false) }
    var viewportWidth by remember { mutableIntStateOf(0) }
    var viewportHeight by remember { mutableIntStateOf(0) }
    var previewStreamObserver by remember {
        mutableStateOf<androidx.lifecycle.Observer<androidx.camera.view.PreviewView.StreamState>?>(null)
    }
    var zoomState by remember { mutableStateOf<ZoomState?>(null) }
    var torchAvailable by remember { mutableStateOf(false) }
    var torchEnabled by remember { mutableStateOf(false) }
    var restoreTorchAfterResume by remember { mutableStateOf(false) }
    var torchTogglePending by remember { mutableStateOf(false) }
    var torchFeedbackTrigger by remember { mutableIntStateOf(0) }
    var torchFeedbackEnabled by remember { mutableStateOf(false) }
    var savedZoomRatio by rememberSaveable { mutableFloatStateOf(1f) }
    var frozenZoomRatio by remember { mutableFloatStateOf(1f) }
    var frozenBaseZoomRatio by remember { mutableFloatStateOf(1f) }
    var frozenCaptureViewportWidth by remember { mutableIntStateOf(0) }
    var frozenCaptureViewportHeight by remember { mutableIntStateOf(0) }
    var frozenPanOffset by remember { mutableStateOf(Offset.Zero) }
    val scope = rememberCoroutineScope()
    var frozenPanFlingJob by remember { mutableStateOf<Job?>(null) }
    var frozenFrame by remember { mutableStateOf<Bitmap?>(null) }
    var frozenFrameTrace by remember { mutableStateOf<FreezeTiming.Trace?>(null) }
    var freezeFlashTrigger by remember { mutableIntStateOf(0) }
    var showFrozenBorder by remember { mutableStateOf(false) }
    var isCapturingStill by remember { mutableStateOf(false) }
    var showFocusIndicator by remember { mutableStateOf(false) }
    var focusPointPosition by remember { mutableStateOf(Offset.Zero) }
    var focusLocked by remember { mutableStateOf<Boolean?>(null) }
    var focusAnimationKey by remember { mutableIntStateOf(0) }
    var isRebindingAfterFreeze by remember { mutableStateOf(false) }
    val latestZoomState = rememberUpdatedState(zoomState)
    val latestTorchEnabled = rememberUpdatedState(torchEnabled)
    val latestTorchAvailable = rememberUpdatedState(torchAvailable)
    val latestTorchTogglePending = rememberUpdatedState(torchTogglePending)
    val latestIsCapturingStill = rememberUpdatedState(isCapturingStill)
    val latestFrozenFrame = rememberUpdatedState(frozenFrame)
    val latestPreviewView = rememberUpdatedState(previewView)
    val latestCameraError = rememberUpdatedState(cameraError)
    val frozenRenderScale = frozenFrame?.let { frame ->
        val viewportCompensation = FrozenPanPolicy.viewportScaleCompensation(
            captureViewportWidth = frozenCaptureViewportWidth,
            captureViewportHeight = frozenCaptureViewportHeight,
            viewportWidth = viewportWidth,
            viewportHeight = viewportHeight,
            imageWidth = frame.width,
            imageHeight = frame.height,
        )
        (frozenZoomRatio / frozenBaseZoomRatio).coerceAtLeast(1f) * viewportCompensation
    } ?: 1f
    val currentZoomErrorHandler: (CameraError) -> Unit = { error ->
        if (error != CameraError.ZoomFailed) onCameraError(error)
    }
    val latestZoomErrorHandler = rememberUpdatedState(currentZoomErrorHandler)
    LaunchedEffect(frozenFrame, isRebindingAfterFreeze, isCapturingStill, cameraError) {
        if (frozenFrame == null || isRebindingAfterFreeze || isCapturingStill || cameraError != null) {
            frozenPanFlingJob?.cancel()
            frozenPanFlingJob = null
        }
    }
    val freezeWithLatestAnalysisFrame: () -> Unit = {
        val fallback = frameStore.freezeLatest()
        if (fallback == null) {
            isCapturingStill = false
            showFrozenBorder = false
        } else {
            showFrozenBorder = true
            frozenBaseZoomRatio = savedZoomRatio
            frozenZoomRatio = savedZoomRatio
            frozenCaptureViewportWidth = viewportWidth
            frozenCaptureViewportHeight = viewportHeight
            frozenPanOffset = Offset.Zero
            frozenFrame = fallback
            isCapturingStill = false
            torchEnabled = false
            torchAvailable = false
            previewController.unbind()
        }
    }
    val latestFallbackHandler = rememberUpdatedState(freezeWithLatestAnalysisFrame)
    LaunchedEffect(previewBound, cameraError, hasDismissedGestureGuide) {
        if (previewBound && cameraError == null && !hasDismissedGestureGuide) {
            showGestureGuide = true
        }
    }
    LaunchedEffect(frozenFrame, frozenFrameTrace) {
        val trace = frozenFrameTrace
        if (frozenFrame != null && trace != null) {
            withFrameNanos { }
            FreezeTiming.mark(trace, "frozen-frame-next-compose-frame")
        }
    }
    val liveCaptureHandler: (Offset, Boolean) -> Unit = { tapPosition, focusAtPoint ->
        if (cameraError == null && frozenFrame == null && !isCapturingStill) {
            showFocusIndicator = focusAtPoint
            if (focusAtPoint) {
                focusPointPosition = tapPosition
                focusLocked = null
                focusAnimationKey++
            }
            restoreTorchAfterResume = torchEnabled
            torchTogglePending = false
            val trace = FreezeTiming.start()
            frozenBaseZoomRatio = savedZoomRatio
            frozenZoomRatio = savedZoomRatio
            frozenCaptureViewportWidth = viewportWidth
            frozenCaptureViewportHeight = viewportHeight
            frozenPanOffset = Offset.Zero
            showFrozenBorder = !focusAtPoint
            freezeFlashTrigger++
            isCapturingStill = true
            val view = previewView
            previewController.focusAndCapture(
                trace = trace,
                focusAtPoint = focusAtPoint,
                x = tapPosition.x,
                y = tapPosition.y,
                previewWidth = view?.width ?: 0,
                previewHeight = view?.height ?: 0,
                onFocusResult = { focused ->
                    if (focusAtPoint) focusLocked = focused
                },
                onCaptured = { captured ->
                    frameStore.freezeLatest()?.let { if (!it.isRecycled) it.recycle() }
                    frameStore.pause()
                    frozenPanOffset = Offset.Zero
                    frozenFrame = captured
                    frozenFrameTrace = trace
                    showFrozenBorder = true
                    isCapturingStill = false
                    torchEnabled = false
                    torchAvailable = false
                    previewController.unbind()
                    FreezeTiming.mark(trace, "frozen-frame-state-set size=${captured.width}x${captured.height}")
                },
                onError = { error ->
                    FreezeTiming.mark(trace, "capture-error fallback=$error")
                    latestFallbackHandler.value()
                    if (frozenFrame != null) {
                        frozenFrameTrace = trace
                        FreezeTiming.mark(trace, "fallback-frame-state-set")
                    }
                },
            )
        }
    }
    val latestLiveCaptureHandler = rememberUpdatedState(liveCaptureHandler)

    LaunchedEffect(targetRotation) {
        previewController.updateTargetRotation(targetRotation)
    }
    LaunchedEffect(frozenFrame, frozenRenderScale, viewportWidth, viewportHeight) {
        val frame = frozenFrame ?: return@LaunchedEffect
        frozenPanOffset = FrozenPanPolicy.clamp(
            offset = frozenPanOffset,
            viewportWidth = viewportWidth,
            viewportHeight = viewportHeight,
            imageWidth = frame.width,
            imageHeight = frame.height,
            scale = frozenRenderScale,
        )
    }
    DisposableEffect(previewController) {
        onDispose {
            previewStreamObserver?.let { observer ->
                latestPreviewView.value?.previewStreamState?.removeObserver(observer)
            }
            previewController.shutdown()
            frameStore.close()
            latestFrozenFrame.value?.let { if (!it.isRecycled) it.recycle() }
        }
    }

    val finishCameraResume = {
        frozenFrame?.let { if (!it.isRecycled) it.recycle() }
        frozenFrame = null
        frameStore.resume()
        isRebindingAfterFreeze = false
        onCameraReady()
        if (restoreTorchAfterResume) {
            restoreTorchAfterResume = false
            if (torchAvailable) {
                torchTogglePending = true
                previewController.toggleTorch(enabled = true) { succeeded ->
                    torchTogglePending = false
                    torchEnabled = succeeded
                }
            }
        }
    }
    val handleCameraBound = {
        previewBound = true
        val view = previewView
        if (frozenFrame == null || view == null) {
            finishCameraResume()
        } else {
            previewStreamObserver?.let(view.previewStreamState::removeObserver)
            val observer = object : androidx.lifecycle.Observer<androidx.camera.view.PreviewView.StreamState> {
                override fun onChanged(value: androidx.camera.view.PreviewView.StreamState) {
                    if (value == androidx.camera.view.PreviewView.StreamState.STREAMING) {
                        view.previewStreamState.removeObserver(this)
                        previewStreamObserver = null
                        finishCameraResume()
                    }
                }
            }
            previewStreamObserver = observer
            view.previewStreamState.observe(lifecycleOwner, observer)
        }
    }

    LaunchedEffect(previewView, lifecycleOwner) {
        val view = previewView ?: return@LaunchedEffect
        previewBound = false
        previewController.bind(
            lifecycleOwner = lifecycleOwner,
            previewView = view,
            requestedZoomRatio = savedZoomRatio,
            frameStore = frameStore,
            onBound = handleCameraBound,
            onTorchAvailability = { available ->
                torchAvailable = available
                if (!available) torchEnabled = false
            },
            onZoomState = {
                zoomState = it
            },
            onFrameError = {},
            onError = currentZoomErrorHandler,
        )
    }

    LaunchedEffect(retryCameraTrigger) {
        if (retryCameraTrigger > 0 && latestCameraError.value != null && frozenFrame == null) {
            val view = previewView ?: return@LaunchedEffect
            onCameraReady()
            previewBound = false
            previewController.bind(
                lifecycleOwner = lifecycleOwner,
                previewView = view,
                requestedZoomRatio = savedZoomRatio,
                frameStore = frameStore,
                onBound = handleCameraBound,
                onTorchAvailability = { available ->
                    torchAvailable = available
                    if (!available) torchEnabled = false
                },
                onZoomState = { zoomState = it },
                onFrameError = {},
                onError = currentZoomErrorHandler,
            )
        }
    }

    val resumeFrozenFrame = {
        if (!isRebindingAfterFreeze && frozenFrame != null) {
            showFrozenBorder = false
            frameStore.pause()
            onCameraReady()
            isRebindingAfterFreeze = true
            previewBound = false
            previewController.rebind(
                requestedZoomRatio = savedZoomRatio,
                onBound = handleCameraBound,
                onTorchAvailability = { available ->
                    torchAvailable = available
                    if (!available) torchEnabled = false
                },
                onZoomState = {
                    zoomState = it
                },
                onFrameError = {},
                onError = { error ->
                    previewStreamObserver?.let { observer ->
                        previewView?.previewStreamState?.removeObserver(observer)
                        previewStreamObserver = null
                    }
                    isRebindingAfterFreeze = false
                    onCameraError(error)
                },
            )
        }
    }
    val retryFrozenFrame = {
        if (cameraError != null && !isRebindingAfterFreeze) {
            onCameraReady()
            isRebindingAfterFreeze = true
            previewBound = false
            previewController.rebind(
                requestedZoomRatio = savedZoomRatio,
                onBound = handleCameraBound,
                onTorchAvailability = { available ->
                    torchAvailable = available
                    if (!available) torchEnabled = false
                },
                onZoomState = { zoomState = it },
                onFrameError = {},
                onError = { error ->
                    previewStreamObserver?.let { observer ->
                        previewView?.previewStreamState?.removeObserver(observer)
                        previewStreamObserver = null
                    }
                    isRebindingAfterFreeze = false
                    onCameraError(error)
                },
            )
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged {
                viewportWidth = it.width
                viewportHeight = it.height
            },
    ) {
        AndroidCameraPreview(
            modifier = Modifier.fillMaxSize(),
            onPreviewViewCreated = { previewView = it },
        )
        if (frozenFrame == null && cameraError == null && previewBound) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .magnifierAccessibilitySemantics(
                        contentDescription = stringResource(R.string.live_magnifier_description),
                        stateDescription = stringResource(
                            R.string.magnifier_state_live,
                            zoomState?.zoomRatio ?: savedZoomRatio,
                        ),
                        actionLabel = if (isCapturingStill) null else {
                            stringResource(R.string.freeze_image_action)
                        },
                        onAction = { latestLiveCaptureHandler.value(Offset.Zero, false) },
                    )
                    .pointerInput(previewController) {
                        detectMagnifierGestures(
                            onZoom = { scale ->
                                if (isCapturingStill) return@detectMagnifierGestures
                                val state = latestZoomState.value ?: return@detectMagnifierGestures
                                previewController.zoomBy(
                                    gestureScale = scale,
                                    zoomState = state,
                                    onZoomRequested = { requestedRatio ->
                                        if (!isCapturingStill && frozenFrame == null) {
                                            savedZoomRatio = requestedRatio
                                        }
                                    },
                                    onError = latestZoomErrorHandler.value,
                                )
                            },
                            onTap = { latestLiveCaptureHandler.value(it, false) },
                            onLongPress = { latestLiveCaptureHandler.value(it, true) },
                            onSwipeUp = {
                                if (latestIsCapturingStill.value) return@detectMagnifierGestures
                                if (!latestTorchAvailable.value) return@detectMagnifierGestures
                                if (latestTorchTogglePending.value) return@detectMagnifierGestures
                                torchTogglePending = true
                                val requestedTorchState = !latestTorchEnabled.value
                                previewController.toggleTorch(requestedTorchState) { succeeded ->
                                    torchTogglePending = false
                                    if (succeeded) {
                                        torchEnabled = requestedTorchState
                                        torchFeedbackEnabled = requestedTorchState
                                        torchFeedbackTrigger++
                                    }
                                }
                            },
                        )
                    },
            )
        }
        if (cameraError != null && frozenFrame == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .magnifierAccessibilitySemantics(
                        contentDescription = stringResource(R.string.live_magnifier_description),
                        stateDescription = stringResource(
                            R.string.magnifier_state_error,
                            stringResource(
                                R.string.magnifier_state_live,
                                zoomState?.zoomRatio ?: savedZoomRatio,
                            ),
                            stringResource(cameraError.messageRes),
                        ),
                        actionLabel = stringResource(R.string.retry_camera),
                        announceStateChange = true,
                        onAction = onRetryCamera,
                    ),
            )
        }
        frozenFrame?.let { frame ->
            FrozenFrameContent(
                frame = frame,
                zoomRatio = frozenZoomRatio,
                baseZoomRatio = frozenBaseZoomRatio,
                viewportScaleCompensation = FrozenPanPolicy.viewportScaleCompensation(
                    captureViewportWidth = frozenCaptureViewportWidth,
                    captureViewportHeight = frozenCaptureViewportHeight,
                    viewportWidth = viewportWidth,
                    viewportHeight = viewportHeight,
                    imageWidth = frame.width,
                    imageHeight = frame.height,
                ),
                panOffset = frozenPanOffset,
                isRebinding = isRebindingAfterFreeze,
                isCapturingStill = isCapturingStill,
                rebindError = cameraError,
                semanticsContentDescription = stringResource(R.string.frozen_magnifier_description),
                semanticsStateDescription = if (cameraError == null) {
                    stringResource(R.string.magnifier_state_frozen, frozenZoomRatio)
                } else {
                    stringResource(
                        R.string.magnifier_state_error,
                        stringResource(R.string.magnifier_state_frozen, frozenZoomRatio),
                        stringResource(cameraError.messageRes),
                    )
                },
                semanticsActionLabel = when {
                    cameraError != null -> stringResource(R.string.retry_camera)
                    isRebindingAfterFreeze -> null
                    else -> stringResource(R.string.resume_live_preview_action)
                },
                semanticsAnnounceStateChange = cameraError != null,
                onSemanticsAction = if (cameraError == null) resumeFrozenFrame else retryFrozenFrame,
                onZoom = { scale ->
                    val bounds = zoomState ?: return@FrozenFrameContent
                    val previousZoomRatio = frozenZoomRatio
                    val frozenMinimum = maxOf(bounds.minZoomRatio, frozenBaseZoomRatio)
                    val ratio = ZoomPolicy.ratioAfterGesture(
                        currentRatio = previousZoomRatio,
                        gestureScale = scale,
                        minRatio = frozenMinimum,
                        maxRatio = bounds.maxZoomRatio,
                    )
                    val previousRenderScale = frozenRenderScale
                    frozenZoomRatio = ratio
                    savedZoomRatio = ratio
                    frozenPanFlingJob?.cancel()
                    frozenPanFlingJob = null
                    frozenPanOffset = FrozenPanPolicy.clamp(
                        offset = frozenPanOffset,
                        viewportWidth = viewportWidth,
                        viewportHeight = viewportHeight,
                        imageWidth = frame.width,
                        imageHeight = frame.height,
                        scale = previousRenderScale * (ratio / previousZoomRatio),
                    )
                },
                onPan = { delta ->
                    frozenPanFlingJob?.cancel()
                    frozenPanFlingJob = null
                    frozenPanOffset = FrozenPanPolicy.clamp(
                        offset = frozenPanOffset + delta,
                        viewportWidth = viewportWidth,
                        viewportHeight = viewportHeight,
                        imageWidth = frame.width,
                        imageHeight = frame.height,
                        scale = frozenRenderScale,
                    )
                },
                onPanFling = { velocity ->
                    frozenPanFlingJob?.cancel()
                    frozenPanFlingJob = null
                    if (FrozenPanPolicy.shouldFling(velocity) &&
                        !isRebindingAfterFreeze && !isCapturingStill && cameraError == null
                    ) {
                        frozenPanFlingJob = scope.launch {
                            val initialOffset = frozenPanOffset
                            var startTimeNanos = 0L
                            while (true) {
                                val frameTimeNanos = withFrameNanos { it }
                                if (startTimeNanos == 0L) startTimeNanos = frameTimeNanos
                                val elapsedMillis = (frameTimeNanos - startTimeNanos) / 1_000_000L
                                val proposed = FrozenPanPolicy.flingOffset(
                                    start = initialOffset,
                                    velocity = velocity,
                                    elapsedMillis = elapsedMillis,
                                )
                                val bounded = FrozenPanPolicy.clamp(
                                    offset = proposed,
                                    viewportWidth = viewportWidth,
                                    viewportHeight = viewportHeight,
                                    imageWidth = frame.width,
                                    imageHeight = frame.height,
                                    scale = frozenRenderScale,
                                )
                                frozenPanOffset = bounded
                                if (bounded != proposed || elapsedMillis >= FrozenPanPolicy.FLING_DURATION_MILLIS) {
                                    break
                                }
                            }
                        }
                    }
                },
                onGestureStart = {
                    frozenPanFlingJob?.cancel()
                    frozenPanFlingJob = null
                },
                onResume = { resumeFrozenFrame() },
            )
        }
        FreezeScreenFlash(
            trigger = freezeFlashTrigger,
            showBorder = showFrozenBorder,
        )
        if (torchFeedbackTrigger > 0) {
            TorchToggleFeedback(
                trigger = torchFeedbackTrigger,
                isOn = torchFeedbackEnabled,
            )
        }
        if (showFocusIndicator && isCapturingStill) {
            FocusAcquisitionIndicator(
                position = focusPointPosition,
                previewWidth = previewView?.width ?: 0,
                previewHeight = previewView?.height ?: 0,
                focusLocked = focusLocked,
                animationKey = focusAnimationKey,
            )
        }
        if (frozenFrame == null) {
            zoomState?.let { currentZoom ->
                ZoomIndicator(
                    zoomRatio = currentZoom.zoomRatio,
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 56.dp),
                )
            }
        } else {
            ZoomIndicator(
                zoomRatio = frozenZoomRatio,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 56.dp),
            )
        }
        if ((previewBound && cameraError == null) || frozenFrame != null) {
            Surface(
                onClick = { showGestureGuide = true },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 56.dp, end = 12.dp),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
                contentColor = MaterialTheme.colorScheme.onSurface,
            ) {
                Text(
                    text = stringResource(R.string.gesture_help_button),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
        if (showGestureGuide) {
            GestureHelpDialog(
                onDismiss = {
                    showGestureGuide = false
                    hasDismissedGestureGuide = true
                    gestureHelpPreferences.edit().putBoolean("dismissed", true).apply()
                },
            )
        }
    }
}

@Composable
private fun GestureHelpDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.gesture_help_title),
                modifier = Modifier.clickable(onClick = onDismiss),
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 480.dp)
                    .pointerInput(onDismiss) {
                        detectTapGestures(onTap = { onDismiss() })
                    }
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                GestureHelpItem(
                    title = stringResource(R.string.gesture_help_tap_title),
                    description = stringResource(R.string.gesture_help_tap_description),
                )
                GestureHelpItem(
                    title = stringResource(R.string.gesture_help_hold_title),
                    description = stringResource(R.string.gesture_help_hold_description),
                )
                GestureHelpItem(
                    title = stringResource(R.string.gesture_help_pinch_title),
                    description = stringResource(R.string.gesture_help_pinch_description),
                )
                GestureHelpItem(
                    title = stringResource(R.string.gesture_help_light_title),
                    description = stringResource(R.string.gesture_help_light_description),
                )
                GestureHelpItem(
                    title = stringResource(R.string.gesture_help_drag_title),
                    description = stringResource(R.string.gesture_help_drag_description),
                )
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text(stringResource(R.string.gesture_help_dismiss))
            }
        },
    )
}

@Composable
private fun GestureHelpItem(title: String, description: String) {
    Column {
        Text(text = title, style = MaterialTheme.typography.titleMedium)
        Text(text = description, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun FrozenFrameContent(
    frame: Bitmap,
    zoomRatio: Float,
    baseZoomRatio: Float,
    viewportScaleCompensation: Float,
    panOffset: Offset,
    isRebinding: Boolean,
    isCapturingStill: Boolean,
    rebindError: CameraError?,
    semanticsContentDescription: String,
    semanticsStateDescription: String,
    semanticsActionLabel: String?,
    semanticsAnnounceStateChange: Boolean,
    onSemanticsAction: () -> Unit,
    onZoom: (Float) -> Unit,
    onPan: (Offset) -> Unit,
    onPanFling: (Offset) -> Unit,
    onGestureStart: () -> Unit,
    onResume: () -> Unit,
) {
    val latestOnZoom = rememberUpdatedState(onZoom)
    val latestOnPan = rememberUpdatedState(onPan)
    val latestOnPanFling = rememberUpdatedState(onPanFling)
    val latestOnGestureStart = rememberUpdatedState(onGestureStart)
    val latestOnResume = rememberUpdatedState(onResume)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clipToBounds()
            .magnifierAccessibilitySemantics(
                contentDescription = semanticsContentDescription,
                stateDescription = semanticsStateDescription,
                actionLabel = semanticsActionLabel,
                announceStateChange = semanticsAnnounceStateChange,
                onAction = onSemanticsAction,
            )
            .pointerInput(frame, isRebinding, isCapturingStill, rebindError) {
                detectMagnifierGestures(
                    onZoom = { scale ->
                        if (!isRebinding && !isCapturingStill && rebindError == null) latestOnZoom.value(scale)
                    },
                    onPan = { delta ->
                        if (!isRebinding && !isCapturingStill && rebindError == null) latestOnPan.value(delta)
                    },
                    onPanFling = { velocity ->
                        if (!isRebinding && !isCapturingStill && rebindError == null) {
                            latestOnPanFling.value(velocity)
                        }
                    },
                    onGestureStart = { latestOnGestureStart.value() },
                    onTap = {
                        if (!isRebinding && !isCapturingStill) latestOnResume.value()
                    },
                )
            },
    ) {
        androidx.compose.foundation.Image(
            bitmap = frame.asImageBitmap(),
            contentDescription = null,
            modifier = Modifier.fillMaxSize().graphicsLayer {
                val relativeZoom = (zoomRatio / baseZoomRatio).coerceAtLeast(1f) *
                    viewportScaleCompensation
                scaleX = relativeZoom
                scaleY = relativeZoom
                translationX = panOffset.x
                translationY = panOffset.y
            },
            contentScale = androidx.compose.ui.layout.ContentScale.Fit,
        )
    }
}

@Composable
private fun FocusAcquisitionIndicator(
    position: Offset,
    previewWidth: Int,
    previewHeight: Int,
    focusLocked: Boolean?,
    animationKey: Int,
) {
    val convergence = remember { Animatable(0f) }
    val pulse = remember { Animatable(0f) }
    val density = LocalDensity.current

    LaunchedEffect(animationKey) {
        convergence.snapTo(0f)
        convergence.animateTo(1f, animationSpec = tween(durationMillis = 560))
    }
    LaunchedEffect(focusLocked, animationKey) {
        pulse.snapTo(0f)
        if (focusLocked == false) {
            while (true) {
                pulse.animateTo(1f, tween(durationMillis = 680))
                pulse.animateTo(0f, tween(durationMillis = 680))
            }
        }
    }

    val bracketColor = when (focusLocked) {
        true -> Color(0xFF65E695)
        false -> Color(0xFFFFC15A)
        null -> Color.White
    }
    val outerRadius = with(density) { 68.dp.toPx() }
    val innerRadius = with(density) { 38.dp.toPx() }
    val radius = outerRadius + (innerRadius - outerRadius) * convergence.value +
        pulse.value * with(density) { 5.dp.toPx() }
    val cornerLength = with(density) { 20.dp.toPx() }
    val strokeWidth = with(density) { 4.dp.toPx() }

    Box(modifier = Modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            if (previewWidth > 0 && previewHeight > 0) {
                val center = Offset(
                    x = position.x.coerceIn(0f, size.width),
                    y = position.y.coerceIn(0f, size.height),
                )
                val alpha = if (focusLocked == false) 0.65f + pulse.value * 0.35f else 1f
                val color = bracketColor.copy(alpha = alpha)
                val left = center.x - radius
                val right = center.x + radius
                val top = center.y - radius
                val bottom = center.y + radius

                drawLine(color, Offset(left, top), Offset(left + cornerLength, top), strokeWidth)
                drawLine(color, Offset(left, top), Offset(left, top + cornerLength), strokeWidth)
                drawLine(color, Offset(right, top), Offset(right - cornerLength, top), strokeWidth)
                drawLine(color, Offset(right, top), Offset(right, top + cornerLength), strokeWidth)
                drawLine(color, Offset(left, bottom), Offset(left + cornerLength, bottom), strokeWidth)
                drawLine(color, Offset(left, bottom), Offset(left, bottom - cornerLength), strokeWidth)
                drawLine(color, Offset(right, bottom), Offset(right - cornerLength, bottom), strokeWidth)
                drawLine(color, Offset(right, bottom), Offset(right, bottom - cornerLength), strokeWidth)
                if (focusLocked == true) {
                    drawCircle(color, radius = with(density) { 3.dp.toPx() }, center = center)
                }
            }
        }
        if (focusLocked == false) {
            Surface(
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 32.dp),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                contentColor = MaterialTheme.colorScheme.onSurface,
            ) {
                Text(
                    text = stringResource(R.string.camera_focus_timeout),
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
    }
}

@Composable
private fun FreezeScreenFlash(
    trigger: Int,
    showBorder: Boolean,
) {
    val alpha = remember { Animatable(0f) }
    val view = LocalView.current

    LaunchedEffect(trigger) {
        if (trigger > 0) {
            alpha.snapTo(0.8f)
            alpha.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 450),
            )
        }
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        if (alpha.value > 0f) {
            drawRect(Color.White.copy(alpha = alpha.value))
        }
        if (showBorder) {
            val strokeWidth = 6.dp.toPx()
            val inset = strokeWidth / 2f
            val (topLeft, topRight, bottomRight, bottomLeft) = view.roundedDisplayCornerRadii()
            val width = size.width
            val height = size.height
            val left = inset
            val top = inset
            val right = width - inset
            val bottom = height - inset
            val outline = Path().apply {
                moveTo(left + topLeft, top)
                lineTo(right - topRight, top)
                quadraticTo(right, top, right, top + topRight)
                lineTo(right, bottom - bottomRight)
                quadraticTo(right, bottom, right - bottomRight, bottom)
                lineTo(left + bottomLeft, bottom)
                quadraticTo(left, bottom, left, bottom - bottomLeft)
                lineTo(left, top + topLeft)
                quadraticTo(left, top, left + topLeft, top)
                close()
            }
            drawPath(
                path = outline,
                color = Color.White,
                style = Stroke(width = strokeWidth),
            )
        }
    }
}

@Composable
private fun TorchToggleFeedback(
    trigger: Int,
    isOn: Boolean,
) {
    val alpha = remember { Animatable(0f) }
    val scale = remember { Animatable(0.72f) }

    LaunchedEffect(trigger) {
        alpha.snapTo(0.86f)
        scale.snapTo(0.72f)
        launch {
            scale.animateTo(1.08f, animationSpec = tween(durationMillis = 700))
        }
        delay(300)
        alpha.animateTo(0f, animationSpec = tween(durationMillis = 500))
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Canvas(
            modifier = Modifier
                .size(384.dp)
                .graphicsLayer {
                    this.alpha = alpha.value
                    scaleX = scale.value
                    scaleY = scale.value
                },
        ) {
            val iconColor = Color.White
            val torch = Path().apply {
                moveTo(size.width * 0.31f, size.height * 0.22f)
                lineTo(size.width * 0.69f, size.height * 0.22f)
                lineTo(size.width * 0.61f, size.height * 0.42f)
                lineTo(size.width * 0.57f, size.height * 0.47f)
                lineTo(size.width * 0.57f, size.height * 0.76f)
                lineTo(size.width * 0.43f, size.height * 0.76f)
                lineTo(size.width * 0.43f, size.height * 0.47f)
                lineTo(size.width * 0.39f, size.height * 0.42f)
                close()
            }
            drawPath(path = torch, color = iconColor)
            if (isOn) {
                val rayWidth = size.width * 0.045f
                val rays = listOf(
                    Offset(size.width * 0.5f, size.height * 0.04f) to
                        Offset(size.width * 0.5f, size.height * 0.13f),
                    Offset(size.width * 0.16f, size.height * 0.12f) to
                        Offset(size.width * 0.23f, size.height * 0.19f),
                    Offset(size.width * 0.84f, size.height * 0.12f) to
                        Offset(size.width * 0.77f, size.height * 0.19f),
                )
                rays.forEach { (start, end) ->
                    drawLine(
                        color = iconColor,
                        start = start,
                        end = end,
                        strokeWidth = rayWidth,
                    )
                }
            }
        }
    }
}

private fun View.roundedDisplayCornerRadii(): List<Float> {
    val fallback = 28.dp.value * resources.displayMetrics.density
    if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.S) {
        return List(4) { fallback }
    }

    val insets = rootWindowInsets ?: return List(4) { fallback }
    return listOf(
        android.view.RoundedCorner.POSITION_TOP_LEFT,
        android.view.RoundedCorner.POSITION_TOP_RIGHT,
        android.view.RoundedCorner.POSITION_BOTTOM_RIGHT,
        android.view.RoundedCorner.POSITION_BOTTOM_LEFT,
    ).map { position ->
        insets.getRoundedCorner(position)?.radius?.toFloat() ?: fallback
    }
}

@Composable
private fun ZoomIndicator(
    zoomRatio: Float,
    modifier: Modifier = Modifier,
) {
    val locale = LocalLocale.current.platformLocale
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Text(
            text = String.format(locale, "%.1fx", zoomRatio),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
private fun AndroidCameraPreview(
    modifier: Modifier = Modifier,
    onPreviewViewCreated: (androidx.camera.view.PreviewView) -> Unit,
) {
    androidx.compose.ui.viewinterop.AndroidView(
        modifier = modifier,
        factory = { context ->
            androidx.camera.view.PreviewView(context).apply {
                scaleType = androidx.camera.view.PreviewView.ScaleType.FILL_CENTER
                // Use TextureView so the Compose gesture/indicator overlay shares the same window layer.
                implementationMode = androidx.camera.view.PreviewView.ImplementationMode.COMPATIBLE
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
                onPreviewViewCreated(this)
            }
        },
        update = { onPreviewViewCreated(it) },
    )
}

@Composable
internal fun PermissionMessage(
    title: String,
    actionLabel: String,
    onAction: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Button(
            onClick = onAction,
            modifier = Modifier.padding(top = 24.dp),
        ) {
            Text(actionLabel)
        }
    }
}