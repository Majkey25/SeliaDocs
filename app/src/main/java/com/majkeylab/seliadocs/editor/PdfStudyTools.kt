package com.majkeylab.seliadocs.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.majkeylab.seliadocs.R
import com.majkeylab.seliadocs.data.AnnotationRect
import com.majkeylab.seliadocs.data.ElementDraft
import com.majkeylab.seliadocs.data.ElementEntity
import com.majkeylab.seliadocs.data.ElementKind
import com.majkeylab.seliadocs.data.PageEntity
import com.majkeylab.seliadocs.data.decodeAnnotationRects
import com.majkeylab.seliadocs.data.encodeAnnotationRects
import com.majkeylab.seliadocs.data.encodeSourceRect
import com.majkeylab.seliadocs.data.isPdfMarkup
import com.majkeylab.seliadocs.pdf.PdfTextSelection
import com.majkeylab.seliadocs.pdf.PdfTextBounds
import kotlinx.coroutines.CancellationException

internal fun isTextMarkup(kind: String): Boolean =
    runCatching { ElementKind.valueOf(kind).isPdfMarkup() }.getOrDefault(false)

internal fun pdfMarkupDraft(page: PageEntity, selection: PdfTextSelection, kind: ElementKind, color: Int): ElementDraft {
    require(isTextMarkup(kind.name) && selection.bounds.isNotEmpty() && selection.text.isNotBlank())
    val left = selection.bounds.minOf { it.left }
    val top = selection.bounds.minOf { it.top }
    val right = selection.bounds.maxOf { it.right }
    val bottom = selection.bounds.maxOf { it.bottom }
    val width = right - left
    val height = bottom - top
    require(width > 0 && height > 0)
    return ElementDraft(
        kind = kind,
        x = left * page.widthPoints, y = top * page.heightPoints,
        width = width * page.widthPoints, height = height * page.heightPoints,
        text = selection.text,
        colorArgb = if (kind == ElementKind.HIGHLIGHT) color else color or 0xFF000000.toInt(),
        annotationRects = encodeAnnotationRects(selection.bounds.map {
            AnnotationRect(
                ((it.left - left) / width).coerceIn(0f, 1f), ((it.top - top) / height).coerceIn(0f, 1f),
                ((it.right - left) / width).coerceIn(0f, 1f), ((it.bottom - top) / height).coerceIn(0f, 1f),
            )
        }),
        sourcePageId = page.id,
        sourceRect = encodeSourceRect(AnnotationRect(left, top, right, bottom)),
    )
}

@Composable
internal fun PdfSelectionPreview(selection: PdfTextSelection?, region: AnnotationRect?, modifier: Modifier) {
    Canvas(modifier.testTag("pdf-text-selection-preview")) {
        val bounds = selection?.bounds ?: region?.let { listOf(PdfTextBounds(it.left, it.top, it.right, it.bottom)) }.orEmpty()
        bounds.forEach { rect ->
            drawRect(Color(0x663156D9), Offset(rect.left * size.width, rect.top * size.height),
                Size((rect.right - rect.left) * size.width, (rect.bottom - rect.top) * size.height), blendMode = BlendMode.Multiply)
        }
    }
}

@Composable
internal fun PdfMarkupElement(element: ElementEntity, modifier: Modifier) {
    val rects = remember(element.annotationRects) { decodeAnnotationRects(element.annotationRects) }
    val description = element.text.orEmpty()
    Canvas(modifier.semantics { contentDescription = description }) {
        val color = Color(element.colorArgb ?: 0x66FFD54F)
        rects.forEach { rect ->
            val left = rect.left * size.width
            val top = rect.top * size.height
            val right = rect.right * size.width
            val bottom = rect.bottom * size.height
            if (element.kind == ElementKind.HIGHLIGHT.name) {
                drawRect(color, Offset(left, top), Size(right - left, bottom - top), blendMode = BlendMode.Multiply)
            } else {
                val y = if (element.kind == ElementKind.UNDERLINE.name) bottom else (top + bottom) / 2f
                drawLine(color, Offset(left, y), Offset(right, y), ((bottom - top) * 0.08f).coerceAtLeast(1f))
            }
        }
    }
}

