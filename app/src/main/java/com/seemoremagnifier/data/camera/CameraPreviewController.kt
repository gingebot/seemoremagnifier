package com.seemoremagnifier.data.camera

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.view.Surface
import androidx.camera.core.CameraSelector
import androidx.camera.core.CameraControl
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.ZoomState
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import android.graphics.Bitmap
import com.seemoremagnifier.domain.magnifier.ZoomPolicy
import com.seemoremagnifier.data.frame.LatestFrameStore
import com.seemoremagnifier.ui.magnifier.CameraError
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Executor
import java.util.concurrent.CancellationException
import java.util.concurrent.ExecutionException

/** Owns the CameraX preview use case used by the live magnifier screen. */
class CameraPreviewController(context: Context) {
    private val appContext = context.applicationContext
    private val mainExecutor: Executor = ContextCompat.getMainExecutor(appContext)
    private var cameraProvider: ProcessCameraProvider? = null
    private var previewUseCase: Preview? = null
    private var analysisUseCase: ImageAnalysis? = null
    private var cameraControl: androidx.camera.core.CameraControl? = null
    private var requestedZoomRatio: Float? = null
    private var bindGeneration = 0
    private val analysisExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private var activeLifecycleOwner: LifecycleOwner? = null
    private var activePreviewView: PreviewView? = null
    private var activeFrameStore: LatestFrameStore<Bitmap>? = null

    fun bind(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        requestedZoomRatio: Float,
        frameStore: LatestFrameStore<Bitmap>,
        onBound: () -> Unit,
        onZoomState: (ZoomState) -> Unit,
        onFrameError: (CameraError) -> Unit,
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

                val oldUseCases = listOfNotNull(previewUseCase, analysisUseCase)
                if (oldUseCases.isNotEmpty()) provider.unbind(*oldUseCases.toTypedArray())

                val preview = Preview.Builder()
                    .setTargetRotation(previewView.display?.rotation ?: Surface.ROTATION_0)
                    .build()
                    .also { it.setSurfaceProvider(previewView.surfaceProvider) }

                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setTargetRotation(previewView.display?.rotation ?: Surface.ROTATION_0)
                    .build()
                analysis.setAnalyzer(analysisExecutor) { image ->
                    analyzeFrame(image, previewView, frameStore, onFrameError)
                }

                val camera = provider.bindToLifecycle(lifecycleOwner, selector, preview, analysis)
                cameraProvider = provider
                previewUseCase = preview
                analysisUseCase = analysis
                cameraControl = camera.cameraControl
                activeLifecycleOwner = lifecycleOwner
                activePreviewView = previewView
                activeFrameStore = frameStore
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
        val useCases = listOfNotNull(previewUseCase, analysisUseCase).toTypedArray()
        if (useCases.isEmpty()) return
        cameraProvider?.unbind(*useCases)
        previewUseCase = null
        analysisUseCase = null
        cameraControl = null
    }

    fun rebind(
        requestedZoomRatio: Float,
        onBound: () -> Unit,
        onZoomState: (ZoomState) -> Unit,
        onFrameError: (CameraError) -> Unit,
        onError: (CameraError) -> Unit,
    ) {
        val owner = activeLifecycleOwner ?: run {
            onError(CameraError.BindingFailed)
            return
        }
        val view = activePreviewView ?: run {
            onError(CameraError.BindingFailed)
            return
        }
        val store = activeFrameStore ?: run {
            onError(CameraError.BindingFailed)
            return
        }
        bind(owner, view, requestedZoomRatio, store, onBound, onZoomState, onFrameError, onError)
    }

    fun shutdown() {
        unbind()
        activeLifecycleOwner = null
        activePreviewView = null
        activeFrameStore = null
        cameraProvider = null
        requestedZoomRatio = null
        analysisExecutor.shutdown()
    }

    private fun analyzeFrame(
        image: ImageProxy,
        previewView: PreviewView,
        frameStore: LatestFrameStore<Bitmap>,
        onFrameError: (CameraError) -> Unit,
    ) {
        try {
            if (frameStore.isPaused()) return
            val width = previewView.width
            val height = previewView.height
            if (width <= 0 || height <= 0) return
            val bitmap = AnalysisFrameConverter.convert(image, width, height)
            frameStore.offer(bitmap)
        } catch (_: OutOfMemoryError) {
            onFrameError(CameraError.FrameAllocationFailed)
        } catch (_: Exception) {
            onFrameError(CameraError.FrameConversionFailed)
        } finally {
            image.close()
        }
    }
}