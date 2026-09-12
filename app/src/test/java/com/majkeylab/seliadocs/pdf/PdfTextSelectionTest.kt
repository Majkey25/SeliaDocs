package com.majkeylab.seliadocs.pdf

import com.majkeylab.seliadocs.recognition.ImageOcrRegion
import com.majkeylab.seliadocs.recognition.ImageOcrResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class PdfTextSelectionTest {
    private val words = listOf(
        ImageOcrRegion("First", 0.1f, 0.1f, 0.3f, 0.2f),
        ImageOcrRegion("line", 0.4f, 0.1f, 0.6f, 0.2f),
        ImageOcrRegion("Second", 0.1f, 0.3f, 0.4f, 0.4f),
    )
    private val ocr = ImageOcrResult("First line\nSecond", words)

    @Test
    fun boundsRejectNonfiniteInvertedOrOutOfPageValues() {
        listOf(Float.NaN, Float.POSITIVE_INFINITY, -0.1f, 1.1f).forEach { invalid ->
            assertThrows(IllegalArgumentException::class.java) { PdfTextBounds(invalid, 0f, 1f, 1f) }
        }
        assertThrows(IllegalArgumentException::class.java) { PdfTextBounds(0.8f, 0f, 0.2f, 1f) }
        assertThrows(IllegalArgumentException::class.java) { PdfTextBounds(0f, 0.5f, 1f, 0.5f) }
    }

    @Test
    fun ocrSelectionUsesReadingOrderAcrossLinesAndReverseDrag() {
        val forward = selectOcrText(ocr, 0.5f, 0.15f, 0.2f, 0.35f)
        assertEquals("line Second", forward?.text)
        assertEquals(true, forward?.isOcr)
        assertEquals(2, forward?.bounds?.size)
        assertEquals(forward, selectOcrText(ocr, 0.2f, 0.35f, 0.5f, 0.15f))
    }

    @Test
    fun ocrPointAndRectangleSelectOnlyIntersectedWords() {
        assertEquals("First", selectOcrText(ocr, 0.2f, 0.15f, 0.2f, 0.15f)?.text)
        assertEquals("First line", selectOcrText(ocr, 0.05f, 0.15f, 0.7f, 0.15f)?.text)
        assertNull(selectOcrText(ocr, 0.8f, 0.8f, 0.9f, 0.9f))
        assertThrows(IllegalArgumentException::class.java) { selectOcrText(ocr, Float.NaN, 0f, 1f, 1f) }
    }

    @Test
    fun selectionRejectsOversizedOrEmptyPayloads() {
        val bounds = PdfTextBounds(0f, 0f, 1f, 1f)
        assertThrows(IllegalArgumentException::class.java) { PdfTextSelection("x".repeat(10_001), listOf(bounds), false) }
        assertThrows(IllegalArgumentException::class.java) { PdfTextSelection("x", List(2_001) { bounds }, false) }
        assertThrows(IllegalArgumentException::class.java) { PdfTextSelection(" ", listOf(bounds), false) }
        assertThrows(IllegalArgumentException::class.java) { PdfTextSelection("x", emptyList(), false) }
    }
}
