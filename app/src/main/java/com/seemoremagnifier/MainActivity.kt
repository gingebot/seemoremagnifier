package com.seemoremagnifier

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.seemoremagnifier.data.camera.CameraPreviewController
import com.seemoremagnifier.data.frame.LatestFrameStore
import com.seemoremagnifier.domain.magnifier.ZoomPolicy
import com.seemoremagnifier.ui.magnifier.CameraError
import com.seemoremagnifier.ui.magnifier.detectMagnifierGestures
import androidx.camera.core.ZoomState

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    MagnifierPermissionScreen()
                }
            }
        }
    }
}

@Composable
internal fun MagnifierPermissionScreen() {
    val context = LocalContext.current
    val activity = context as? ComponentActivity
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
    var permissionPermanentlyDenied by remember(permissionGranted) {
        mutableStateOf(
            !permissionGranted &&
                permissionPreferences.getBoolean("requested", false) &&
                activity?.let {
                    !it.shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)
                } == true,
        )
    }
    var cameraError by remember { mutableStateOf<CameraError?>(null) }
    var frameError by remember { mutableStateOf<CameraError?>(null) }
    var retryToken by remember { mutableStateOf(0) }

    LaunchedEffect(frameError) {
        if (frameError != null) {
            kotlinx.coroutines.delay(2_500)
            frameError = null
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasRequestedPermission = true
        permissionGranted = granted
        showPermissionExplanation = !granted
        permissionPermanentlyDenied = !granted && hasRequestedPermission &&
            activity?.let { !it.shouldShowRequestPermissionRationale(Manifest.permission.CAMERA) } == true
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
                    permissionPermanentlyDenied = activity?.let {
                        !it.shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)
                    } == true
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
                retryToken = retryToken,
                frameError = frameError,
                onFrameError = { frameError = it },
                onCameraError = { cameraError = it },
                onCameraReady = { cameraError = null },
                onRetry = {
                    cameraError = null
                    retryToken++
                },
                cameraError = cameraError,
            )
        }

        showPermissionExplanation -> {
            PermissionMessage(
                title = stringResource(R.string.camera_permission_title),
                message = stringResource(R.string.camera_permission_rationale),
                actionLabel = if (permissionPermanentlyDenied) {
                    stringResource(R.string.open_app_settings)
                } else {
                    stringResource(R.string.retry_permission)
                },
                onAction = {
                    if (permissionPermanentlyDenied) {
                        context.startActivity(
                            Intent(
                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.fromParts("package", context.packageName, null),
                            ),
                        )
                    } else {
                        permissionPreferences.edit().putBoolean("requested", true).apply()
                        permissionLauncher.launch(Manifest.permission.CAMERA)
                    }
                },
            )
        }

        else -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    CircularProgressIndicator()
                    Text(stringResource(R.string.camera_permission_initial_message))
                }
            }
        }
    }
}

