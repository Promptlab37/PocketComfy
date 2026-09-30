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

    // 1 · Podklady: obrázek, scénář nebo děj — podle volby (5.17, stejné kroky u všech).
    SectionCard(title = "1 · " + t("Podklady")) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (scene.zdroj != SbZdroj.DEJ) {
                StoryboardPole(scene, onPick = { vm.pickSbStoryboard(it) }, onClear = { vm.clearSbStoryboard() })
            }
            if (scene.zdroj == SbZdroj.SCENAR) {
                DarkTextField(
                    value = scene.scenar,
                    onValueChange = { vm.setSbScenar(it) },
                    placeholder = t("Vlož scénář"),
                    minHeight = 160.dp,
                    onClear = { vm.setSbScenar("") },
                    rostouci = true,
                )
                // Scénář ze souboru (5.20): ChatGPT ho často dá ke stažení jako PDF nebo Word.
                val vyberSouboru = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { vm.nactiSbScenar(it) }
                OutlineButton(t("Načíst ze souboru"), color = TextMid, modifier = Modifier.fillMaxWidth()) {
                    vyberSouboru.launch(arrayOf(
                        "text/plain", "text/markdown", "text/x-markdown", "application/pdf",
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                    ))
                }
            }
            if (scene.zdroj == SbZdroj.DEJ) {
                DarkTextField(
                    value = scene.dej,
                    onValueChange = { vm.setSbDej(it) },
                    placeholder = t("Vězeň v železné masce je osvobozen a ukáže se, že je to král"),
                    minHeight = 90.dp,
                    onClear = { vm.setSbDej("") },
                )
                PillRow(
                    items = SbFilmScene.DELKY,
                    selected = scene.cilSekund,
                    label = { "$it s" },
                    onSelect = { vm.setSbCil(it) },
                )
            }
        }
    }

    // Postavy: u „z děje“ povinné; se storyboardem nepovinné a každá fotka
    // musí mít jméno postavy z plánu — jinak přepisovač hádá, kdo je kdo (5.17, kritici).
    SectionCard(title = t("Postavy")) {
        if (scene.zdroj == SbZdroj.DEJ) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                scene.postavy.forEachIndexed { i, p ->
                    RefDlazdicka(thumb = p.nahled, onPick = { }, onRemove = { vm.removeSbPostava(i) })
                }
                val zbyva = SbFilmScene.MAX_POSTAV - scene.postavy.size
                if (zbyva > 0) RefPridat(zbyva) { uris -> uris.forEach { vm.addSbPostava(it) } }
            }
        } else if (scene.postavy.isEmpty()) {
            SkladaciSekce(title = t("Skutečné tváře"), souhrn = t("volitelné"), klic = "sbfilm_tvare") {
                RefPridat(SbFilmScene.MAX_POSTAV) { uris -> uris.forEach { vm.addSbPostava(it) } }
            }
        } else {
            FotkyPostav(vm, scene)
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

    // 2 · Příprava: jedno tlačítko — plán i prompty, pak už jen Natočit (5.15–5.17).
    val lzePripravit = when (scene.zdroj) {
        SbZdroj.STORYBOARD -> scene.storyboard != null
        SbZdroj.SCENAR -> scene.storyboard != null && scene.scenar.isNotBlank()
        SbZdroj.DEJ -> scene.dej.isNotBlank() && scene.postavy.isNotEmpty()
    }
    val pripraveno = scene.zadaniUseku.isNotEmpty() && scene.zadaniUseku.size == scene.useky.size &&
        (scene.zdroj != SbZdroj.SCENAR || cz.promptlab.h3video.data.otiskScenare(scene.scenar) == scene.scenarPlanu)
    val pripravuje = bezi && (akce == MainViewModel.SbAkce.CTENI || akce == MainViewModel.SbAkce.NAVRH)
    SectionCard(title = "2 · " + t("Příprava")) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlineButton(
                if (pripravuje) t("Připravuji film…") else if (!pripraveno) t("Připravit film") else t("Připravit znovu"),
                color = if (pripraveno || !lzePripravit) TextMid else Amber,
                enabled = lzePripravit,
                modifier = Modifier.fillMaxWidth(),
            ) { if (!bezi) vm.pripravitFilm() }
            if (akce == MainViewModel.SbAkce.CTENI || akce == MainViewModel.SbAkce.NAVRH) SjedKPrubehu(pripravuje) {
                PrubehPrepisu(vm, barva = Amber)
                chyba?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
            }
            // Souhrn — na první pohled je vidět, jestli příprava sedí (kritici 30. 9. 2026).
            if (scene.panely.isNotEmpty() && !pripravuje) {
                Text(souhrnFilmu(scene), style = MaterialTheme.typography.labelLarge, color = TextHi)
            }
            // Co kontrola čtení našla — proč se prompty nenapsaly.
            if (!pripravuje) scene.nalezy.forEach { n ->
                Text(
                    if (n.cislo > 0) "${n.cislo} · ${n.text}" else n.text,
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }

    if (scene.panely.isNotEmpty()) Kontrola(vm, scene, bezi, akce, chyba)

    // Hudba se přidává jen pod hotovým filmem (výsledek / galerie), ne v kartě (5.18).
}

/**
 * Fotky postav se storyboardem: pod každou jména postav z plánu, klepnutím
 * se určí, kdo na ní je (jedna fotka na postavu). Jména u „Mám storyboard“
 * přibudou po přípravě, u scénáře hned po vložení.
 */
@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun FotkyPostav(vm: MainViewModel, scene: SbFilmScene) {
    val jmena = androidx.compose.runtime.remember(scene.panely, scene.vzhled, scene.scenar, scene.zdroj) {
        cz.promptlab.h3video.data.jmenaPostav(scene)
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        scene.postavy.forEachIndexed { i, p ->
            val jmeno = scene.jmenoFotky(p.soubor)
            Row(verticalAlignment = Alignment.CenterVertically) {
                RefDlazdicka(thumb = p.nahled, onPick = { }, onRemove = { vm.removeSbPostava(i) })
                Spacer(Modifier.width(12.dp))
                Box(Modifier.weight(1f)) {
                    if (jmena.isNotEmpty()) PillRow(
                        items = jmena,
                        selected = jmeno.orEmpty(),
                        label = { it },
                        onSelect = { vm.setSbFotkaJmeno(i, if (it.equals(jmeno, ignoreCase = true)) "" else it) },
                    )
                }
            }
        }
        val zbyva = SbFilmScene.MAX_POSTAV - scene.postavy.size
        if (zbyva > 0) RefPridat(zbyva) { uris -> uris.forEach { vm.addSbPostava(it) } }
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

/** `FOTOŽIJE · 15 s · 2 úseky · 2 repliky · Syn, Otec` */
private fun souhrnFilmu(scene: SbFilmScene): String {
    val repliky = scene.panely.flatMap { cz.promptlab.h3video.data.SbFilmPrepis.repliky(it.repliky) }
    val mluvci = repliky.map { it.first }.distinct()
    return listOfNotNull(
        scene.nazev.takeIf { it.isNotBlank() },
        "%.0f s".format(scene.sekundy),
        pocet(scene.useky.size, "%d úsek", "%d úseky", "%d úseků"),
        pocet(repliky.size, "%d replika", "%d repliky", "%d replik"),
        mluvci.joinToString(", ").takeIf { it.isNotBlank() },
    ).joinToString(" · ")
}

/**
 * Storyboard + scénář: krok 2 · Kontrola. Záběry, texty do střihu a prompty
 * jsou sbalené — po „Připravit film“ se jen natočí. Co vyžaduje zásah
 * (nesoulad počtu, chybějící prompty), je vidět i ve sbaleném stavu.
 */
@Composable
private fun Kontrola(
    vm: MainViewModel,
    scene: SbFilmScene,
    bezi: Boolean,
    akce: MainViewModel.SbAkce?,
    chyba: String?,
) {
    val useky = scene.useky
    val promptyHotove = scene.zadaniUseku.size == useky.size && useky.isNotEmpty()
    SectionCard(title = "3 · " + t("Kontrola")) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SkladaciSekce(
                title = t("Záběry"),
                souhrn = listOfNotNull(
                    "%.1f s".format(scene.sekundy),
                    pocet(useky.size, "%d úsek", "%d úseky", "%d úseků"),
                    pocet(scene.panely.size, "%d panel", "%d panely", "%d panelů"),
                    (if (scene.zdroj == SbZdroj.SCENAR) t("časy ze scénáře") else t("časy ze storyboardu"))
                        .takeIf { scene.casyZeStoryboardu },
                    t("okna rozdělena odhadem").takeIf { scene.scenarOdhadem },
                ).joinToString(" · "),
                klic = "sbscenar_zabery",
            ) {
                var index = 0
                useky.forEachIndexed { u, usek ->
                    if (u > 0) HorizontalDivider(color = Amber.copy(alpha = .5f), modifier = Modifier.padding(vertical = 4.dp))
                    usek.panely.forEach { _ ->
                        val i = index++
                        scene.panely.getOrNull(i)?.let { p ->
                            // Výřez panelu nad replikou: přečtené jde porovnat s obrázkem (kritici 30. 9. 2026).
                            val sb = scene.storyboard
                            if (sb != null && scene.seStoryboardem && scene.radku > 0 && scene.sloupcu > 0) {
                                VyrezPanelu(sb, scene.radku, scene.sloupcu, p.cislo - 1)
                            }
                            scene.nalezy.filter { it.cislo == p.cislo }.forEach { n ->
                                Text(n.text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                            }
                            PanelRadek(
                                cislo = p.cislo, popis = p.popis, sekundy = p.sekundy,
                                repliky = p.repliky, podani = p.podani, zvuk = p.zvuk,
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
            }
            if (scene.strih.isNotEmpty()) SkladaciSekce(
                title = t("Texty do střihu"),
                souhrn = "${scene.strih.size}",
                klic = "sbscenar_strih",
            ) {
                val casy = cz.promptlab.h3video.data.SbScenar.casyPanelu(scene.panely)
                scene.strih.forEach { r ->
                    val cas = cz.promptlab.h3video.data.SbScenar.casPro(casy, r.cislo)
                        ?.let { (od, doS) -> "${casStrihu(od)}–${casStrihu(doS)}" } ?: "${r.cislo}"
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
            if (promptyHotove) SkladaciSekce(
                title = t("Prompty"),
                souhrn = pocet(useky.size, "%d úsek", "%d úseky", "%d úseků"),
                klic = "sbscenar_prompty",
            ) {
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
            // Po úpravě záběrů prompty neplatí — tlačítko je vidět i se sbalenými záběry.
            val pise = bezi && akce == MainViewModel.SbAkce.NATOCENI
            if (!promptyHotove || pise) {
                OutlineButton(
                    if (pise) t("Píšu prompty…")
                    else if (scene.nalezy.isNotEmpty()) t("Pokračovat – napsat prompty") else t("Napsat prompty"),
                    color = Amber,
                    modifier = Modifier.fillMaxWidth(),
                ) { if (!bezi) vm.pripravitSbPrompty() }
            }
            if (akce == MainViewModel.SbAkce.NATOCENI) SjedKPrubehu(pise) {
                PrubehPrepisu(vm, barva = Amber)
                chyba?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
            }
        }
    }
}

/** Výřez jednoho panelu z plného obrázku storyboardu (čitelné repliky, ne náhled). */
@Composable
private fun VyrezPanelu(soubor: java.io.File, radku: Int, sloupcu: Int, poradi: Int) {
    val bmp by androidx.compose.runtime.produceState<android.graphics.Bitmap?>(null, soubor, radku, sloupcu, poradi) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                val r = poradi / sloupcu
                val c = poradi % sloupcu
                if (poradi < 0 || r >= radku) return@runCatching null
                val meze = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                android.graphics.BitmapFactory.decodeFile(soubor.absolutePath, meze)
                val w = meze.outWidth / sloupcu
                val h = meze.outHeight / radku
                @Suppress("DEPRECATION")
                val dek = if (android.os.Build.VERSION.SDK_INT >= 31) android.graphics.BitmapRegionDecoder.newInstance(soubor.absolutePath)
                else android.graphics.BitmapRegionDecoder.newInstance(soubor.absolutePath, false)
                val opt = android.graphics.BitmapFactory.Options().apply { inSampleSize = maxOf(1, w / 720) }
                dek?.decodeRegion(android.graphics.Rect(c * w, r * h, (c + 1) * w, (r + 1) * h), opt).also { dek?.recycle() }
            }.getOrNull()
        }
    }
    bmp?.let {
        Image(
            it.asImageBitmap(), null,
            Modifier.fillMaxWidth().padding(bottom = 4.dp).clip(RoundedCornerShape(10.dp)),
            contentScale = ContentScale.FillWidth,
        )
    }
}
