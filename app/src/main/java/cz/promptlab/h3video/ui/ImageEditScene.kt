package cz.promptlab.h3video.ui

import cz.promptlab.h3video.data.t

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cz.promptlab.h3video.MainViewModel
import cz.promptlab.h3video.data.Aspect
import cz.promptlab.h3video.data.ImageEditScene
import cz.promptlab.h3video.ui.theme.Cyan
import cz.promptlab.h3video.ui.theme.Outline1
import cz.promptlab.h3video.ui.theme.Surface2
import cz.promptlab.h3video.ui.theme.TextLow
import cz.promptlab.h3video.ui.theme.TextMid
import kotlin.math.roundToInt

/**
 * Karta **Úprava obrázku** — Krea 2 Turbo + LoRA Krea 2 Identity Edit.
 *
 * Jediná karta, která nevyrábí video. Vezme fotku a upraví ji podle věty;
 * obličej z předlohy má zůstat.
 */
@Composable
fun ImageEditSection(vm: MainViewModel) {
    val scene by vm.edit.collectAsStateWithLifecycle()

    var pickFor by remember { mutableStateOf<String?>(null) }
    val imageOnly = PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
    val pick = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> pickFor?.let { vm.pickEditImage(it, uri) }; pickFor = null }

    SectionCard(
        title = t("Fotka k úpravě"),
        subtitle = t("Z ní se bere podoba i scéna")
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            EditSlot(
                thumb = scene.thumb,
                popisek = t("Upravovaná fotka"),
                modifier = Modifier.weight(1f),
                onPick = { pickFor = "source"; pick.launch(imageOnly) },
                onClear = { vm.clearEditImage("source") },
            )
            EditSlot(
                thumb = scene.personThumb,
                popisek = t("Osoba navíc (nepovinné)"),
                modifier = Modifier.weight(1f),
                onPick = { pickFor = "person"; pick.launch(imageOnly) },
                onClear = { vm.clearEditImage("person") },
            )
        }
    }

    SectionCard(
        title = t("Co se má změnit"),
        subtitle = t("Napiš to jednoduše, běžnou větou")
    ) {
        DarkTextField(
            value = scene.prompt,
            onValueChange = { vm.setEditPrompt(it) },
            placeholder = t("Dej jí červenou bundu a přesaď je na zasněženou horskou cestu"),
            minHeight = 110.dp,
            onClear = { vm.setEditPrompt("") },
        )
    }

    // Rozlišení a jemné páčky nikdo nemění při každém běhu – jsou sbalené,
    // ať na obrazovce zbyde jen fotka, zadání a tlačítko.
    SkladaciSekce(
        title = t("Nastavení úpravy"),
        souhrn = scene.resolution.label + " · vidí " + scene.groundingPx + " px" +
            " · věrnost %.2f".format(scene.refBoost),
        klic = "nastaveni-edit",
    ) {
        SectionCard(
            title = t("Rozlišení"),
            subtitle = t("Kolem 1 MP je u tohohle modelu nejjistější"),
            trailing = {
                Text(
                    scene.resolution.label,
                    style = MaterialTheme.typography.titleMedium, color = Cyan
                )
            }
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Column {
                    Text(t("Poměr stran"), style = MaterialTheme.typography.labelMedium, color = TextLow)
                    Spacer(Modifier.height(8.dp))
                    PillRow(
                        items = listOf(
                            Aspect.SQUARE_1_1, Aspect.PORTRAIT_9_16, Aspect.LANDSCAPE_16_9,
                            Aspect.PORTRAIT_3_4, Aspect.LANDSCAPE_4_3,
                        ),
                        selected = scene.aspect,
                        label = { it.label },
                        onSelect = { vm.setEditAspect(it) },
                    )
                }
                LabeledSlider(
                    label = "Velikost",
                    value = "%.1f MP".format(scene.megapixels),
                    position = scene.megapixels,
                    range = 0.4f..ImageEditScene.MAX_MEGAPIXELS,
                    onChange = { vm.setEditMegapixels((it * 10).roundToInt() / 10f) },
                    note = t("Nad 1 MP se u dvou lidí začíná rozpadat podoba."),
                )
            }
        }

        SectionCard(
            title = t("Síla úpravy"),
            subtitle = t("Kompromis mezi poslušností zadání a věrností obličeje")
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                LabeledSlider(
                    label = t("Vidění předlohy"),
                    value = "${scene.groundingPx} px",
                    position = scene.groundingPx.toFloat(),
                    range = ImageEditScene.MIN_GROUNDING.toFloat()..
                        ImageEditScene.MAX_GROUNDING.toFloat(),
                    onChange = { vm.setEditGrounding((it / 64).roundToInt() * 64) },
                    note = t("Víc = věrnější podoba, míň = poslušnější úprava. Na lidi dej 1024."),
                )
                LabeledSlider(
                    label = t("Věrnost předloze"),
                    value = "%.2f".format(scene.refBoost),
                    position = scene.refBoost,
                    range = 0.5f..3f,
                    onChange = { vm.setEditRefBoost((it * 100).roundToInt() / 100f) },
                    note = t("1,00 je vypnuto. Na věrné obličeje zkus 1,5–2."),
                )
            }
        }
    }
}

@Composable
private fun EditSlot(
    thumb: android.graphics.Bitmap?,
    popisek: String,
    modifier: Modifier = Modifier,
    onPick: () -> Unit,
    onClear: () -> Unit,
) {
    Column(modifier) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(14.dp))
                .background(Surface2)
                .border(1.dp, Outline1, RoundedCornerShape(14.dp))
                .clickable { onPick() }
        ) {
            if (thumb != null) {
                Image(
                    bitmap = thumb.asImageBitmap(),
                    contentDescription = popisek,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(28.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Surface2)
                        .clickable { onClear() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Close, "Odebrat", Modifier.size(16.dp), TextMid)
                }
            } else {
                Icon(
                    Icons.Default.AddPhotoAlternate, popisek,
                    Modifier.align(Alignment.Center).size(30.dp), TextMid
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(popisek, style = MaterialTheme.typography.bodySmall, color = TextLow)
    }
}
