package com.seemoremagnifier.ui.magnifier

import androidx.annotation.StringRes
import com.seemoremagnifier.R

enum class CameraError(@param:StringRes val messageRes: Int) {
    PermissionMissing(R.string.camera_permission_missing),
    RearCameraUnavailable(R.string.rear_camera_unavailable),
    InitializationFailed(R.string.camera_initialization_failed),
    BindingFailed(R.string.camera_binding_failed),
    ZoomFailed(R.string.camera_zoom_failed),
}