package com.example.engine.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.example.engine.arabic.ArabicTextProcessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.util.zip.Inflater
import java.util.zip.InflaterInputStream

data class ExtractedPage(
    val pageNumber: Int,
    val text: String,
    val normalizedText: String
)

data class ExtractedPdfDocument(
    val title: String,
    val pageCount: Int,
    val pages: List<ExtractedPage>
)

object PdfExtractor {

    /**
     * Copies a PDF from SAF Uri to private app storage and extracts text per page
     */
    suspend fun importAndExtractPdf(
        context: Context,
        uri: Uri,
        destFileName: String
    ): Result<Pair<File, ExtractedPdfDocument>> = withContext(Dispatchers.IO) {
        try {
            val docsDir = File(context.filesDir, "documents").apply { if (!exists()) mkdirs() }
            val destFile = File(docsDir, destFileName)

            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            } ?: return@withContext Result.failure(Exception("تعذر قراءة ملف PDF من المصدر"))

            val extractedDoc = extractTextFromPdfFile(context, destFile)
            Result.success(Pair(destFile, extractedDoc))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Extracts text from an offline PDF file
     */
    suspend fun extractTextFromPdfFile(
        context: Context,
        file: File
    ): ExtractedPdfDocument = withContext(Dispatchers.IO) {
        val pages = mutableListOf<ExtractedPage>()
        var pageCount = 0

        var pfd: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null

        try {
            pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            renderer = PdfRenderer(pfd)
            pageCount = renderer.pageCount
        } catch (e: Exception) {
            // If PdfRenderer fails, we continue with direct binary parser
        }

        // Direct PDF Stream Text Parser
        val streamPages = parsePdfStreamText(file)

        if (pageCount == 0 && streamPages.isEmpty()) {
            throw IllegalArgumentException("تعذر فتح ملف PDF أو استخراج أي صفحة منه")
        }

        // Never manufacture page text. A scanned PDF must be handled by a future
        // page-OCR flow, not indexed as if it contained curriculum text.
        if (streamPages.isEmpty()) {
            throw IllegalArgumentException(
                "الملف PDF عبارة عن صور ممسوحة ضوئياً ولا يحتوي نصاً قابلاً للاستخراج. استخدم نسخة نصية من الكتاب."
            )
        }

        val totalPages = maxOf(pageCount, streamPages.size)

        for (p in 1..totalPages) {
            val streamText = streamPages.getOrNull(p - 1)?.trim() ?: ""
            val finalText = streamText
            if (finalText.isBlank()) continue

            pages.add(
                ExtractedPage(
                    pageNumber = p,
                    text = finalText,
                    normalizedText = ArabicTextProcessor.normalize(finalText)
                )
            )
        }

        renderer?.close()
        pfd?.close()

        ExtractedPdfDocument(
            title = file.nameWithoutExtension,
            pageCount = totalPages,
            pages = pages
        )
    }

    /**
     * Renders a specific page of a PDF to a high-resolution Bitmap
     */
    suspend fun renderPageToBitmap(
        file: File,
        pageNumber: Int,
        scaleFactor: Float = 2.0f
    ): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = PdfRenderer(pfd)
            val index = (pageNumber - 1).coerceIn(0, renderer.pageCount - 1)
            val page = renderer.openPage(index)

            val width = (page.width * scaleFactor).toInt()
            val height = (page.height * scaleFactor).toInt()
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

            page.close()
            renderer.close()
            pfd.close()

            bitmap
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Native pure-Kotlin PDF content stream reader and Flate stream decompressor
     */
    private fun parsePdfStreamText(file: File): List<String> {
        val pageTexts = mutableListOf<String>()
        try {
            val bytes = file.readBytes()
            val contentStreams = extractStreams(bytes)

            for (stream in contentStreams) {
                val text = extractTextFromStream(stream)
                if (text.isNotBlank()) {
                    pageTexts.add(text)
                }
            }
        } catch (e: Exception) {
            // fallback
        }
        return pageTexts
    }

    private fun extractStreams(bytes: ByteArray): List<ByteArray> {
        val streams = mutableListOf<ByteArray>()
        val streamKeyword = "stream".toByteArray(Charsets.ISO_8859_1)
        val endstreamKeyword = "endstream".toByteArray(Charsets.ISO_8859_1)

        var index = 0
        while (index < bytes.size) {
            val streamStart = indexOf(bytes, streamKeyword, index)
            if (streamStart == -1) break

            var dataStart = streamStart + streamKeyword.size
            // skip CRLF or LF after 'stream'
            if (dataStart < bytes.size && bytes[dataStart] == '\r'.code.toByte()) dataStart++
            if (dataStart < bytes.size && bytes[dataStart] == '\n'.code.toByte()) dataStart++

            val endstreamPos = indexOf(bytes, endstreamKeyword, dataStart)
            if (endstreamPos == -1) break

            val streamBytes = bytes.copyOfRange(dataStart, endstreamPos)

            // Try decompressing with Inflater
            val decompressed = tryDecompress(streamBytes)
            if (decompressed != null && decompressed.isNotEmpty()) {
                streams.add(decompressed)
            } else if (streamBytes.isNotEmpty()) {
                streams.add(streamBytes)
            }

            index = endstreamPos + endstreamKeyword.size
        }
        return streams
    }

    private fun tryDecompress(data: ByteArray): ByteArray? {
        return try {
            val inflater = Inflater(false)
            inflater.setInput(data)
            val outputStream = ByteArrayOutputStream(data.size * 2)
            val buffer = ByteArray(4096)
            while (!inflater.finished()) {
                val count = inflater.inflate(buffer)
                if (count == 0 && inflater.needsInput()) break
                outputStream.write(buffer, 0, count)
            }
            inflater.end()
            outputStream.toByteArray()
        } catch (e: Exception) {
            null
        }
    }

    private fun extractTextFromStream(stream: ByteArray): String {
        val content = String(stream, Charsets.ISO_8859_1)
        val sb = StringBuilder()

        // Match BT ... ET blocks or string literals (text) Tj and [(t)(e)(x)(t)] TJ
        val btEtRegex = Regex("BT[\\s\\S]*?ET")
        val tjRegex = Regex("\\((.*?)\\)\\s*(?:Tj|'|\")")
        val tjArrayRegex = Regex("\\[(.*?)\\]\\s*TJ")

        val blocks = btEtRegex.findAll(content).map { it.value }.toList()
        val textBlocks = if (blocks.isNotEmpty()) blocks else listOf(content)

        for (block in textBlocks) {
            // Find (text) Tj
            tjRegex.findAll(block).forEach { match ->
                val raw = match.groupValues[1]
                sb.append(decodePdfString(raw)).append(" ")
            }

            // Find [(text)] TJ
            tjArrayRegex.findAll(block).forEach { match ->
                val arrayContent = match.groupValues[1]
                val itemRegex = Regex("\\((.*?)\\)")
                itemRegex.findAll(arrayContent).forEach { itemMatch ->
                    val raw = itemMatch.groupValues[1]
                    sb.append(decodePdfString(raw))
                }
                sb.append(" ")
            }
        }

        return sb.toString().replace(Regex("\\s+"), " ").trim()
    }

    private fun decodePdfString(raw: String): String {
        return raw.replace("\\(", "(")
            .replace("\\)", ")")
            .replace("\\n", "\n")
            .replace("\\r", "\r")
            .replace("\\t", "\t")
            .replace("\\\\", "\\")
    }

    private fun indexOf(source: ByteArray, target: ByteArray, fromIndex: Int): Int {
        if (fromIndex >= source.size || target.isEmpty()) return -1
        val max = source.size - target.size
        for (i in fromIndex..max) {
            var found = true
            for (j in target.indices) {
                if (source[i + j] != target[j]) {
                    found = false
                    break
                }
            }
            if (found) return i
        }
        return -1
    }
}
