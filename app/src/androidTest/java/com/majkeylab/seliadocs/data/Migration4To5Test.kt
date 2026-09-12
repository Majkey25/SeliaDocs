package com.majkeylab.seliadocs.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Migration4To5Test {
    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), SeliaDocsDatabase::class.java)

    @Test
    fun legacyImageAndOcrSurviveWithNoInventedAnnotationOrSourceLink() {
        helper.createDatabase("migration-4-5", 4).use { database ->
            database.execSQL("INSERT INTO notebooks VALUES ('book', 'Study', 'SAGE', 'SOLID', 'BLANK', 'PORTRAIT', 0, 0, 1, 2, NULL)")
            database.execSQL("""INSERT INTO pages (id, notebookId, pageIndex, paper, widthPoints, heightPoints,
                pageMode, bookmarked, createdAt, updatedAt) VALUES ('page', 'book', 0, 'BLANK', 595, 842, 'PAPER', 0, 1, 2)""")
            database.execSQL("""INSERT INTO elements (id, pageId, zIndex, kind, x, y, width, height, rotation,
                text, assetId, ocrRegions) VALUES ('image', 'page', 0, 'IMAGE', 10, 20, 200, 100, 0,
                'Existing text', 'image.png', 'existing-regions')""")
        }
        helper.runMigrationsAndValidate("migration-4-5", 5, true, SeliaDocsDatabase.MIGRATION_4_5).use { database ->
            database.query("SELECT text, assetId, ocrRegions, colorArgb, annotationRects, sourcePageId, sourceRect FROM elements").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("Existing text", cursor.getString(0))
                assertEquals("image.png", cursor.getString(1))
                assertEquals("existing-regions", cursor.getString(2))
                (3..6).forEach { assertTrue(cursor.isNull(it)) }
            }
        }
    }
}
