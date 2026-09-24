package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.Memory
import com.example.ui.theme.*

/**
 * ChapterSwitcherDialog:
 * Senior-optimized high-contrast modal for selecting and switching active book chapters
 * with live passage count metrics and instant auditory confirmation.
 */
@Composable
fun ChapterSwitcherDialog(
    currentChapter: String,
    allChapters: List<String>,
    memories: List<Memory>,
    onSelectChapter: (String) -> Unit,
    onAuditionChapter: (String) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.85f)
                .padding(vertical = 16.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = DarkNavySurface),
            border = BorderStroke(2.dp, AmberGold)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = AmberGold.copy(alpha = 0.2f),
                            modifier = Modifier.size(44.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.MenuBook,
                                    contentDescription = null,
                                    tint = AmberGold,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "SELECT ACTIVE CHAPTER",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 1.sp,
                                    color = AmberGold
                                )
                            )
                            Text(
                                text = "Tap a chapter to focus your voice dictation",
                                style = MaterialTheme.typography.bodySmall.copy(color = LightGrayMuted)
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(40.dp).testTag("close_chapter_dialog_button")
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = OffWhiteText)
                    }
                }

                Spacer(Modifier.height(16.dp))
                HorizontalDivider(color = BorderSubtle, thickness = 1.dp)
                Spacer(Modifier.height(16.dp))

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(allChapters) { chapterName ->
                        val isSelected = chapterName.equals(currentChapter, ignoreCase = true)
                        val chapterMemories = memories.filter { it.chapter.equals(chapterName, ignoreCase = true) }
                        val wordCount = chapterMemories.sumOf {
                            (it.formattedProse ?: it.transcript).split(Regex("\\s+")).count { w -> w.isNotBlank() }
                        }

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(18.dp))
                                .clickable {
                                    onSelectChapter(chapterName)
                                }
                                .testTag("chapter_item_${chapterName.take(10).replace(" ", "_")}"),
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) MidnightCard else DeepNavy
                            ),
                            border = BorderStroke(
                                width = if (isSelected) 2.dp else 1.dp,
                                color = if (isSelected) AmberGold else BorderSubtle
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = chapterName,
                                            style = MaterialTheme.typography.titleMedium.copy(
                                                fontWeight = FontWeight.Bold,
                                                color = if (isSelected) AmberGold else OffWhiteText,
                                                fontSize = 17.sp
                                            )
                                        )
                                        if (isSelected) {
                                            Spacer(Modifier.width(8.dp))
                                            Icon(
                                                imageVector = Icons.Default.CheckCircle,
                                                contentDescription = "Active Chapter",
                                                tint = AmberGold,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }

                                    Spacer(Modifier.height(6.dp))

                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = EmeraldDark
                                        ) {
                                            Text(
                                                text = "${chapterMemories.size} Passages",
                                                color = EmeraldVoice,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }

                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = MidnightCard,
                                            border = BorderStroke(1.dp, BorderSubtle)
                                        ) {
                                            Text(
                                                text = "$wordCount words",
                                                color = LightGrayMuted,
                                                fontSize = 11.sp,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }

                                if (chapterMemories.isNotEmpty()) {
                                    IconButton(
                                        onClick = { onAuditionChapter(chapterName) },
                                        modifier = Modifier.size(44.dp).testTag("audition_chapter_$chapterName")
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                                            contentDescription = "Listen to Chapter",
                                            tint = SkyBlue,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = AmberGold, contentColor = DeepNavy),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                ) {
                    Text("Close & Return to Dictation", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            }
        }
    }
}
