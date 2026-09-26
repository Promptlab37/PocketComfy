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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import cz.promptlab.h3video.data.LongMmPozornost
import cz.promptlab.h3video.data.LongMmRezim
import cz.promptlab.h3video.data.LongMmRozliseni
import cz.promptlab.h3video.data.LongMmScene
import cz.promptlab.h3video.data.t
import cz.promptlab.h3video.ui.theme.Amber
import cz.promptlab.h3video.ui.theme.Cyan
import cz.promptlab.h3video.ui.theme.Outline1
import cz.promptlab.h3video.ui.theme.Surface2
import cz.promptlab.h3video.ui.theme.TextLow
import cz.promptlab.h3video.ui.theme.TextMid

/**
 * Karta **Long MiniMax** — navazující záběry přes latent.
 *
 * Karta má dva režimy a každý ukazuje jen to, co v něm opravdu něco dělá:
 * první záběr chce reference a plátno, navázání chce předchozí celek a latent.
 * Plátno se u navázání nenabízí vůbec — latent se nedá přepočítat na jiné
 * rozměry, takže měnit ho není volba, ale chyba.
 */
@Composable
fun LongMmSection(vm: MainViewModel) {
    val scene by vm.longMm.collectAsStateWithLifecycle()
    val latenty by vm.longMmLatenty.collectAsStateWithLifecycle()
    val latentChyba by vm.longMmLatentChyba.collectAsStateWithLifecycle()
    val predchozi by vm.longMmPredchozi.collectAsStateWithLifecycle()
    val sceny by vm.longMmSceny.collectAsStateWithLifecycle()
    val delky by vm.longMmDelky.collectAsStateWithLifecycle()
    val zahozene by vm.longMmZahozene.collectAsStateWithLifecycle()
    val stavPrepisu by vm.rewriteState.collectAsStateWithLifecycle()
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val prepisujeSe = (stavPrepisu as? MainViewModel.RewriteState.Busy)?.druh ==
        MainViewModel.PraceNaPromptu.VYLEPSENI

    // Nabídka scén se plní ze serveru. Načítá se při přepnutí na navázání,
    // aby byl záběr dokončený před chvílí vidět bez restartu aplikace.
    // Přepočítává se i po doběhnutí záběru (přibude položka v galerii i latent)
    // a po změně scény — jinak by v kartě zůstal vybraný ten předminulý.
    LaunchedEffect(scene.rezim, predchozi.size, latenty.size, scene.nazev) {
        // Latenty se nactou vzdycky. U prvniho zaberu podle nich karta pozna,
        // ze uz nejaky hotovy je a da se zahodit; predvybrat zdrojove video
        // ma smysl jen pri navazovani.
        vm.loadLongMmLatenty()
        if (scene.rezim == LongMmRezim.NAVAZANI) vm.predvyberLongMmZdroj()
    }

    SectionCard(title = t("Co se dělá"), subtitle = scene.rezim.popis) {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        PillRow(
            items = LongMmRezim.entries.toList(),
            selected = scene.rezim,
            label = { it.title },
            onSelect = { vm.setLongMmRezim(it) },
        )
        // Zahozeni posledniho zaberu je NAHORE schvalne. Karta navazuje vzdy
        // na nejnovejsi zaber sceny, takze kdyz se posledni nepovede, tohle
        // je prvni vec, kterou uzivatel hleda - a dole v kartach ji nenasel.
        if (vm.longMmLzeZahodit) OutlineButton(
            text = t("Zahodit poslední záběr a zkusit ho znovu"),
            modifier = Modifier.fillMaxWidth(),
            onClick = { vm.zahodPosledniLongMmZaber() },
        )
        val zahozenoTady = zahozene.count { it.startsWith(vm.longMmNazevSouboru() + "_") }
        if (zahozenoTady > 0) {
            Text(
                t("Zahozeno: %d").format(zahozenoTady),
                style = MaterialTheme.typography.bodySmall, color = Amber,
            )
            OutlineButton(
                text = t("Vrátit zahozené zpět"),
                modifier = Modifier.fillMaxWidth(),
                onClick = { vm.vratZahozeneLongMm() },
            )
        }
      }
    }

    SectionCard(title = t("Model")) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            PillRow(
                items = cz.promptlab.h3video.data.LongMmModel.entries.toList(),
                selected = scene.model,
                label = { it.title },
                onSelect = { vm.setLongMmModel(it) },
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    t("Kroky"),
                    style = MaterialTheme.typography.labelMedium, color = TextLow,
                )
                androidx.compose.material3.Slider(
                    value = scene.kroky.toFloat(),
                    onValueChange = { vm.setLongMmKroky(Math.round(it)) },
                    valueRange = LongMmScene.MIN_KROKU.toFloat()..LongMmScene.MAX_KROKU.toFloat(),
                    steps = LongMmScene.MAX_KROKU - LongMmScene.MIN_KROKU - 1,
                    modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                    colors = sliderColors(),
                )
                Text(
                    "${scene.kroky}",
                    style = MaterialTheme.typography.labelMedium, color = TextMid,
                    modifier = Modifier.width(26.dp),
                )
            }
            // Přepínač „Dva průchody" tu byl a je pryč (4.24). Zapnutý
            // u Turba dal rozmazaný obraz s artefakty — sigmy zjemnění
            // jsou součást receptu pro LoRA TaoMate, ne obecná volba.
            // Vypnout je pořád možné, zapnout mimo sestavu 3 + 2 ne.
            if (scene.model.dvojiPruchod) Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    t("Dva průchody"),
                    style = MaterialTheme.typography.labelMedium, color = TextLow,
                )
                Spacer(Modifier.width(12.dp))
                PillRow(
                    items = listOf(false, true),
                    selected = scene.dvaPruchody,
                    label = { if (it) t("Zapnuto") else t("Vypnuto") },
                    onSelect = { vm.setLongMmDvaPruchody(it) },
                )
            }
            // Dvouprůchodová sestava: počet kroků platí pro průchod DOLE.
            // Zjemnění nahoře má pevné, odladěné sigmy (dva kroky) — stejně
            // jako karta „3 kroky", odkud je celé zapojení převzaté.
            // Ostrost detailů. Autorovo vlastní ovládání (Detail Daemon),
            // které jsme měli celou dobu na nule — a výsledky vypadaly
            // „trochu mázle". V nápovědě uzlu doporučuje 0,1–0,3.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    t("Ostrost detailů"),
                    style = MaterialTheme.typography.labelMedium, color = TextLow,
                )
                androidx.compose.material3.Slider(
                    value = scene.ostrost,
                    onValueChange = { vm.setLongMmOstrost(Math.round(it * 20f) / 20f) },
                    valueRange = -0.5f..0.5f,
                    steps = 19,
                    modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                    colors = sliderColors(),
                )
                Text(
                    if (scene.ostrost == 0f) t("vyp") else "%+.2f".format(scene.ostrost),
                    style = MaterialTheme.typography.labelMedium, color = TextMid,
                    modifier = Modifier.width(46.dp),
                )
            }
            // Vlastní posuvník, ne sdílený: ten jede od 0,5 a tady je potřeba
            // dosáhnout i na nulu (= bez LoRA) a na rozsah 0,2–0,6, který
            // u konceptových LoRA doporučuje autor modelu Eros.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    t("Síla LoRA"),
                    style = MaterialTheme.typography.labelMedium, color = TextLow,
                )
                androidx.compose.material3.Slider(
                    value = scene.silaLory,
                    onValueChange = { vm.setLongMmLoraSila(Math.round(it * 20f) / 20f) },
                    valueRange = 0f..1.2f,
                    steps = 23,
                    modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                    colors = sliderColors(),
                )
                Text(
                    if (scene.silaLory <= 0f) t("bez") else "%.2f".format(scene.silaLory),
                    style = MaterialTheme.typography.labelMedium, color = TextMid,
                    modifier = Modifier.width(38.dp),
                )
            }
        }
    }

    SectionCard(
        title = t("Zrychlovací pozornost"),
    ) {
        PillRow(
            items = LongMmPozornost.entries.toList(),
            selected = scene.pozornost,
            label = { it.title },
            onSelect = { vm.setLongMmPozornost(it) },
        )
    }


    if (scene.rezim == LongMmRezim.NAVAZANI) {
        // JEDNA volba, ne dvě. Video a latent bývaly dvě samostatná pole a
        // dokázala se rozejít — 22. 9. 2026 se tak loď přilepila k ženě
        // v kavárně. Scéna je proto jedna věc a appka si k ní dohledá obojí.
        SectionCard(
            title = t("Na kterou scénu se navazuje"),
            subtitle = t("Naváže se na její poslední hotový záběr"),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (sceny.isEmpty()) {
                    Text(
                        latentChyba ?: t("Na serveru zatím žádná scéna není. Začni prvním záběrem."),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (latentChyba != null) MaterialTheme.colorScheme.error else TextLow,
                    )
                } else {
                    Dropdown(
                        label = t("Nejnovější je nahoře"),
                        items = sceny,
                        selected = sceny.firstOrNull { it == scene.nazev.trim() } ?: sceny.first(),
                        render = { it },
                        onSelect = { vm.vyberLongMmScenu(it) },
                    )
                }
                // Co přesně se použije. Bez toho se dá jen doufat.
                val zaber = vm.longMmZaberSceny(scene.nazev.trim())
                val zTelefonu = scene.zdroj?.name?.startsWith("longmm_zdroj") == true
                Text(
                    when {
                        zTelefonu -> t("Naváže se na video z telefonu: %s").format(scene.zdroj?.name.orEmpty())
                        zaber != null -> t("Naváže se na záběr z %s · %s")
                            .format(cas(zaber.createdAt), delkaText(delky[zaber.id] ?: zaber.seconds))
                        else -> t("K téhle scéně nemám v galerii žádný záběr — vyber video z telefonu.")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (zaber == null && !zTelefonu) Amber else TextLow,
                )
                if (scene.latent.isNotBlank()) Text(
                    t("Latent: %s").format(scene.latent),
                    style = MaterialTheme.typography.bodySmall, color = TextLow,
                )
                ZdrojVideoRadek(vm, scene, zTelefonu)
                OutlineButton(
                    text = t("Načíst znovu ze serveru"),
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { vm.loadLongMmLatenty() },
                )
            }
        }

        SectionCard(
            title = t("Držet podobu z fotek"),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                PillRow(
                    items = listOf(false, true),
                    selected = scene.referenceVNavazani,
                    label = { if (it) t("Zapnuto") else t("Vypnuto") },
                    onSelect = { vm.setLongMmReferenceVNavazani(it) },
                )
                if (scene.referenceVNavazani) Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    scene.reference.forEachIndexed { i, ref ->
                        RefDlazdicka(
                            thumb = ref.nahled,
                            onPick = { uri -> vm.pickLongMmRef(i, uri) },
                            onRemove = { vm.removeLongMmRef(i) },
                        )
                    }
                    if (scene.reference.size < LongMmScene.MAX_REFERENCI) {
                        RefPridat(LongMmScene.MAX_REFERENCI - scene.reference.size) { uris ->
                            vm.pickLongMmRefs(uris)
                        }
                    }
                }
            }
        }
    } else {
        SectionCard(
            title = t("Podoba"),
            subtitle = t("Fotky, podle kterých model drží postavy a místo (nepovinné)"),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    scene.reference.forEachIndexed { i, ref ->
                        RefDlazdicka(
                            thumb = ref.nahled,
                            onPick = { uri -> vm.pickLongMmRef(i, uri) },
                            onRemove = { vm.removeLongMmRef(i) },
                        )
                    }
                    if (scene.reference.size < LongMmScene.MAX_REFERENCI) {
                        RefPridat(LongMmScene.MAX_REFERENCI - scene.reference.size) { uris ->
                            vm.pickLongMmRefs(uris)
                        }
                    }
                }
                // Rozhoduje se o tom ve chvíli, kdy se ta fotka vkládá — mít
                // volbu jen v režimu Navázat znamenalo, že ji tady nikdo nenašel.
                if (scene.reference.isNotEmpty()) {
                    Text(
                        t("Posílat je i do navazujících záběrů"),
                        style = MaterialTheme.typography.labelMedium, color = TextLow,
                    )
                    PillRow(
                        items = listOf(false, true),
                        selected = scene.referenceVNavazani,
                        label = { if (it) t("Zapnuto") else t("Vypnuto") },
                        onSelect = { vm.setLongMmReferenceVNavazani(it) },
                    )
                }
            }
        }

        // Zapíná se jen tady, u zakládání scény. Uprostřed řetězu by změnila
        // podání obrazu a spoj by byl vidět.
        SectionCard(
            title = t("Realističtější podání"),
            subtitle = t("LoRA h3-realism-people, navrch k rychlostní"),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                PillRow(
                    items = listOf(false, true),
                    selected = scene.realismus,
                    label = { if (it) t("Zapnuto") else t("Vypnuto") },
                    onSelect = { vm.setLongMmRealismus(it) },
                )
                if (scene.realismus) SilaLory(
                    hodnota = scene.realismusSila,
                    onZmena = { vm.setLongMmRealismusSila(it) },
                )
            }
        }

        SectionCard(title = t("Plátno"), subtitle = t("Pro celý řetěz se volí jen teď")) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                PillRow(
                    items = LongMmRozliseni.entries.toList(),
                    selected = scene.rozliseni,
                    label = { it.title },
                    onSelect = { vm.setLongMmRozliseni(it) },
                )
                PillRow(
                    items = LongMmPomer.entries.toList(),
                    selected = scene.pomer,
                    label = { it.title },
                    onSelect = { vm.setLongMmPomer(it) },
                )
            }
        }
    }

    SectionCard(
        title = t("Co se má dít"),
        subtitle = if (scene.rezim == LongMmRezim.PRVNI) t("Popis prvního záběru")
        else t("Popis toho, co se stane dál"),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            DarkTextField(
                value = scene.prompt,
                onValueChange = { vm.setLongMmPrompt(it) },
                placeholder = if (scene.rezim == LongMmRezim.PRVNI)
                    t("Muž z <Picture 1> stojí u okna a otočí se do místnosti.")
                else t("Přejde ke stolu a posadí se."),
                minHeight = 110.dp,
                onClear = { vm.setLongMmPrompt("") },
            )
            // Tentýž přepisovač, na kterém jede All in One: psací příručky
            // MiniMaxu zná, takže z pár českých slov udělá zadání v tvaru,
            // na který je H3 trénovaný.
            OutlineButton(
                text = if (prepisujeSe) t("Přepisuji…") else t("✨ Vylepšit zadání"),
                color = Cyan,
                modifier = Modifier.fillMaxWidth(),
            ) { if (!prepisujeSe) vm.vylepsiLongMmPrompt() }
            PrubehPrepisu(vm)
            // Překlad do angličtiny a hlavně „Vrátit původní" — po vylepšení
            // se zadání přepíše a bez tohohle by se k němu člověk nedostal.
            PrekladPromptu(vm, MainViewModel.PromptPole.LONGMM)
        }
    }

    SectionCard(title = t("Délka záběru"), subtitle = t("Týká se jen tohohle kusu, ne celku")) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.material3.Slider(
                value = scene.sekundy.toFloat(),
                onValueChange = { vm.setLongMmSekundy(Math.round(it)) },
                valueRange = LongMmScene.MIN_S.toFloat()..LongMmScene.MAX_S.toFloat(),
                steps = LongMmScene.MAX_S - LongMmScene.MIN_S - 1,
                modifier = Modifier.weight(1f),
                colors = sliderColors(),
            )
            Text(
                "%d s".format(scene.sekundy),
                style = MaterialTheme.typography.labelMedium, color = TextMid,
                modifier = Modifier.width(44.dp).padding(start = 10.dp),
            )
        }
    }

    // Jméno se zadává jen tam, kde řetěz vzniká. Při navazování ho určuje
    // vybraná scéna — psát ho podruhé by znamenalo, že se dá rozejít s tím,
    // co je vybrané nahoře.
    if (scene.rezim == LongMmRezim.PRVNI) SectionCard(
        title = t("Název scény"),
        subtitle = t("Podle něj se pak navazuje a číslují se latenty"),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            DarkTextField(
                value = scene.nazev,
                onValueChange = { vm.setLongMmNazev(it) },
                placeholder = t("kuchyne"),
                minHeight = 56.dp,
                onClear = { vm.setLongMmNazev("") },
            )
            Text(
                t("Uloží se jako %s_00001, %s_00002 …")
                    .format(vm.longMmNazevSouboru(), vm.longMmNazevSouboru()),
                style = MaterialTheme.typography.bodySmall, color = TextLow,
            )
            // Nový řetěz začíná čistou kartou. Latenty na serveru se nemažou —
            // jsou jediná cesta, jak se k dřívější scéně ještě vrátit, a
            // v nabídce nepřekážejí: řadí se od nejnovějšího a nesou v názvu
            // jméno svého řetězu.
            OutlineButton(
                text = t("Začít nový řetěz"),
                modifier = Modifier.fillMaxWidth(),
            ) { vm.resetLongMm() }
        }
    }
}

