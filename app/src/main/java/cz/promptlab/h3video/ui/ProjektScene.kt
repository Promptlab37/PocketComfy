package cz.promptlab.h3video.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Movie
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cz.promptlab.h3video.MainViewModel
import cz.promptlab.h3video.data.KARTY_PRO_ZABER
import cz.promptlab.h3video.data.Zaber
import cz.promptlab.h3video.data.t
import cz.promptlab.h3video.ui.theme.Amber
import cz.promptlab.h3video.ui.theme.Cyan
import cz.promptlab.h3video.ui.theme.Ok
import cz.promptlab.h3video.ui.theme.Outline1
import cz.promptlab.h3video.ui.theme.Surface1
import cz.promptlab.h3video.ui.theme.Surface2
import cz.promptlab.h3video.ui.theme.TextHi
import cz.promptlab.h3video.ui.theme.TextLow
import cz.promptlab.h3video.ui.theme.TextMid
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Karta **Projekt** — rozcestník, ne generátor.
 *
 * Drží záběry v pořadí, jejich popisy a to, čím se mají vyrobit. Vyrábí se
 * z nich přepnutím do příslušné karty; hotový výsledek se k záběru připojí
 * sám. Proto tu není tlačítko Generovat — každý záběr má vlastní.
 */
@Composable
fun ProjektSection(vm: MainViewModel) {
    val scene by vm.projekt.collectAsStateWithLifecycle()
    val projekt = scene.projekt

    if (projekt == null) {
        SectionCard(
            title = t("Projekt"),
            subtitle = t("Záběry filmu v pořadí, na jednom místě")
        ) {
            Column {
                if (scene.projekty.isEmpty()) {
                    Text(
                        t("Zatím tu nic není. Projekt drží pořadí záběrů, jejich popisy " +
                            "a hotové výsledky — appka pak ví, co k čemu patří."),
                        style = MaterialTheme.typography.bodySmall, color = TextLow,
                    )
                    Spacer(Modifier.height(12.dp))
                } else {
                    scene.projekty.forEach { p ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { vm.otevriProjekt(p.id) }
                                .padding(vertical = 10.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Default.Movie, null, Modifier.size(20.dp), Cyan)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(p.nazev, style = MaterialTheme.typography.bodyMedium, color = TextHi)
                                Text(
                                    t("%d záběrů · %d hotových").format(p.zabery.size, p.hotovych),
                                    style = MaterialTheme.typography.bodySmall, color = TextLow,
                                )
                            }
                            Icon(
                                Icons.Default.Close, t("Smazat projekt"),
                                Modifier
                                    .size(30.dp)
                                    .clip(RoundedCornerShape(50))
                                    .clickable { vm.smazProjekt(p.id) }
                                    .padding(6.dp),
                                TextLow,
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
                var nazev by remember { mutableStateOf("") }
                DarkTextField(
                    value = nazev,
                    onValueChange = { nazev = it },
                    placeholder = t("Název nového projektu"),
                    minHeight = 56.dp,
                    singleLine = true,
                    onClear = { nazev = "" },
                )
                Spacer(Modifier.height(8.dp))
                OutlineButton(t("Založit projekt"), color = Cyan) {
                    vm.novyProjekt(nazev)
                    nazev = ""
                }
            }
        }
        return
    }

    SectionCard(
        title = projekt.nazev,
        subtitle = t("%d záběrů · %d hotových").format(projekt.zabery.size, projekt.hotovych),
        trailing = {
            Text(
                t("Zpět na projekty"),
                style = MaterialTheme.typography.bodySmall, color = TextMid,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { vm.otevriProjekt(null) }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
    ) {
        Column {
            if (projekt.zabery.isEmpty()) {
                Text(
                    t("Přidej první záběr. U každého napíšeš, co se v něm děje, " +
                        "a vybereš kartu, která ho umí vyrobit."),
                    style = MaterialTheme.typography.bodySmall, color = TextLow,
                )
                Spacer(Modifier.height(12.dp))
            }
            projekt.zabery.forEachIndexed { i, z ->
                ZaberRadek(
                    vm = vm,
                    poradi = i + 1,
                    zaber = z,
                    prvni = i == 0,
                    posledni = i == projekt.zabery.lastIndex,
                )
                Spacer(Modifier.height(10.dp))
            }
            OutlineButton(
                t("Přidat záběr"),
                color = Cyan,
                icon = { Icon(Icons.Default.Add, null, Modifier.size(18.dp), Cyan) },
            ) { vm.pridejZaber() }

            scene.cekaZaber?.let { cekaId ->
                val ceka = projekt.zabery.firstOrNull { it.id == cekaId }
                if (ceka != null) {
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            t("Další hotový výsledek se připojí k záběru „%s“.")
                                .format(ceka.nazev.ifBlank { t("bez názvu") }),
                            style = MaterialTheme.typography.bodySmall, color = Amber,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            t("Zrušit"),
                            style = MaterialTheme.typography.bodySmall, color = TextMid,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { vm.zrusCekaniNaZaber() }
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ZaberRadek(
    vm: MainViewModel,
    poradi: Int,
    zaber: Zaber,
    prvni: Boolean,
    posledni: Boolean,
) {
    val ctx = LocalContext.current
    val historie by vm.history.collectAsStateWithLifecycle()
    var rozbaleno by remember(zaber.id) { mutableStateOf(zaber.nazev.isBlank()) }

    val polozka = zaber.vysledek?.let { id -> historie.firstOrNull { it.id == id } }
    var nahled by remember(zaber.vysledek) { mutableStateOf<android.graphics.Bitmap?>(null) }
    LaunchedEffect(zaber.vysledek) {
        nahled = polozka?.let { p ->
            withContext(Dispatchers.IO) {
                when {
                    p.isModel3d -> cz.promptlab.h3video.util.ImageUtils.nahled3d(ctx, p.file(ctx))
                        .takeIf { it.exists() }
                        ?.let { cz.promptlab.h3video.util.ImageUtils.loadFileThumb(it) }
                    p.isImage -> cz.promptlab.h3video.util.ImageUtils.loadFileThumb(p.file(ctx))
                    p.isAudio -> null
                    else -> prvniSnimek(p.file(ctx))
                }
            }
        }
    }

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Surface1)
            .border(1.dp, if (zaber.jeHotovy) Ok.copy(alpha = .4f) else Outline1, RoundedCornerShape(14.dp))
            .padding(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(width = 76.dp, height = 48.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Surface2),
                contentAlignment = Alignment.Center,
            ) {
                nahled?.let {
                    Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                }
                if (nahled == null) {
                    Text(
                        "$poradi",
                        style = MaterialTheme.typography.titleMedium,
                        color = if (zaber.jeHotovy) Ok else TextLow,
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { rozbaleno = !rozbaleno }
                    .padding(vertical = 4.dp)
            ) {
                Text(
                    zaber.nazev.ifBlank { t("Záběr %d").format(poradi) },
                    style = MaterialTheme.typography.bodyMedium, color = TextHi,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Text(
                    when {
                        zaber.jeHotovy -> t("hotovo · %s").format(zaber.karta?.short ?: "")
                        zaber.karta != null -> t("čeká na výrobu · %s").format(zaber.karta.short)
                        else -> t("bez karty")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (zaber.jeHotovy) Ok else TextLow,
                )
            }
            Icon(
                Icons.Default.ArrowUpward, t("Nahoru"),
                Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(50))
                    .clickable(enabled = !prvni) { vm.presunZaber(zaber.id, -1) }
                    .padding(7.dp),
                if (prvni) TextLow.copy(alpha = .3f) else TextMid,
            )
            Icon(
                Icons.Default.ArrowDownward, t("Dolů"),
                Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(50))
                    .clickable(enabled = !posledni) { vm.presunZaber(zaber.id, 1) }
                    .padding(7.dp),
                if (posledni) TextLow.copy(alpha = .3f) else TextMid,
            )
        }

        if (rozbaleno) {
            Spacer(Modifier.height(10.dp))
            DarkTextField(
                value = zaber.nazev,
                onValueChange = { v -> vm.upravZaber(zaber.id) { it.copy(nazev = v) } },
                placeholder = t("Označení, třeba „1A — Anna vejde do haly“"),
                minHeight = 56.dp,
                singleLine = true,
                onClear = { vm.upravZaber(zaber.id) { it.copy(nazev = "") } },
            )
            Spacer(Modifier.height(8.dp))
            DarkTextField(
                value = zaber.popis,
                onValueChange = { v -> vm.upravZaber(zaber.id) { it.copy(popis = v) } },
                placeholder = t("Co se v záběru děje — odsud se předvyplní zadání"),
                minHeight = 92.dp,
                onClear = { vm.upravZaber(zaber.id) { it.copy(popis = "") } },
            )
            Spacer(Modifier.height(10.dp))
            Text(t("Čím vyrobit"), style = MaterialTheme.typography.labelMedium, color = TextLow)
            Spacer(Modifier.height(6.dp))
            PillRow(
                items = KARTY_PRO_ZABER,
                selected = zaber.karta,
                label = { it?.short ?: "" },
                onSelect = { k -> vm.upravZaber(zaber.id) { it.copy(karta = k) } },
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (zaber.karta != null) {
                    OutlineButton(t("Vyrobit"), color = Cyan) { vm.pripravZaber(zaber.id) }
                }
                if (zaber.jeHotovy) {
                    OutlineButton(t("Odpojit výsledek"), color = TextMid) {
                        vm.pripojVysledek(zaber.id, null)
                    }
                }
                OutlineButton(t("Smazat záběr"), color = TextMid) { vm.smazZaber(zaber.id) }
            }
        }
    }
}

/** První snímek videa pro dlaždici záběru. */
private fun prvniSnimek(f: java.io.File): android.graphics.Bitmap? = runCatching {
    val r = android.media.MediaMetadataRetriever()
    try {
        r.setDataSource(f.absolutePath)
        r.getFrameAtTime(0)
    } finally {
        runCatching { r.release() }
    }
}.getOrNull()
