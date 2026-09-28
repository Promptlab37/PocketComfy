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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import cz.promptlab.h3video.data.LongMmPomer
import cz.promptlab.h3video.data.SbFilmScene
import cz.promptlab.h3video.data.SbRozliseni
import cz.promptlab.h3video.data.SbZdroj
import cz.promptlab.h3video.data.t
import cz.promptlab.h3video.ui.theme.Amber
import cz.promptlab.h3video.ui.theme.Outline1
import cz.promptlab.h3video.ui.theme.Surface2
import cz.promptlab.h3video.ui.theme.TextHi
import cz.promptlab.h3video.ui.theme.TextLow
import cz.promptlab.h3video.ui.theme.TextMid

/**
 * Karta **Film ze storyboardu**: storyboard → Přečíst → plán → Natočit
 * (spodní tlačítko). Návrh prošel kritikem 28. 9. 2026: délky počítá appka,
 * plán je vidět a jde upravit, nic navíc.
 */
@Composable
@OptIn(ExperimentalLayoutApi::class)
fun SbFilmSection(vm: MainViewModel) {
    val scene by vm.sbFilm.collectAsStateWithLifecycle()
    val stav by vm.rewriteState.collectAsStateWithLifecycle()
    val bezi = (stav as? MainViewModel.RewriteState.Busy)?.druh == MainViewModel.PraceNaPromptu.VYLEPSENI
    val chyba = (stav as? MainViewModel.RewriteState.Fail)
        ?.takeIf { it.druh == MainViewModel.PraceNaPromptu.VYLEPSENI }?.message

    SectionCard(title = t("Plán")) {
        PillRow(
            items = SbZdroj.entries.toList(),
            selected = scene.zdroj,
            label = { it.title },
            onSelect = { vm.setSbZdroj(it) },
        )
    }

    if (scene.zdroj == SbZdroj.STORYBOARD) SectionCard(title = t("Storyboard")) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            StoryboardPole(scene, onPick = { vm.pickSbStoryboard(it) }, onClear = { vm.clearSbStoryboard() })
            if (scene.storyboard != null) {
                OutlineButton(
                    if (bezi && scene.panely.isEmpty()) t("Čtu storyboard…")
                    else if (scene.panely.isEmpty()) t("Přečíst storyboard") else t("Přečíst znovu"),
                    color = Amber,
                    modifier = Modifier.fillMaxWidth(),
                ) { if (!bezi) vm.precistSbStoryboard() }
            }
            if (scene.panely.isEmpty()) {
                PrubehPrepisu(vm, barva = Amber)
                chyba?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
            }
        }
    }

    SectionCard(title = t("Postavy")) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            scene.postavy.forEachIndexed { i, p ->
                RefDlazdicka(thumb = p.nahled, onPick = { }, onRemove = { vm.removeSbPostava(i) })
            }
            val zbyva = SbFilmScene.MAX_POSTAV - scene.postavy.size
            if (zbyva > 0) RefPridat(zbyva) { uris -> uris.forEach { vm.addSbPostava(it) } }
        }
    }

    SectionCard(title = t("Děj")) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            DarkTextField(
                value = scene.dej,
                onValueChange = { vm.setSbDej(it) },
                placeholder = t("Vězeň v železné masce je osvobozen a ukáže se, že je to král"),
                minHeight = 90.dp,
                onClear = { vm.setSbDej("") },
            )
            if (scene.zdroj == SbZdroj.DEJ) {
                PillRow(
                    items = SbFilmScene.DELKY,
                    selected = scene.cilSekund,
                    label = { "$it s" },
                    onSelect = { vm.setSbCil(it) },
                )
                OutlineButton(
                    if (bezi && scene.panely.isEmpty()) t("Navrhuji záběry…")
                    else if (scene.panely.isEmpty()) t("Navrhnout záběry") else t("Navrhnout znovu"),
                    color = Amber,
                    modifier = Modifier.fillMaxWidth(),
                ) { if (!bezi) vm.navrhnoutSbZabery() }
                if (scene.panely.isEmpty()) {
                    PrubehPrepisu(vm, barva = Amber)
                    chyba?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                }
            }
        }
    }

    if (scene.panely.isNotEmpty()) {
        val useky = scene.useky
        SectionCard(title = scene.nazev.ifBlank { t("Záběry") }) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    listOfNotNull(
                        "%.1f s".format(scene.sekundy),
                        pocet(useky.size, "%d úsek", "%d úseky", "%d úseků"),
                        pocet(scene.panely.size, "%d panel", "%d panely", "%d panelů"),
                        t("časy ze storyboardu").takeIf { scene.casyZeStoryboardu },
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.labelLarge, color = TextHi,
                )
                var index = 0
                useky.forEachIndexed { u, usek ->
                    if (u > 0) HorizontalDivider(color = Amber.copy(alpha = .5f), modifier = Modifier.padding(vertical = 4.dp))
                    usek.panely.forEach { _ ->
                        val i = index++
                        scene.panely.getOrNull(i)?.let { p ->
                            PanelRadek(
                                cislo = p.cislo, popis = p.popis, sekundy = p.sekundy,
                                onPopis = { vm.setSbPanelPopis(i, it) },
                                onSekundy = { vm.setSbPanelSekundy(i, it) },
                                onSmazat = { vm.smazSbPanel(i) },
                            )
                        }
                    }
                }
                PrubehPrepisu(vm, barva = Amber)
                chyba?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
            }
        }
    }

    SectionCard(title = t("Plátno")) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            PillRow(
                items = SbRozliseni.entries.toList(),
                selected = scene.rozliseni,
                label = { it.title },
                onSelect = { vm.setSbRozliseni(it) },
            )
            PillRow(
                items = LongMmPomer.entries.toList(),
                selected = scene.pomer,
                label = { it.title },
                onSelect = { vm.setSbPomer(it) },
            )
        }
    }
}