/**
 * Krátký popisek hotového záběru do nabídky: čas a délka.
 *
 * Ne zadání a ne název souboru — přepsané zadání pro H3 má klidně tři řádky
 * a soubor se jmenuje podle čísla úlohy (`68761ae4-…mp4`). Ani jedno se do
 * jednořádkové nabídky nehodí.
 */
private fun cas(millis: Long): String =
    java.text.SimpleDateFormat("d. M. HH:mm", java.util.Locale.getDefault())
        .format(java.util.Date(millis))

private fun delkaText(sekundy: Float): String =
    if (sekundy > 0f) "%.0f s".format(sekundy) else "?"

private fun popisekZaberu(
    item: cz.promptlab.h3video.data.VideoItem,
    zmerena: Float?,
): String {
    val kdy = java.text.SimpleDateFormat("d. M. HH:mm", java.util.Locale.getDefault())
        .format(java.util.Date(item.createdAt))
    // Přednost má změřená délka. Uložená je u záznamů z verzí do 3.87 špatně
    // (brala se z hlavního posuvníku appky) a zpětně ji opravit nejde jinak.
    val sekundy = zmerena ?: item.seconds
    val delka = if (sekundy > 0f) " · %.0f s".format(sekundy) else ""
    return kdy + delka
}

/**
 * Výběr videa z telefonu. Když se navazuje na hotový záběr z Galerie aplikace,
 * je tohle jen druhá možnost — proto se tu v tu chvíli neukazuje žádný název
 * souboru: ten by byl stejně jen číslo úlohy.
 */
