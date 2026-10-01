package cz.promptlab.h3video.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cz.promptlab.h3video.MainViewModel
import cz.promptlab.h3video.data.SbDialogy
import cz.promptlab.h3video.data.SbFilmPrepis
import cz.promptlab.h3video.data.SbFilmScene
import cz.promptlab.h3video.data.VoiceSource
import cz.promptlab.h3video.data.t
import cz.promptlab.h3video.ui.theme.Amber
import cz.promptlab.h3video.ui.theme.Cyan
import cz.promptlab.h3video.ui.theme.TextHi
import cz.promptlab.h3video.ui.theme.TextLow
import cz.promptlab.h3video.ui.theme.TextMid
import cz.promptlab.h3video.util.LinePlayer

/**
 * Dialogy přes Higgs ve Filmu ze storyboardu (5.64): hlas každé postavy
 * (knihovna Higgse, nebo vlastní vzorek) a nahrávka každé repliky.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SbDialogySekce(vm: MainViewModel, scene: SbFilmScene) {
    val repliky = scene.panely.flatMap { p -> SbFilmPrepis.repliky(p.repliky).mapIndexed { i, (kdo, text) -> Triple(p, i, kdo.trim() to text) } }
    if (repliky.isEmpty()) return
    val voices by vm.voices.collectAsStateWithLifecycle()
    val stavy by vm.sbNamlouvani.collectAsStateWithLifecycle()
    val player = remember { LinePlayer() }
    var hraje by remember { mutableStateOf<Int?>(null) }
    DisposableEffect(Unit) { onDispose { player.stop() } }
    var vzorekPro by remember { mutableStateOf<String?>(null) }
    val vyberVzorek = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        vzorekPro?.let { vm.setSbVzorekMluvciho(it, uri) }
        vzorekPro = null
    }
    var zvukPro by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    val vyberZvuk = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        zvukPro?.let { (c, i) -> vm.setSbZvukRepliky(c, i, uri) }
        zvukPro = null
    }

    SkladaciSekce(
        title = t("Dialogy přes Higgs"),
        souhrn = if (!scene.dialogyHiggs) t("vypnuto")
        else "%d / %d".format(repliky.size - SbDialogy.chybejici(scene).size, repliky.size),
        klic = "sbfilm_dialogy",
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = scene.dialogyHiggs, onCheckedChange = { vm.setSbDialogyHiggs(it) })
            Spacer(Modifier.width(8.dp))
            Text(t("Zapnout"), style = MaterialTheme.typography.bodyMedium, color = TextMid)
        }
        if (!scene.dialogyHiggs) return@SkladaciSekce

        // Hlas každé postavy.
        val mluvci = repliky.map { it.third.first }.distinctBy { it.lowercase() }
        mluvci.forEach { kdo ->
            var otevreno by remember(kdo) { mutableStateOf(false) }
            val hlas = SbDialogy.hlasMluvciho(scene, kdo)
            Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(kdo, style = MaterialTheme.typography.labelLarge, color = Amber, modifier = Modifier.width(110.dp))
                    Row(
                        Modifier.weight(1f).clip(RoundedCornerShape(10.dp))
                            .clickable { otevreno = !otevreno; if (otevreno && voices.isEmpty()) vm.loadVoices() }
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Default.GraphicEq, null, Modifier.size(16.dp), Cyan)
                        Spacer(Modifier.width(6.dp))
                        Text(
                            when (hlas) {
                                is VoiceSource.Library -> hlas.voiceName
                                is VoiceSource.Sample -> hlas.label
                                null -> t("Vybrat hlas")
                            },
                            style = MaterialTheme.typography.bodySmall, color = if (hlas != null) TextHi else TextMid,
                        )
                    }
                    if (hlas != null) IconButton(onClick = { vm.setSbHlasMluvciho(kdo, null) }) {
                        Icon(Icons.Default.Close, t("Odebrat hlas"), Modifier.size(16.dp), TextMid)
                    }
                }
                if (otevreno) Column(Modifier.padding(start = 110.dp)) {
                    if (voices.isEmpty()) Text(t("Načítám hlasy z počítače…"), style = MaterialTheme.typography.bodySmall, color = TextMid)
                    voices.forEach { v ->
                        Text(
                            v.name, style = MaterialTheme.typography.bodyMedium, color = TextHi,
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                                .clickable { vm.setSbHlasMluvciho(kdo, VoiceSource.Library(v.id, v.name)); otevreno = false }
                                .padding(horizontal = 8.dp, vertical = 8.dp),
                        )
                    }
                    OutlineButton(t("Naklonovat ze zvukového souboru"), color = TextMid, modifier = Modifier.fillMaxWidth()) {
                        vzorekPro = kdo; otevreno = false; vyberVzorek.launch("audio/*")
                    }
                }
            }
        }

        // Nahrávka každé repliky.
        repliky.forEach { (p, i, rep) ->
            val (kdo, text) = rep
            val klic = SbDialogy.klic(p.cislo, i)
            val nahravka = SbDialogy.platna(scene, p, i, kdo, text)
            val kod = p.cislo * 100 + i
            Column(Modifier.fillMaxWidth().padding(top = 10.dp)) {
                Text("${p.cislo} · $kdo: „$text“", style = MaterialTheme.typography.bodySmall, color = TextHi)
                val stav = stavy[klic]
                Text(
                    stav ?: nahravka?.let { "%.1f s".format(it.delkaS) } ?: t("bez nahrávky"),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (stav == null && nahravka != null) Cyan else TextLow,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(onClick = { vm.namluvSbRepliky(p.cislo, i) }) {
                        Icon(Icons.Default.Mic, t("Namluvit"), tint = Cyan)
                    }
                    if (nahravka != null) IconButton(onClick = {
                        if (hraje == kod) { player.stop(); hraje = null }
                        else { hraje = kod; player.play(kod, nahravka.soubor) { hraje = null } }
                    }) {
                        Icon(if (hraje == kod) Icons.Default.Stop else Icons.Default.PlayArrow, t("Přehrát"), tint = TextHi)
                    }
                    IconButton(onClick = { zvukPro = p.cislo to i; vyberZvuk.launch("audio/*") }) {
                        Icon(Icons.Default.AudioFile, t("Zvukový soubor"), tint = TextMid)
                    }
                    if (nahravka != null) IconButton(onClick = { vm.smazSbNahravku(p.cislo, i) }) {
                        Icon(Icons.Default.Close, t("Odebrat"), tint = TextLow)
                    }
                }
            }
        }
        if (SbDialogy.chybejici(scene).isNotEmpty()) {
            Spacer(Modifier.size(8.dp))
            OutlineButton(t("Namluvit repliky"), color = Cyan, modifier = Modifier.fillMaxWidth()) { vm.namluvSbRepliky() }
        }
    }
}
