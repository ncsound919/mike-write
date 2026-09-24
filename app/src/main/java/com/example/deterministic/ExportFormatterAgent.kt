package com.example.deterministic

import android.content.Context
import com.example.data.Memory
import java.io.File

/**
 * Agent 11 — Export Formatter
 * Generates structured clean Markdown, HTML/EPUB-ready, and Plain Text book files
 * on-device deterministically with zero API latency or token cost.
 */
object ExportFormatterAgent {

    fun formatMarkdown(
        bookTitle: String,
        authorName: String,
        dedication: String,
        authorBio: String,
        chapters: List<AssembledChapter>
    ): String {
        val sb = StringBuilder()
        sb.appendLine("# $bookTitle")
        sb.appendLine("### By $authorName")
        sb.appendLine()
        sb.appendLine("---")
        sb.appendLine()
        sb.appendLine("## Dedication")
        sb.appendLine("*$dedication*")
        sb.appendLine()
        sb.appendLine("---")
        sb.appendLine()
        sb.appendLine("## Table of Contents")
        chapters.forEachIndexed { i, c ->
            sb.appendLine("${i + 1}. [${c.chapterTitle}](#${c.chapterTitle.lowercase().replace(" ", "-").replace(":", "")}) — ${c.wordCount} words")
        }
        sb.appendLine()
        sb.appendLine("---")
        sb.appendLine()

        for (chap in chapters) {
            sb.appendLine("## ${chap.chapterTitle}")
            sb.appendLine()
            if (chap.memories.isEmpty()) {
                sb.appendLine("*(Draft in progress)*")
            } else {
                for (mem in chap.memories) {
                    sb.appendLine(mem.transcript)
                    sb.appendLine()
                }
            }
            sb.appendLine("---")
            sb.appendLine()
        }

        sb.appendLine("## About the Author")
        sb.appendLine(authorBio)
        sb.appendLine()
        return sb.toString()
    }

    fun exportToFile(context: Context, fileName: String, content: String): File {
        val file = File(context.filesDir, fileName)
        file.writeText(content)
        return file
    }
}
