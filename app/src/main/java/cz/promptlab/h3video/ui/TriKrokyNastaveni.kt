package cz.promptlab.h3video.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cz.promptlab.h3video.MainViewModel
import cz.promptlab.h3video.comfy.ThreeStepBuilder
import cz.promptlab.h3video.data.GenParams
import cz.promptlab.h3video.data.SAMPLERS
import cz.promptlab.h3video.data.SCHEDULERS
import cz.promptlab.h3video.data.t
import cz.promptlab.h3video.ui.theme.Amber
import cz.promptlab.h3video.ui.theme.TextLow
import cz.promptlab.h3video.ui.theme.TextMid
import kotlin.math.roundToInt

/** Megapixely prvního průchodu, které karta nabízí (předloha má 0,2). */
private val TK_MPX = listOf(0.2f, 0.3f, 0.4f, 0.5f, 0.6f, 0.7f, 0.8f, 1.0f)

/** Nastavení karty 3 kroky mění předlohu, vrátit jde jedním tlačítkem. */
fun GenParams.tkJakoPredloha(): Boolean {
    val d = GenParams()
    return tkMpx == d.tkMpx && tkZvetseni == d.tkZvetseni && tkKroky == d.tkKroky &&
        tkSampler == d.tkSampler && tkScheduler == d.tkScheduler &&
        tkShiftObraz == d.tkShiftObraz && tkShiftZvuk == d.tkShiftZvuk &&
        tkUnet == d.tkUnet && tkVernost == d.tkVernost
}

/** Souhrn do sbalené sekce Nastavení: „3+2 kroky · 608×352 → 960×544". */
fun GenParams.tkSouhrn(): String {
    val (prvni, druhy) = ThreeStepBuilder.rozmery(aspect, ThreeStepBuilder.Nastaveni.z(this))
    return "%d+2 %s · %d×%d → %d×%d".format(
        tkKroky, t("kroky"), prvni.first, prvni.second, druhy.first, druhy.second,
    )
}

/**
 * Vlastní nastavení karty **3 kroky**. Každá volba vede do konkrétního uzlu
 * předlohy (viz [ThreeStepBuilder]); výchozí hodnoty jsou ty z předlohy.
 */
@Composable
fun TriKrokyNastaveni(vm: MainViewModel, params: GenParams, sReferencemi: Boolean) {
    val n = ThreeStepBuilder.Nastaveni.z(params)
    val (prvni, druhy) = ThreeStepBuilder.rozmery(params.aspect, n)

    SectionCard(
        title = t("Rozlišení"),
        subtitle = t("První průchod → výsledek po zvětšení"),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                "%d×%d → %d×%d".format(prvni.first, prvni.second, druhy.first, druhy.second),
                style = MaterialTheme.typography.titleMedium,
            )
            val i = TK_MPX.indexOfFirst { kotlin.math.abs(it - params.tkMpx) < 0.001f }
                .let { if (it < 0) 0 else it }
            LabeledSlider(
                label = t("První průchod"),
                value = "%.1f MPx".format(params.tkMpx),
                position = i.toFloat(),
                range = 0f..(TK_MPX.size - 1).toFloat(),
                onChange = { v ->
                    val mpx = TK_MPX[v.roundToInt().coerceIn(0, TK_MPX.size - 1)]
                    vm.update { it.copy(tkMpx = mpx) }
                },
                note = t("Předloha má 0,2. Víc = ostřejší a pomalejší."),
            )
            LabeledSlider(
                label = t("Zvětšení v latentu"),
                value = "×%.2f".format(params.tkZvetseni),
                position = params.tkZvetseni,
                range = 1f..2f,
                onChange = { v ->
                    vm.update { it.copy(tkZvetseni = (v * 20).roundToInt() / 20f) }
                },
                note = t("Předloha má ×1,58."),
            )
            if (params.tkZvetseni != ThreeStepBuilder.ZVETSENI) Text(
                t("Vrátit ×1,58 z předlohy"),
                style = MaterialTheme.typography.bodySmall, color = TextMid,
                modifier = Modifier.clickable {
                    vm.update { it.copy(tkZvetseni = ThreeStepBuilder.ZVETSENI) }
                },
            )
        }
    }

    SectionCard(title = t("Vzorkování"), subtitle = t("První průchod; zjemnění má 2 kroky napevno")) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            LabeledSlider(
                label = t("Počet kroků"),
                value = "${params.tkKroky}",
                position = params.tkKroky.toFloat(),
                range = 2f..10f,
                onChange = { v -> vm.update { it.copy(tkKroky = v.roundToInt()) } },
                note = t("Zrychlovací LoRA je trénovaná na 3."),
            )
            Dropdown("Sampler", SAMPLERS, params.tkSampler, { it }) { v ->
                vm.update { it.copy(tkSampler = v) }
            }
            Dropdown(t("Plánovač (scheduler)"), SCHEDULERS, params.tkScheduler, { it }) { v ->
                vm.update { it.copy(tkScheduler = v) }
            }
            LabeledSlider(
                label = t("Sigma shift – obraz"),
                value = "%.1f".format(params.tkShiftObraz),
                position = params.tkShiftObraz,
                range = 1f..20f,
                onChange = { v -> vm.update { it.copy(tkShiftObraz = (v * 2).roundToInt() / 2f) } },
                note = t("Předloha má 12."),
            )
            LabeledSlider(
                label = t("Sigma shift – zvuk"),
                value = "%.1f".format(params.tkShiftZvuk),
                position = params.tkShiftZvuk,
                range = 1f..10f,
                onChange = { v -> vm.update { it.copy(tkShiftZvuk = (v * 2).roundToInt() / 2f) } },
                note = t("Předloha má 3."),
            )
        }
    }

    val modely by vm.availableUnets.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.loadUnets() }
    SectionCard(title = t("Model")) {
        val h3 = modely.filter { it.contains("h3", ignoreCase = true) }
        Dropdown(
            t("Váhy"),
            listOf("") + h3,
            params.tkUnet,
            { if (it.isEmpty()) t("Z předlohy (fl2va)") else it.removeSuffix(".safetensors") },
        ) { v -> vm.update { it.copy(tkUnet = v) } }
    }

    if (sReferencemi) SectionCard(title = t("Věrnost referencí")) {
        PillRow(
            items = listOf("max", "match"),
            selected = params.tkVernost,
            label = { if (it == "match") t("Vyvážené") else t("Maximální detail") },
            onSelect = { v -> vm.update { it.copy(tkVernost = v) } },
        )
    }

    if (!params.tkJakoPredloha()) {
        Spacer(Modifier.height(4.dp))
        OutlineButton(
            t("Vrátit hodnoty z předlohy"),
            modifier = Modifier.fillMaxWidth(),
            color = Amber,
        ) {
            val d = GenParams()
            vm.update {
                it.copy(
                    tkMpx = d.tkMpx, tkZvetseni = d.tkZvetseni, tkKroky = d.tkKroky,
                    tkSampler = d.tkSampler, tkScheduler = d.tkScheduler,
                    tkShiftObraz = d.tkShiftObraz, tkShiftZvuk = d.tkShiftZvuk,
                    tkUnet = d.tkUnet, tkVernost = d.tkVernost,
                )
            }
        }
    }
}
