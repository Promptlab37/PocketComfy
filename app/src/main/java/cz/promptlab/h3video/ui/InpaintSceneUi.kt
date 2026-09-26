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
import androidx.compose.foundation.layout.FlowRow
import cz.promptlab.h3video.comfy.InpaintBuilder
import cz.promptlab.h3video.data.InpaintRezim
import cz.promptlab.h3video.data.Smer
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
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun InpaintSection(vm: MainViewModel) {
    val scene by vm.inpaint.collectAsStateWithLifecycle()

    val imageOnly = PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
    val pick = rememberLauncherForActivityResult(
        cz.promptlab.h3video.ui.VyberMedii()
    ) { uri -> vm.pickInpaintImage(uri) }

    var maluje by remember { mutableStateOf(false) }
    val masku = scene.rezim.chceMasku

    SectionCard(title = t("Co se dělá"), subtitle = t("Dvě úlohy nad stejnou fotkou")) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            PillRow(
                items = InpaintRezim.entries.toList(),
                selected = scene.rezim,
                label = { it.title },
                onSelect = { vm.setInpaintRezim(it) },
            )
            Text(
                scene.rezim.detail,
                style = MaterialTheme.typography.bodySmall, color = TextLow,
            )
        }
    }

    SectionCard(
        title = if (masku) t("Fotka, do které se maluje") else t("Fotka, která se rozšíří"),
        subtitle = if (!masku)
            t("Štětec tady není potřeba — nové místo si graf označí sám")
        else if (scene.maskPainted)
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
                    if (masku && scene.maskPainted) Ok.copy(alpha = .5f) else Outline1,
                    RoundedCornerShape(14.dp)
                )
                .clickable {
                    if (scene.source == null) pick.launch(imageOnly)
                    else if (masku) maluje = true
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
                    // Štětec se u rozšíření vůbec neukazuje: masku vyrábí
                    // uzel výřezu z přilepeného místa a namalovaná by do
                    // grafu ani nešla.
                    if (masku) Box(
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

    if (!masku) SectionCard(
        title = t("Kam a o kolik"),
        subtitle = t("Qwen doporučuje 30–50 % plochy navíc na jeden směr"),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Smer.entries.forEach { smer ->
                    val zvoleny = smer in scene.smery
                    OutlineButton(
                        (if (zvoleny) "✓ " else "") + smer.title,
                        color = if (zvoleny) Cyan else TextMid,
                    ) { vm.prepniInpaintSmer(smer) }
                }
            }
            LabeledSlider(
                label = t("O kolik"),
                value = "%d %%".format(scene.procent),
                position = scene.procent.toFloat(),
                range = 10f..InpaintBuilder.ROZSIRENI_MAX.toFloat(),
                onChange = { vm.setInpaintProcent(it.roundToInt()) },
                note = t("Počítá se z rozměru fotky a platí pro každý zvolený směr zvlášť."),
            )
        }
    }

    SectionCard(
        title = if (masku) t("Co má na tom místě být")
        else t("Co má na přilepeném místě být (nepovinné)"),
        // Každý model čte zadání jinak: Flux Fill maluje do díry to, co
        // popíšeš, kdežto Klein bere zadání jako příkaz k úpravě — popis
        // typu „muž s břichem" pro něj znamená „nech to tak".
        subtitle = if (!masku)
            t("Můžeš nechat prázdné — model scénu dotáhne podle fotky")
        else if (scene.model == InpaintModel.FILL)
            t("Popiš to jako výsledný obraz, ne jako příkaz")
        else t("Tenhle model poslouchá příkazy — napiš, co se s tím místem má stát")
    ) {
        Column {
            DarkTextField(
                value = scene.prompt,
                onValueChange = { vm.setInpaintPrompt(it) },
                placeholder = if (!masku)
                    t("celá postava, nohy v džínách a botách, dlážděný chodník")
                else if (scene.model == InpaintModel.FILL)
                    t("dřevěná lavička pod stromem, dopolední světlo")
                else t("posaď ho na dřevěnou lavičku pod stromem"),
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
    // Rozšíření jede vždy na Qwen 2.1 — nabídka podle něj, ne podle modelu
    // vybraného pro domalování.
    val loryRozsireni = vm.inpaintLoraNabidka(InpaintModel.QWEN21, vsechnyLory)

    @Composable
    fun LoraKarta(nabidka: List<String>) = SectionCard(
        title = t("Doplňková LoRA"),
        subtitle = if (nabidka.isEmpty()) t("Na serveru není žádná LoRA pro tenhle model")
        else t("Pomůže tam, kde model sám tápe — třeba na anatomii"),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            LoraRozbalovaci(
                popisek = t("LoRA"),
                volby = nabidka.map { LoraVolba(it) },
                vybrana = scene.lora.takeIf { it in nabidka }.orEmpty(),
                onVybrat = { vm.setInpaintLora(it) },
                otevreno = { vm.refreshInpaintLoras() },
            )
            if (scene.lora.isNotBlank() && scene.lora in nabidka) {
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
    if (!masku) LoraKarta(loryRozsireni)

    // U rozšíření se model nevybírá — vlastní předlohu má jen Qwen 2.1.
    if (masku) SkladaciSekce(
        title = t("Model a doladění"),
        souhrn = scene.model.title +
            // Jen když LoRA k modelu opravdu patří — volbu z jiného modelu
            // graf nedostane a ve shrnutí by strašila.
            (if (scene.lora in lory) " · LoRA" else "") +
            (if (scene.model == InpaintModel.FILL && scene.sila < 1f)
                " · síla %.2f".format(scene.sila) else ""),
        klic = "nastaveni-inpaint",
    ) {
        SectionCard(
            title = t("Čím domalovat"),
            subtitle = t("Když se výsledek nepovede, zkus jiný model — každý kreslí jinak")
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
        LoraKarta(lory)

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
