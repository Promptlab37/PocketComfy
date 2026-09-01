package cz.promptlab.h3video.ui

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cz.promptlab.h3video.data.VideoItem
import cz.promptlab.h3video.ui.theme.Cyan
import cz.promptlab.h3video.ui.theme.Danger
import cz.promptlab.h3video.ui.theme.Ok
import cz.promptlab.h3video.ui.theme.Outline1
import cz.promptlab.h3video.ui.theme.Surface1
import cz.promptlab.h3video.ui.theme.Surface2
import cz.promptlab.h3video.ui.theme.TextHi
import cz.promptlab.h3video.ui.theme.TextLow
import cz.promptlab.h3video.ui.theme.TextMid
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistoryScreen(
    items: List<VideoItem>,
    totalBytes: Long,
    onOpen: (VideoItem) -> Unit,
    onDelete: (VideoItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (items.isEmpty()) {
        Column(
            modifier
                .fillMaxSize()
                .padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.Default.VideoLibrary, null, Modifier.size(44.dp), Outline1)
            Spacer(Modifier.height(14.dp))
            Text("Zatím tu nic není", style = MaterialTheme.typography.titleMedium, color = TextMid)
            Spacer(Modifier.height(6.dp))
            Text(
                "Vygenerovaná videa se ukládají sem a zůstanou tu,\ni když je počítač vypnutý.",
                style = MaterialTheme.typography.bodySmall, color = TextLow,
                textAlign = TextAlign.Center
            )
        }
        return
    }

    LazyColumn(
        modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            // Zelená fajfka u videa znamená, že kopie je i v telefonu; zbytek žije
            // jen tady v aplikaci, dokud si ho nestáhneš.
            Text(
                "${videoCount(items.size)} · ${"%.1f".format(totalBytes / 1_048_576f)} MB",
                style = MaterialTheme.typography.bodySmall, color = TextLow
            )
        }
        items(items, key = { it.id }) { item ->
            HistoryRow(item, onOpen = { onOpen(item) }, onDelete = { onDelete(item) })
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun HistoryRow(item: VideoItem, onOpen: () -> Unit, onDelete: () -> Unit) {
    val ctx = LocalContext.current
    var thumb by remember(item.id) { mutableStateOf<Bitmap?>(null) }
    var confirmDelete by remember(item.id) { mutableStateOf(false) }

    LaunchedEffect(item.id) {
        thumb = withContext(Dispatchers.IO) {
            // Obrázkový výsledek se načte přímo; MediaMetadataRetriever by na
            // PNG vrátil null a dlaždice by zůstala prázdná. Skladba náhled
            // nemá – dlaždici dělá nota.
            when {
                item.isAudio -> null
                item.isImage -> runCatching {
                    android.graphics.BitmapFactory.decodeFile(item.file(ctx).absolutePath)
                }.getOrNull()
                else -> frameOf(item.file(ctx))
            }
        }
    }

    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Surface1)
            .border(1.dp, Outline1, RoundedCornerShape(18.dp))
            .clickable(onClick = onOpen)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(width = 108.dp, height = 68.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Surface2),
            contentAlignment = Alignment.Center
        ) {
            thumb?.let {
                Image(
                    it.asImageBitmap(), null,
                    Modifier.fillMaxSize(), contentScale = ContentScale.Crop
                )
            }
            // Skladba nemá náhled – dlaždici dělá nota.
            if (item.isAudio) {
                Icon(Icons.Default.MusicNote, null, Modifier.size(30.dp), TextLow)
            }
            // Obrázek se nepřehrává – trojúhelník by sliboval video.
            if (!item.isImage && !item.isAudio) Box(
                Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color.Black.copy(alpha = .5f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.PlayArrow, null, Modifier.size(18.dp), Color.White)
            }
        }

        Spacer(Modifier.width(12.dp))

        Column(Modifier.weight(1f)) {
            Text(
                item.prompt.ifBlank { "(bez popisu)" },
                style = MaterialTheme.typography.bodyMedium,
                color = TextHi,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    // délku neznáme u videa, na které aplikace navázala po restartu
                    if (item.seconds > 0f)
                        "%.1f s · %s · %s".format(item.seconds, item.resolution, dateOf(item.createdAt))
                    else "%s · %s".format(item.resolution, dateOf(item.createdAt)),
                    style = MaterialTheme.typography.bodySmall, color = TextLow
                )
                if (item.inGallery) {
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.Default.CheckCircle, "V galerii telefonu", Modifier.size(13.dp), Ok)
                }
            }
        }

        Box(
            Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(50))
                .clickable {
                    if (confirmDelete) onDelete() else confirmDelete = true
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.DeleteOutline, null, Modifier.size(20.dp),
                if (confirmDelete) Danger else TextLow
            )
        }
    }
}

/**
 * Náhled videa v seznamu. Vytahuje se rovnou zmenšený snímek – getFrameAtTime by
 * vrátil plné rozlišení, takže jeden řádek s HD videem by v paměti držel přes 4 MB.
 */
private fun frameOf(file: File): Bitmap? = runCatching {
    // MediaMetadataRetriever je AutoCloseable až od API 29, proto ručně
    val r = MediaMetadataRetriever()
    try {
        r.setDataSource(file.absolutePath)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            r.getScaledFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 320, 320)
        } else {
            r.getFrameAtTime(0)
        }
    } finally {
        r.release()
    }
}.getOrNull()

/** Česky se počítá jinak: 1 video, 2–4 videa, 5 a víc videí. */
private fun videoCount(n: Int): String = when {
    n == 1 -> "1 video"
    n in 2..4 -> "$n videa"
    else -> "$n videí"
}

private fun dateOf(millis: Long): String =
    SimpleDateFormat("d. M. HH:mm", Locale.getDefault()).format(Date(millis))
