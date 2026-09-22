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
    val stavPrepisu by vm.rewriteState.collectAsStateWithLifecycle()
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val prepisujeSe = (stavPrepisu as? MainViewModel.RewriteState.Busy)?.druh ==
        MainViewModel.PraceNaPromptu.VYLEPSENI

    // Nabídku latentů plní server. Načítá se při přepnutí na navázání, aby byl
    // záběr dokončený před chvílí vidět bez restartu appky. Zároveň se předvybere
    // poslední výsledek téhle karty, ať uživatel nemusí vybírat nic.
    LaunchedEffect(scene.rezim, predchozi.size) {
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
        SectionCard(
            title = t("Na co se navazuje"),
            subtitle = t("Poslední hotový celek téhle scény"),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // Navazuje se skoro vždycky na to, co appka vyrobila před chvílí.
                // Nabídka je proto z Galerie aplikace; do galerie telefonu se
                // výsledky kopírují jen při zapnutém automatickém ukládání,
                // takže systémový výběr by u většiny lidí ukázal prázdno.
                if (predchozi.isNotEmpty()) {
                    Dropdown(
                        label = t("Předchozí záběry téhle karty"),
                        items = predchozi,
                        selected = predchozi.firstOrNull { it.file(ctx) == scene.zdroj }
                            ?: predchozi.first(),
                        render = { it.displayTitle },
                        onSelect = { vm.setLongMmZdrojZGalerie(it) },
                    )
                }
                ZdrojVideoRadek(vm, scene)
            }
        }

        SectionCard(
            title = t("Latent předchozího záběru"),
            subtitle = t("Odsud se pokračuje bez ztráty kvality"),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (latenty.isEmpty()) {
                    Text(
                        latentChyba ?: t("Na serveru zatím žádný latent není. Začni prvním záběrem."),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (latentChyba != null) MaterialTheme.colorScheme.error else TextLow,
                    )
                } else {
                    Dropdown(
                        label = t("Nejnovější je nahoře"),
                        items = latenty,
                        selected = scene.latent.ifBlank { latenty.first() },
                        render = { it },
                        onSelect = { vm.setLongMmLatent(it) },
                    )
                }
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

    SectionCard(
        title = t("Název řetězu"),
        subtitle = t("Latenty se pod ním na serveru číslují"),
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
        }
    }
}

@Composable
private fun ZdrojVideoRadek(vm: MainViewModel, scene: cz.promptlab.h3video.data.LongMmScene) {
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
        Icon(Icons.Default.Movie, null, Modifier.size(22.dp), if (scene.zdroj != null) Cyan else TextMid)
        Spacer(Modifier.width(12.dp))
        Text(
            scene.zdroj?.name ?: t("Vybrat video z galerie"),
            style = MaterialTheme.typography.bodyMedium,
            color = if (scene.zdroj != null) TextMid else TextLow,
            modifier = Modifier.weight(1f),
        )
        if (scene.zdroj != null) {
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
