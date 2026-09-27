package com.example.data

import com.example.deterministic.ChapterAssemblerAgent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BookChaptersTest {

    @Test
    fun canonicalTaxonomyHasSixUniqueChapters() {
        assertEquals(6, BookChapters.STANDARD.size)
        assertEquals(BookChapters.STANDARD.size, BookChapters.STANDARD.toSet().size)
    }

    @Test
    fun readinessEvaluatorUsesTheCanonicalTaxonomy() {
        assertEquals(BookChapters.STANDARD, BookReadinessEvaluator.STANDARD_CHAPTERS)
    }

    @Test
    fun chapterAssemblerCoversTheCanonicalTaxonomy() {
        val titles = ChapterAssemblerAgent.assemble(emptyList()).map { it.chapterTitle }
        assertTrue(titles.containsAll(BookChapters.STANDARD))
    }

    @Test
    fun memoriesInCanonicalChaptersCountAsChapterBreadth() {
        val memories = BookChapters.STANDARD.take(3).mapIndexed { index, chapter ->
            Memory(
                id = index.toLong() + 1,
                transcript = "A story recorded in $chapter.",
                formattedProse = "A story recorded in $chapter.",
                chapter = chapter
            )
        }

        val report = BookReadinessEvaluator.evaluate(
            bookTitle = "Test Memoir",
            authorName = "Test Author",
            dedication = "To family",
            authorBio = "An author.",
            memories = memories
        )

        val breadthStep = report.steps.first { it.id == "chapter_breadth" }
        assertTrue(
            "Expected three canonical chapters to satisfy chapter breadth, report was $report",
            breadthStep.isCompleted
        )
    }
}
