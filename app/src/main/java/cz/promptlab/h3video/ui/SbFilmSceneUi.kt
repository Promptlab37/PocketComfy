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
import androidx.compose.foundation.relocation.bringIntoViewRequester
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
 * Karta **Film ze storyboardu** ve třech očíslovaných krocích:
 * 1 · Storyboard (Přečíst) nebo 1 · Děj (Navrhnout záběry) → 2 · záběry
 * (Napsat scénář) → 3 · Scénář → Natočit film. Natočit jde až s hotovým
 * scénářem (uživatel 29. 9. 2026: „aby každému bylo jasné, jak ty kroky
 * udělat“). Zvýrazněné (Amber) je jen tlačítko dalšího kroku; hotový krok
 * má neutrální „…znovu“. Plátno je nad krokem 2 — poměr stran jde do
 * přepisovače a jeho změna scénář smaže. Návrh prošel kritikem.
 */
@Composable
@OptIn(ExperimentalLayoutApi::class)
fun SbFilmSection(vm: MainViewModel) {
    val scene by vm.sbFilm.collectAsStateWithLifecycle()
    val stav by vm.rewriteState.collectAsStateWithLifecycle()
    val bezi = (stav as? MainViewModel.RewriteState.Busy)?.druh == MainViewModel.PraceNaPromptu.VYLEPSENI
    val chyba = (stav as? MainViewModel.RewriteState.Fail)
        ?.takeIf { it.druh == MainViewModel.PraceNaPromptu.VYLEPSENI }?.message
    val akce by vm.sbAkce.collectAsStateWithLifecycle()
    val cte = bezi && akce == MainViewModel.SbAkce.CTENI
    val navrhuje = bezi && akce == MainViewModel.SbAkce.NAVRH

    // Model nahoře, pod ním jeho nastavení (uživatel 29. 9. 2026, kritik).
    SectionCard(title = t("Model")) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            PillRow(
                items = cz.promptlab.h3video.data.SbModel.entries.toList(),
                selected = scene.model,
                label = { it.title },
                onSelect = { vm.setSbModel(it) },
            )
            // Turbo a 3 + 2 mají kroky v receptu — jen souhrn, nic k rozbalení.
            val kvalita = scene.model == cz.promptlab.h3video.data.SbModel.KVALITA
            SkladaciSekce(
                title = t("Nastavení modelu"),
                souhrn = (if (scene.kroky in 2..4) t("%d kroky") else t("%d kroků")).format(scene.kroky),
                klic = "sbfilm_model_nastaveni",
                rozbalitelne = kvalita,
            ) {
                LabeledSlider(
                    label = t("Kroky"),
                    value = "${scene.kroky}",
                    position = scene.kroky.toFloat(),
                    range = SbFilmScene.KVALITA_MIN_KROKU.toFloat()..SbFilmScene.KVALITA_MAX_KROKU.toFloat(),
                    onChange = { vm.setSbKrokyKvalita(Math.round(it)) },
                )
            }
        }
    }

    SectionCard(title = t("Plán")) {
        PillRow(
            items = SbZdroj.entries.toList(),
            selected = scene.zdroj,
            label = { it.title },
            onSelect = { vm.setSbZdroj(it) },
        )
    }

    if (scene.zdroj == SbZdroj.STORYBOARD) SectionCard(title = "1 · " + t("Storyboard")) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            StoryboardPole(scene, onPick = { vm.pickSbStoryboard(it) }, onClear = { vm.clearSbStoryboard() })
            if (scene.storyboard != null) {
                OutlineButton(
                    if (cte) t("Čtu storyboard…")
                    else if (scene.panely.isEmpty()) t("Přečíst storyboard") else t("Přečíst znovu"),
                    color = if (scene.panely.isEmpty()) Amber else TextMid,
                    modifier = Modifier.fillMaxWidth(),
                ) { if (!bezi) vm.precistSbStoryboard() }
            }
            if (akce == MainViewModel.SbAkce.CTENI) SjedKPrubehu(cte) {
                PrubehPrepisu(vm, barva = Amber)
                chyba?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
            }
        }
    }

    // Storyboard bez textu + scénář zvlášť (5.14): obrázek i scénář, jedno tlačítko.
    if (scene.zdroj == SbZdroj.SCENAR) SectionCard(title = "1 · " + t("Storyboard a scénář")) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            StoryboardPole(scene, onPick = { vm.pickSbStoryboard(it) }, onClear = { vm.clearSbStoryboard() })
            DarkTextField(
                value = scene.scenar,
                onValueChange = { vm.setSbScenar(it) },
                placeholder = t("Vlož scénář"),
                minHeight = 160.dp,
                onClear = { vm.setSbScenar("") },
                rostouci = true,
            )
            if (scene.storyboard != null && scene.scenar.isNotBlank()) {
                OutlineButton(
                    if (cte) t("Čtu storyboard a scénář…")
                    else if (scene.panely.isEmpty()) t("Přečíst storyboard a scénář") else t("Přečíst znovu"),
                    color = if (scene.panely.isEmpty()) Amber else TextMid,
                    modifier = Modifier.fillMaxWidth(),
                ) { if (!bezi) vm.precistSbScenar() }
            }
            if (akce == MainViewModel.SbAkce.CTENI) SjedKPrubehu(cte) {
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

    // Se scénářem Děj nemá smysl — scénář ho nahrazuje (5.14).
    if (scene.zdroj != SbZdroj.SCENAR) SectionCard(title = if (scene.zdroj == SbZdroj.DEJ) "1 · " + t("Děj") else t("Děj")) {
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
                    if (navrhuje) t("Navrhuji záběry…")
                    else if (scene.panely.isEmpty()) t("Navrhnout záběry") else t("Navrhnout znovu"),
                    color = if (scene.panely.isEmpty()) Amber else TextMid,
                    modifier = Modifier.fillMaxWidth(),
                ) { if (!bezi) vm.navrhnoutSbZabery() }
                if (akce == MainViewModel.SbAkce.NAVRH) SjedKPrubehu(navrhuje) {
                    PrubehPrepisu(vm, barva = Amber)
                    chyba?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                }
            }
        }
    }

    // Podkresová hudba (YuE2) — jen když ji server umí.
    val hudbaDostupna by vm.sbHudbaDostupna.collectAsStateWithLifecycle()
    androidx.compose.runtime.LaunchedEffect(Unit) { vm.overSbHudbu() }
    // Hudba se přidává až k hotovému filmu, který se uživateli líbí (5.13).
    val historie by vm.history.collectAsStateWithLifecycle()
    val film = androidx.compose.runtime.remember(historie) { vm.posledniFilmProHudbu() }
    if (hudbaDostupna && film != null) SectionCard(title = t("Podkresová hudba")) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            run {
                DarkTextField(
                    value = scene.hudbaStyl,
                    onValueChange = { vm.setSbHudbaStyl(it) },
                    placeholder = cz.promptlab.h3video.comfy.SbHudbaBuilder.STYL_VYCHOZI,
                    onClear = { vm.setSbHudbaStyl("") },
                    minHeight = 70.dp,
                    rostouci = true,
                )
                LabeledSlider(
                    label = t("Hlasitost hudby"),
                    value = "${scene.hudbaHlasitost} dB",
                    position = scene.hudbaHlasitost.toFloat(),
                    range = cz.promptlab.h3video.comfy.SbHudbaBuilder.HLASITOST_MIN.toFloat()..
                        cz.promptlab.h3video.comfy.SbHudbaBuilder.HLASITOST_MAX.toFloat(),
                    onChange = { vm.setSbHudbaHlasitost(Math.round(it)) },
                )
                OutlineButton(
                    t("Přidat hudbu k poslednímu filmu"),
                    color = Amber,
                    modifier = Modifier.fillMaxWidth(),
                ) { vm.pridatHudbu(film, scene.hudbaStyl, scene.hudbaHlasitost) }
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

    if (scene.panely.isNotEmpty()) {
        val useky = scene.useky
        val seScenarem = scene.zdroj == SbZdroj.SCENAR
        val scenarHotovy = scene.zadaniUseku.size == useky.size && useky.isNotEmpty()
        SectionCard(title = "2 · " + scene.nazev.ifBlank { t("Záběry") }) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    listOfNotNull(
                        "%.1f s".format(scene.sekundy),
                        pocet(useky.size, "%d úsek", "%d úseky", "%d úseků"),
                        pocet(scene.panely.size, "%d panel", "%d panely", "%d panelů"),
                        t("časy ze storyboardu").takeIf { scene.casyZeStoryboardu && !seScenarem },
                        t("časy ze scénáře").takeIf { scene.casyZeStoryboardu && seScenarem },
                        t("okna rozdělena odhadem").takeIf { seScenarem && scene.scenarOdhadem },
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.labelLarge, color = TextHi,
                )
                // Jiný počet oken ve scénáři než panelů v obrázku: čísla by neseděla.
                if (seScenarem && scene.panelyObrazku > 0 && scene.oknaScenare > 0 && scene.panelyObrazku != scene.oknaScenare) {
                    Text(
                        t("Scénář má %d oken, storyboard %d panelů.").format(scene.oknaScenare, scene.panelyObrazku),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error,
                    )
                }
                var index = 0
                useky.forEachIndexed { u, usek ->
                    if (u > 0) HorizontalDivider(color = Amber.copy(alpha = .5f), modifier = Modifier.padding(vertical = 4.dp))
                    usek.panely.forEach { _ ->
                        val i = index++
                        scene.panely.getOrNull(i)?.let { p ->
                            PanelRadek(
                                cislo = p.cislo, popis = p.popis, sekundy = p.sekundy,
                                repliky = p.repliky,
                                podani = p.podani,
                                zvuk = p.zvuk,
                                onPodani = { vm.setSbPanelPodani(i, it) },
                                onZvuk = { vm.setSbPanelZvuk(i, it) },
                                onRepliky = { vm.setSbPanelRepliky(i, it) },
                                onPopis = { vm.setSbPanelPopis(i, it) },
                                onSekundy = { vm.setSbPanelSekundy(i, it) },
                                onSmazat = { vm.smazSbPanel(i) },
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                val pripravuje = bezi && akce == MainViewModel.SbAkce.NATOCENI
                OutlineButton(
                    if (pripravuje) (if (seScenarem) t("Píšu prompty…") else t("Píšu scénář…"))
                    else if (!scenarHotovy) (if (seScenarem) t("Napsat prompty") else t("Napsat scénář")) else t("Napsat znovu"),
                    color = if (scenarHotovy) TextMid else Amber,
                    modifier = Modifier.fillMaxWidth(),
                ) { if (!bezi) vm.pripravitSbPrompty() }
                if (akce == MainViewModel.SbAkce.NATOCENI) SjedKPrubehu(pripravuje) {
                    PrubehPrepisu(vm, barva = Amber)
                    chyba?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                }
            }
        }

        // Texty na videu, výzva a poznámky ze scénáře — do H3 nejdou (5.14).
        if (seScenarem && scene.strih.isNotEmpty()) {
            SectionCard(title = t("Texty do střihu") + " (${scene.strih.size})") {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    val casy = cz.promptlab.h3video.data.SbScenar.casyPanelu(scene.panely)
                    scene.strih.forEach { r ->
                        val cas = casy[r.cislo]?.let { (od, doS) -> "${casStrihu(od)}–${casStrihu(doS)}" } ?: "${r.cislo}"
                        val poznamka = r.druh == cz.promptlab.h3video.data.SbTextStrihu.Druh.POZNAMKA
                        Row {
                            Text(cas, style = MaterialTheme.typography.labelMedium, color = Amber, modifier = Modifier.width(84.dp))
                            TextVesel(
                                r.text, style = MaterialTheme.typography.bodySmall,
                                color = if (poznamka) TextLow else TextHi, modifier = Modifier.weight(1f),
                            )
                        }
                    }
                    if (scene.strih.any { it.druh != cz.promptlab.h3video.data.SbTextStrihu.Druh.POZNAMKA }) {
                        OutlineButton(t("Uložit titulky (.srt)"), color = TextMid, modifier = Modifier.fillMaxWidth()) {
                            vm.ulozSbTitulky()
                        }
                    }
                }
            }
        }

        // Scénář = prompty pro H3, přesně to, co dostane model; jde upravit
        // a Natočit film použije tuhle podobu.
        if (scenarHotovy) {
            SectionCard(title = "3 · " + if (seScenarem) t("Prompty") else t("Scénář")) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    scene.zadaniUseku.forEachIndexed { k, text ->
                        Text(
                            t("Úsek %d · %s").format(k + 1, "%.1f s".format(useky[k].sekundy)),
                            style = MaterialTheme.typography.labelLarge, color = Amber,
                        )
                        DarkTextField(
                            value = text,
                            onValueChange = { vm.setSbZadaniUseku(k, it) },
                            placeholder = "",
                            onClear = { vm.setSbZadaniUseku(k, "") },
                            minHeight = 120.dp,
                            rostouci = true,
                        )
                    }
                }
            }
        }
    }


}