@Composable
private fun CameraPreviewContent(
    retryToken: Int,
    frameError: CameraError?,
    onFrameError: (CameraError) -> Unit,
    cameraError: CameraError?,
    onCameraError: (CameraError) -> Unit,
    onCameraReady: () -> Unit,
    onRetry: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewController = remember(context) { CameraPreviewController(context) }
    val frameStore = remember { LatestFrameStore<Bitmap> { bitmap -> bitmap.recycle() } }
    var previewView by remember { mutableStateOf<androidx.camera.view.PreviewView?>(null) }
    var previewBound by remember { mutableStateOf(false) }
    var previewStreamObserver by remember {
        mutableStateOf<androidx.lifecycle.Observer<androidx.camera.view.PreviewView.StreamState>?>(null)
    }
    var zoomState by remember { mutableStateOf<ZoomState?>(null) }
    var savedZoomRatio by rememberSaveable { mutableFloatStateOf(1f) }
    var frozenZoomRatio by remember { mutableFloatStateOf(1f) }
    var frozenBaseZoomRatio by remember { mutableFloatStateOf(1f) }
    var frozenFrame by remember { mutableStateOf<Bitmap?>(null) }
    var isCapturingStill by remember { mutableStateOf(false) }
    var isRebindingAfterFreeze by remember { mutableStateOf(false) }
    var zoomErrorVisible by remember { mutableStateOf(false) }
    val latestZoomState = rememberUpdatedState(zoomState)
    val latestFrozenFrame = rememberUpdatedState(frozenFrame)
    val latestPreviewView = rememberUpdatedState(previewView)
    val currentZoomErrorHandler: (CameraError) -> Unit = { error ->
        if (error == CameraError.ZoomFailed) zoomErrorVisible = true else onCameraError(error)
    }
    val latestZoomErrorHandler = rememberUpdatedState(currentZoomErrorHandler)
    val freezeWithLatestAnalysisFrame: (CameraError) -> Unit = { error ->
        val fallback = frameStore.freezeLatest()
        if (fallback == null) {
            isCapturingStill = false
            onFrameError(CameraError.NoFrameAvailable)
        } else {
            frozenBaseZoomRatio = savedZoomRatio
            frozenZoomRatio = savedZoomRatio
            frozenFrame = fallback
            isCapturingStill = false
            previewController.unbind()
            onFrameError(error)
        }
    }
    val latestFallbackHandler = rememberUpdatedState(freezeWithLatestAnalysisFrame)
    val liveTapHandler: (Offset) -> Unit = { tapPosition ->
        if (cameraError == null && frozenFrame == null && !isCapturingStill) {
            isCapturingStill = true
            val view = previewView
            previewController.focusAndCapture(
                x = tapPosition.x,
                y = tapPosition.y,
                previewWidth = view?.width ?: 0,
                previewHeight = view?.height ?: 0,
                onCaptured = { captured ->
                    frameStore.freezeLatest()?.let { if (!it.isRecycled) it.recycle() }
                    frameStore.pause()
                    frozenBaseZoomRatio = savedZoomRatio
                    frozenZoomRatio = savedZoomRatio
                    frozenFrame = captured
                    isCapturingStill = false
                    previewController.unbind()
                },
                onError = { error -> latestFallbackHandler.value(error) },
            )
        }
    }
    val latestLiveTapHandler = rememberUpdatedState(liveTapHandler)

    LaunchedEffect(zoomErrorVisible) {
        if (zoomErrorVisible) {
            kotlinx.coroutines.delay(1_800)
            zoomErrorVisible = false
        }
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

    LaunchedEffect(previewView, lifecycleOwner, retryToken) {
        val view = previewView ?: return@LaunchedEffect
        previewBound = false
        previewController.bind(
            lifecycleOwner = lifecycleOwner,
            previewView = view,
            requestedZoomRatio = savedZoomRatio,
            frameStore = frameStore,
            onBound = handleCameraBound,
            onZoomState = {
                zoomState = it
                savedZoomRatio = it.zoomRatio
            },
            onFrameError = onFrameError,
            onError = currentZoomErrorHandler,
        )
    }

    val resumeFrozenFrame = {
        if (!isRebindingAfterFreeze && frozenFrame != null) {
            frameStore.pause()
            onCameraReady()
            isRebindingAfterFreeze = true
            previewBound = false
            previewController.rebind(
                requestedZoomRatio = savedZoomRatio,
                onBound = handleCameraBound,
                onZoomState = {
                    zoomState = it
                    savedZoomRatio = it.zoomRatio
                },
                onFrameError = onFrameError,
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

    Box(modifier = Modifier.fillMaxSize()) {
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
                                previewController.zoomBy(scale, state, latestZoomErrorHandler.value)
                            },
                            onTap = { latestLiveTapHandler.value(it) },
                        )
                    },
            )
        }
        if (isCapturingStill) {
            Surface(
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 32.dp),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                contentColor = MaterialTheme.colorScheme.onSurface,
            ) {
                Text(
                    text = stringResource(R.string.camera_focusing),
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
        frozenFrame?.let { frame ->
            FrozenFrameContent(
                frame = frame,
                zoomRatio = frozenZoomRatio,
                baseZoomRatio = frozenBaseZoomRatio,
                isRebinding = isRebindingAfterFreeze,
                isCapturingStill = isCapturingStill,
                rebindError = cameraError,
                onZoom = { scale ->
                    val bounds = zoomState ?: return@FrozenFrameContent
                    val frozenMinimum = maxOf(bounds.minZoomRatio, frozenBaseZoomRatio)
                    val ratio = ZoomPolicy.ratioAfterGesture(
                        currentRatio = frozenZoomRatio,
                        gestureScale = scale,
                        minRatio = frozenMinimum,
                        maxRatio = bounds.maxZoomRatio,
                    )
                    frozenZoomRatio = ratio
                    savedZoomRatio = ratio
                },
                onResume = { resumeFrozenFrame() },
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
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 104.dp),
            )
        }
        if (zoomErrorVisible && cameraError == null) {
            Text(
                text = stringResource(R.string.camera_zoom_failed),
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 32.dp),
                color = MaterialTheme.colorScheme.onErrorContainer,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        frameError?.let { error ->
            Text(
                text = stringResource(error.messageRes),
                modifier = Modifier.align(Alignment.BottomCenter)
                    .padding(bottom = if (zoomErrorVisible) 64.dp else 32.dp),
                color = MaterialTheme.colorScheme.onErrorContainer,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        when {
            cameraError != null && frozenFrame == null -> PermissionMessage(
                title = stringResource(R.string.camera_unavailable_title),
                message = stringResource(cameraError.messageRes),
                actionLabel = stringResource(R.string.retry_camera),
                onAction = onRetry,
            )
            !previewBound && frozenFrame == null -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        }
    }
}

@Composable
private fun FrozenFrameContent(
    frame: Bitmap,
    zoomRatio: Float,
    baseZoomRatio: Float,
    isRebinding: Boolean,
    isCapturingStill: Boolean,
    rebindError: CameraError?,
    onZoom: (Float) -> Unit,
    onResume: () -> Unit,
) {
    val latestOnZoom = rememberUpdatedState(onZoom)
    val latestOnResume = rememberUpdatedState(onResume)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clipToBounds()
            .pointerInput(frame, isRebinding, isCapturingStill, rebindError) {
                detectMagnifierGestures(
                    onZoom = { scale ->
                        if (!isRebinding && !isCapturingStill && rebindError == null) latestOnZoom.value(scale)
                    },
                    onTap = {
                        if (!isRebinding && !isCapturingStill && rebindError == null) latestOnResume.value()
                    },
                )
            },
    ) {
        androidx.compose.foundation.Image(
            bitmap = frame.asImageBitmap(),
            contentDescription = stringResource(R.string.frozen_status),
            modifier = Modifier.fillMaxSize().graphicsLayer {
                val relativeZoom = (zoomRatio / baseZoomRatio).coerceAtLeast(1f)
                scaleX = relativeZoom
                scaleY = relativeZoom
            },
            contentScale = androidx.compose.ui.layout.ContentScale.Fit,
        )
        Surface(
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 56.dp),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
            contentColor = MaterialTheme.colorScheme.onSurface,
        ) {
            Text(
                text = stringResource(R.string.frozen_status),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                style = MaterialTheme.typography.titleMedium,
            )
        }
        Text(
            text = when {
                isCapturingStill -> stringResource(R.string.camera_focusing)
                isRebinding -> stringResource(R.string.camera_rebinding)
                rebindError != null -> stringResource(rebindError.messageRes)
                else -> stringResource(R.string.tap_to_resume)
            },
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 32.dp),
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.bodyLarge,
        )
        if (rebindError != null && !isCapturingStill) {
            Button(
                onClick = onResume,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 84.dp),
            ) { Text(stringResource(R.string.retry_camera)) }
        } else if (isCapturingStill) {
            Button(
                onClick = onResume,
                enabled = false,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 84.dp),
            ) { Text(stringResource(R.string.camera_focusing)) }
        } else {
            Button(
                onClick = onResume,
                enabled = !isRebinding && !isCapturingStill,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 84.dp),
            ) { Text(stringResource(R.string.resume_live_view)) }
        }
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
            style = MaterialTheme.typography.titleMedium,
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
    message: String,
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
            textAlign = TextAlign.Center,
        )
        Text(
            text = message,
            modifier = Modifier.padding(top = 16.dp),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
        Button(
            onClick = onAction,
            modifier = Modifier.padding(top = 24.dp),
        ) {
            Text(actionLabel)
        }
    }
}