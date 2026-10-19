package com.example.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.example.data.model.CompressionQuality
import com.example.data.model.PdfOrientation
import com.example.data.model.PdfPageSize
import com.example.data.model.ScanPage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

data class PdfExportConfig(
    val title: String,
    val pageSize: PdfPageSize = PdfPageSize.A4,
    val orientation: PdfOrientation = PdfOrientation.PORTRAIT,
    val quality: CompressionQuality = CompressionQuality.HIGH,
    val author: String = "Clarity PDF",
    val isPasswordProtected: Boolean = false,
    val password: String = ""
)

object PdfGenerator {

    /**
     * Generates a multi-page PDF document from a list of ScanPages.
     * Sequential, memory-safe execution with progress callback.
     */
    suspend fun generatePdf(
        context: Context,
        pages: List<ScanPage>,
        config: PdfExportConfig,
        onProgress: (current: Int, total: Int, message: String) -> Unit = { _, _, _ -> }
    ): File = withContext(Dispatchers.IO) {
        val totalPages = pages.size
        require(totalPages > 0) { "Cannot generate PDF with 0 pages" }

        val docDir = File(context.filesDir, "documents").apply { mkdirs() }
        val sanitizedTitle = config.title.trim().replace(Regex("[\\\\/:*?\"<>|]"), "_")
        val outputFile = File(docDir, "${sanitizedTitle}_${System.currentTimeMillis()}.pdf")

        val pdfDoc = PdfDocument()

        try {
            for (index in pages.indices) {
                val pageNumber = index + 1
                onProgress(pageNumber, totalPages, "Processing page $pageNumber of $totalPages...")

                val page = pages[index]
                // 1. Get or create processed page image
                val imagePath = page.processedImagePath ?: DocumentEnhancer.processPage(context, page, isThumbnail = false)

                // 2. Decode bitmap with target DPI scaling
                val maxDim = when (config.quality) {
                    CompressionQuality.STANDARD -> 1400
                    CompressionQuality.HIGH -> 2000
                    CompressionQuality.MAXIMUM -> 2800
                }
                val bitmap = DocumentEnhancer.decodeSampledBitmap(imagePath, maxDim, maxDim)
                    ?: continue

                // 3. Determine page dimensions (points: 72 points = 1 inch)
                val isLandscape = when (config.orientation) {
                    PdfOrientation.PORTRAIT -> false
                    PdfOrientation.LANDSCAPE -> true
                    PdfOrientation.AUTO -> bitmap.width > bitmap.height
                }

                val (pageWidth, pageHeight) = if (config.pageSize == PdfPageSize.AUTO) {
                    // Fit to Image aspect ratio mapped to standard pt space
                    val scale = min(1.0, 842.0 / max(bitmap.width, bitmap.height))
                    Pair((bitmap.width * scale).roundToInt(), (bitmap.height * scale).roundToInt())
                } else {
                    val w = if (isLandscape) config.pageSize.heightPt else config.pageSize.widthPt
                    val h = if (isLandscape) config.pageSize.widthPt else config.pageSize.heightPt
                    Pair(w, h)
                }

                // 4. Create PDF Page
                val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                val pdfPage = pdfDoc.startPage(pageInfo)
                val canvas = pdfPage.canvas

                // Fill background with clean white
                canvas.drawColor(Color.WHITE)

                // 5. Fit bitmap preserving aspect ratio
                val margin = if (config.pageSize == PdfPageSize.AUTO) 0f else 18f // 1/4 inch margin
                val availW = pageWidth - (margin * 2)
                val availH = pageHeight - (margin * 2)

                val bmpRatio = bitmap.width.toFloat() / bitmap.height.toFloat()
                val targetRatio = availW / availH

                val (drawW, drawH) = if (bmpRatio > targetRatio) {
                    Pair(availW, availW / bmpRatio)
                } else {
                    Pair(availH * bmpRatio, availH)
                }

                val drawLeft = margin + (availW - drawW) / 2f
                val drawTop = margin + (availH - drawH) / 2f

                val destRect = RectF(drawLeft, drawTop, drawLeft + drawW, drawTop + drawH)
                val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

                canvas.drawBitmap(bitmap, null, destRect, paint)
                pdfDoc.finishPage(pdfPage)
                bitmap.recycle()
            }

            onProgress(totalPages, totalPages, "Saving PDF file...")
            FileOutputStream(outputFile).use { out ->
                pdfDoc.writeTo(out)
            }
        } finally {
            pdfDoc.close()
        }

        outputFile
    }

