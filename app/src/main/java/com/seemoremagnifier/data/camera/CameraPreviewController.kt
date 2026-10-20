package com.seemoremagnifier.data.camera

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.view.Surface
import androidx.camera.core.CameraSelector
import androidx.camera.core.CameraControl
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
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
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** Owns the CameraX preview use case used by the live magnifier screen. */
class CameraPreviewController(context: Context) {
    private val appContext = context.applicationContext
    private val mainExecutor: Executor = ContextCompat.getMainExecutor(appContext)
    private var cameraProvider: ProcessCameraProvider? = null
    private var previewUseCase: Preview? = null
    private var analysisUseCase: ImageAnalysis? = null
    private var imageCaptureUseCase: ImageCapture? = null
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

                val oldUseCases = listOfNotNull(previewUseCase, analysisUseCase, imageCaptureUseCase)
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

                val imageCapture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                    .setTargetRotation(previewView.display?.rotation ?: Surface.ROTATION_0)
                    .build()

                val camera = provider.bindToLifecycle(lifecycleOwner, selector, preview, analysis, imageCapture)
                cameraProvider = provider
                previewUseCase = preview
                analysisUseCase = analysis
                imageCaptureUseCase = imageCapture
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
        onZoomRequested: (Float) -> Unit,
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
        onZoomRequested(targetRatio)
        val zoomFuture = try {
            control.setZoomRatio(targetRatio)
        } catch (_: Exception) {
            requestedZoomRatio = zoomState.zoomRatio
            onZoomRequested(zoomState.zoomRatio)
            onError(CameraError.ZoomFailed)
            return
        }
        zoomFuture.addListener({
            try {
                zoomFuture.get()
            } catch (exception: Exception) {
                if (!isSupersededZoomRequest(exception)) {
                    requestedZoomRatio = zoomState.zoomRatio
                    onZoomRequested(zoomState.zoomRatio)
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
        val useCases = listOfNotNull(previewUseCase, analysisUseCase, imageCaptureUseCase).toTypedArray()
        if (useCases.isNotEmpty()) cameraProvider?.unbind(*useCases)
        previewUseCase = null
        analysisUseCase = null
        imageCaptureUseCase = null
        cameraControl = null
    }

    fun focusAndCapture(
        x: Float,
        y: Float,
        previewWidth: Int,
        previewHeight: Int,
        onFocusResult: (Boolean) -> Unit,
        onCaptured: (Bitmap) -> Unit,
        onError: (CameraError) -> Unit,
    ) {
        val capture = imageCaptureUseCase ?: run {
            onError(CameraError.CaptureFailed)
            return
        }
        val preview = activePreviewView ?: run {
            onError(CameraError.CaptureFailed)
            return
        }
        val captureGeneration = bindGeneration
        val captureStarted = AtomicBoolean(false)
        val focusResultReported = AtomicBoolean(false)
        val reportFocusResult: (Boolean) -> Unit = { focused ->
            if (focusResultReported.compareAndSet(false, true) && captureGeneration == bindGeneration) {
                onFocusResult(focused)
            }
        }
        val captureStill: () -> Unit = {
            if (captureStarted.compareAndSet(false, true) &&
                captureGeneration == bindGeneration && imageCaptureUseCase === capture
            ) {
                try {
                    capture.takePicture(analysisExecutor, object : ImageCapture.OnImageCapturedCallback() {
                        override fun onCaptureSuccess(image: ImageProxy) {
                            try {
                                val bitmap = AnalysisFrameConverter.convertStill(image, previewWidth, previewHeight)
                                mainExecutor.execute {
                                    if (captureGeneration == bindGeneration && imageCaptureUseCase === capture) {
                                        onCaptured(bitmap)
                                    } else {
                                        bitmap.recycle()
                                    }
                                }
                            } catch (_: OutOfMemoryError) {
                                mainExecutor.execute {
                                    if (captureGeneration == bindGeneration) onError(CameraError.FrameAllocationFailed)
                                }
                            } catch (_: Exception) {
                                mainExecutor.execute {
                                    if (captureGeneration == bindGeneration) onError(CameraError.CaptureFailed)
                                }
                            } finally {
                                image.close()
                            }
                        }

                        override fun onError(exception: ImageCaptureException) {
                            mainExecutor.execute {
                                if (captureGeneration == bindGeneration) onError(CameraError.CaptureFailed)
                            }
                        }
                    })
                } catch (_: Exception) {
                    mainExecutor.execute {
                        if (captureGeneration == bindGeneration) onError(CameraError.CaptureFailed)
                    }
                }
            }
        }

        val control = cameraControl
        if (control == null || previewWidth <= 0 || previewHeight <= 0) {
            reportFocusResult(false)
            captureStill()
            return
        }
        try {
            val point = preview.meteringPointFactory.createPoint(
                x.coerceIn(0f, previewWidth.toFloat()),
                y.coerceIn(0f, previewHeight.toFloat()),
            )
            val action = FocusMeteringAction.Builder(point)
                .setAutoCancelDuration(3, TimeUnit.SECONDS)
                .build()
            val focusFuture = control.startFocusAndMetering(action)
            val timeout = Runnable {
                reportFocusResult(false)
                captureStill()
            }
            val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
            mainHandler.postDelayed(timeout, FOCUS_TIMEOUT_MS)
            focusFuture.addListener({
                mainHandler.removeCallbacks(timeout)
                val focused = try {
                    focusFuture.get().isFocusSuccessful
                } catch (_: Exception) {
                    false
                }
                reportFocusResult(focused)
                captureStill()
            }, mainExecutor)
        } catch (_: Exception) {
            reportFocusResult(false)
            captureStill()
        }
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

    private companion object {
        const val FOCUS_TIMEOUT_MS = 900L
    }
}