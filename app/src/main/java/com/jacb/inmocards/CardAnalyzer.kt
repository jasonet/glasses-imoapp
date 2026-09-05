package com.jacb.inmocards

import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy

class CardAnalyzer(
    private val onRank: (CardRank?) -> Unit,
    private val onError: (Throwable) -> Unit
) : ImageAnalysis.Analyzer, AutoCloseable {
    private val recognizer = RankTemplateRecognizer()
    private var lastAnalysisAt = 0L

    @Volatile
    var intervalMs: Long = 350L

    override fun analyze(image: ImageProxy) {
        val now = System.currentTimeMillis()
        if (now - lastAnalysisAt < intervalMs) {
            image.close()
            return
        }
        lastAnalysisAt = now

        var roi: Bitmap? = null
        try {
            roi = buildRecognitionRegion(image)
            onRank(recognizer.recognize(roi))
        } catch (error: Throwable) {
            onError(error)
        } finally {
            roi?.recycle()
            image.close()
        }
    }

    private fun buildRecognitionRegion(image: ImageProxy): Bitmap {
        val plane = image.planes.first()
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride
        val paddedWidth = image.width + (rowStride - pixelStride * image.width) / pixelStride
        val padded = Bitmap.createBitmap(paddedWidth, image.height, Bitmap.Config.ARGB_8888)
        plane.buffer.rewind()
        padded.copyPixelsFromBuffer(plane.buffer)

        val unpadded = Bitmap.createBitmap(padded, 0, 0, image.width, image.height)
        if (unpadded !== padded) padded.recycle()

        val rotation = image.imageInfo.rotationDegrees
        val oriented = if (rotation == 0) unpadded else {
            Bitmap.createBitmap(
                unpadded,
                0,
                0,
                unpadded.width,
                unpadded.height,
                Matrix().apply { postRotate(rotation.toFloat()) },
                true
            ).also { unpadded.recycle() }
        }

        val left = (oriented.width * 0.35f).toInt()
        val top = (oriented.height * 0.25f).toInt()
        val width = (oriented.width * 0.30f).toInt().coerceAtLeast(1)
        val height = (oriented.height * 0.50f).toInt().coerceAtLeast(1)
        return Bitmap.createBitmap(oriented, left, top, width, height).also {
            if (it !== oriented) oriented.recycle()
        }
    }

    override fun close() = Unit
}