@Composable
private fun ZdrojVideoRadek(
    vm: MainViewModel,
    scene: cz.promptlab.h3video.data.LongMmScene,
    zTelefonu: Boolean,
) {
    val pick = rememberLauncherForActivityResult(
        cz.promptlab.h3video.ui.VyberMedii()
    ) { uri -> vm.pickLongMmZdroj(uri) }
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
        Icon(Icons.Default.Movie, null, Modifier.size(22.dp), if (zTelefonu) Cyan else TextMid)
        Spacer(Modifier.width(12.dp))
        Text(
            if (zTelefonu) scene.zdroj?.name.orEmpty() else t("Vybrat video z telefonu"),
            style = MaterialTheme.typography.bodyMedium,
            color = if (zTelefonu) TextMid else TextLow,
            modifier = Modifier.weight(1f),
        )
        if (zTelefonu) {
            Icon(
                Icons.Default.Close, t("Odebrat"),
                Modifier
                    .size(20.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .clickable { vm.clearLongMmZdroj() },
                TextMid
            )
        }
    }
}

/**
 * Prázdný čtvereček ➕: v galerii jde označit **víc fotek najednou** (až do
 * [zbyva]). Dřív se galerie otevírala jen na jednu a další čtvereček se
 * objevil až po výběru — uživatel to bral tak, že víc fotek nejde.
 */
