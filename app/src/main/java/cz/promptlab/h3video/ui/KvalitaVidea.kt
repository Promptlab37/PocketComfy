package cz.promptlab.h3video.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cz.promptlab.h3video.MainViewModel
import cz.promptlab.h3video.data.GenParams
import cz.promptlab.h3video.data.t
import cz.promptlab.h3video.ui.theme.Cyan
import cz.promptlab.h3video.ui.theme.Outline1
import cz.promptlab.h3video.ui.theme.Surface1
import cz.promptlab.h3video.ui.theme.TextHi
import cz.promptlab.h3video.ui.theme.TextLow

/**
 * Kvalita a rychlost videa — JEDNO místo pro všechny H3 karty (5.39, uživatel:
 * „ať to nemusím hledat jak idiot pokaždé v jiné kartě“). Dřív byla Sage
 * a shift zvuku ve dvakrát sbaleném „Pokročilém“, u 3 kroků dvakrát, a Film ze
 * storyboardu i Long MiniMax měly hodnoty natvrdo.
 */
fun souhrnKvality(p: GenParams, sTeaCache: Boolean = true, sShiftem: Boolean = true): String = listOfNotNull(
    p.pozornost.title,
    if (p.teaCache && sTeaCache) t("TeaCache zapnutá") else null,
    if (sShiftem) t("shift zvuku %s").format("%.1f".format(p.shiftAudio)) else null,
).joinToString(" · ")

/** Sekce v Nastavení. */
@Composable
fun KvalitaVideaNastaveni(vm: MainViewModel) {
    val params by vm.params.collectAsStateWithLifecycle()
    SectionCard(title = t("Zrychlení a vzorkování"), stav = souhrnKvality(params)) {
        KvalitaVideaVolby(vm, params)
    }
}

/** Přepínače a posuvník — v Nastavení i v okně z karty. */
@Composable
private fun KvalitaVideaVolby(vm: MainViewModel, params: GenParams, sTeaCache: Boolean = true, sShiftem: Boolean = true) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Attention", style = MaterialTheme.typography.bodyMedium, color = TextHi)
        PillRow(
            items = cz.promptlab.h3video.data.Pozornost.entries.toList(),
            selected = params.pozornost,
            label = { it.title },
            onSelect = { v -> vm.update { it.copy(pozornost = v) } },
        )
        // Jen volby, které karta opravdu použije (žádné mrtvé volby).
        if (sTeaCache) Prepinac("TeaCache", params.teaCache) { v -> vm.update { it.copy(teaCache = v) } }
        if (sShiftem) LabeledSlider(
            label = t("Sigma shift – zvuk"),
            value = "%.1f".format(params.shiftAudio),
            position = params.shiftAudio, range = 1f..10f,
            onChange = { v -> vm.update { it.copy(shiftAudio = (v * 10).toInt() / 10f) } },
        )
    }
}

@Composable
private fun Prepinac(nazev: String, zapnuto: Boolean, onZmena: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(nazev, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(checked = zapnuto, onCheckedChange = onZmena, colors = switchColors())
    }
}

/** Řádek v kartě: stav jedním pohledem, ťuknutí otevře Nastavení. */
@Composable
fun KvalitaVideaRadek(vm: MainViewModel, params: GenParams, sTeaCache: Boolean = true, sShiftem: Boolean = true) {
    var otevreno by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    if (otevreno) {
        val aktualni by vm.params.collectAsStateWithLifecycle()
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { otevreno = false },
            title = { Text(t("Zrychlení a vzorkování")) },
            text = { KvalitaVideaVolby(vm, aktualni, sTeaCache, sShiftem) },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { otevreno = false }) { Text(t("Hotovo"), color = Cyan) }
            },
            containerColor = Surface1,
        )
    }
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Surface1)
            .border(1.dp, Outline1, RoundedCornerShape(12.dp))
            .clickable { otevreno = true }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Název a pod ním souhrn — vedle sebe se na telefon nevešly (souhrn se uřízl).
        Column(Modifier.weight(1f)) {
            Text(t("Zrychlení a vzorkování"), style = MaterialTheme.typography.bodyMedium, color = TextHi)
            Text(
                souhrnKvality(params, sTeaCache, sShiftem),
                style = MaterialTheme.typography.bodySmall, color = Cyan,
            )
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, Modifier.size(20.dp), tint = TextLow)
    }
}
