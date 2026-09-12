package com.majkeylab.seliadocs.pdf

import android.content.Context
import android.os.Build
import com.majkeylab.seliadocs.recognition.recognizeBitmap
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

internal class PdfTextSelector(context: Context) {
    private val sandbox = PdfSandboxClient(context)

    suspend fun select(
        file: File,
        pageIndex: Int,
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        allowOcr: Boolean = true,
    ): PdfTextSelection? {
        require(pageIndex >= 0)
        validatePdfSelectionCoordinates(startX, startY, endX, endY)
        if (Build.VERSION.SDK_INT >= 35) {
            sandbox.selectText(file, pageIndex, startX, startY, endX, endY)
                ?.takeIf { it.intersectsRegion(startX, startY, endX, endY) }
                ?.let { return it }
        } else if (!allowOcr) {
            throw UnsupportedOperationException(PdfProtocol.ERROR_SELECTION_UNSUPPORTED)
        }
        if (!allowOcr) return null
        val pages = sandbox.inspect(file).pages
        require(pageIndex in pages.indices) { PdfProtocol.ERROR_INVALID }
        val size = pages[pageIndex]
        require(size.width > 0 && size.height > 0) { PdfProtocol.ERROR_INVALID }
        val scale = 2_048f / maxOf(size.width, size.height)
        // Take ownership even if cancellation arrives while the non-interruptible Binder render finishes.
        val bitmap = withContext(NonCancellable) {
            sandbox.renderPage(
                file, pageIndex,
                (size.width * scale).roundToInt().coerceAtLeast(1),
                (size.height * scale).roundToInt().coerceAtLeast(1),
            )
        }
        return try {
            currentCoroutineContext().ensureActive()
            val words = recognizeBitmap(bitmap, wordLevel = true)
            withContext(Dispatchers.Default) { selectOcrText(words, startX, startY, endX, endY) }
        } finally {
            bitmap.recycle()
        }
    }
}
