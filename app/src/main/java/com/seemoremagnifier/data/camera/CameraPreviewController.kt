package com.seemoremagnifier.data.camera

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.view.Surface
import androidx.camera.core.CameraSelector
import androidx.camera.core.CameraControl
import androidx.camera.core.Preview
import androidx.camera.core.ZoomState
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.seemoremagnifier.domain.magnifier.ZoomPolicy
import com.seemoremagnifier.ui.magnifier.CameraError
import java.util.concurrent.Executor
import java.util.concurrent.CancellationException
import java.util.concurrent.ExecutionException

/** Owns the CameraX preview use case used by the live magnifier screen. */
class CameraPreviewController(context: Context) {
    private val appContext = context.applicationContext
    private val mainExecutor: Executor = ContextCompat.getMainExecutor(appContext)
    private var cameraProvider: ProcessCameraProvider? = null
    private var previewUseCase: Preview? = null
    private var cameraControl: androidx.camera.core.CameraControl? = null
    private var requestedZoomRatio: Float? = null
    private var bindGeneration = 0

    fun bind(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        requestedZoomRatio: Float,
        onBound: () -> Unit,
        onZoomState: (ZoomState) -> Unit,
        onError: (CameraError) -> Unit,
    ) {
        val generation = ++bindGeneration
        if (ContextCompat.checkSelfPermission(appContext, Manifest.permission.CAMERA) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            onError(CameraError.PermissionMissing)
            return
        }

        val providerFuture = try {
            ProcessCameraProvider.getInstance(appContext)
        } catch (_: Exception) {
            onError(CameraError.InitializationFailed)
            return
        }

        providerFuture.addListener({
            if (generation != bindGeneration) return@addListener
            try {
                val provider = providerFuture.get()
                val selector = CameraSelector.DEFAULT_BACK_CAMERA
                if (!provider.hasCamera(selector)) {
                    onError(CameraError.RearCameraUnavailable)
                    return@addListener
                }

                previewUseCase?.let { provider.unbind(it) }

                val preview = Preview.Builder()
                    .setTargetRotation(previewView.display?.rotation ?: Surface.ROTATION_0)
                    .build()
                    .also { it.setSurfaceProvider(previewView.surfaceProvider) }

                val camera = provider.bindToLifecycle(lifecycleOwner, selector, preview)
                cameraProvider = provider
                previewUseCase = preview
                cameraControl = camera.cameraControl
                camera.cameraInfo.zoomState.observe(lifecycleOwner, onZoomState)
                val zoomState = camera.cameraInfo.zoomState.value
                if (zoomState != null) {
                    val restoredRatio = requestedZoomRatio.coerceIn(
                        zoomState.minZoomRatio,
                        zoomState.maxZoomRatio,
                    )
                    this.requestedZoomRatio = restoredRatio
                    try {
                        val zoomFuture = camera.cameraControl.setZoomRatio(restoredRatio)
                        zoomFuture.addListener({
                            try {
                                zoomFuture.get()
                            } catch (exception: Exception) {
                                reportZoomFailureUnlessSuperseded(exception, onError)
                            }
                        }, mainExecutor)
                    } catch (_: Exception) {
                        onError(CameraError.ZoomFailed)
                    }
                }
                onBound()
            } catch (_: Exception) {
                onError(CameraError.BindingFailed)
            }
        }, mainExecutor)
    }

    fun zoomBy(
        gestureScale: Float,
        zoomState: ZoomState,
        onError: (CameraError) -> Unit,
    ) {
        val targetRatio = try {
            ZoomPolicy.ratioAfterGesture(
                currentRatio = requestedZoomRatio ?: zoomState.zoomRatio,
                gestureScale = gestureScale,
                minRatio = zoomState.minZoomRatio,
                maxRatio = zoomState.maxZoomRatio,
            )
        } catch (_: IllegalArgumentException) {
            return
        }

        val control = cameraControl ?: return
        requestedZoomRatio = targetRatio
        val zoomFuture = try {
            control.setZoomRatio(targetRatio)
        } catch (_: Exception) {
            requestedZoomRatio = zoomState.zoomRatio
            onError(CameraError.ZoomFailed)
            return
        }
        zoomFuture.addListener({
            try {
                zoomFuture.get()
            } catch (exception: Exception) {
                if (!isSupersededZoomRequest(exception)) {
                    requestedZoomRatio = zoomState.zoomRatio
                    onError(CameraError.ZoomFailed)
                }
            }
        }, mainExecutor)
    }

    private fun reportZoomFailureUnlessSuperseded(
        exception: Exception,
        onError: (CameraError) -> Unit,
    ) {
        if (!isSupersededZoomRequest(exception)) {
            onError(CameraError.ZoomFailed)
        }
    }

    private fun isSupersededZoomRequest(exception: Exception): Boolean {
        val cause = if (exception is ExecutionException) exception.cause else exception
        return cause is CancellationException || cause is CameraControl.OperationCanceledException
    }

    fun unbind() {
        bindGeneration++
        val preview = previewUseCase ?: return
        cameraProvider?.unbind(preview)
        previewUseCase = null
        cameraControl = null
        requestedZoomRatio = null
        cameraProvider = null
    }
}