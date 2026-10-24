// SPDX-FileCopyrightText: 2026 SeeMoreMagnifier contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package com.seemoremagnifier.data.camera

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import androidx.camera.core.ImageProxy
import java.io.ByteArrayOutputStream
import kotlin.math.ceil

/** Converts CameraX YUV_420_888 frames to display-sized, non-mirrored rear-camera bitmaps. */
internal object AnalysisFrameConverter {
    private const val MAX_OUTPUT_WIDTH = 1280
    private const val MAX_OUTPUT_HEIGHT = 720
    private const val MAX_STILL_WIDTH = 2560
    private const val MAX_STILL_HEIGHT = 2560
    private const val JPEG_QUALITY = 82

    fun convert(image: ImageProxy, previewWidth: Int, previewHeight: Int): Bitmap {
        require(image.format == android.graphics.ImageFormat.YUV_420_888) {
            "Unsupported analysis format: ${image.format}"
        }
        require(previewWidth > 0 && previewHeight > 0) { "Preview dimensions are not available." }

        val width = image.width
        val height = image.height
        val nv21 = toNv21(image)
        val jpeg = ByteArrayOutputStream().use { output ->
            val yuv = YuvImage(nv21, android.graphics.ImageFormat.NV21, width, height, null)
            check(yuv.compressToJpeg(Rect(0, 0, width, height), JPEG_QUALITY, output)) {
                "YUV-to-JPEG conversion failed."
            }
            output.toByteArray()
        }

        val rotation = ((image.imageInfo.rotationDegrees % 360) + 360) % 360
        val rotatedWidth = if (rotation == 90 || rotation == 270) height else width
        val rotatedHeight = if (rotation == 90 || rotation == 270) width else height
        val sampleSize = calculateSampleSize(rotatedWidth, rotatedHeight)
        val decoded = BitmapFactory.decodeByteArray(
            jpeg,
            0,
            jpeg.size,
            BitmapFactory.Options().apply { inSampleSize = sampleSize },
        ) ?: error("JPEG frame decode failed.")

        val oriented = if (rotation == 0) decoded else {
            val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
            val rotated = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
            if (rotated !== decoded) decoded.recycle()
            rotated
        }

        return centerCropToAspect(oriented, previewWidth.toFloat() / previewHeight)
    }

    /** Decodes a high-resolution CameraX JPEG still, preserving its full detail within safe bounds. */
    fun convertStill(image: ImageProxy, previewWidth: Int, previewHeight: Int): Bitmap {
        require(image.format == android.graphics.ImageFormat.JPEG) {
            "Unsupported still format: ${image.format}"
        }
        require(previewWidth > 0 && previewHeight > 0) { "Preview dimensions are not available." }
        val jpegPlane = image.planes.firstOrNull() ?: error("Captured still has no JPEG data.")
        val jpegBuffer = jpegPlane.buffer.duplicate()
        val jpeg = ByteArray(jpegBuffer.remaining()).also(jpegBuffer::get)
        val bounds = BitmapFactory.Options().also {
            it.inJustDecodeBounds = true
            BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, it)
        }
        val rotation = ((image.imageInfo.rotationDegrees % 360) + 360) % 360
        val sourceWidth = bounds.outWidth.takeIf { it > 0 } ?: image.width
        val sourceHeight = bounds.outHeight.takeIf { it > 0 } ?: image.height
        val rotatedWidth = if (rotation == 90 || rotation == 270) sourceHeight else sourceWidth
        val rotatedHeight = if (rotation == 90 || rotation == 270) sourceWidth else sourceHeight
        val sampleSize = stillSampleSizeFor(rotatedWidth, rotatedHeight)
        val decoded = BitmapFactory.decodeByteArray(
            jpeg,
            0,
            jpeg.size,
            BitmapFactory.Options().apply { inSampleSize = sampleSize },
        ) ?: error("Captured still could not be decoded.")

        val oriented = if (rotation == 0) decoded else {
            val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
            val rotated = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
            if (rotated !== decoded) decoded.recycle()
            rotated
        }
        return centerCropToAspect(oriented, previewWidth.toFloat() / previewHeight)
    }

    private fun toNv21(image: ImageProxy): ByteArray {
        val width = image.width
        val height = image.height
        val ySize = width * height
        val chromaWidth = ceil(width / 2f).toInt()
        val chromaHeight = ceil(height / 2f).toInt()
        val output = ByteArray(ySize + 2 * chromaWidth * chromaHeight)
        val planes = image.planes

        copyPlane(
            plane = planes[0],
            width = width,
            height = height,
            output = output,
            outputOffset = 0,
            outputPixelStride = 1,
        )
        // NV21 interleaves V then U.
        copyPlane(
            plane = planes[2],
            width = chromaWidth,
            height = chromaHeight,
            output = output,
            outputOffset = ySize,
            outputPixelStride = 2,
        )
        copyPlane(
            plane = planes[1],
            width = chromaWidth,
            height = chromaHeight,
            output = output,
            outputOffset = ySize + 1,
            outputPixelStride = 2,
        )
        return output
    }

    private fun copyPlane(
        plane: ImageProxy.PlaneProxy,
        width: Int,
        height: Int,
        output: ByteArray,
        outputOffset: Int,
        outputPixelStride: Int,
    ) {
        val buffer = plane.buffer.duplicate()
        val source = ByteArray(buffer.remaining()).also(buffer::get)
        var outputIndex = outputOffset
        for (row in 0 until height) {
            val rowStart = row * plane.rowStride
            for (column in 0 until width) {
                val sourceIndex = rowStart + column * plane.pixelStride
                if (sourceIndex < source.size && outputIndex < output.size) {
                    output[outputIndex] = source[sourceIndex]
                }
                outputIndex += outputPixelStride
            }
        }
    }

    private fun calculateSampleSize(width: Int, height: Int): Int {
        var sampleSize = 1
        while (width / sampleSize > MAX_OUTPUT_WIDTH || height / sampleSize > MAX_OUTPUT_HEIGHT) {
            sampleSize *= 2
        }
        return sampleSize
    }

    internal fun stillSampleSizeFor(width: Int, height: Int): Int {
        require(width > 0 && height > 0) { "Still dimensions must be positive." }
        var sampleSize = 1
        while (width / sampleSize > MAX_STILL_WIDTH || height / sampleSize > MAX_STILL_HEIGHT) {
            sampleSize *= 2
        }
        return sampleSize
    }

    private fun centerCropToAspect(bitmap: Bitmap, targetAspectRatio: Float): Bitmap {
        val bitmapAspectRatio = bitmap.width.toFloat() / bitmap.height
        val cropWidth: Int
        val cropHeight: Int
        if (bitmapAspectRatio > targetAspectRatio) {
            cropHeight = bitmap.height
            cropWidth = (cropHeight * targetAspectRatio).toInt().coerceIn(1, bitmap.width)
        } else {
            cropWidth = bitmap.width
            cropHeight = (cropWidth / targetAspectRatio).toInt().coerceIn(1, bitmap.height)
        }
        val left = (bitmap.width - cropWidth) / 2
        val top = (bitmap.height - cropHeight) / 2
        if (left == 0 && top == 0 && cropWidth == bitmap.width && cropHeight == bitmap.height) {
            return bitmap
        }
        val cropped = Bitmap.createBitmap(bitmap, left, top, cropWidth, cropHeight)
        if (cropped !== bitmap) bitmap.recycle()
        return cropped
    }
}