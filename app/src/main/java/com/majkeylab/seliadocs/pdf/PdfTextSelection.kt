package com.majkeylab.seliadocs.pdf

import com.majkeylab.seliadocs.recognition.ImageOcrResult

/** A text rectangle in normalized page coordinates, independent of zoom and raster resolution. */
internal data class PdfTextBounds(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    init {
        require(listOf(left, top, right, bottom).all { it.isFinite() && it in 0f..1f })
        require(left < right && top < bottom)
    }
}

internal data class PdfTextSelection(val text: String, val bounds: List<PdfTextBounds>, val isOcr: Boolean) {
    init {
        require(text.isNotBlank() && bounds.isNotEmpty())
        require(text.length <= PdfProtocol.MAX_SELECTION_TEXT && bounds.size <= PdfProtocol.MAX_SELECTION_BOUNDS) {
            PdfProtocol.ERROR_LIMIT
        }
    }
}

internal fun validatePdfSelectionCoordinates(startX: Float, startY: Float, endX: Float, endY: Float) {
    require(listOf(startX, startY, endX, endY).all { it.isFinite() && it in 0f..1f }) {
        "PDF selection coordinates must be finite and normalized"
    }
}

internal fun selectOcrText(
    result: ImageOcrResult,
    startX: Float,
    startY: Float,
    endX: Float,
    endY: Float,
): PdfTextSelection? {
    validatePdfSelectionCoordinates(startX, startY, endX, endY)
    val words = result.regions
    val start = words.indexOfFirst { startX in it.left..it.right && startY in it.top..it.bottom }
    val end = words.indexOfFirst { endX in it.left..it.right && endY in it.top..it.bottom }
    val selected = if (start >= 0 && end >= 0) {
        words.subList(minOf(start, end), maxOf(start, end) + 1)
    } else {
        words.filter {
            it.right >= minOf(startX, endX) && it.left <= maxOf(startX, endX) &&
                it.bottom >= minOf(startY, endY) && it.top <= maxOf(startY, endY)
        }
    }
    if (selected.isEmpty()) return null
    return PdfTextSelection(
        text = selected.joinToString(" ") { it.text },
        bounds = selected.map { PdfTextBounds(it.left, it.top, it.right, it.bottom) },
        isOcr = true,
    )
}
