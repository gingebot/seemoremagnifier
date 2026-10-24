// SPDX-FileCopyrightText: 2026 SeeMoreMagnifier contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package com.seemoremagnifier.ui.magnifier

import androidx.annotation.StringRes
import com.seemoremagnifier.R

enum class CameraError(@param:StringRes val messageRes: Int) {
    PermissionMissing(R.string.camera_permission_missing),
    RearCameraUnavailable(R.string.rear_camera_unavailable),
    InitializationFailed(R.string.camera_initialization_failed),
    BindingFailed(R.string.camera_binding_failed),
    ZoomFailed(R.string.camera_zoom_failed),
    NoFrameAvailable(R.string.no_frame_available),
    FrameConversionFailed(R.string.frame_conversion_failed),
    FrameAllocationFailed(R.string.frame_allocation_failed),
    CaptureFailed(R.string.camera_capture_failed),
}