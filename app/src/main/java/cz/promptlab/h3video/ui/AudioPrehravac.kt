package cz.promptlab.h3video.ui

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import cz.promptlab.h3video.ui.theme.Cyan
import cz.promptlab.h3video.ui.theme.Surface1
import cz.promptlab.h3video.ui.theme.TextHi
import cz.promptlab.h3video.ui.theme.TextLow
import java.io.File

/**
 * Jednoduchý přehrávač skladby: play/pauza, posuvník a časy. Video přehrávač
 * by MP3 zvládl taky, ale ukazoval by jen černý obdélník.
 */
@Composable
fun AudioPrehravac(file: File, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val player = remember(file.path) {
        ExoPlayer.Builder(ctx).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
            prepare()
        }
    }
    DisposableEffect(file.path) { onDispose { player.release() } }

    var hraje by remember(file.path) { mutableStateOf(false) }
    var pozice by remember(file.path) { mutableLongStateOf(0L) }
    var delka by remember(file.path) { mutableLongStateOf(0L) }
    // Při tažení posuvníku se ukazuje prst, ne přehrávač – jinak by posuvník
    // cukal zpátky na starou pozici, dokud nedoběhne seek.
    var tazeni by remember(file.path) { mutableFloatStateOf(-1f) }

    LaunchedEffect(file.path) {
        while (true) {
            if (tazeni < 0f) pozice = player.currentPosition.coerceAtLeast(0L)
            delka = player.duration.coerceAtLeast(0L)
            hraje = player.isPlaying
            kotlinx.coroutines.delay(250)
        }
    }

    fun cas(ms: Long): String {
        val s = (ms / 1000).toInt()
        return "%d:%02d".format(s / 60, s % 60)
    }

    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Surface1)
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (hraje) Icons.Default.Pause else Icons.Default.PlayArrow,
                if (hraje) "Pauza" else "Přehrát",
                Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Cyan.copy(alpha = .16f))
                    .clickable {
                        if (player.isPlaying) player.pause() else {
                            if (player.playbackState == ExoPlayer.STATE_ENDED) player.seekTo(0)
                            player.play()
                        }
                    }
                    .padding(10.dp),
                Cyan
            )
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Slider(
                    value = when {
                        tazeni >= 0f -> tazeni
                        delka > 0L -> pozice.toFloat() / delka
                        else -> 0f
                    },
                    onValueChange = { tazeni = it },
                    onValueChangeFinished = {
                        if (delka > 0L) player.seekTo((tazeni * delka).toLong())
                        pozice = (tazeni * delka).toLong()
                        tazeni = -1f
                    },
                    colors = sliderColors()
                )
                Row {
                    Text(
                        cas(if (tazeni >= 0f && delka > 0) (tazeni * delka).toLong() else pozice),
                        style = MaterialTheme.typography.bodySmall, color = TextHi
                    )
                    Spacer(Modifier.weight(1f))
                    Text(cas(delka), style = MaterialTheme.typography.bodySmall, color = TextLow)
                }
            }
            Spacer(Modifier.size(6.dp))
            Icon(Icons.Default.MusicNote, null, Modifier.size(22.dp), TextLow)
        }
    }
}