@Composable
internal fun PdfSelectionBar(
    selection: PdfTextSelection?, busy: Boolean, message: String?,
    onCopy: (String) -> Unit, onMarkup: (ElementKind) -> Unit, onDismiss: () -> Unit,
    onExcerpt: () -> Unit,
    onCapture: (() -> Unit)?,
) {
    Surface {
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp).testTag("pdf-selection-bar")) {
            Text(
                text = when {
                    busy -> stringResource(R.string.pdf_selecting_text)
                    selection != null -> selection.text
                    else -> message.orEmpty()
                },
                maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
            if (selection?.isOcr == true) Text(stringResource(R.string.pdf_ocr_selection), style = MaterialTheme.typography.labelSmall)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (selection != null && !busy) {
                    TextButton(onClick = { onCopy(selection.text) }) { Text(stringResource(R.string.copy_text)) }
                    TextButton(onClick = onExcerpt) { Text(stringResource(R.string.copy_to_notebook)) }
                    listOf(ElementKind.HIGHLIGHT to R.string.highlight_text, ElementKind.UNDERLINE to R.string.underline_text,
                        ElementKind.STRIKEOUT to R.string.strikeout_text).forEach { (kind, label) ->
                        TextButton(onClick = { onMarkup(kind) }, modifier = Modifier.testTag("pdf-apply-${kind.name.lowercase()}")) {
                            Text(stringResource(label))
                        }
                    }
                }
                if (!busy && onCapture != null) {
                    TextButton(onClick = onCapture) { Text(stringResource(R.string.capture_to_notebook)) }
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            }
        }
    }
}

@Composable
internal fun ExcerptDestinationDialog(viewModel: EditorViewModel, asImage: Boolean, onDismiss: () -> Unit) {
    val notebooks by viewModel.excerptNotebooks.collectAsStateWithLifecycle(initialValue = emptyList())
    var notebookId by remember { mutableStateOf<String?>(null) }
    var pages by remember { mutableStateOf<List<PageEntity>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    LaunchedEffect(notebookId) {
        pages = emptyList()
        val id = notebookId ?: return@LaunchedEffect
        loading = true
        failed = false
        try {
            pages = viewModel.excerptPages(id)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            failed = true
        } finally {
            loading = false
        }
    }
    AlertDialog(
        modifier = Modifier.testTag("excerpt-destination"),
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text(stringResource(R.string.copy_to_notebook)) },
        text = {
            LazyColumn(Modifier.heightIn(max = 360.dp)) {
                if (failed) item { Text(stringResource(R.string.excerpt_failed)) }
                if (loading || saving) item { Text(stringResource(R.string.please_wait)) }
                if (notebookId == null) {
                    if (notebooks.isEmpty()) item { Text(stringResource(R.string.no_notebooks)) }
                    items(notebooks, key = { it.id }) { notebook ->
                        TextButton(onClick = { notebookId = notebook.id }, modifier = Modifier.fillMaxWidth()) {
                            Text(notebook.title, modifier = Modifier.fillMaxWidth(), maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                } else {
                    items(pages, key = { it.id }) { page ->
                        TextButton(
                            enabled = !saving,
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                saving = true
                                failed = false
                                viewModel.copyPdfSelectionTo(page.id, asImage) { saved ->
                                    saving = false
                                    if (saved) onDismiss() else failed = true
                                }
                            },
                        ) {
                            Column(Modifier.fillMaxWidth()) {
                                Text(stringResource(R.string.page_number, page.pageIndex + 1))
                                page.title?.takeIf { it.isNotBlank() }?.let { title ->
                                    Text(title, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(enabled = !saving, onClick = {
                if (notebookId == null) onDismiss() else notebookId = null
            }) { Text(stringResource(if (notebookId == null) R.string.cancel else R.string.back)) }
        },
    )
}
