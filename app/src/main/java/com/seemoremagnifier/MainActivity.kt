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
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.seemoremagnifier.data.camera.CameraPreviewController
import com.seemoremagnifier.data.frame.LatestFrameStore
import com.seemoremagnifier.ui.magnifier.CameraError
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
    var frozenFrame by remember { mutableStateOf<Bitmap?>(null) }

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
        frozenFrame != null -> {
            FrozenFrameContent(
                frame = frozenFrame!!,
                onResume = {
                    frozenFrame?.recycle()
                    frozenFrame = null
                    cameraError = null
                    retryToken++
                },
            )
        }
        permissionGranted -> {
            CameraPreviewContent(
                retryToken = retryToken,
                frameError = frameError,
                onFreeze = {
                    val captured = it
                    if (captured == null) {
                        frameError = CameraError.NoFrameAvailable
                    } else {
                        cameraError = null
                        retryToken++
                        frozenFrame = captured
                    }
                },
                onFrameError = { frameError = it },
                onCameraError = { cameraError = it },
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
    onFreeze: (Bitmap?) -> Unit,
    frameError: CameraError?,
    onFrameError: (CameraError) -> Unit,
    cameraError: CameraError?,
    onCameraError: (CameraError) -> Unit,
    onRetry: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewController = remember(context) { CameraPreviewController(context) }
    val frameStore = remember { LatestFrameStore<Bitmap> { bitmap -> bitmap.recycle() } }
    var previewView by remember { mutableStateOf<androidx.camera.view.PreviewView?>(null) }
    var previewBound by remember { mutableStateOf(false) }
    var zoomState by remember { mutableStateOf<ZoomState?>(null) }
    var savedZoomRatio by rememberSaveable { mutableFloatStateOf(1f) }
    var zoomErrorVisible by remember { mutableStateOf(false) }
    val latestZoomState = rememberUpdatedState(zoomState)
    val currentZoomErrorHandler: (CameraError) -> Unit = { error ->
        if (error == CameraError.ZoomFailed) {
            zoomErrorVisible = true
        } else {
            onCameraError(error)
        }
    }
    val latestZoomErrorHandler = rememberUpdatedState(currentZoomErrorHandler)

    LaunchedEffect(zoomErrorVisible) {
        if (zoomErrorVisible) {
            kotlinx.coroutines.delay(1_800)
            zoomErrorVisible = false
        }
    }
    DisposableEffect(previewController) {
        onDispose {
            previewController.shutdown()
            frameStore.close()
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
            onBound = { previewBound = true },
            onZoomState = {
                zoomState = it
                savedZoomRatio = it.zoomRatio
            },
            onFrameError = onFrameError,
            onError = currentZoomErrorHandler,
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidCameraPreview(
            modifier = Modifier.fillMaxSize(),
            onPreviewViewCreated = { previewView = it },
        )
        if (cameraError == null && previewBound) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(previewController) {
                        detectTransformGestures { _, _, zoom, _ ->
                            val state = latestZoomState.value ?: return@detectTransformGestures
                            if (zoom.isFinite() && zoom > 0f && kotlin.math.abs(zoom - 1f) > 0.001f) {
                                previewController.zoomBy(
                                    zoom,
                                    state,
                                    latestZoomErrorHandler.value,
                                )
                            }
                        }
                    }
                    .pointerInput(frameStore) {
                        detectTapGestures { onFreeze(frameStore.freezeLatest()) }
                    },
            )
        }
        zoomState?.let { currentZoom ->
            ZoomIndicator(
                zoomRatio = currentZoom.zoomRatio,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 56.dp),
            )
        }
        if (zoomErrorVisible && cameraError == null) {
            Text(
                text = stringResource(R.string.camera_zoom_failed),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 32.dp),
                color = MaterialTheme.colorScheme.onErrorContainer,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        frameError?.let { error ->
            Text(
                text = stringResource(error.messageRes),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = if (zoomErrorVisible) 64.dp else 32.dp),
                color = MaterialTheme.colorScheme.onErrorContainer,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        when {
            cameraError != null -> {
                PermissionMessage(
                    title = stringResource(R.string.camera_unavailable_title),
                    message = stringResource(cameraError.messageRes),
                    actionLabel = stringResource(R.string.retry_camera),
                    onAction = onRetry,
                )
            }

            !previewBound -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
        }
    }
}

@Composable
private fun FrozenFrameContent(
    frame: Bitmap,
    onResume: () -> Unit,
) {
    DisposableEffect(frame) {
        onDispose { if (!frame.isRecycled) frame.recycle() }
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(frame) {
                detectTapGestures(onTap = { onResume() })
            },
    ) {
        androidx.compose.foundation.Image(
            bitmap = frame.asImageBitmap(),
            contentDescription = stringResource(R.string.frozen_status),
            modifier = Modifier.fillMaxSize(),
            contentScale = androidx.compose.ui.layout.ContentScale.Fit,
        )
        Surface(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 56.dp),
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
            text = stringResource(R.string.tap_to_resume),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp),
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.bodyLarge,
        )
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