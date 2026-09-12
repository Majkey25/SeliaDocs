package com.majkeylab.seliadocs.editor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.pdf.PdfDocument
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.majkeylab.seliadocs.backup.validTestStrokePayload
import com.majkeylab.seliadocs.data.AnnotationRect
import com.majkeylab.seliadocs.data.AssetStore
import com.majkeylab.seliadocs.data.ElementEntity
import com.majkeylab.seliadocs.data.PageEntity
import com.majkeylab.seliadocs.data.PdfSourceEntity
import com.majkeylab.seliadocs.data.StrokeEntity
import java.io.File
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PageExcerptCaptureTest {
    private lateinit var context: Context
    private lateinit var root: File
    private lateinit var assets: AssetStore
    private val page = PageEntity("page", "book", 0, "BLANK", 400, 400)

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        root = File(context.cacheDir, "page-capture-${System.nanoTime()}")
        assets = AssetStore(root)
        assets.prepare()
    }

    @After
    fun tearDown() { root.deleteRecursively() }

    @Test
    fun croppedPngContainsMarkupTextAndInkAtCorrectCoordinates() = runTest {
        val mark = element("HIGHLIGHT").copy(x = 100f, y = 100f, width = 80f, height = 40f,
            colorArgb = 0x66FF0000, annotationRects = "0,0,1,1")
        val text = element("TEXT").copy(id = "text", x = 100f, y = 150f, text = "Study text")
        val ink = validTestStrokePayload()
        val stroke = StrokeEntity("stroke", page.id, 0, ink.brushKind, ink.colorArgb, ink.size, ink.epsilon, ink.inputs)
        val result = capturePageExcerpt(context, assets, page, null, listOf(stroke), listOf(mark, text), emptyList(),
            AnnotationRect(0.1f, 0.1f, 0.8f, 0.7f))
        assertEquals(280, result.width)
        assertEquals(240, result.height)
        assertEquals("image/png", result.mimeType)
        val bitmap = requireNotNull(BitmapFactory.decodeFile(result.file.path))
        try {
            val highlighted = bitmap.getPixel(70, 70)
            assertTrue(Color.red(highlighted) > Color.blue(highlighted) + 40)
            assertTrue((60 until 220).any { x -> (110 until 145).any { y -> Color.red(bitmap.getPixel(x, y)) < 80 } })
            assertTrue((5 until 35).any { x -> Color.red(bitmap.getPixel(x, x)) < 100 })
        } finally { bitmap.recycle() }
        assertEquals(listOf(result.file), assets.files())
    }

    @Test
    fun sandboxPdfBackgroundIsIncludedInCrop() = runTest {
        val file = assets.file("source.pdf")
        val document = PdfDocument()
        try {
            val pdfPage = document.startPage(PdfDocument.PageInfo.Builder(400, 400, 1).create())
            pdfPage.canvas.drawColor(Color.BLUE)
            document.finishPage(pdfPage)
            file.outputStream().use(document::writeTo)
        } finally { document.close() }
        val source = PdfSourceEntity("pdf", "book", "source.pdf", "Source", 1, file.length(), "0".repeat(64), 1)
        val result = capturePageExcerpt(context, assets, page.copy(pdfSourceId = "pdf", pdfPageIndex = 0), source,
            emptyList(), emptyList(), emptyList(), AnnotationRect(0.25f, 0.25f, 0.75f, 0.75f))
        val bitmap = requireNotNull(BitmapFactory.decodeFile(result.file.path))
        try { assertEquals(Color.BLUE, bitmap.getPixel(100, 100)) } finally { bitmap.recycle() }
        assertTrue(assets.files().none { it.name.endsWith(".tmp") })
    }

    @Test
    fun largePageCropsStayBoundedAndPositiveAtPageEdges() = runTest {
        val result = capturePageExcerpt(context, assets, page.copy(widthPoints = 14_400, heightPoints = 14_400), null,
            emptyList(), emptyList(), emptyList(), AnnotationRect(0.9999f, 0.9999f, 1f, 1f))
        assertEquals(1, result.width)
        assertEquals(1, result.height)
    }

    @Test
    fun fullLargePageCaptureUsesTwoKLimit() = runTest {
        val result = capturePageExcerpt(context, assets, page.copy(widthPoints = 10_000, heightPoints = 5_000), null,
            emptyList(), emptyList(), emptyList(), AnnotationRect(0f, 0f, 1f, 1f))
        assertEquals(2_048, result.width)
        assertEquals(1_024, result.height)
    }

    @Test
    fun tinyInteriorCropPreservesInsertedImageDetail() = runTest {
        val pixels = IntArray(512 * 512) { index ->
            if (((index % 512) / 8 + (index / 512) / 8) % 2 == 0) Color.RED else Color.BLUE
        }
        val original = Bitmap.createBitmap(pixels, 512, 512, Bitmap.Config.ARGB_8888)
        try {
            assets.file("checker.png").outputStream().use { output ->
                check(original.compress(Bitmap.CompressFormat.PNG, 100, output))
            }
        } finally { original.recycle() }
        val imagePage = page.copy(widthPoints = 512, heightPoints = 512)
        val image = element("IMAGE").copy(x = 0f, y = 0f, width = 512f, height = 512f,
            text = null, assetId = "checker.png")
        val result = capturePageExcerpt(context, assets, imagePage, null, emptyList(), listOf(image), emptyList(),
            AnnotationRect(0.375f, 0.375f, 0.40625f, 0.40625f))
        assertEquals(16, result.width)
        assertEquals(16, result.height)
        val crop = requireNotNull(BitmapFactory.decodeFile(result.file.path))
        try {
            // Interior pixels are away from crop and checker boundaries; no resampling is needed at 1:1 scale.
            assertEquals(Color.RED, crop.getPixel(2, 2))
            assertEquals(Color.BLUE, crop.getPixel(10, 2))
            assertEquals(Color.BLUE, crop.getPixel(2, 10))
            assertEquals(Color.RED, crop.getPixel(10, 10))
        } finally { crop.recycle() }
    }

    @Test
    fun renderFailureLeavesNoPartialAsset() = runTest {
        val missingImage = element("IMAGE").copy(text = null, assetId = "missing.png")
        assertTrue(runCatching {
            capturePageExcerpt(context, assets, page, null, emptyList(), listOf(missingImage), emptyList(),
                AnnotationRect(0f, 0f, 1f, 1f))
        }.isFailure)
        assertTrue(assets.files().isEmpty())
    }

    @Test
    fun cancellationBeforeOwnershipDeliveryRemovesPublishedAsset() = runBlocking {
        val dispatcher = StandardTestDispatcher()
        val task = async(dispatcher) {
            capturePageExcerpt(context, assets, page, null, emptyList(), emptyList(), emptyList(), AnnotationRect(0f, 0f, 1f, 1f))
        }
        dispatcher.scheduler.runCurrent()
        try {
            withTimeout(10_000) {
                while (assets.files().none { it.extension == "png" }) delay(10)
            }
        } finally {
            task.cancel()
            withTimeout(10_000) {
                while (!task.isCompleted) {
                    dispatcher.scheduler.runCurrent()
                    delay(10)
                }
            }
        }
        assertTrue(task.isCancelled)
        assertTrue(assets.files().isEmpty())
    }

    @Test
    fun mismatchedPageContentAndMissingPdfBackgroundAreRejected() = runTest {
        assertTrue(runCatching {
            capturePageExcerpt(context, assets, page, null, emptyList(), listOf(element("TEXT").copy(pageId = "other")),
                emptyList(), AnnotationRect(0f, 0f, 1f, 1f))
        }.isFailure)
        assertTrue(runCatching {
            capturePageExcerpt(context, assets, page.copy(pdfSourceId = "pdf", pdfPageIndex = 0), null,
                emptyList(), emptyList(), emptyList(), AnnotationRect(0f, 0f, 1f, 1f))
        }.isFailure)
        assertTrue(assets.files().isEmpty())
    }

    private fun element(kind: String) = ElementEntity("element", page.id, 0, kind, 100f, 100f, 180f, 80f,
        0f, "Selected text", null, null, null, null)
}
