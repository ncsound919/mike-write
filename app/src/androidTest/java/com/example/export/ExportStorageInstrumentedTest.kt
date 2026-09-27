package com.example.export

import android.content.Context
import android.os.Build
import android.provider.MediaStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.data.Memory
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Verifies the on-device storage path. On Android 10+ this writes through MediaStore into
 * Documents/MikeWrite; the row must then be visible to the media scanner.
 */
@RunWith(AndroidJUnit4::class)
class ExportStorageInstrumentedTest {

    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private val memories = listOf(
        Memory(
            id = 1,
            createdAt = 1,
            transcript = "We built a treehouse in the summer with Grandpa.",
            formattedProse = "We built a treehouse in the summer with Grandpa.",
            passageTitle = "The Treehouse",
            chapter = "Chapter 1: Early Days"
        )
    )

    private fun save(fileName: String, format: ExportFormat): ExportResult = runBlocking {
        MemoirExportEngine.saveToStorage(
            context = context,
            fileName = fileName,
            format = format,
            bookTitle = "My Life",
            authorName = "Mike",
            chapterTitle = null,
            memories = memories
        )
    }

    private fun findInMediaStore(fileName: String): Boolean {
        val collection = MediaStore.Files.getContentUri("external")
        val projection = arrayOf(MediaStore.MediaColumns.DISPLAY_NAME)
        val selection = "${MediaStore.MediaColumns.DISPLAY_NAME} = ?"
        context.contentResolver.query(collection, projection, selection, arrayOf(fileName), null).use { cursor ->
            return cursor != null && cursor.count > 0
        }
    }

    @Test
    fun saveToStorageWritesAVisibleFile() {
        val fileName = "instrumented_storage_${System.currentTimeMillis()}.txt"
        val result = save(fileName, ExportFormat.TXT)
        assertTrue("save failed: ${result.message}", result.success)
        assertEquals("text/plain", result.mimeType)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            assertNotNull(result.uri)
            assertEquals("content", result.uri!!.scheme)
            assertTrue("file not visible in MediaStore", findInMediaStore(fileName))
            context.contentResolver.delete(result.uri!!, null, null)
        }
    }

    @Test
    fun savePdfToStorageSucceeds() {
        val result = save("instrumented_storage_${System.currentTimeMillis()}.pdf", ExportFormat.PDF)
        assertTrue("save failed: ${result.message}", result.success)
        assertEquals("application/pdf", result.mimeType)
        result.uri?.let { context.contentResolver.delete(it, null, null) }
    }
}
