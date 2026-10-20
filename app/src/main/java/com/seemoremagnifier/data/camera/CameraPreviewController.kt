package com.seemoremagnifier.data.camera

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.view.Surface
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.seemoremagnifier.ui.magnifier.CameraError
import java.util.concurrent.Executor

/** Owns the CameraX preview use case used by the live magnifier screen. */
class CameraPreviewController(context: Context) {
    private val appContext = context.applicationContext
    private val mainExecutor: Executor = ContextCompat.getMainExecutor(appContext)
    private var cameraProvider: ProcessCameraProvider? = null
    private var previewUseCase: Preview? = null
    private var bindGeneration = 0

    fun bind(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        onBound: () -> Unit,
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

                provider.bindToLifecycle(lifecycleOwner, selector, preview)
                cameraProvider = provider
                previewUseCase = preview
                onBound()
            } catch (_: Exception) {
                onError(CameraError.BindingFailed)
            }
        }, mainExecutor)
    }

    fun unbind() {
        bindGeneration++
        val preview = previewUseCase ?: return
        cameraProvider?.unbind(preview)
        previewUseCase = null
        cameraProvider = null
    }
}