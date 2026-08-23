package com.example

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import android.widget.MediaController
import android.widget.Toast
import android.widget.VideoView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Movie
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import java.io.File

data class FileCategoryInfo(
    val icon: ImageVector,
    val iconColor: Color,
    val backgroundColor: Color,
    val label: String
)

fun getFileCategoryInfo(filename: String): FileCategoryInfo {
    val ext = filename.substringAfterLast(".", "").lowercase()
    return when (ext) {
        "jpg", "jpeg", "png", "webp", "gif", "bmp", "svg", "heic" -> FileCategoryInfo(
            icon = Icons.Default.Image,
            iconColor = Color(0xFF38BDF8),
            backgroundColor = Color(0x2438BDF8),
            label = "تصویر"
        )
        "mp4", "mkv", "webm", "3gp", "avi", "mov", "flv", "ts", "m4v" -> FileCategoryInfo(
            icon = Icons.Default.Movie,
            iconColor = Color(0xFFA855F7),
            backgroundColor = Color(0x24A855F7),
            label = "ویدیو"
        )
        "mp3", "wav", "m4a", "ogg", "aac", "flac", "opus", "wma" -> FileCategoryInfo(
            icon = Icons.Default.Audiotrack,
            iconColor = Color(0xFFEC4899),
            backgroundColor = Color(0x24EC4899),
            label = "صدا"
        )
        "pdf", "doc", "docx", "txt", "rtf", "odt", "xls", "xlsx", "ppt", "pptx", "csv" -> FileCategoryInfo(
            icon = Icons.Default.Description,
            iconColor = Color(0xFFF97316),
            backgroundColor = Color(0x24F97316),
            label = "سند"
        )
        "zip", "rar", "7z", "tar", "gz", "bz2", "xz" -> FileCategoryInfo(
            icon = Icons.Default.FolderZip,
            iconColor = Color(0xFFFBBF24),
            backgroundColor = Color(0x24FBBF24),
            label = "فشرده"
        )
        "apk", "xapk", "apks" -> FileCategoryInfo(
            icon = Icons.Default.Android,
            iconColor = Color(0xFF22C55E),
            backgroundColor = Color(0x2422C55E),
            label = "برنامه"
        )
        "kt", "java", "py", "js", "ts", "html", "css", "json", "xml", "c", "cpp", "h", "cs", "sql", "sh" -> FileCategoryInfo(
            icon = Icons.Default.Code,
            iconColor = Color(0xFF6366F1),
            backgroundColor = Color(0x246366F1),
            label = "کد"
        )
        else -> FileCategoryInfo(
            icon = Icons.Default.InsertDriveFile,
            iconColor = Color(0xFF94A3B8),
            backgroundColor = Color(0x2494A3B8),
            label = "فایل"
        )
    }
}

@Composable
fun FileVectorIconBadge(
    filename: String,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp
) {
    val info = remember(filename) { getFileCategoryInfo(filename) }
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(12.dp))
            .background(info.backgroundColor)
            .border(BorderStroke(1.dp, info.iconColor.copy(alpha = 0.35f)), RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = info.icon,
            contentDescription = info.label,
            tint = info.iconColor,
            modifier = Modifier.size(size * 0.52f)
        )
    }
}

fun getFileType(filename: String): String {
    val ext = filename.substringAfterLast(".").lowercase()
    return when (ext) {
        "jpg", "jpeg", "png", "webp", "gif" -> "image"
        "mp3", "wav", "m4a", "ogg", "aac" -> "audio"
        "mp4", "mkv", "webm", "3gp", "avi" -> "video"
        else -> "other"
    }
}

fun formatMs(ms: Int): String {
    val secTotal = ms / 1000
    val min = secTotal / 60
    val sec = secTotal % 60
    return String.format("%02d:%02d", min, sec)
}

@Composable
fun ImagePreviewWidget(fileSource: String, modifier: Modifier = Modifier) {
    var isExpanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        AsyncImage(
            model = fileSource,
            contentDescription = "تصویر پیوست شده",
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 180.dp)
                .clip(RoundedCornerShape(8.dp))
                .clickable { isExpanded = true },
            contentScale = ContentScale.Crop
        )

        if (isExpanded) {
            Dialog(onDismissRequest = { isExpanded = false }) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .background(Color.Black.copy(alpha = 0.92f), RoundedCornerShape(12.dp))
                        .clickable { isExpanded = false },
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = fileSource,
                        contentDescription = "تصویر کامل",
                        modifier = Modifier.fillMaxWidth(),
                        contentScale = ContentScale.Fit
                    )
                }
            }
        }
    }
}

@Composable
fun AudioPlayerWidget(fileSource: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var currentPosition by remember { mutableStateOf(0) }
    var duration by remember { mutableStateOf(0) }

    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            while (isPlaying && mediaPlayer != null) {
                try {
                    currentPosition = mediaPlayer?.currentPosition ?: 0
                } catch (e: Exception) {}
                kotlinx.coroutines.delay(200)
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            mediaPlayer?.release()
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(Color(0xFF00A884), CircleShape)
                .clickable {
                    if (mediaPlayer == null) {
                        try {
                            mediaPlayer = MediaPlayer().apply {
                                setDataSource(fileSource)
                                prepare()
                                duration = this.duration
                                setOnCompletionListener {
                                    isPlaying = false
                                    currentPosition = 0
                                }
                            }
                        } catch (e: Exception) {
                            Toast.makeText(context, "خطا در پخش فایل صوتی", Toast.LENGTH_SHORT).show()
                            return@clickable
                        }
                    }
                    
                    if (isPlaying) {
                        mediaPlayer?.pause()
                        isPlaying = false
                    } else {
                        mediaPlayer?.start()
                        isPlaying = true
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Text(if (isPlaying) "⏸" else "▶", fontSize = 14.sp, color = Color.White)
        }

        Column(modifier = Modifier.weight(1f)) {
            val progress = if (duration > 0) currentPosition.toFloat() / duration else 0f
            LinearProgressIndicator(
                progress = progress,
                modifier = Modifier.fillMaxWidth().height(4.dp),
                color = Color(0xFF00A884),
                trackColor = Color.White.copy(alpha = 0.2f)
            )
            
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = formatMs(currentPosition),
                    fontSize = 10.sp,
                    color = Color.White.copy(alpha = 0.7f)
                )
                Text(
                    text = formatMs(duration),
                    fontSize = 10.sp,
                    color = Color.White.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
fun VideoPreviewWidget(fileSource: String, modifier: Modifier = Modifier) {
    var isPlayingDialog by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(140.dp)
            .background(Color.Black, RoundedCornerShape(8.dp))
            .clickable { isPlayingDialog = true },
        contentAlignment = Alignment.Center
    ) {
        Text("📹", fontSize = 42.sp)
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(Color.Black.copy(alpha = 0.62f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text("▶", color = Color.White, fontSize = 24.sp)
        }

        if (isPlayingDialog) {
            Dialog(onDismissRequest = { isPlayingDialog = false }) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16/9f)
                        .background(Color.Black, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    AndroidView(
                        factory = { ctx ->
                            VideoView(ctx).apply {
                                setVideoURI(Uri.parse(fileSource))
                                val controller = MediaController(ctx)
                                controller.setAnchorView(this)
                                setMediaController(controller)
                                start()
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}
