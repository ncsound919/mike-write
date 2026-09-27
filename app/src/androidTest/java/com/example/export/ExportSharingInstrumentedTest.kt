package com.example.export

import android.content.Context
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
 * On a real device the FileProvider URI path (skipped under Robolectric on Windows) can
 * be verified end to end: a shareable file must produce a `content://` URI whose bytes
 * are readable back through the provider.
 */
@RunWith(AndroidJUnit4::class)
class ExportSharingInstrumentedTest {

    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private val memories = listOf(
        Memory(
            id = 1,
            createdAt = 1,
            transcript = "We built a treehouse in the summer with Grandpa.",
            formattedProse = "We built a treehouse in the summer with Grandpa.",
            passageTitle = "The Treehouse",
            chapter = "Chapter 1: Early Days",
            sensoryDetails = "Fresh pine and warm sun"
        )
    )

    private fun share(fileName: String, format: ExportFormat): ExportResult = runBlocking {
        MemoirExportEngine.createShareableFile(
            context = context,
            fileName = fileName,
            format = format,
            bookTitle = "My Life",
            authorName = "Mike",
            chapterTitle = null,
            memories = memories
        )
    }

    @Test
    fun pdfShareableFileProducesReadableProviderUri() {
        val result = share("instrumented_share.pdf", ExportFormat.PDF)
        assertTrue("share failed: ${result.message}", result.success)
        assertNotNull(result.uri)
        assertEquals("content", result.uri!!.scheme)
        assertEquals("application/pdf", result.mimeType)

        val bytes = context.contentResolver.openInputStream(result.uri!!)!!.use { it.readBytes() }
        assertTrue("shared PDF is empty", bytes.isNotEmpty())
    }

    @Test
    fun textAndMarkdownShareableFilesSucceed() {
        assertTrue(share("instrumented_share.txt", ExportFormat.TXT).success)
        assertTrue(share("instrumented_share.md", ExportFormat.MARKDOWN).success)
    }
}
