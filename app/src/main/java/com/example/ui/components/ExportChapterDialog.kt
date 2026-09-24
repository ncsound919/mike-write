package com.example.ui.components

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.Memory
import com.example.export.ExportFormat
import com.example.export.MemoirExportEngine
import com.example.ui.theme.*
import kotlinx.coroutines.launch

/**
 * Modal dialog offering full-featured export of memoir chapters or whole books
 * into Text (.txt), Markdown (.md), or typeset PDF (.pdf).
 */
@Composable
fun ExportChapterDialog(
    bookTitle: String,
    authorName: String,
    allChapters: List<String>,
    memories: List<Memory>,
    initialChapter: String? = null,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedChapter by remember { mutableStateOf(initialChapter ?: "All Chapters") }
    var selectedFormat by remember { mutableStateOf(ExportFormat.PDF) }
    var includeLiteraryNotes by remember { mutableStateOf(true) }
    var isExporting by remember { mutableStateOf(false) }
    var exportStatusMessage by remember { mutableStateOf<String?>(null) }

    val chapterOptions = listOf("All Chapters") + allChapters.distinct()

    val filteredMemories = remember(selectedChapter, memories) {
        if (selectedChapter == "All Chapters") {
            memories
        } else {
            memories.filter { (it.chapter?.trim() ?: "Prologue") == selectedChapter.trim() }
        }
    }

    Dialog(onDismissRequest = { if (!isExporting) onDismiss() }) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = DarkNavySurface),
            border = BorderStroke(1.dp, BorderSubtle),
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp)
                .testTag("export_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                // Dialog Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(AmberGold.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = null,
                                tint = AmberGold,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Export Memoir",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = OffWhiteText
                                )
                            )
                            Text(
                                text = "Save to device or share with family",
                                style = MaterialTheme.typography.bodySmall.copy(color = LightGrayMuted)
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        enabled = !isExporting,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = LightGrayMuted)
                    }
                }

                Spacer(Modifier.height(20.dp))

                // Scope Selection: Chapter Dropdown / Selector
                Text(
                    text = "SELECT CHAPTER OR SCOPE",
                    style = MaterialTheme.typography.labelMedium.copy(
                        color = LightGrayMuted,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                )
                Spacer(Modifier.height(6.dp))

                var expandedChapterDropdown by remember { mutableStateOf(false) }
                Box {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MidnightCard,
                        border = BorderStroke(1.dp, BorderSubtle),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { expandedChapterDropdown = true }
                            .testTag("export_chapter_selector")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = selectedChapter,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp,
                                    color = OffWhiteText
                                )
                                Text(
                                    text = "${filteredMemories.size} passages available",
                                    fontSize = 11.sp,
                                    color = AmberGold
                                )
                            }
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = LightGrayMuted)
                        }
                    }

                    DropdownMenu(
                        expanded = expandedChapterDropdown,
                        onDismissRequest = { expandedChapterDropdown = false },
                        modifier = Modifier.background(MidnightCard)
                    ) {
                        chapterOptions.forEach { chap ->
                            DropdownMenuItem(
                                text = { Text(chap, color = OffWhiteText) },
                                onClick = {
                                    selectedChapter = chap
                                    expandedChapterDropdown = false
                                }
                            )
                        }
                    }
                }

                Spacer(Modifier.height(18.dp))

                // Export Format Selection
                Text(
                    text = "SELECT EXPORT FORMAT",
                    style = MaterialTheme.typography.labelMedium.copy(
                        color = LightGrayMuted,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                )
                Spacer(Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ExportFormatCard(
                        format = ExportFormat.PDF,
                        label = "PDF Book",
                        description = "Formatted & Typeset",
                        icon = Icons.Default.PictureAsPdf,
                        isSelected = selectedFormat == ExportFormat.PDF,
                        activeColor = AmberGold,
                        modifier = Modifier.weight(1f),
                        onClick = { selectedFormat = ExportFormat.PDF }
                    )

                    ExportFormatCard(
                        format = ExportFormat.TXT,
                        label = "Text File",
                        description = "Universal .txt",
                        icon = Icons.Default.Description,
                        isSelected = selectedFormat == ExportFormat.TXT,
                        activeColor = SkyBlue,
                        modifier = Modifier.weight(1f),
                        onClick = { selectedFormat = ExportFormat.TXT }
                    )

                    ExportFormatCard(
                        format = ExportFormat.MARKDOWN,
                        label = "Markdown",
                        description = "For Publishing (.md)",
                        icon = Icons.Default.EditNote,
                        isSelected = selectedFormat == ExportFormat.MARKDOWN,
                        activeColor = PerspectiveTeal,
                        modifier = Modifier.weight(1f),
                        onClick = { selectedFormat = ExportFormat.MARKDOWN }
                    )
                }

                Spacer(Modifier.height(14.dp))

                // Toggle for literary reflections & notes
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Include Story Notes & Reflections",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = OffWhiteText
                        )
                        Text(
                            text = "Preserve emotional tone and character anchors",
                            fontSize = 11.sp,
                            color = LightGrayMuted
                        )
                    }
                    Switch(
                        checked = includeLiteraryNotes,
                        onCheckedChange = { includeLiteraryNotes = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = DeepNavy,
                            checkedTrackColor = AmberGold,
                            uncheckedThumbColor = LightGrayMuted,
                            uncheckedTrackColor = MidnightCard
                        )
                    )
                }

                if (exportStatusMessage != null) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = exportStatusMessage.orEmpty(),
                        color = AmberGold,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(Modifier.height(20.dp))

                // Actions: Save to Device or Share Directly
                if (isExporting) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(color = AmberGold, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(12.dp))
                        Text("Exporting manuscript...", color = LightGrayMuted, fontSize = 13.sp)
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Save directly to Documents folder
                        Button(
                            onClick = {
                                scope.launch {
                                    isExporting = true
                                    val safeTitle = bookTitle.replace(Regex("[^a-zA-Z0-9_]"), "_").take(25)
                                    val safeChap = if (selectedChapter == "All Chapters") "Full_Book" else selectedChapter.replace(Regex("[^a-zA-Z0-9_]"), "_").take(20)
                                    val fileName = "${safeTitle}_${safeChap}.${selectedFormat.extension}"

                                    val result = MemoirExportEngine.saveToStorage(
                                        context = context,
                                        fileName = fileName,
                                        format = selectedFormat,
                                        bookTitle = bookTitle,
                                        authorName = authorName,
                                        chapterTitle = if (selectedChapter == "All Chapters") null else selectedChapter,
                                        memories = filteredMemories
                                    )

                                    isExporting = false
                                    if (result.success) {
                                        Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
                                        exportStatusMessage = "✓ ${result.message}"
                                    } else {
                                        Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
                                        exportStatusMessage = result.message
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = AmberGold, contentColor = DeepNavy),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("save_to_device_button")
                        ) {
                            Icon(Icons.Default.SaveAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Save File", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }

                        // Share / Open in other apps
                        FilledTonalButton(
                            onClick = {
                                scope.launch {
                                    isExporting = true
                                    val safeTitle = bookTitle.replace(Regex("[^a-zA-Z0-9_]"), "_").take(25)
                                    val safeChap = if (selectedChapter == "All Chapters") "Full_Book" else selectedChapter.replace(Regex("[^a-zA-Z0-9_]"), "_").take(20)
                                    val fileName = "${safeTitle}_${safeChap}.${selectedFormat.extension}"

                                    val result = MemoirExportEngine.createShareableFile(
                                        context = context,
                                        fileName = fileName,
                                        format = selectedFormat,
                                        bookTitle = bookTitle,
                                        authorName = authorName,
                                        chapterTitle = if (selectedChapter == "All Chapters") null else selectedChapter,
                                        memories = filteredMemories
                                    )

                                    isExporting = false
                                    if (result.success && result.uri != null) {
                                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                            type = result.mimeType
                                            putExtra(Intent.EXTRA_STREAM, result.uri)
                                            putExtra(Intent.EXTRA_SUBJECT, "$bookTitle - $selectedChapter")
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        context.startActivity(Intent.createChooser(shareIntent, "Share Memoir Export"))
                                        onDismiss()
                                    } else {
                                        Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
                                        exportStatusMessage = result.message
                                    }
                                }
                            },
                            colors = ButtonDefaults.filledTonalButtonColors(containerColor = MidnightCard, contentColor = OffWhiteText),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, BorderSubtle),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("share_export_button")
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, tint = SkyBlue, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Share...", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExportFormatCard(
    format: ExportFormat,
    label: String,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    activeColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (isSelected) activeColor.copy(alpha = 0.15f) else MidnightCard,
        border = BorderStroke(
            if (isSelected) 1.5.dp else 1.dp,
            if (isSelected) activeColor else BorderSubtle
        ),
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .testTag("export_format_${format.name.lowercase()}")
    ) {
        Column(
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isSelected) activeColor else LightGrayMuted,
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = label,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                color = if (isSelected) OffWhiteText else LightGrayMuted
            )
            Text(
                text = format.extension.uppercase(),
                fontSize = 9.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (isSelected) activeColor else LightGrayMuted.copy(alpha = 0.6f)
            )
        }
    }
}
