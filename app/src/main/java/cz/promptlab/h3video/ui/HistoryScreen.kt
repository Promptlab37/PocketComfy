package cz.promptlab.h3video.ui

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cz.promptlab.h3video.data.*
import cz.promptlab.h3video.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistoryScreen(
    items: List<VideoItem>, totalBytes: Long,
    onOpen: (VideoItem) -> Unit, onDelete: (VideoItem) -> Unit,
    onFavorite: (VideoItem) -> Unit, onRename: (VideoItem, String) -> Unit,
    onCreate: () -> Unit,
    smazane: VideoItem? = null, onUndo: () -> Unit = {}, modifier: Modifier = Modifier,
) {
    var kind by rememberSaveable { mutableStateOf<MediaKind?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var favoritesOnly by rememberSaveable { mutableStateOf(false) }
    var order by rememberSaveable { mutableStateOf(HistoryOrder.NEWEST) }
    var grid by rememberSaveable { mutableStateOf(true) }
    var sorting by remember { mutableStateOf(false) }
    var renameId by rememberSaveable { mutableStateOf<String?>(null) }
    var deleteId by rememberSaveable { mutableStateOf<String?>(null) }
    val visible = remember(items, kind, query, favoritesOnly, order) {
        filterHistory(items, query, kind, favoritesOnly, order)
    }
    val counts = remember(items) { items.groupingBy { it.mediaKind }.eachCount() }
    val favorites = remember(items) { items.count { it.favorite } }
    val listState = rememberLazyGridState()
    var previousFilter by rememberSaveable { mutableStateOf("") }
    val currentFilter = "$kind|$query|$favoritesOnly|$order|$grid"
    LaunchedEffect(currentFilter) {
        if (previousFilter != currentFilter) listState.scrollToItem(0)
        previousFilter = currentFilter
    }

    Box(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(t("Knihovna obsahu"), style = MaterialTheme.typography.headlineSmall, color = TextHi)
                Text("${polozkyCount(items.size)} · ${formatStorage(totalBytes)} ${t("v aplikaci")}",
                    style = MaterialTheme.typography.bodySmall, color = TextMid)
                if (items.isNotEmpty()) {
                    DarkTextField(value = query, onValueChange = { query = it },
                        placeholder = t("Hledat název, zadání nebo seed…"),
                        minHeight = 48.dp, singleLine = true, onClear = { query = "" })
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = kind == null, onClick = { kind = null }, label = { Text(t("Vše")) })
                        MediaKind.entries.filter { (counts[it] ?: 0) > 0 || kind == it }.forEach { media ->
                            FilterChip(selected = kind == media, onClick = { kind = media },
                                label = { Text("${media.label()} · ${counts[media] ?: 0}") })
                        }
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        FilterChip(selected = favoritesOnly, onClick = { favoritesOnly = !favoritesOnly },
                            label = { Text("${t("Oblíbené")} · $favorites") },
                            leadingIcon = { Icon(Icons.Default.Star, null, Modifier.size(16.dp)) })
                        Spacer(Modifier.weight(1f))
                        Box {
                            IconButton(onClick = { sorting = true }) {
                                Icon(Icons.AutoMirrored.Filled.Sort, t("Řazení") + ": " + order.label(), tint = TextMid)
                            }
                            DropdownMenu(expanded = sorting, onDismissRequest = { sorting = false }) {
                                HistoryOrder.entries.forEach { value ->
                                    DropdownMenuItem(text = { Text(value.label()) },
                                        onClick = { order = value; sorting = false },
                                        trailingIcon = { if (order == value) Icon(Icons.Default.Check, null, tint = Cyan) })
                                }
                            }
                        }
                        IconButton(onClick = { grid = !grid }) {
                            Icon(if (grid) Icons.AutoMirrored.Filled.ViewList else Icons.Default.GridView,
                                if (grid) t("Zobrazit seznam") else t("Zobrazit mřížku"), tint = TextMid)
                        }
                    }
                    Text("${polozkyCount(visible.size)} · ${order.label()}", color = TextLow,
                        style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(bottom = 8.dp))
                }
            }
            if (items.isEmpty()) {
                LibraryEmpty(Icons.Default.AutoAwesome, t("Prostor pro vaše nápady"),
                    t("Videa, obrázky, hudba i 3D modely na jednom místě. Hotové výstupy tu zůstanou dostupné i bez serveru."),
                    t("Vytvořit první obsah"), onCreate, Modifier.weight(1f))
            } else if (visible.isEmpty()) {
                LibraryEmpty(
                    if (favoritesOnly && query.isBlank()) Icons.Default.StarOutline else Icons.Default.SearchOff,
                    if (favoritesOnly && favorites == 0) t("Vyberte si to nejlepší") else t("Žádné odpovídající výstupy"),
                    if (favoritesOnly && favorites == 0) t("Označte výstupy hvězdičkou a budete je mít vždy po ruce.")
                    else t("Zkuste jiné hledání nebo zrušte filtry."),
                    t("Zobrazit vše"), { query = ""; kind = null; favoritesOnly = false }, Modifier.weight(1f))
            } else {
                LazyVerticalGrid(
                    columns = if (grid) GridCells.Adaptive(160.dp) else GridCells.Fixed(1),
                    state = listState, modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = if (smazane != null) 88.dp else 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(visible, key = { it.id }, contentType = { "asset" }) { item ->
                        AssetCard(item, grid, { onOpen(item) }, { onFavorite(item) },
                            { renameId = item.id }, { deleteId = item.id })
                    }
                }
            }
        }
        if (smazane != null) {
            Snackbar(modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp),
                containerColor = Surface2, contentColor = TextHi,
                action = { TextButton(onClick = onUndo) { Text(t("Vrátit"), color = Cyan) } },
            ) { Text(t("Výstup odstraněn")) }
        }
    }
    items.firstOrNull { it.id == renameId }?.let { item ->
        RenameResultDialog(item, { renameId = null }, { onRename(item, it); renameId = null })
    }
    items.firstOrNull { it.id == deleteId }?.let { item ->
        AlertDialog(onDismissRequest = { deleteId = null },
            icon = { Icon(Icons.Default.DeleteOutline, null, tint = Danger) },
            title = { Text(t("Odstranit výstup?")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(item.displayTitle, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    Text(t("Odstraní se kopie v aplikaci. Soubory uložené do telefonu zůstanou zachované."))
                }
            },
            confirmButton = { TextButton(onClick = { onDelete(item); deleteId = null }) { Text(t("Odstranit"), color = Danger) } },
            dismissButton = { TextButton(onClick = { deleteId = null }) { Text(t("Zrušit")) } })
    }
}

