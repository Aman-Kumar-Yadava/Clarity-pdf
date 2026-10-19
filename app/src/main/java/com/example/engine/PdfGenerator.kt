package com.example.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.example.data.model.CompressionQuality
import com.example.data.model.PdfOrientation
import com.example.data.model.PdfPageSize
import com.example.data.model.ScanPage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

data class PdfExportConfig(
    val title: String,
    val pageSize: PdfPageSize = PdfPageSize.A4,
    val orientation: PdfOrientation = PdfOrientation.PORTRAIT,
    val quality: CompressionQuality = CompressionQuality.HIGH,
    val isPasswordProtected: Boolean = false,
    val password: String = ""
)

private class CountingOutputStream(private val target: OutputStream) : OutputStream() {
    var count: Long = 0L
        private set

    override fun write(b: Int) {
        target.write(b)
        count++
    }

    override fun write(b: ByteArray) {
        target.write(b)
        count += b.size
    }

    override fun write(b: ByteArray, off: Int, len: Int) {
        target.write(b, off, len)
        count += len
    }

    override fun flush() = target.flush()
    override fun close() = target.close()
}

object PdfGenerator {

    /**
     * Generates a clean, ultra-compact multi-page PDF document.
     * Uses standard PDF 1.4 image XObjects with DCTDecode (JPEG stream),
     * stripping all EXIF and camera metadata and avoiding raw raster bloat.
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

        val totalObjects = 2 + totalPages * 3
        val objectOffsets = LongArray(totalObjects + 1)

        FileOutputStream(outputFile).buffered().use { rawOut ->
            val out = CountingOutputStream(rawOut)

            // 1. PDF Header (Binary marker ensures 8-bit clean transport)
            writeAscii(out, "%PDF-1.4\r\n%\u00E2\u00E3\u00CF\u00D3\r\n")

            // 2. Object 1: Catalog
            objectOffsets[1] = out.count
            writeAscii(out, "1 0 obj\r\n<<\r\n  /Type /Catalog\r\n  /Pages 2 0 R\r\n>>\r\nendobj\r\n")

            // 3. Object 2: Pages root
            objectOffsets[2] = out.count
            val kids = (0 until totalPages).joinToString(" ") { "${3 + it * 3} 0 R" }
            writeAscii(out, "2 0 obj\r\n<<\r\n  /Type /Pages\r\n  /Kids [$kids]\r\n  /Count $totalPages\r\n>>\r\nendobj\r\n")

            // 4. Stream each page sequentially
            for (index in pages.indices) {
                val pageNumber = index + 1
                onProgress(pageNumber, totalPages, "Optimizing page $pageNumber of $totalPages...")

                val pageObj = 3 + index * 3
                val contentObj = pageObj + 1
                val imageObj = pageObj + 2

                val page = pages[index]
                val imagePath = page.processedImagePath ?: DocumentEnhancer.processPage(context, page, isThumbnail = false)

                // Decode with dimension limits for the chosen compression preset
                val maxDim = config.quality.maxDimension
                val bitmap = DocumentEnhancer.decodeSampledBitmap(imagePath, maxDim, maxDim)
                    ?: continue

                // Compress clean bitmap to JPEG bytes, stripping all metadata
                val jpegBaos = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, config.quality.jpegQuality, jpegBaos)
                val jpegBytes = jpegBaos.toByteArray()

                // Calculate Page Dimensions in points (1 inch = 72 pt)
                val isLandscape = when (config.orientation) {
                    PdfOrientation.PORTRAIT -> false
                    PdfOrientation.LANDSCAPE -> true
                    PdfOrientation.AUTO -> bitmap.width > bitmap.height
                }

                val (pageW, pageH) = if (config.pageSize == PdfPageSize.AUTO) {
                    val scale = min(1.0, 842.0 / max(bitmap.width, bitmap.height))
                    Pair(
                        (bitmap.width * scale).toFloat().coerceAtLeast(100f),
                        (bitmap.height * scale).toFloat().coerceAtLeast(100f)
                    )
                } else {
                    val w = if (isLandscape) config.pageSize.heightPt.toFloat() else config.pageSize.widthPt.toFloat()
                    val h = if (isLandscape) config.pageSize.widthPt.toFloat() else config.pageSize.heightPt.toFloat()
                    Pair(w, h)
                }

                val margin = if (config.pageSize == PdfPageSize.AUTO) 0f else 18f
                val availW = max(10f, pageW - margin * 2f)
                val availH = max(10f, pageH - margin * 2f)

                val imgRatio = bitmap.width.toFloat() / bitmap.height.toFloat()
                val availRatio = availW / availH

                val (drawW, drawH) = if (imgRatio > availRatio) {
                    Pair(availW, availW / imgRatio)
                } else {
                    Pair(availH * imgRatio, availH)
                }

                val drawX = margin + (availW - drawW) / 2f
                val drawY = margin + (availH - drawH) / 2f

                val imgWidth = bitmap.width
                val imgHeight = bitmap.height
                bitmap.recycle() // Recycle immediately to free memory

                // Content stream
                val contentStreamStr = String.format(
                    Locale.US,
                    "q\r\n%.2f 0 0 %.2f %.2f %.2f cm\r\n/Im1 Do\r\nQ\r\n",
                    drawW, drawH, drawX, drawY
                )
                val contentBytes = contentStreamStr.toByteArray(Charsets.US_ASCII)

                // Write Page Object
                objectOffsets[pageObj] = out.count
                writeAscii(
                    out,
                    "$pageObj 0 obj\r\n<<\r\n" +
                    "  /Type /Page\r\n" +
                    "  /Parent 2 0 R\r\n" +
                    String.format(Locale.US, "  /MediaBox [0 0 %.2f %.2f]\r\n", pageW, pageH) +
                    "  /Contents $contentObj 0 R\r\n" +
                    "  /Resources <<\r\n" +
                    "    /ProcSet [/PDF /ImageC]\r\n" +
                    "    /XObject << /Im1 $imageObj 0 R >>\r\n" +
                    "  >>\r\n" +
                    ">>\r\nendobj\r\n"
                )

                // Write Content Object
                objectOffsets[contentObj] = out.count
                writeAscii(
                    out,
                    "$contentObj 0 obj\r\n<<\r\n  /Length ${contentBytes.size}\r\n>>\r\nstream\r\n"
                )
                out.write(contentBytes)
                writeAscii(out, "\r\nendstream\r\nendobj\r\n")

                // Write Image XObject (standard /Filter /DCTDecode for ultra-compact JPEGs)
                objectOffsets[imageObj] = out.count
                writeAscii(
                    out,
                    "$imageObj 0 obj\r\n<<\r\n" +
                    "  /Type /XObject\r\n" +
                    "  /Subtype /Image\r\n" +
                    "  /Width $imgWidth\r\n" +
                    "  /Height $imgHeight\r\n" +
                    "  /ColorSpace /DeviceRGB\r\n" +
                    "  /BitsPerComponent 8\r\n" +
                    "  /Filter /DCTDecode\r\n" +
                    "  /Length ${jpegBytes.size}\r\n" +
                    ">>\r\nstream\r\n"
                )
                out.write(jpegBytes)
                writeAscii(out, "\r\nendstream\r\nendobj\r\n")
            }

            // 5. Cross-Reference Table
            val startXref = out.count
            writeAscii(out, "xref\r\n0 ${totalObjects + 1}\r\n")
            writeAscii(out, "0000000000 65535 f\r\n")
            for (id in 1..totalObjects) {
                writeAscii(out, String.format(Locale.US, "%010d %05d n\r\n", objectOffsets[id], 0))
            }

            // 6. Trailer
            writeAscii(
                out,
                "trailer\r\n<<\r\n" +
                "  /Size ${totalObjects + 1}\r\n" +
                "  /Root 1 0 R\r\n" +
                ">>\r\nstartxref\r\n$startXref\r\n%%EOF\r\n"
            )
            out.flush()
        }

        outputFile
    }

    /**
     * Compresses an existing PDF file by extracting and re-encoding pages
     * into lightweight, metadata-free JPEG DCTDecode streams.
     * Can reduce bloated 10MB–80MB PDFs down to ~500KB–1.5MB.
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
        val totalPages = renderer.pageCount

        val targetMaxDim = compressionLevel.maxDimension
        val targetQuality = compressionLevel.jpegQuality

        val totalObjects = 2 + totalPages * 3
        val objectOffsets = LongArray(totalObjects + 1)

        FileOutputStream(outputFile).buffered().use { rawOut ->
            val out = CountingOutputStream(rawOut)

            writeAscii(out, "%PDF-1.4\r\n%\u00E2\u00E3\u00CF\u00D3\r\n")
            objectOffsets[1] = out.count
            writeAscii(out, "1 0 obj\r\n<<\r\n  /Type /Catalog\r\n  /Pages 2 0 R\r\n>>\r\nendobj\r\n")

            objectOffsets[2] = out.count
            val kids = (0 until totalPages).joinToString(" ") { "${3 + it * 3} 0 R" }
            writeAscii(out, "2 0 obj\r\n<<\r\n  /Type /Pages\r\n  /Kids [$kids]\r\n  /Count $totalPages\r\n>>\r\nendobj\r\n")

            for (i in 0 until totalPages) {
                val page = renderer.openPage(i)
                val pageW = page.width.toFloat()
                val pageH = page.height.toFloat()

                // Calculate scaling
                val maxSrcDim = max(page.width, page.height)
                val scale = min(1.0f, targetMaxDim.toFloat() / maxSrcDim.toFloat())
                val renderW = (page.width * scale).roundToInt().coerceAtLeast(100)
                val renderH = (page.height * scale).roundToInt().coerceAtLeast(100)

                val bmp = Bitmap.createBitmap(renderW, renderH, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bmp)
                canvas.drawColor(Color.WHITE)
                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()

                val baos = ByteArrayOutputStream()
                bmp.compress(Bitmap.CompressFormat.JPEG, targetQuality, baos)
                bmp.recycle()
                val jpegBytes = baos.toByteArray()

                val pageObj = 3 + i * 3
                val contentObj = pageObj + 1
                val imageObj = pageObj + 2

                val contentStreamStr = String.format(
                    Locale.US,
                    "q\r\n%.2f 0 0 %.2f 0 0 cm\r\n/Im1 Do\r\nQ\r\n",
                    pageW, pageH
                )
                val contentBytes = contentStreamStr.toByteArray(Charsets.US_ASCII)

                objectOffsets[pageObj] = out.count
                writeAscii(
                    out,
                    "$pageObj 0 obj\r\n<<\r\n" +
                    "  /Type /Page\r\n" +
                    "  /Parent 2 0 R\r\n" +
                    String.format(Locale.US, "  /MediaBox [0 0 %.2f %.2f]\r\n", pageW, pageH) +
                    "  /Contents $contentObj 0 R\r\n" +
                    "  /Resources <<\r\n" +
                    "    /ProcSet [/PDF /ImageC]\r\n" +
                    "    /XObject << /Im1 $imageObj 0 R >>\r\n" +
                    "  >>\r\n" +
                    ">>\r\nendobj\r\n"
                )

                objectOffsets[contentObj] = out.count
                writeAscii(out, "$contentObj 0 obj\r\n<<\r\n  /Length ${contentBytes.size}\r\n>>\r\nstream\r\n")
                out.write(contentBytes)
                writeAscii(out, "\r\nendstream\r\nendobj\r\n")

                objectOffsets[imageObj] = out.count
                writeAscii(
                    out,
                    "$imageObj 0 obj\r\n<<\r\n" +
                    "  /Type /XObject\r\n" +
                    "  /Subtype /Image\r\n" +
                    "  /Width $renderW\r\n" +
                    "  /Height $renderH\r\n" +
                    "  /ColorSpace /DeviceRGB\r\n" +
                    "  /BitsPerComponent 8\r\n" +
                    "  /Filter /DCTDecode\r\n" +
                    "  /Length ${jpegBytes.size}\r\n" +
                    ">>\r\nstream\r\n"
                )
                out.write(jpegBytes)
                writeAscii(out, "\r\nendstream\r\nendobj\r\n")
            }

            val startXref = out.count
            writeAscii(out, "xref\r\n0 ${totalObjects + 1}\r\n")
            writeAscii(out, "0000000000 65535 f\r\n")
            for (id in 1..totalObjects) {
                writeAscii(out, String.format(Locale.US, "%010d %05d n\r\n", objectOffsets[id], 0))
            }

            writeAscii(
                out,
                "trailer\r\n<<\r\n" +
                "  /Size ${totalObjects + 1}\r\n" +
                "  /Root 1 0 R\r\n" +
                ">>\r\nstartxref\r\n$startXref\r\n%%EOF\r\n"
            )
            out.flush()
        }

        renderer.close()
        pfd.close()
        outputFile
    }

    /**
     * Combines multiple existing PDF files into a single unified, compact PDF.
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

        // Count total pages across files
        var totalPages = 0
        for (file in pdfFiles) {
            if (!file.exists()) continue
            try {
                val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                val r = PdfRenderer(pfd)
                totalPages += r.pageCount
                r.close()
                pfd.close()
            } catch (_: Exception) {}
        }
        require(totalPages > 0) { "No pages found to combine" }

        val totalObjects = 2 + totalPages * 3
        val objectOffsets = LongArray(totalObjects + 1)

        FileOutputStream(outputFile).buffered().use { rawOut ->
            val out = CountingOutputStream(rawOut)

            writeAscii(out, "%PDF-1.4\r\n%\u00E2\u00E3\u00CF\u00D3\r\n")
            objectOffsets[1] = out.count
            writeAscii(out, "1 0 obj\r\n<<\r\n  /Type /Catalog\r\n  /Pages 2 0 R\r\n>>\r\nendobj\r\n")

            objectOffsets[2] = out.count
            val kids = (0 until totalPages).joinToString(" ") { "${3 + it * 3} 0 R" }
            writeAscii(out, "2 0 obj\r\n<<\r\n  /Type /Pages\r\n  /Kids [$kids]\r\n  /Count $totalPages\r\n>>\r\nendobj\r\n")

            var pageIdx = 0
            for (file in pdfFiles) {
                if (!file.exists()) continue
                val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                val renderer = PdfRenderer(pfd)
                for (p in 0 until renderer.pageCount) {
                    val page = renderer.openPage(p)
                    val pageW = page.width.toFloat()
                    val pageH = page.height.toFloat()

                    val maxDim = 1600
                    val scale = min(1.0f, maxDim.toFloat() / max(page.width, page.height).toFloat())
                    val renderW = (page.width * scale).roundToInt().coerceAtLeast(100)
                    val renderH = (page.height * scale).roundToInt().coerceAtLeast(100)

                    val bmp = Bitmap.createBitmap(renderW, renderH, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(bmp)
                    canvas.drawColor(Color.WHITE)
                    page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    page.close()

                    val baos = ByteArrayOutputStream()
                    bmp.compress(Bitmap.CompressFormat.JPEG, 80, baos)
                    bmp.recycle()
                    val jpegBytes = baos.toByteArray()

                    val pageObj = 3 + pageIdx * 3
                    val contentObj = pageObj + 1
                    val imageObj = pageObj + 2

                    val contentStreamStr = String.format(
                        Locale.US,
                        "q\r\n%.2f 0 0 %.2f 0 0 cm\r\n/Im1 Do\r\nQ\r\n",
                        pageW, pageH
                    )
                    val contentBytes = contentStreamStr.toByteArray(Charsets.US_ASCII)

                    objectOffsets[pageObj] = out.count
                    writeAscii(
                        out,
                        "$pageObj 0 obj\r\n<<\r\n" +
                        "  /Type /Page\r\n" +
                        "  /Parent 2 0 R\r\n" +
                        String.format(Locale.US, "  /MediaBox [0 0 %.2f %.2f]\r\n", pageW, pageH) +
                        "  /Contents $contentObj 0 R\r\n" +
                        "  /Resources <<\r\n" +
                        "    /ProcSet [/PDF /ImageC]\r\n" +
                        "    /XObject << /Im1 $imageObj 0 R >>\r\n" +
                        "  >>\r\n" +
                        ">>\r\nendobj\r\n"
                    )

                    objectOffsets[contentObj] = out.count
                    writeAscii(out, "$contentObj 0 obj\r\n<<\r\n  /Length ${contentBytes.size}\r\n>>\r\nstream\r\n")
                    out.write(contentBytes)
                    writeAscii(out, "\r\nendstream\r\nendobj\r\n")

                    objectOffsets[imageObj] = out.count
                    writeAscii(
                        out,
                        "$imageObj 0 obj\r\n<<\r\n" +
                        "  /Type /XObject\r\n" +
                        "  /Subtype /Image\r\n" +
                        "  /Width $renderW\r\n" +
                        "  /Height $renderH\r\n" +
                        "  /ColorSpace /DeviceRGB\r\n" +
                        "  /BitsPerComponent 8\r\n" +
                        "  /Filter /DCTDecode\r\n" +
                        "  /Length ${jpegBytes.size}\r\n" +
                        ">>\r\nstream\r\n"
                    )
                    out.write(jpegBytes)
                    writeAscii(out, "\r\nendstream\r\nendobj\r\n")

                    pageIdx++
                    onProgress(pageIdx, totalPages)
                }
                renderer.close()
                pfd.close()
            }

            val startXref = out.count
            writeAscii(out, "xref\r\n0 ${totalObjects + 1}\r\n")
            writeAscii(out, "0000000000 65535 f\r\n")
            for (id in 1..totalObjects) {
                writeAscii(out, String.format(Locale.US, "%010d %05d n\r\n", objectOffsets[id], 0))
            }

            writeAscii(
                out,
                "trailer\r\n<<\r\n" +
                "  /Size ${totalObjects + 1}\r\n" +
                "  /Root 1 0 R\r\n" +
                ">>\r\nstartxref\r\n$startXref\r\n%%EOF\r\n"
            )
            out.flush()
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

    private fun writeAscii(out: OutputStream, str: String) {
        out.write(str.toByteArray(Charsets.US_ASCII))
    }
}
