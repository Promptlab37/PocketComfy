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
    smazane: VideoItem? = null,
    onUndo: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var filtr by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("vse") }
    var hledani by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("") }
    val zobrazene = remember(items, filtr, hledani) {
        items
            .filter {
                when (filtr) {
                    "video" -> !it.isImage && !it.isAudio
                    "obrazek" -> it.isImage
                    "hudba" -> it.isAudio
                    else -> true
                }
            }
            .filter { hledani.isBlank() || it.prompt.contains(hledani.trim(), ignoreCase = true) }
    }
    // Filtr má smysl, až když je v galerii víc druhů výsledků.
    val druhu = remember(items) {
        listOf(
            items.any { !it.isImage && !it.isAudio },
            items.any { it.isImage },
            items.any { it.isAudio },
        ).count { it }
    }

    Box(modifier.fillMaxSize()) {
        if (items.isEmpty()) {
            Column(
                Modifier
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
        } else LazyColumn(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (druhu > 1) item(key = "filtr") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FiltrChip("Vše", filtr == "vse") { filtr = "vse" }
                    FiltrChip("Videa", filtr == "video") { filtr = "video" }
                    FiltrChip("Obrázky", filtr == "obrazek") { filtr = "obrazek" }
                    FiltrChip("Hudba", filtr == "hudba") { filtr = "hudba" }
                }
            }
            if (items.size >= 6) item(key = "hledani") {
                DarkTextField(
                    value = hledani,
                    onValueChange = { hledani = it },
                    placeholder = "Hledat v popisech…",
                    minHeight = 48.dp,
                    singleLine = true,
                    onClear = { hledani = "" },
                )
            }
            item(key = "pocet") {
                // Zelená fajfka u videa znamená, že kopie je i v telefonu; zbytek žije
                // jen tady v aplikaci, dokud si ho nestáhneš.
                Text(
                    "${polozkyCount(zobrazene.size)} · ${"%.1f".format(totalBytes / 1_048_576f)} MB",
                    style = MaterialTheme.typography.bodySmall, color = TextLow
                )
            }
            if (zobrazene.isEmpty()) item(key = "nic") {
                Text(
                    "Tomuhle filtru nic neodpovídá.",
                    style = MaterialTheme.typography.bodySmall, color = TextLow,
                    modifier = Modifier.padding(vertical = 20.dp)
                )
            }
            items(zobrazene, key = { it.id }) { item ->
                HistoryRow(item, onOpen = { onOpen(item) }, onDelete = { onDelete(item) })
            }
            item { Spacer(Modifier.height(if (smazane != null) 64.dp else 16.dp)) }
        }

        // Lišta Vrátit: pár vteřin po smazání jde položku vytáhnout z koše.
        if (smazane != null) {
            Row(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 12.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Surface2)
                    .border(1.dp, Outline1, RoundedCornerShape(14.dp))
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Smazáno", style = MaterialTheme.typography.bodyMedium, color = TextMid)
                Spacer(Modifier.width(16.dp))
                Text(
                    "Vrátit",
                    style = MaterialTheme.typography.bodyMedium, color = Cyan,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onUndo)
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
        }
    }
}

@Composable
private fun FiltrChip(text: String, vybrano: Boolean, onClick: () -> Unit) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = if (vybrano) Cyan else TextMid,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (vybrano) Cyan.copy(alpha = .14f) else Surface1)
            .border(1.dp, if (vybrano) Cyan.copy(alpha = .45f) else Outline1, RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp)
    )
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
                // Zmenšený náhled – plné PNG (klidně gigapixel ze Zvětšit)
                // by na dlaždici sežralo desítky MB a seznam by cukal.
                item.isImage -> cz.promptlab.h3video.util.ImageUtils.loadFileThumb(item.file(ctx))
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

/** Česky se počítá jinak: 1 položka, 2–4 položky, 5 a víc položek. */
private fun polozkyCount(n: Int): String = when {
    n == 1 -> "1 položka"
    n in 2..4 -> "$n položky"
    else -> "$n položek"
}

private fun dateOf(millis: Long): String =
    SimpleDateFormat("d. M. HH:mm", Locale.getDefault()).format(Date(millis))
