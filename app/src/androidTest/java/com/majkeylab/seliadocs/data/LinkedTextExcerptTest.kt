package com.majkeylab.seliadocs.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LinkedTextExcerptTest {
    private lateinit var database: SeliaDocsDatabase
    private lateinit var repository: SeliaDocsRepository

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), SeliaDocsDatabase::class.java).build()
        repository = SeliaDocsRepository(database)
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun excerptAcrossNotebooksKeepsTextAndSourceAfterSourcePageDeletion() = runTest {
        val sourceBook = repository.createNotebook(request("Source"))
        val targetBook = repository.createNotebook(request("Study"))
        val source = repository.getPages(sourceBook).single()
        val target = repository.getPages(targetBook).single()
        val draft = draft(source.id)
        val excerpt = repository.addLinkedExcerpt(target.id, draft)
        assertEquals(target.id, excerpt.pageId)
        assertEquals(draft.text, excerpt.text)
        assertEquals(source.id, excerpt.sourcePageId)
        assertEquals(draft.sourceRect, excerpt.sourceRect)
        assertEquals(source, repository.getPage(source.id))
        repository.addPage(sourceBook)
        repository.deletePage(source.id)
        assertNull(repository.getPage(source.id))
        assertEquals(excerpt, repository.getElement(excerpt.id))
    }

    @Test
    fun missingOrTrashedSourceAndTargetAreRejectedWithoutWriting() = runTest {
        val sourceBook = repository.createNotebook(request("Source"))
        val targetBook = repository.createNotebook(request("Study"))
        val source = repository.getPages(sourceBook).single().id
        val target = repository.getPages(targetBook).single().id
        assertTrue(runCatching { repository.addLinkedExcerpt(target, draft("missing")) }.isFailure)
        assertTrue(runCatching { repository.addLinkedExcerpt("missing", draft(source)) }.isFailure)
        repository.setTrashed(sourceBook, true)
        assertTrue(runCatching { repository.addLinkedExcerpt(target, draft(source)) }.isFailure)
        repository.setTrashed(sourceBook, false)
        repository.setTrashed(targetBook, true)
        assertTrue(runCatching { repository.addLinkedExcerpt(target, draft(source)) }.isFailure)
        assertTrue(repository.getElements(target).isEmpty())
    }

    @Test
    fun unsupportedKindOrIncompleteLinkMetadataIsRejected() = runTest {
        val book = repository.createNotebook(request("Study"))
        val page = repository.getPages(book).single().id
        listOf(draft(page).copy(sourceRect = null), draft(page).copy(kind = ElementKind.MATH),
            draft(page).copy(sourceRect = "0,0,2,1"), draft(page).copy(assetId = "hidden.png")).forEach { invalid ->
            assertTrue(runCatching { repository.addLinkedExcerpt(page, invalid) }.isFailure)
        }
        assertTrue(repository.getElements(page).isEmpty())
    }

    @Test
    fun imageExcerptKeepsAssetAndSourceMetadata() = runTest {
        val book = repository.createNotebook(request("Study"))
        val page = repository.getPages(book).single().id
        val draft = draft(page).copy(kind = ElementKind.IMAGE, assetId = "captured.png")
        val excerpt = repository.addLinkedExcerpt(page, draft)
        assertEquals("IMAGE", excerpt.kind)
        assertEquals("captured.png", excerpt.assetId)
        assertEquals(draft.sourceRect, excerpt.sourceRect)
        assertEquals(page, excerpt.sourcePageId)
    }

    private fun request(title: String) = CreateNotebookRequest(title, CoverColor.SAGE, CoverPattern.SOLID,
        PaperTemplate.BLANK, PageOrientation.PORTRAIT, false)

    private fun draft(sourcePageId: String) = ElementDraft(ElementKind.TEXT, 20f, 30f, 250f, 80f,
        text = "A quoted passage", sourcePageId = sourcePageId,
        sourceRect = encodeSourceRect(AnnotationRect(0.1f, 0.2f, 0.8f, 0.3f)))
}