@Composable
private fun AssetCard(item: VideoItem, grid: Boolean, onOpen: () -> Unit, onFavorite: () -> Unit,
    onRename: () -> Unit, onDelete: () -> Unit,
) {
    Surface(shape = RoundedCornerShape(18.dp), color = Surface1,
        border = androidx.compose.foundation.BorderStroke(1.dp, Outline1)) {
        if (grid) {
            Column {
                AssetThumbnail(item, Modifier.fillMaxWidth().aspectRatio(1.5f).clickable(onClick = onOpen))
                Column(Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(start = 12.dp, end = 12.dp, top = 10.dp)) {
                    Text(item.displayTitle, color = TextHi, style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2, minLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(assetDetails(item), color = TextMid, style = MaterialTheme.typography.bodySmall,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
                }
                Row(Modifier.fillMaxWidth().padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(dateOf(item.createdAt), Modifier.weight(1f), style = MaterialTheme.typography.labelSmall,
                        color = TextLow, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    FavoriteButton(item, onFavorite)
                    AssetMenu(item, onRename, onDelete)
                }
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AssetThumbnail(item, Modifier.padding(start = 10.dp).size(76.dp).clip(RoundedCornerShape(10.dp)).clickable(onClick = onOpen))
                Column(Modifier.weight(1f).clickable(onClick = onOpen).padding(start = 12.dp, top = 12.dp, bottom = 12.dp)) {
                    Text(item.displayTitle, color = TextHi, style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(assetDetails(item), color = TextMid, style = MaterialTheme.typography.bodySmall,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(dateOf(item.createdAt), color = TextLow, style = MaterialTheme.typography.labelSmall)
                }
                Column { FavoriteButton(item, onFavorite); AssetMenu(item, onRename, onDelete) }
            }
        }
    }
}

@Composable
internal fun FavoriteButton(item: VideoItem, onClick: () -> Unit) {
    IconToggleButton(checked = item.favorite, onCheckedChange = { onClick() }) {
        Icon(if (item.favorite) Icons.Default.Star else Icons.Default.StarOutline,
            if (item.favorite) t("Odebrat z oblíbených") else t("Přidat do oblíbených"),
            tint = if (item.favorite) Amber else TextMid, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun AssetMenu(item: VideoItem, onRename: () -> Unit, onDelete: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val ctx = LocalContext.current
    Box {
        IconButton(onClick = { expanded = true }) { Icon(Icons.Default.MoreVert, t("Akce výstupu"), tint = TextMid) }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text(t("Přejmenovat")) }, leadingIcon = { Icon(Icons.Default.Edit, null) },
                onClick = { expanded = false; onRename() })
            DropdownMenuItem(text = { Text(t("Kopírovat zadání")) }, enabled = item.prompt.isNotBlank(),
                leadingIcon = { Icon(Icons.Default.ContentCopy, null) },
                onClick = { expanded = false; copyResultText(ctx, item.prompt) })
            DropdownMenuItem(text = { Text(t("Kopírovat seed")) }, leadingIcon = { Icon(Icons.Default.Tag, null) },
                onClick = { expanded = false; copyResultText(ctx, item.seed.toString()) })
            HorizontalDivider()
            DropdownMenuItem(text = { Text(t("Odstranit"), color = Danger) },
                leadingIcon = { Icon(Icons.Default.DeleteOutline, null, tint = Danger) },
                onClick = { expanded = false; onDelete() })
        }
    }
}

@Composable
private fun AssetThumbnail(item: VideoItem, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    var thumb by remember(item.id, item.fileName) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(item.id, item.fileName) {
        thumb = withContext(Dispatchers.IO) {
            runCatching {
                when {
                    item.isAudio -> null
                    item.isModel3d -> cz.promptlab.h3video.util.ImageUtils.nahled3d(ctx, item.file(ctx))
                        .takeIf { it.isFile && it.length() > 0 }?.let { cz.promptlab.h3video.util.ImageUtils.loadFileThumb(it) }
                    item.isImage -> cz.promptlab.h3video.util.ImageUtils.loadFileThumb(item.file(ctx))
                    else -> frameOf(item.file(ctx))
                }
            }.getOrNull()
        }
    }
    Box(modifier.background(Surface2), contentAlignment = Alignment.Center) {
        val bitmap = thumb
        if (bitmap != null) Image(bitmap.asImageBitmap(), item.displayTitle, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else Icon(item.mediaKind.icon(), item.mediaKind.label(), Modifier.size(32.dp),
            when (item.mediaKind) { MediaKind.AUDIO -> Rose; MediaKind.MODEL3D -> Cyan; else -> TextMid })
        if (item.mediaKind == MediaKind.VIDEO && bitmap != null) {
            Surface(shape = RoundedCornerShape(50), color = Color.Black.copy(alpha = .55f)) {
                Icon(Icons.Default.PlayArrow, t("Přehrát"), Modifier.padding(6.dp).size(22.dp), Color.White)
            }
        }
        if (item.inGallery) Icon(Icons.Default.CheckCircle, t("Uloženo v telefonu"),
            Modifier.align(Alignment.BottomStart).padding(6.dp).background(Ink, RoundedCornerShape(50)).size(18.dp), Ok)
    }
}

@Composable
private fun LibraryEmpty(icon: ImageVector, title: String, detail: String, action: String,
    onAction: () -> Unit, modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(shape = RoundedCornerShape(24.dp), color = Violet.copy(alpha = .12f)) {
            Icon(icon, null, Modifier.padding(20.dp).size(36.dp), Cyan)
        }
        Spacer(Modifier.height(20.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center, color = TextHi)
        Spacer(Modifier.height(8.dp))
        Text(detail, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, color = TextMid)
        Spacer(Modifier.height(20.dp))
        OutlineButton(action, color = Cyan, onClick = onAction)
    }
}

private fun assetDetails(item: VideoItem): String = listOfNotNull(
    item.fileName.substringAfterLast('.', "").uppercase().ifBlank { null },
    item.resolution.takeIf { it.isNotBlank() && !item.isAudio && !item.isModel3d },
    if (item.seconds > 0 && !item.isImage && !item.isModel3d) "%.1f s".format(item.seconds) else null,
).joinToString(" · ")

private fun MediaKind.label(): String = t(when (this) {
    MediaKind.VIDEO -> "Videa"; MediaKind.IMAGE -> "Obrázky"; MediaKind.AUDIO -> "Hudba"; MediaKind.MODEL3D -> "3D modely"
})
private fun MediaKind.icon(): ImageVector = when (this) {
    MediaKind.VIDEO -> Icons.Default.Movie; MediaKind.IMAGE -> Icons.Default.Image
    MediaKind.AUDIO -> Icons.Default.MusicNote; MediaKind.MODEL3D -> Icons.Default.ViewInAr
}
private fun HistoryOrder.label(): String = t(when (this) {
    HistoryOrder.NEWEST -> "Nejnovější"; HistoryOrder.OLDEST -> "Nejstarší"; HistoryOrder.NAME -> "Podle názvu"
})
private fun formatStorage(bytes: Long): String = when {
    bytes >= 1_073_741_824L -> "%.1f GB".format(bytes / 1_073_741_824.0)
    bytes >= 1_048_576L -> "%.1f MB".format(bytes / 1_048_576.0)
    else -> "%.1f KB".format(bytes / 1024.0)
}

private fun frameOf(file: File): Bitmap? = runCatching {
    val retriever = MediaMetadataRetriever()
    try {
        retriever.setDataSource(file.absolutePath)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1)
            retriever.getScaledFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 400, 400)
        else retriever.getFrameAtTime(0)?.let { original ->
            val scale = minOf(1f, 400f / maxOf(original.width, original.height))
            Bitmap.createScaledBitmap(original, (original.width * scale).toInt().coerceAtLeast(1),
                (original.height * scale).toInt().coerceAtLeast(1), true).also { if (it !== original) original.recycle() }
        }
    } finally { retriever.release() }
}.getOrNull()

private fun polozkyCount(n: Int): String = when {
    Jazyk.anglicky -> if (n == 1) "1 item" else "$n items"
    n == 1 -> "1 položka"; n in 2..4 -> "$n položky"; else -> "$n položek"
}
private fun dateOf(millis: Long): String = SimpleDateFormat(
    if (Jazyk.anglicky) "MMM d, yyyy" else "d. M. yyyy",
    if (Jazyk.anglicky) Locale.ENGLISH else Locale.forLanguageTag("cs"),
).format(Date(millis))