@Composable
internal fun RefPridat(zbyva: Int, onPick: (List<android.net.Uri>) -> Unit) {
    val vice = rememberLauncherForActivityResult(
        cz.promptlab.h3video.ui.VyberViceMedii(maxOf(2, zbyva))
    ) { uris -> if (uris.isNotEmpty()) onPick(uris.take(zbyva)) }
    val jedna = rememberLauncherForActivityResult(
        cz.promptlab.h3video.ui.VyberMedii()
    ) { uri -> if (uri != null) onPick(listOf(uri)) }
    val imageOnly = PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
    Box(
        Modifier
            .size(72.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Surface2)
            .border(1.dp, Outline1, RoundedCornerShape(12.dp))
            .clickable { if (zbyva >= 2) vice.launch(imageOnly) else jedna.launch(imageOnly) }
    ) {
        Icon(
            Icons.Default.AddPhotoAlternate, null,
            Modifier.align(Alignment.Center).size(22.dp), TextMid
        )
    }
}

@Composable
internal fun RefDlazdicka(
    thumb: android.graphics.Bitmap?,
    onPick: (android.net.Uri?) -> Unit,
    onRemove: () -> Unit,
) {
    val pick = rememberLauncherForActivityResult(
        cz.promptlab.h3video.ui.VyberMedii()
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
