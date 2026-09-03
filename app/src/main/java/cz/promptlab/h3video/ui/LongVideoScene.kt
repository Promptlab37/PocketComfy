package cz.promptlab.h3video.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cz.promptlab.h3video.MainViewModel
import cz.promptlab.h3video.comfy.LongVideoBuilder
import cz.promptlab.h3video.data.LongScene
import cz.promptlab.h3video.data.LongStart
import cz.promptlab.h3video.data.longHints
import cz.promptlab.h3video.data.t
import cz.promptlab.h3video.ui.theme.Amber
import cz.promptlab.h3video.ui.theme.Cyan
import cz.promptlab.h3video.ui.theme.Outline1
import cz.promptlab.h3video.ui.theme.Surface2
import cz.promptlab.h3video.ui.theme.TextLow
import cz.promptlab.h3video.ui.theme.TextMid
import kotlin.math.roundToInt

/**
 * Karta **Dlouhé video** — řetěz až šesti navazujících úseků v jednom běhu.
 *
 * Úsek má vlastní zadání, délku i LoRA. To je celý smysl karty: jinak by
 * stačilo napsat jeden popis a nechat model generovat delší záběr, jenže na
 * ten model není trénovaný. Tady se místo toho poskládá víc krátkých záběrů,
 * které si předávají kontext — a v hotovém MP4 mezi nimi není střih.
 */
@Composable
fun LongVideoSection(vm: MainViewModel) {
    val scene by vm.long.collectAsStateWithLifecycle()

    SectionCard(
        title = t("Odkud začít"),
        subtitle = t("Buď se naváže na hotové video, nebo se první záběr vyrobí")
    ) {
        Column {
            PillRow(
                items = LongStart.entries.toList(),
                selected = scene.zacatek,
                label = { it.nazev },
                onSelect = { vm.setLongStart(it) },
            )
            Spacer(Modifier.height(8.dp))
            Text(scene.zacatek.popis, style = MaterialTheme.typography.bodySmall, color = TextLow)
        }
    }

    if (scene.zacatek == LongStart.EXISTING_VIDEO) {
        SectionCard(
            title = t("Video, na které se navazuje"),
            subtitle = t("Jeho konec se použije jako kontext prvního úseku")
        ) {
            ZdrojoveVideo(vm, scene)
        }
    } else {
        SectionCard(
            title = t("První záběr"),
            subtitle = t("Vznikne z popisu a všechny úseky pak navazují na něj")
        ) {
            Column {
                DarkTextField(
                    value = scene.startPrompt,
                    onValueChange = { vm.setLongStartPrompt(it) },
                    placeholder = "A man walks into an empty warehouse, cinematic, static camera",
                    minHeight = 110.dp,
                    onClear = { vm.setLongStartPrompt("") },
                )
                Spacer(Modifier.height(8.dp))
                DelkaRadek(scene.startSeconds) { vm.setLongStartSeconds(it) }

                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            t("Rychlý první záběr"),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            t("Spočítá se na pětině plochy a latent se neuronově zvětší. " +
                                "Znatelně rychlejší, o kus měkčí."),
                            style = MaterialTheme.typography.bodySmall, color = TextLow
                        )
                    }
                    Switch(
                        checked = scene.rychlyZacatek,
                        onCheckedChange = { vm.setLongRychlyZacatek(it) },
                        colors = switchColors(),
                    )
                }
                if (scene.rychlyZacatek) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        t("Potřebuje balík Comfyui_Minimax_h3_latent_Upscaler a jeho model " +
                            "ve složce models/latent_upscale_models."),
                        style = MaterialTheme.typography.bodySmall, color = Amber
                    )
                }
            }
        }
    }

    SectionCard(
        title = t("Reference"),
        subtitle = t("Nepovinné — drží podobu postav a věcí ve všech úsecích")
    ) {
        Column {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                scene.refs.forEach { slot ->
                    RefDlazdice(
                        thumb = slot.thumb,
                        onPick = { uri -> vm.pickLongRef(slot.key, uri) },
                        onRemove = { vm.removeLongRef(slot.key) },
                    )
                }
                if (scene.refs.size < 6) {
                    Box(
                        Modifier
                            .size(72.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Surface2)
                            .border(1.dp, Outline1, RoundedCornerShape(12.dp))
                            .clickable { vm.addLongRef() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Add, t("Přidat referenci"), Modifier.size(22.dp), TextMid)
                    }
                }
            }
        }
    }

    SectionCard(
        title = t("Úseky"),
        subtitle = t("Každý je vlastní záběr — navazují na sebe v tomhle pořadí"),
        trailing = {
            Text(
                "%.0f s".format(scene.odhadSekund),
                style = MaterialTheme.typography.headlineSmall, color = Cyan
            )
        }
    ) {
        Column {
            scene.useky.forEachIndexed { idx, usek ->
                if (idx > 0) Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        t("Úsek %d").format(idx + 1),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (usek.prompt.isBlank()) TextLow else Cyan,
                        modifier = Modifier.weight(1f),
                    )
                    if (scene.useky.size > 1) {
                        Icon(
                            Icons.Default.Close, t("Odebrat úsek"),
                            Modifier
                                .size(20.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { vm.removeLongUsek(usek.key) },
                            TextMid
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                DarkTextField(
                    value = usek.prompt,
                    onValueChange = { vm.setLongUsekPrompt(usek.key, it) },
                    placeholder = if (idx == 0)
                        "Continue the scene — he turns towards the camera"
                    else "Continue the existing scene from the previous generation",
                    minHeight = 96.dp,
                    onClear = { vm.setLongUsekPrompt(usek.key, "") },
                )
                Spacer(Modifier.height(6.dp))
                DelkaRadek(usek.seconds) { vm.setLongUsekSeconds(usek.key, it) }
            }

            if (scene.canAddUsek) {
                Spacer(Modifier.height(14.dp))
                OutlineButton(
                    t("Přidat úsek (max %d)").format(LongVideoBuilder.MAX_USEKU),
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { vm.addLongUsek() },
                )
            }

            longHints(scene).forEach { hint ->
                Spacer(Modifier.height(8.dp))
                Text(hint, style = MaterialTheme.typography.bodySmall, color = Amber)
            }
        }
    }
}

/** Posuvník délky jednoho záběru. Model je trénovaný zhruba na 5–15 s. */
@Composable
private fun DelkaRadek(seconds: Float, onChange: (Float) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(t("Délka"), style = MaterialTheme.typography.labelMedium, color = TextLow)
        Slider(
            value = seconds,
            onValueChange = { onChange(it.roundToInt().toFloat()) },
            valueRange = 5f..15f,
            steps = 9,
            modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
            colors = sliderColors(),
        )
        Text(
            "%.0f s".format(seconds),
            style = MaterialTheme.typography.labelMedium, color = TextMid,
            modifier = Modifier.width(38.dp),
        )
    }
}

