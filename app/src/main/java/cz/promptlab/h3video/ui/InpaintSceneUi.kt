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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cz.promptlab.h3video.MainViewModel
import cz.promptlab.h3video.data.InpaintModel
import cz.promptlab.h3video.data.t
import cz.promptlab.h3video.ui.theme.Cyan
import cz.promptlab.h3video.ui.theme.Ok
import cz.promptlab.h3video.ui.theme.Outline1
import cz.promptlab.h3video.ui.theme.Surface2
import cz.promptlab.h3video.ui.theme.TextLow
import cz.promptlab.h3video.ui.theme.TextMid
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Karta **Domalovat** — fotka, prstem začmáraný kus a věta, co tam má být.
 * Model přepíše jen to pod maskou, zbytek fotky zůstane bajt po bajtu stejný
 * (uzly Inpaint Crop & Stitch vyřežou okolí masky a hotový kus vlepí zpět).
 */
@Composable
fun InpaintSection(vm: MainViewModel) {
    val scene by vm.inpaint.collectAsStateWithLifecycle()

    val imageOnly = PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
    val pick = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> vm.pickInpaintImage(uri) }

    var maluje by remember { mutableStateOf(false) }

    SectionCard(
        title = t("Fotka, do které se maluje"),
        subtitle = if (scene.maskPainted)
            t("Maska je namalovaná — klepnutím na štětec ji předěláš")
        else t("Vyber fotku a pak prstem začmárej místo, které se má přemalovat")
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(4f / 3f)
                .clip(RoundedCornerShape(14.dp))
                .background(Surface2)
                .border(
                    1.dp,
                    if (scene.maskPainted) Ok.copy(alpha = .5f) else Outline1,
                    RoundedCornerShape(14.dp)
                )
                .clickable {
                    if (scene.source == null) pick.launch(imageOnly) else maluje = true
                }
        ) {
            val thumb = scene.thumb
            if (thumb != null) {
                Image(
                    bitmap = thumb.asImageBitmap(),
                    contentDescription = t("Fotka k domalování"),
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
                Row(
                    Modifier.align(Alignment.TopEnd).padding(6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Surface2)
                            .clickable { maluje = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Brush, t("Malovat masku"),
                            Modifier.size(16.dp),
                            if (scene.maskPainted) Ok else Cyan
                        )
                    }
                    Box(
                        Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Surface2)
                            .clickable { vm.clearInpaintImage() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Close, t("Odebrat"), Modifier.size(16.dp), TextMid)
                    }
                }
            } else {
                Icon(
                    Icons.Default.AddPhotoAlternate, t("Vybrat fotku"),
                    Modifier.align(Alignment.Center).size(34.dp), TextMid
                )
            }
        }
    }

    SectionCard(
        title = t("Co má na tom místě být"),
        // Každý model čte zadání jinak: Flux Fill maluje do díry to, co
        // popíšeš, kdežto Klein bere zadání jako příkaz k úpravě — popis
        // typu „muž s břichem" pro něj znamená „nech to tak".
        subtitle = if (scene.model == InpaintModel.KLEIN)
            t("Klein poslouchá příkazy — napiš, co se s tím místem má stát")
        else t("Popiš to jako výsledný obraz, ne jako příkaz")
    ) {
        Column {
            DarkTextField(
                value = scene.prompt,
                onValueChange = { vm.setInpaintPrompt(it) },
                placeholder = if (scene.model == InpaintModel.KLEIN)
                    t("posaď ho na dřevěnou lavičku pod stromem")
                else t("dřevěná lavička pod stromem, dopolední světlo"),
                minHeight = 100.dp,
                onClear = { vm.setInpaintPrompt("") },
            )
            Spacer(Modifier.height(10.dp))
            PrekladPromptu(vm, MainViewModel.PromptPole.DOMALOVAT)
        }
    }

    // Nabídka LoRA se čte ze serveru, ať se nová stažená objeví sama. Sbírá se
    // jako stav — dorazí až po chvíli a karta se na ni musí překreslit.
    LaunchedEffect(Unit) { vm.refreshInpaintLoras() }
    val vsechnyLory by vm.inpaintLoras.collectAsStateWithLifecycle()
    val lory = vm.inpaintLoraNabidka(scene.model, vsechnyLory)

    SkladaciSekce(
        title = t("Model a doladění"),
        souhrn = scene.model.title +
            (if (scene.lora.isNotBlank()) " · LoRA" else "") +
            (if (scene.model == InpaintModel.FILL && scene.sila < 1f)
                " · síla %.2f".format(scene.sila) else ""),
        klic = "nastaveni-inpaint",
    ) {
        SectionCard(
            title = t("Čím domalovat"),
            subtitle = t("Když se výsledek nepovede, zkus druhý model — každý kreslí jinak")
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                PillRow(
                    items = InpaintModel.entries.toList(),
                    selected = scene.model,
                    label = { it.title },
                    onSelect = { vm.setInpaintModel(it) },
                )
                Text(
                    scene.model.detail,
                    style = MaterialTheme.typography.bodySmall, color = TextLow
                )
            }
        }

        // Základní modely mají o některých motivech jen mlhavou představu —
        // hlavně o anatomii. LoRA trénovaná přímo na to je jediné, co s tím
        // spolehlivě pohne; musí ale patřit ke stejné rodině jako model.
        SectionCard(
            title = t("Doplňková LoRA"),
            subtitle = if (lory.isEmpty())
                t("Na serveru není žádná LoRA pro tenhle model")
            else t("Pomůže tam, kde model sám tápe — třeba na anatomii")
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                PillRow(
                    items = listOf("") + lory,
                    selected = scene.lora,
                    label = { if (it.isEmpty()) t("Žádná") else it.substringBeforeLast(".") },
                    onSelect = { vm.setInpaintLora(it) },
                )
                if (scene.lora.isNotBlank()) {
                    LabeledSlider(
                        label = t("Síla LoRA"),
                        value = "%.2f".format(scene.loraSila),
                        position = scene.loraSila,
                        range = 0.2f..1.4f,
                        onChange = { vm.setInpaintLoraSila((it * 20).roundToInt() / 20f) },
                        note = t("Kolem 0,8–1,0 bývá nejjistější; víc už deformuje okolí."),
                    )
                }
            }
        }

        if (scene.model == InpaintModel.FILL) {
            SectionCard(
                title = t("Síla přemalování"),
                subtitle = t("Kolik z původního místa se smí zahodit")
            ) {
                LabeledSlider(
                    label = t("Síla"),
                    value = "%.2f".format(scene.sila),
                    position = scene.sila,
                    range = 0.3f..1f,
                    onChange = { vm.setInpaintSila((it * 20).roundToInt() / 20f) },
                    note = t("1,00 = pod maskou vzniká všechno znovu. Na dokreslení " +
                        "detailu (ne výměnu obsahu) zkus 0,50–0,70 — tvar a póza zůstanou."),
                )
            }
        }
    }

    if (maluje) {
        val file = scene.source
        // Dekódování na pozadí – fotka na 2560 px by při otevření editoru
        // na okamžik zamrazila UI.
        var bmp by remember(file?.path) {
            mutableStateOf<android.graphics.Bitmap?>(null)
        }
        LaunchedEffect(file?.path) {
            bmp = withContext(Dispatchers.IO) {
                file?.let {
                    runCatching { android.graphics.BitmapFactory.decodeFile(it.absolutePath) }
                        .getOrNull()
                }
            }
        }
        bmp?.let { podklad ->
            MaskEditor(
                bitmap = podklad,
                onDone = { vysledek ->
                    vm.ulozInpaintMasku(vysledek)
                    maluje = false
                },
                onClose = { maluje = false },
                titulek = t("Začmárej místo, které se přemaluje"),
                podtitulek = t("Maluj s malým přesahem — okraje se prolnou samy. ") +
                    t("Dvěma prsty přiblížíš na detaily."),
                vyzva = t("Nejdřív začmárej místo"),
            )
        }
    }

    Spacer(Modifier.height(2.dp))
}
