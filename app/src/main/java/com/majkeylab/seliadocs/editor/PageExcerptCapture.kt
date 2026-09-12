package com.majkeylab.seliadocs.editor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import com.majkeylab.seliadocs.data.AnnotationRect
import com.majkeylab.seliadocs.data.AssetStore
import com.majkeylab.seliadocs.data.BlockEntity
import com.majkeylab.seliadocs.data.ElementEntity
import com.majkeylab.seliadocs.data.PageEntity
import com.majkeylab.seliadocs.data.PdfSourceEntity
import com.majkeylab.seliadocs.data.StrokeEntity
import com.majkeylab.seliadocs.pdf.PdfSandboxClient
import com.majkeylab.seliadocs.pdf.fitPdfRenderSize
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID
import kotlin.math.ceil
import kotlin.math.floor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

private const val CAPTURE_MAX_DIMENSION = 2_048
private const val CAPTURE_MAX_IMAGE_PIXELS = 4L * 1_024 * 1_024

/** Caller holds LibraryMutationGate and owns the returned asset until it is linked to an element. */
internal suspend fun capturePageExcerpt(
    context: Context,
    assets: AssetStore,
    page: PageEntity,
    pdfSource: PdfSourceEntity?,
    strokes: List<StrokeEntity>,
    elements: List<ElementEntity>,
    blocks: List<BlockEntity>,
    rect: AnnotationRect,
): ImportedAsset {
    var temporary: File? = null
    var stored: File? = null
    try {
        return withContext(Dispatchers.IO) {
            require(strokes.all { it.pageId == page.id } && elements.all { it.pageId == page.id } && blocks.all { it.pageId == page.id })
            require((page.pdfSourceId == null) == (pdfSource == null))
            if (pdfSource != null) require(pdfSource.id == page.pdfSourceId && pdfSource.notebookId == page.notebookId && page.pdfPageIndex != null)
            val size = fitPdfRenderSize(page.widthPoints, page.heightPoints, maxDimension = CAPTURE_MAX_DIMENSION)
            val left = floor(rect.left * size.width).toInt().coerceIn(0, size.width - 1)
            val top = floor(rect.top * size.height).toInt().coerceIn(0, size.height - 1)
            val right = ceil(rect.right * size.width).toInt().coerceIn(left + 1, size.width)
            val bottom = ceil(rect.bottom * size.height).toInt().coerceIn(top + 1, size.height)
            var background: Bitmap? = null
            var crop: Bitmap? = null
            try {
                currentCoroutineContext().ensureActive()
                if (pdfSource != null) {
                    // Assign ownership before returning across a cancellable dispatcher boundary.
                    withContext(NonCancellable) {
                        background = PdfSandboxClient(context).renderPage(assets.requireFile(pdfSource.assetId),
                            requireNotNull(page.pdfPageIndex), size.width, size.height)
                    }
                }
                currentCoroutineContext().ensureActive()
                val bitmap = Bitmap.createBitmap(right - left, bottom - top, Bitmap.Config.ARGB_8888)
                crop = bitmap
                val canvas = Canvas(bitmap)
                canvas.translate(-left.toFloat(), -top.toFloat())
                canvas.scale(size.width.toFloat() / page.widthPoints, size.height.toFloat() / page.heightPoints)
                // Calculated bitmap pixel peak: 16 MiB each for background, crop, image decode and orientation copy.
                // This 64 MiB budget excludes renderer buffers and the rest of the app; it is not a measured peak.
                PdfExporter(assets, maxImageDecodePixels = CAPTURE_MAX_IMAGE_PIXELS)
                    .renderPage(canvas, page, strokes, elements, blocks, background)
                currentCoroutineContext().ensureActive()
                temporary = File.createTempFile(".page-excerpt-", ".tmp", assets.prepare())
                requireNotNull(temporary).outputStream().use { output ->
                    check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) { "Excerpt image could not be encoded" }
                    output.fd.sync()
                }
                currentCoroutineContext().ensureActive()
                val id = "${UUID.randomUUID()}.png"
                val destination = assets.file(id)
                check(!destination.exists()) { "Excerpt asset already exists" }
                Files.move(requireNotNull(temporary).toPath(), destination.toPath(), StandardCopyOption.ATOMIC_MOVE)
                stored = destination
                temporary = null
                ImportedAsset(id, "image/png", bitmap.width, bitmap.height, destination)
            } finally {
                crop?.recycle()
                background?.recycle()
            }
        }
    } catch (failure: Throwable) {
        withContext(NonCancellable + Dispatchers.IO) {
            listOfNotNull(temporary, stored).forEach { file ->
                runCatching { Files.deleteIfExists(file.toPath()) }.exceptionOrNull()?.let(failure::addSuppressed)
            }
        }
        throw failure
    }
}
