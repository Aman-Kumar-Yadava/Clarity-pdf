package com.example.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.media.ExifInterface
import com.example.data.model.EnhancementMode
import com.example.data.model.ScanPage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.min

object DocumentEnhancer {

    /**
     * Decodes a bitmap safely with sub-sampling to respect maximum dimensions.
     */
    fun decodeSampledBitmap(filePath: String, reqWidth: Int = 2048, reqHeight: Int = 2048): Bitmap? {
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeFile(filePath, options)

        var inSampleSize = 1
        if (options.outHeight > reqHeight || options.outWidth > reqWidth) {
            val halfHeight: Int = options.outHeight / 2
            val halfWidth: Int = options.outWidth / 2
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2
            }
        }

        val decodeOptions = BitmapFactory.Options().apply {
            this.inSampleSize = inSampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return BitmapFactory.decodeFile(filePath, decodeOptions)
    }

    /**
     * Reads EXIF rotation from file.
     */
    fun getExifOrientationDegrees(filePath: String): Int {
        return try {
            val exif = ExifInterface(filePath)
            when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90
                ExifInterface.ORIENTATION_ROTATE_180 -> 180
                ExifInterface.ORIENTATION_ROTATE_270 -> 270
                else -> 0
            }
        } catch (_: Exception) {
            0
        }
    }

    /**
     * Processes a single scan page according to its rotation, crop, enhancement mode,
     * brightness, and contrast. Writes the result to a cache file and returns the path.
     */
    suspend fun processPage(context: Context, page: ScanPage, isThumbnail: Boolean = false): String = withContext(Dispatchers.Default) {
        val maxDim = if (isThumbnail) 400 else 2200
        val baseBitmap = decodeSampledBitmap(page.originalImagePath, maxDim, maxDim)
            ?: throw IllegalStateException("Failed to decode image from ${page.originalImagePath}")

        val exifDegrees = getExifOrientationDegrees(page.originalImagePath)
        val totalRotation = (page.rotationDegrees + exifDegrees) % 360

        // 1. Rotate and Crop
        val transformedBitmap = applyCropAndRotation(baseBitmap, page, totalRotation)
        if (transformedBitmap != baseBitmap) {
            baseBitmap.recycle()
        }

        // 2. Apply Enhancement Filter
        val enhancedBitmap = applyEnhancement(transformedBitmap, page.enhancementMode, page.brightness, page.contrast)
        if (enhancedBitmap != transformedBitmap) {
            transformedBitmap.recycle()
        }

        // 3. Save to temporary cache file
        val outputDir = File(context.cacheDir, "processed_pages").apply { mkdirs() }
        val prefix = if (isThumbnail) "thumb_" else "proc_"
        // Clean up previous cache files for this page to keep cache clean
        outputDir.listFiles { file -> file.name.startsWith("${prefix}${page.id}") }?.forEach { it.delete() }
        val outputFile = File(outputDir, "${prefix}${page.id}_${System.currentTimeMillis()}.jpg")
        FileOutputStream(outputFile).use { out ->
            val quality = if (isThumbnail) 75 else 90
            enhancedBitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
        }
        enhancedBitmap.recycle()

        outputFile.absolutePath
    }

    private fun applyCropAndRotation(source: Bitmap, page: ScanPage, rotationDegrees: Int): Bitmap {
        var bmp = source
        // Rotation
        if (rotationDegrees != 0) {
            val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
            bmp = Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
        }

        // Exact crop bounds calculation with safe boundary clamping
        val cropL = page.cropLeft.coerceIn(0f, 0.95f)
        val cropT = page.cropTop.coerceIn(0f, 0.95f)
        val cropR = page.cropRight.coerceIn(cropL + 0.05f, 1f)
        val cropB = page.cropBottom.coerceIn(cropT + 0.05f, 1f)

        val left = (cropL * bmp.width).toInt().coerceIn(0, bmp.width - 2)
        val top = (cropT * bmp.height).toInt().coerceIn(0, bmp.height - 2)
        val right = (cropR * bmp.width).toInt().coerceIn(left + 2, bmp.width)
        val bottom = (cropB * bmp.height).toInt().coerceIn(top + 2, bmp.height)

        val cropWidth = (right - left).coerceAtMost(bmp.width - left)
        val cropHeight = (bottom - top).coerceAtMost(bmp.height - top)

        if (left > 0 || top > 0 || cropWidth < bmp.width || cropHeight < bmp.height) {
            val cropped = Bitmap.createBitmap(bmp, left, top, cropWidth, cropHeight)
            if (cropped != bmp && bmp != source) {
                bmp.recycle()
            }
            return cropped
        }
        return bmp
    }

    private fun applyEnhancement(
        src: Bitmap,
        mode: EnhancementMode,
        brightness: Float,
        contrast: Float
    ): Bitmap {
        return when (mode) {
            EnhancementMode.ORIGINAL -> {
                if (brightness == 0f && contrast == 1f) src
                else applyColorMatrixAdjustment(src, brightness, contrast, grayscale = false)
            }
            EnhancementMode.AUTO_ENHANCE -> {
                // Mild contrast boost + slight brightness
                applyColorMatrixAdjustment(src, brightness = brightness + 10f, contrast = contrast * 1.15f, grayscale = false)
            }
            EnhancementMode.DOCUMENT -> {
                // High contrast document mode - boost contrast, brighten paper background
                applyDocumentMode(src, brightness, contrast)
            }
            EnhancementMode.BLACK_WHITE -> {
                // Adaptive / threshold monochrome
                applyMonochromeThreshold(src, brightness, contrast)
            }
            EnhancementMode.GRAYSCALE -> {
                // Smooth monochrome with enhanced tonal curve
                applyColorMatrixAdjustment(src, brightness = brightness, contrast = contrast * 1.1f, grayscale = true)
            }
            EnhancementMode.COLOR -> {
                // Enhanced color document: saturation + contrast boost
                applyColorBoost(src, brightness, contrast)
            }
            EnhancementMode.PHOTO -> {
                // Natural contrast with shadow preservation
                applyColorMatrixAdjustment(src, brightness = brightness, contrast = contrast * 1.05f, grayscale = false)
            }
        }
    }

    private fun applyColorMatrixAdjustment(
        src: Bitmap,
        brightness: Float,
        contrast: Float,
        grayscale: Boolean
    ): Bitmap {
        val result = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

        val matrix = ColorMatrix()
        if (grayscale) {
            matrix.setSaturation(0f)
        }

        // Apply contrast
        val scale = contrast
        val translate = (-0.5f * scale + 0.5f) * 255f + brightness
        val contrastMatrix = ColorMatrix(floatArrayOf(
            scale, 0f, 0f, 0f, translate,
            0f, scale, 0f, 0f, translate,
            0f, 0f, scale, 0f, translate,
            0f, 0f, 0f, 1f, 0f
        ))
        matrix.postConcat(contrastMatrix)

        paint.colorFilter = ColorMatrixColorFilter(matrix)
        canvas.drawBitmap(src, 0f, 0f, paint)
        return result
    }

    private fun applyColorBoost(src: Bitmap, brightness: Float, contrast: Float): Bitmap {
        val result = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

        val matrix = ColorMatrix()
        matrix.setSaturation(1.3f) // Boost saturation

        val scale = contrast * 1.1f
        val translate = (-0.5f * scale + 0.5f) * 255f + (brightness + 5f)
        val contrastMatrix = ColorMatrix(floatArrayOf(
            scale, 0f, 0f, 0f, translate,
            0f, scale, 0f, 0f, translate,
            0f, 0f, scale, 0f, translate,
            0f, 0f, 0f, 1f, 0f
        ))
        matrix.postConcat(contrastMatrix)

        paint.colorFilter = ColorMatrixColorFilter(matrix)
        canvas.drawBitmap(src, 0f, 0f, paint)
        return result
    }

    /**
     * Document Mode: Whiten background, deepen dark strokes and text.
     */
    private fun applyDocumentMode(src: Bitmap, userBrightness: Float, userContrast: Float): Bitmap {
        val width = src.width
        val height = src.height
        val pixels = IntArray(width * height)
        src.getPixels(pixels, 0, width, 0, 0, width, height)

        val bOffset = userBrightness.toInt()
        val cFactor = (userContrast * 1.4f)

        for (i in pixels.indices) {
            val c = pixels[i]
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF

            // Perceptual luminance
            val lum = (0.299f * r + 0.587f * g + 0.114f * b).toInt()

            // If luminance is high (paper background), push towards white
            val newLum = if (lum > 140) {
                min(255, (lum * 1.25f + 15).toInt())
            } else {
                // Dark ink / text: deepen
                max(0, (lum * 0.75f).toInt())
            }

            // Contrast & user adjustments
            var adjusted = ((newLum - 128) * cFactor + 128 + bOffset).toInt()
            adjusted = adjusted.coerceIn(0, 255)

            // Soft-recombine with subtle original color preservation or clean document look
            val outR = min(255, max(0, ((r.toFloat() / (lum + 1)) * adjusted).toInt()))
            val outG = min(255, max(0, ((g.toFloat() / (lum + 1)) * adjusted).toInt()))
            val outB = min(255, max(0, ((b.toFloat() / (lum + 1)) * adjusted).toInt()))

            pixels[i] = (0xFF shl 24) or (outR shl 16) or (outG shl 8) or outB
        }

        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        result.setPixels(pixels, 0, width, 0, 0, width, height)
        return result
    }

    /**
     * B&W Mode: Clean high-contrast thresholding preserving fine text strokes.
     */
    private fun applyMonochromeThreshold(src: Bitmap, userBrightness: Float, userContrast: Float): Bitmap {
        val width = src.width
        val height = src.height
        val pixels = IntArray(width * height)
        src.getPixels(pixels, 0, width, 0, 0, width, height)

        // Calculate average luminance for adaptive threshold
        var totalLum = 0L
        val step = max(1, (width * height) / 5000)
        var sampleCount = 0
        var idx = 0
        while (idx < pixels.size) {
            val c = pixels[idx]
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF
            totalLum += (0.299f * r + 0.587f * g + 0.114f * b).toInt()
            sampleCount++
            idx += step
        }
        val avgLum = if (sampleCount > 0) (totalLum / sampleCount).toInt() else 128
        val threshold = (avgLum * 0.9f - userBrightness * 0.5f).toInt().coerceIn(60, 210)

        for (i in pixels.indices) {
            val c = pixels[i]
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF
            val lum = (0.299f * r + 0.587f * g + 0.114f * b).toInt()

            val colorVal = if (lum < threshold) 0x1A else 0xFD
            pixels[i] = (0xFF shl 24) or (colorVal shl 16) or (colorVal shl 8) or colorVal
        }

        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        result.setPixels(pixels, 0, width, 0, 0, width, height)
        return result
    }
}