    /**
     * Renders a preview thumbnail of the first page of a PDF file.
     */
    suspend fun renderPdfThumbnail(context: Context, pdfFile: File): String? = withContext(Dispatchers.IO) {
        if (!pdfFile.exists() || pdfFile.length() == 0L) return@withContext null
        try {
            val pfd = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = PdfRenderer(pfd)
            if (renderer.pageCount == 0) {
                renderer.close()
                pfd.close()
                return@withContext null
            }
            val page = renderer.openPage(0)
            val width = 360
            val height = (width * (page.height.toFloat() / page.width.toFloat())).toInt()
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.WHITE)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            page.close()
            renderer.close()
            pfd.close()

            val thumbDir = File(context.cacheDir, "pdf_thumbnails").apply { mkdirs() }
            val thumbFile = File(thumbDir, "thumb_${pdfFile.nameWithoutExtension}.jpg")
            FileOutputStream(thumbFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
            }
            bitmap.recycle()
            thumbFile.absolutePath
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Renders all pages of a PDF file to Bitmaps for the in-app PDF reader.
     */
    suspend fun renderPdfPages(pdfFile: File, maxPages: Int = 50): List<Bitmap> = withContext(Dispatchers.IO) {
        val list = mutableListOf<Bitmap>()
        if (!pdfFile.exists()) return@withContext list
        try {
            val pfd = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = PdfRenderer(pfd)
            val count = min(renderer.pageCount, maxPages)
            for (i in 0 until count) {
                val page = renderer.openPage(i)
                val targetW = 900
                val targetH = (targetW * (page.height.toFloat() / page.width.toFloat())).toInt()
                val bmp = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bmp)
                canvas.drawColor(Color.WHITE)
                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()
                list.add(bmp)
            }
            renderer.close()
            pfd.close()
        } catch (_: Exception) {}
        list
    }

    /**
     * Combines multiple existing PDF files into a single unified PDF.
     */
    suspend fun combinePdfs(
        context: Context,
        pdfFiles: List<File>,
        outputTitle: String,
        onProgress: (current: Int, total: Int) -> Unit = { _, _ -> }
    ): File = withContext(Dispatchers.IO) {
        val docDir = File(context.filesDir, "documents").apply { mkdirs() }
        val sanitized = outputTitle.trim().replace(Regex("[\\\\/:*?\"<>|]"), "_")
        val outputFile = File(docDir, "${sanitized}_${System.currentTimeMillis()}.pdf")

        val newPdf = PdfDocument()
        var globalPageNumber = 1

        val totalPagesAcrossFiles = pdfFiles.sumOf { file ->
            try {
                val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                val r = PdfRenderer(pfd)
                val c = r.pageCount
                r.close()
                pfd.close()
                c
            } catch (_: Exception) { 0 }
        }

        for (file in pdfFiles) {
            if (!file.exists()) continue
            val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = PdfRenderer(pfd)
            for (i in 0 until renderer.pageCount) {
                val page = renderer.openPage(i)
                val pageInfo = PdfDocument.PageInfo.Builder(page.width, page.height, globalPageNumber).create()
                val pdfPage = newPdf.startPage(pageInfo)
                val bmp = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                pdfPage.canvas.drawBitmap(bmp, 0f, 0f, null)
                newPdf.finishPage(pdfPage)
                bmp.recycle()
                page.close()

                onProgress(globalPageNumber, max(1, totalPagesAcrossFiles))
                globalPageNumber++
            }
            renderer.close()
            pfd.close()
        }

        FileOutputStream(outputFile).use { out ->
            newPdf.writeTo(out)
        }
        newPdf.close()
        outputFile
    }

    /**
     * Compresses an existing PDF file by re-rendering its pages with higher compression.
     */
    suspend fun compressPdf(
        context: Context,
        srcPdf: File,
        compressionLevel: CompressionQuality = CompressionQuality.STANDARD
    ): File = withContext(Dispatchers.IO) {
        val docDir = File(context.filesDir, "documents").apply { mkdirs() }
        val outputName = "${srcPdf.nameWithoutExtension}_compressed_${System.currentTimeMillis()}.pdf"
        val outputFile = File(docDir, outputName)

        val pfd = ParcelFileDescriptor.open(srcPdf, ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = PdfRenderer(pfd)
        val newPdf = PdfDocument()

        val scale = when (compressionLevel) {
            CompressionQuality.STANDARD -> 0.7f
            CompressionQuality.HIGH -> 0.85f
            CompressionQuality.MAXIMUM -> 1.0f
        }

        for (i in 0 until renderer.pageCount) {
            val page = renderer.openPage(i)
            val pageInfo = PdfDocument.PageInfo.Builder(page.width, page.height, i + 1).create()
            val pdfPage = newPdf.startPage(pageInfo)

            val renderW = (page.width * scale).toInt()
            val renderH = (page.height * scale).toInt()
            val bmp = Bitmap.createBitmap(renderW, renderH, Bitmap.Config.RGB_565)
            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

            // Draw scaled to target page size
            val destRect = Rect(0, 0, page.width, page.height)
            pdfPage.canvas.drawBitmap(bmp, null, destRect, Paint(Paint.FILTER_BITMAP_FLAG))
            newPdf.finishPage(pdfPage)
            bmp.recycle()
            page.close()
        }
        renderer.close()
        pfd.close()

        FileOutputStream(outputFile).use { out ->
            newPdf.writeTo(out)
        }
        newPdf.close()
        outputFile
    }
}
