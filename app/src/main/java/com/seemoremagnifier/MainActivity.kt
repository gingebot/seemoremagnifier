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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.material3.Button
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
import com.seemoremagnifier.ui.magnifier.detectMagnifierGestures
import androidx.camera.core.ZoomState
import kotlinx.coroutines.launch
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
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
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
                                    torchEnabled = succeeded && requestedTorchState
                                }
                            },
                        )
                    },
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
        Surface(
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 32.dp),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
            contentColor = MaterialTheme.colorScheme.onSurface,
        ) {
            Text(
                text = stringResource(
                    when (focusLocked) {
                        true -> R.string.camera_focus_locked
                        false -> R.string.camera_focus_timeout
                        null -> R.string.camera_hold_steady
                    },
                ),
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                style = MaterialTheme.typography.titleMedium,
            )
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
                contentDescription = context.getString(R.string.live_camera_preview_description)
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