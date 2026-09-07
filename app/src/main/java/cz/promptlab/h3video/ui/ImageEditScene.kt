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
import cz.promptlab.h3video.ui.theme.Amber
import cz.promptlab.h3video.data.EditMotor
import cz.promptlab.h3video.data.EditZamer
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
        Column {
            DarkTextField(
                value = scene.prompt,
                onValueChange = { vm.setEditPrompt(it) },
                placeholder = t("Dej jí červenou bundu a přesaď je na zasněženou horskou cestu"),
                minHeight = 110.dp,
                onClear = { vm.setEditPrompt("") },
            )
            Spacer(Modifier.height(10.dp))
            PrekladPromptu(vm, cz.promptlab.h3video.MainViewModel.PromptPole.UPRAVA)
        }
    }

    SectionCard(
        title = t("Čím upravit"),
        subtitle = t("Dva editační modely, každý jinak postavený")
    ) {
        Column {
            PillRow(
                items = EditMotor.entries.toList(),
                selected = scene.motor,
                label = { it.nazev },
                onSelect = { vm.setEditMotor(it) },
            )
            Spacer(Modifier.height(6.dp))
            Text(
                scene.motor.popis,
                style = MaterialTheme.typography.bodySmall, color = TextLow,
            )
        }
    }

    if (scene.motor == EditMotor.QWEN) SectionCard(
        title = t("Rychlost proti kvalitě"),
        subtitle = t("Obojí má oficiální předloha, liší se počtem kroků")
    ) {
        Column {
            PillRow(
                items = listOf(true, false),
                selected = scene.qwenRychle,
                label = { if (it) t("Rychle — 4 kroky") else t("Kvalitně — 40 kroků") },
                onSelect = { vm.setEditQwenRychle(it) },
            )
            Spacer(Modifier.height(6.dp))
            Text(
                if (scene.qwenRychle) {
                    t("Zrychlovací LoRA. Na běžné úpravy stačí a je to řádově rychlejší.")
                } else {
                    t("Bez zrychlovací LoRA a se skutečným cfg. Poslouchá zadání nejlíp, ale trvá to.")
                },
                style = MaterialTheme.typography.bodySmall, color = TextLow,
            )
        }
    }

    // Rozlišení a jemné páčky nikdo nemění při každém běhu – jsou sbalené,
    // ať na obrazovce zbyde jen fotka, zadání a tlačítko.
    SkladaciSekce(
        title = t("Nastavení úpravy"),
        // Klein páčky na věrnost ani vidění předlohy nemá — vypisovat je
        // v souhrnu by tvrdilo, že něco dělají.
        souhrn = if (scene.motor != EditMotor.KREA2) {
            t("rozměry podle předlohy")
        } else {
            scene.resolution.label + " · vidí " + scene.groundingPx + " px" +
                " · věrnost %.2f".format(scene.refBoost)
        },
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

        // Vidění předlohy i věrnost jsou páčky Krea 2 (Krea2EditGroundedEncode
        // a fidelity dial). Klein je nemá — karta je u něj proto neukazuje.
        if (scene.motor == EditMotor.KREA2) SectionCard(
            title = t("Síla úpravy"),
            subtitle = t("Kompromis mezi poslušností zadání a věrností obličeje")
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                // Tři páčky pod tím táhnou stejným směrem, takže se nastavují
                // najednou. Kdo si je pak dolaďuje ručně, uvidí „Vlastní".
                PillRow(
                    items = EditZamer.entries.toList(),
                    selected = scene.zamer ?: EditZamer.VYVAZENE,
                    label = { it.nazev },
                    onSelect = { vm.setEditZamer(it) },
                )
                Text(
                    (scene.zamer ?: EditZamer.VYVAZENE).popis,
                    style = MaterialTheme.typography.bodySmall, color = TextLow,
                )
                if (scene.zamer == null) {
                    Text(
                        t("Vlastní nastavení páček."),
                        style = MaterialTheme.typography.bodySmall, color = Amber,
                    )
                }
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
                LabeledSlider(
                    label = t("Zámek totožnosti"),
                    value = "%.2f".format(scene.loraSila),
                    position = scene.loraSila,
                    range = 0f..1f,
                    onChange = { vm.setEditLoraSila((it * 100).roundToInt() / 100f) },
                    note = t("Síla LoRA, která drží obličej. Na plné síle model přejde i jasné zadání."),
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
