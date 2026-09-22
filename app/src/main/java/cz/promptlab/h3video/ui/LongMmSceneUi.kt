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
    val stavPrepisu by vm.rewriteState.collectAsStateWithLifecycle()
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val prepisujeSe = (stavPrepisu as? MainViewModel.RewriteState.Busy)?.druh ==
        MainViewModel.PraceNaPromptu.VYLEPSENI

    // Nabídka scén se plní ze serveru. Načítá se při přepnutí na navázání,
    // aby byl záběr dokončený před chvílí vidět bez restartu aplikace.
    // Přepočítává se i po doběhnutí záběru (přibude položka v galerii i latent)
    // a po změně scény — jinak by v kartě zůstal vybraný ten předminulý.
    LaunchedEffect(scene.rezim, predchozi.size, latenty.size, scene.nazev) {
        if (scene.rezim == LongMmRezim.NAVAZANI) {
            vm.loadLongMmLatenty()
            vm.predvyberLongMmZdroj()
        }
    }

    SectionCard(title = t("Co se dělá"), subtitle = scene.rezim.popis) {
        PillRow(
            items = LongMmRezim.entries.toList(),
            selected = scene.rezim,
            label = { it.title },
            onSelect = { vm.setLongMmRezim(it) },
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
    } else {
        SectionCard(
            title = t("Podoba"),
            subtitle = t("Fotky, podle kterých model drží postavy a místo (nepovinné)"),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                scene.reference.forEachIndexed { i, ref ->
                    RefDlazdicka(
                        thumb = ref.nahled,
                        onPick = { uri -> vm.pickLongMmRef(i, uri) },
                        onRemove = { vm.removeLongMmRef(i) },
                    )
                }
                if (scene.reference.size < LongMmScene.MAX_REFERENCI) {
                    RefDlazdicka(
                        thumb = null,
                        onPick = { uri -> vm.pickLongMmRef(scene.reference.size, uri) },
                        onRemove = {},
                    )
                }
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
            // Překlad do angličtiny a hlavně „Vrátit původní" — po vylepšení
            // se zadání přepíše a bez tohohle by se k němu člověk nedostal.
            PrekladPromptu(vm, MainViewModel.PromptPole.LONGMM)
        }
    }

    SectionCard(title = t("Délka záběru"), subtitle = t("Týká se jen tohohle kusu, ne celku")) {
        PillRow(
            items = LongMmScene.DELKY,
            selected = scene.sekundy,
            label = { "$it s" },
            onSelect = { vm.setLongMmSekundy(it) },
        )
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
        ActivityResultContracts.PickVisualMedia()
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

@Composable
private fun RefDlazdicka(
    thumb: android.graphics.Bitmap?,
    onPick: (android.net.Uri?) -> Unit,
    onRemove: () -> Unit,
) {
    val pick = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
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