/**
 * Průběh akce karty. Když akce začne, obrazovka k němu sama sjede — dřív
 * zůstal schovaný pod okrajem a vypadalo to, že se nic neděje (uživatel
 * 29. 9. 2026 u „Napsat scénář“).
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun SjedKPrubehu(bezi: Boolean, content: @Composable () -> Unit) {
    val pozadavek = androidx.compose.runtime.remember { androidx.compose.foundation.relocation.BringIntoViewRequester() }
    Column(androidx.compose.ui.Modifier.bringIntoViewRequester(pozadavek)) { content() }
    androidx.compose.runtime.LaunchedEffect(bezi) {
        if (bezi) {
            // Až se průběh vykreslí (má výšku), jinak není kam sjet.
            kotlinx.coroutines.delay(150)
            pozadavek.bringIntoView()
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
    repliky: String,
    podani: String = "",
    zvuk: String = "",
    onPodani: (String) -> Unit = {},
    onZvuk: (String) -> Unit = {},
    onRepliky: (String) -> Unit,
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
            minHeight = 56.dp,
            onClear = { onPopis("") },
            rostouci = true,
        )
        // Repliky panelu (doslova, v původním jazyce) — jen když nějaké jsou.
        if (repliky.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            DarkTextField(
                value = repliky,
                onValueChange = onRepliky,
                placeholder = "",
                minHeight = 56.dp,
                onClear = { onRepliky("") },
                rostouci = true,
            )
        }
        // Podání a zvuk ze scénáře (5.14) — jen když je scénář uvádí.
        for ((hodnota, zmena) in listOf(podani to onPodani, zvuk to onZvuk)) {
            if (hodnota.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                DarkTextField(
                    value = hodnota,
                    onValueChange = zmena,
                    placeholder = "",
                    minHeight = 48.dp,
                    onClear = { zmena("") },
                    rostouci = true,
                )
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}

/** Český tvar podle počtu: 1 úsek, 2–4 úseky, 5+ úseků. */
private fun pocet(n: Int, jeden: String, dva: String, pet: String): String =
    t(when { n == 1 -> jeden; n in 2..4 -> dva; else -> pet }).format(n)

/** `0:02.5` — čas v seznamu Texty do střihu. */
private fun casStrihu(s: Double): String {
    val d = (s * 10).toInt()
    val sek = d / 10
    return "%d:%02d".format(sek / 60, sek % 60) + if (d % 10 != 0) ".${d % 10}" else ""
}
