package com.majkeylab.seliadocs.pdf

import org.junit.Assert.assertEquals
import org.junit.Test

class PdfProtocolTest {
    @Test
    fun captureLimitDoesNotLowerDefaultPdfExportResolution() {
        assertEquals(PdfRenderSize(2_048, 1_024), fitPdfRenderSize(14_400, 7_200, maxDimension = 2_048))
        assertEquals(PdfRenderSize(4_096, 2_048), fitPdfRenderSize(14_400, 7_200))
    }

    @Test(expected = IllegalArgumentException::class)
    fun zeroRenderLimitIsRejected() {
        fitPdfRenderSize(595, 842, maxDimension = 0)
    }

    @Test
    fun renderSizeFitsSandboxLimitsWithoutChangingAspectRatio() {
        assertEquals(PdfRenderSize(595, 842), fitPdfRenderSize(595, 842))
        assertEquals(PdfRenderSize(4_096, 2_048), fitPdfRenderSize(14_400, 7_200))
    }
}
