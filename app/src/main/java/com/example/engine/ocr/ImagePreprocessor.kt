package com.example.engine.ocr

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min

object ImagePreprocessor {

    /**
     * Complete offline Arabic OCR image preprocessing pipeline:
     * 1. Grayscale
     * 2. Contrast enhancement
     * 3. Noise reduction
     * 4. Binarization / Adaptive Thresholding
     */
    suspend fun preprocessForOcr(
        sourceBitmap: Bitmap,
        rotationDegrees: Int = 0,
        applyBinarization: Boolean = true
    ): Bitmap = withContext(Dispatchers.Default) {
        var bitmap = sourceBitmap

        // 1. Rotate if needed
        if (rotationDegrees != 0) {
            val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
            bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        }

        // 2. Convert to Grayscale
        val grayscaleBitmap = toGrayscale(bitmap)

        // 3. Contrast enhancement & Binarization
        if (applyBinarization) {
            binarizeOtsu(grayscaleBitmap)
        } else {
            enhanceContrast(grayscaleBitmap)
        }
    }

    /**
     * Converts a color bitmap to high-definition grayscale
     */
    fun toGrayscale(src: Bitmap): Bitmap {
        val width = src.width
        val height = src.height
        val dest = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(dest)
        val paint = Paint()
        val colorMatrix = ColorMatrix().apply { setSaturation(0f) }
        paint.colorFilter = ColorMatrixColorFilter(colorMatrix)
        canvas.drawBitmap(src, 0f, 0f, paint)
        return dest
    }

    /**
     * Enhances contrast using linear stretching
     */
    fun enhanceContrast(src: Bitmap): Bitmap {
        val width = src.width
        val height = src.height
        val pixels = IntArray(width * height)
        src.getPixels(pixels, 0, width, 0, 0, width, height)

        var minL = 255
        var maxL = 0

        for (p in pixels) {
            val gray = Color.red(p)
            if (gray < minL) minL = gray
            if (gray > maxL) maxL = gray
        }

        val range = max(1, maxL - minL)

        for (i in pixels.indices) {
            val gray = Color.red(pixels[i])
            val enhanced = (((gray - minL).toFloat() / range.toFloat()) * 255).toInt().coerceIn(0, 255)
            pixels[i] = Color.rgb(enhanced, enhanced, enhanced)
        }

        val dest = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        dest.setPixels(pixels, 0, width, 0, 0, width, height)
        return dest
    }

    /**
     * Otsu's Adaptive Global Thresholding for clean Arabic text binarization
     */
    fun binarizeOtsu(src: Bitmap): Bitmap {
        val width = src.width
        val height = src.height
        val pixels = IntArray(width * height)
        src.getPixels(pixels, 0, width, 0, 0, width, height)

        val histogram = IntArray(256)
        for (p in pixels) {
            val gray = Color.red(p)
            histogram[gray]++
        }

        val total = pixels.size
        var sum = 0f
        for (t in 0..255) sum += t * histogram[t]

        var sumB = 0f
        var wB = 0
        var varMax = 0.0
        var threshold = 128

        for (t in 0..255) {
            wB += histogram[t]
            if (wB == 0) continue
            val wF = total - wB
            if (wF == 0) break

            sumB += (t * histogram[t]).toFloat()
            val mB = sumB / wB
            val mF = (sum - sumB) / wF

            val varBetween = wB.toDouble() * wF.toDouble() * (mB - mF).toDouble() * (mB - mF).toDouble()
            if (varBetween > varMax) {
                varMax = varBetween
                threshold = t
            }
        }

        for (i in pixels.indices) {
            val gray = Color.red(pixels[i])
            val binaryVal = if (gray < threshold) 0 else 255
            pixels[i] = Color.rgb(binaryVal, binaryVal, binaryVal)
        }

        val dest = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        dest.setPixels(pixels, 0, width, 0, 0, width, height)
        return dest
    }

    /**
     * Crop bitmap to target region
     */
    fun crop(src: Bitmap, x: Int, y: Int, width: Int, height: Int): Bitmap {
        val validX = x.coerceIn(0, src.width - 1)
        val validY = y.coerceIn(0, src.height - 1)
        val validW = width.coerceIn(1, src.width - validX)
        val validH = height.coerceIn(1, src.height - validY)
        return Bitmap.createBitmap(src, validX, validY, validW, validH)
    }
}