@Composable
private fun ZdrojoveVideo(vm: MainViewModel, scene: LongScene) {
    val pick = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> vm.pickLongVideo(uri) }
    val videoOnly = PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)

    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Surface2)
            .border(1.dp, Outline1, RoundedCornerShape(12.dp))
            .clickable { pick.launch(videoOnly) }
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.Movie, null, Modifier.size(22.dp), if (scene.sourceVideo != null) Cyan else TextMid)
        Spacer(Modifier.width(12.dp))
        Text(
            scene.sourceVideo?.name ?: t("Vybrat video z galerie"),
            style = MaterialTheme.typography.bodyMedium,
            color = if (scene.sourceVideo != null) TextMid else TextLow,
            modifier = Modifier.weight(1f),
        )
        if (scene.sourceVideo != null) {
            Icon(
                Icons.Default.Close, t("Odebrat"),
                Modifier
                    .size(20.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .clickable { vm.clearLongVideo() },
                TextMid
            )
        }
    }
}

@Composable
private fun RefDlazdice(
    thumb: android.graphics.Bitmap?,
    onPick: (android.net.Uri?) -> Unit,
    onRemove: () -> Unit,
) {
    val pick = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> onPick(uri) }
    val imageOnly = PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)

    Box(
        Modifier
            .size(72.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Surface2)
            .border(1.dp, Outline1, RoundedCornerShape(12.dp))
            .clickable { pick.launch(imageOnly) }
    ) {
        if (thumb != null) {
            Image(
                bitmap = thumb.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().aspectRatio(1f),
            )
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(3.dp)
                    .size(20.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.Black.copy(alpha = .55f))
                    .clickable { onRemove() },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Close, null, Modifier.size(12.dp), TextMid)
            }
        } else {
            Icon(
                Icons.Default.AddPhotoAlternate, null,
                Modifier.align(Alignment.Center).size(22.dp), TextMid
            )
        }
    }
}
