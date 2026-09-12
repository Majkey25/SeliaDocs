package com.majkeylab.seliadocs.editor

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.majkeylab.seliadocs.data.AnnotationRect
import com.majkeylab.seliadocs.data.AssetStore
import com.majkeylab.seliadocs.data.CoverColor
import com.majkeylab.seliadocs.data.CoverPattern
import com.majkeylab.seliadocs.data.ElementEntity
import com.majkeylab.seliadocs.data.ElementKind
import com.majkeylab.seliadocs.data.NotebookContent
import com.majkeylab.seliadocs.data.NotebookEntity
import com.majkeylab.seliadocs.data.PageEntity
import com.majkeylab.seliadocs.data.PageMode
import com.majkeylab.seliadocs.data.PageOrientation
import com.majkeylab.seliadocs.data.PaperTemplate
import com.majkeylab.seliadocs.data.PdfSourceEntity
import com.majkeylab.seliadocs.data.encodeAnnotationRects
import com.majkeylab.seliadocs.pdf.PdfSandboxClient
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PdfMarkupExportPixelTest {
    @Test
    fun exportedHighlightUnderlineAndStrikeoutAppearAtTheirStoredRectangles() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val assetRoot = File(context.cacheDir, "markup-export-${System.nanoTime()}")
        val assets = AssetStore(assetRoot)
        assets.prepare()
        val source = assets.file("source.pdf")
        val output = File(context.cacheDir, "markup-result-${System.nanoTime()}.pdf")
        try {
            val document = PdfDocument()
            try {
                val page = document.startPage(PdfDocument.PageInfo.Builder(600, 800, 1).create())
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 36f }
                listOf(140f, 260f, 380f).forEach { baseline ->
                    page.canvas.drawText("Alpha Beta Gamma", 60f, baseline, paint)
                }
                document.finishPage(page)
                source.outputStream().use(document::writeTo)
            } finally { document.close() }
            val page = PageEntity("page", "notebook", 0, PaperTemplate.BLANK.name, 600, 800,
                pageMode = PageMode.PDF.name, pdfSourceId = "source", pdfPageIndex = 0)
            fun markup(kind: ElementKind, top: Float, color: Int, rectangles: List<AnnotationRect>) = ElementEntity(
                id = kind.name, pageId = page.id, zIndex = kind.ordinal, kind = kind.name,
                x = 40f, y = top, width = 360f, height = 50f, rotation = 0f,
                text = "Alpha Beta Gamma", assetId = null, shapeKind = null, expression = null, resultText = null,
                colorArgb = color, annotationRects = encodeAnnotationRects(rectangles), sourcePageId = page.id,
                sourceRect = encodeAnnotationRects(listOf(AnnotationRect(40f / 600f, top / 800f, 400f / 600f, (top + 50f) / 800f))),
            )
            val full = listOf(AnnotationRect(0f, 0f, 1f, 1f))
            val content = NotebookContent(
                notebook = NotebookEntity("notebook", "Markup export", CoverColor.PERIWINKLE.name,
                    CoverPattern.SOLID.name, PaperTemplate.BLANK.name, PageOrientation.PORTRAIT.name,
                    false, false, 1L, 1L, null),
                pages = listOf(page), strokes = emptyList(), blocks = emptyList(),
                elements = listOf(
                    markup(ElementKind.HIGHLIGHT, 100f, 0x66FFD54F,
                        listOf(AnnotationRect(0f, 0f, 0.4f, 1f), AnnotationRect(0.6f, 0f, 1f, 1f))),
                    markup(ElementKind.UNDERLINE, 220f, 0xFF3156D9.toInt(), full),
                    markup(ElementKind.STRIKEOUT, 340f, 0xFFE53935.toInt(), full),
                ),
                pdfSources = listOf(PdfSourceEntity("source", "notebook", "source.pdf", "Source.pdf",
                    1, source.length(), MessageDigest.getInstance("SHA-256").digest(source.readBytes()).joinToString("") { "%02x".format(it) }, 1L)),
            )
            val sandbox = PdfSandboxClient(context)
            output.outputStream().use { stream ->
                PdfExporter(assets).write(content, stream) { pdf, renderedPage, width, height ->
                    sandbox.renderPage(assets.requireFile(pdf.assetId), requireNotNull(renderedPage.pdfPageIndex), width, height)
                }
            }
            val bitmap = sandbox.renderPage(output, 0, 600, 800)
            try {
                val highlight = bitmap.getPixel(50, 110)
                assertTrue("Exported highlight missing", Color.red(highlight) > 220 && Color.green(highlight) > 180 && Color.blue(highlight) < 220)
                val gap = bitmap.getPixel(220, 101)
                assertTrue("Separate highlight rectangles must keep their gap", Color.red(gap) > 245 && Color.green(gap) > 245 && Color.blue(gap) > 245)
                val underline = bitmap.getPixel(50, 270)
                assertTrue("Exported underline missing or vertically misplaced", Color.blue(underline) > 150 && Color.red(underline) < 100)
                val strikeout = bitmap.getPixel(50, 365)
                assertTrue("Exported strikeout missing or vertically misplaced", Color.red(strikeout) > 160 && Color.green(strikeout) < 130 && Color.blue(strikeout) < 130)
                listOf(240, 345).forEach { y ->
                    val background = bitmap.getPixel(50, y)
                    assertTrue("Line markup incorrectly filled its text rectangle at y=$y",
                        Color.red(background) > 245 && Color.green(background) > 245 && Color.blue(background) > 245)
                }
                var black = 0
                for (y in 112..138) for (x in 65..170) {
                    val color = bitmap.getPixel(x, y)
                    if (Color.red(color) < 70 && Color.green(color) < 70 && Color.blue(color) < 70) black++
                }
                if (black <= 20) {
                    val original = sandbox.renderPage(source, 0, 600, 800)
                    try {
                        var sourceBlack = 0
                        var sample: Pair<Int, Int>? = null
                        for (y in 112..138) for (x in 65..170) {
                            val color = original.getPixel(x, y)
                            if (Color.red(color) < 70 && Color.green(color) < 70 && Color.blue(color) < 70) {
                                sourceBlack++
                                if (sample == null) sample = x to y
                            }
                        }
                        File(context.getExternalFilesDir(null), "pdf-markup-source.png").outputStream().use {
                            check(original.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it))
                        }
                        File(context.getExternalFilesDir(null), "pdf-markup-export.png").outputStream().use {
                            check(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it))
                        }
                        val after = sample?.let { (x, y) -> "#%08X".format(bitmap.getPixel(x, y)) }
                        assertTrue("Highlight washed out source text: sourceBlack=$sourceBlack exportedBlack=$black sourcePixel=$sample after=$after", black > 20)
                    } finally { original.recycle() }
                }
            } finally { bitmap.recycle() }
        } finally {
            output.delete()
            source.delete()
            assetRoot.delete()
        }
    }
}