@Composable
private fun StoryboardPole(
    scene: SbFilmScene,
    onPick: (android.net.Uri?) -> Unit,
    onClear: () -> Unit,
) {
    val pick = rememberLauncherForActivityResult(VyberMedii()) { uri -> onPick(uri) }
    val imageOnly = PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(16.dp))
            .background(Surface2)
            .border(1.dp, Outline1, RoundedCornerShape(16.dp))
            .clickable { pick.launch(imageOnly) },
        contentAlignment = Alignment.Center,
    ) {
        val nahled = scene.storyboardNahled
        if (nahled != null) {
            // Celá mřížka bez ořezu.
            Image(nahled.asImageBitmap(), t("Storyboard"), Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
            Box(
                Modifier.align(Alignment.TopEnd).padding(6.dp).size(28.dp)
                    .clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = .55f))
                    .clickable(onClick = onClear),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Default.Close, t("Odebrat obrázek"), Modifier.size(15.dp), TextMid) }
        } else {
            Icon(Icons.Default.AddPhotoAlternate, t("Storyboard"), Modifier.size(28.dp), TextMid)
        }
    }
}

@Composable
private fun PanelRadek(
    cislo: Int,
    popis: String,
    sekundy: Double,
    onPopis: (String) -> Unit,
    onSekundy: (Double) -> Unit,
    onSmazat: () -> Unit,
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("$cislo", style = MaterialTheme.typography.labelLarge, color = Amber, modifier = Modifier.width(24.dp))
            TextVesel(
                "%.1f s".format(sekundy),
                style = MaterialTheme.typography.labelLarge, color = TextHi,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { onSekundy(sekundy - 0.5) }) {
                Icon(Icons.Default.Remove, t("Kratší"), Modifier.size(18.dp), TextMid)
            }
            IconButton(onClick = { onSekundy(sekundy + 0.5) }) {
                Icon(Icons.Default.Add, t("Delší"), Modifier.size(18.dp), TextMid)
            }
            // Koš, ne křížek — křížek v poli pod tím maže jen text popisu.
            IconButton(onClick = onSmazat) {
                Icon(Icons.Default.Delete, t("Smazat panel"), Modifier.size(18.dp), TextLow)
            }
        }
        DarkTextField(
            value = popis,
            onValueChange = onPopis,
            placeholder = "",
            minHeight = 48.dp,
            onClear = { onPopis("") },
        )
        Spacer(Modifier.height(4.dp))
    }
}

/** Český tvar podle počtu: 1 úsek, 2–4 úseky, 5+ úseků. */
private fun pocet(n: Int, jeden: String, dva: String, pet: String): String =
    t(when { n == 1 -> jeden; n in 2..4 -> dva; else -> pet }).format(n)
