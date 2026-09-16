package cz.promptlab.h3video.ui

import cz.promptlab.h3video.data.t

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cz.promptlab.h3video.MainViewModel
import cz.promptlab.h3video.data.MusicMotor
import cz.promptlab.h3video.data.MusicPlan
import cz.promptlab.h3video.data.MusicRezim
import cz.promptlab.h3video.data.MusicScene
import cz.promptlab.h3video.ui.theme.Amber
import cz.promptlab.h3video.ui.theme.Cyan
import cz.promptlab.h3video.ui.theme.TextLow

/**
 * Výběr nahrávky, ze které se vezme melodie.
 *
 * Z původní nahrávky se **nepřebírá zvuk**, jen noty — a to je přesně ta
 * věc, kterou nikdo nečeká, takže to karta říká nahlas. Bez toho by člověk
 * čekal předabovanou píseň a dostal novou nahrávku té melodie.
 */
@Composable
private fun PredlohaSekce(vm: MainViewModel, scene: MusicScene) {
    val chyba by vm.musicPredlohaChyba.collectAsStateWithLifecycle()
    val vyber = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) {
        vm.pickMusicPredloha(it)
    }
    SectionCard(
        title = t("Nahrávka"),
        subtitle = scene.predloha?.name ?: t("Zatím žádná — vyber skladbu z telefonu")
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlineButton(
                text = if (scene.predloha == null) t("Vybrat nahrávku")
                else t("Vybrat jinou"),
                modifier = Modifier.fillMaxWidth(),
                onClick = { vyber.launch("audio/*") },
            )
            if (scene.predloha != null) {
                OutlineButton(
                    text = t("Odebrat nahrávku"),
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { vm.clearMusicPredloha() },
                )
            }
            chyba?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = Amber)
            }
            Text(
                t("Z nahrávky se vezme jen melodie. Zpěv i doprovod vzniknou znovu ") +
                    t("podle stylu a textu výš — z původního zvuku nezůstane nic."),
                style = MaterialTheme.typography.bodySmall, color = TextLow
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        t("Převzít i akordy"),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        t("Drží i harmonii předlohy. Nový styl se pak prosadí míň."),
                        style = MaterialTheme.typography.bodySmall, color = TextLow
                    )
                }
                Switch(
                    checked = scene.predlohaAkordy,
                    onCheckedChange = { vm.setMusicPredlohaAkordy(it) },
                    colors = switchColors(),
                )
            }
        }
    }
}

/**
 * Karta Hudba: model, styl, text písně a délka.
 *
 * Každý z obou modelů bere jiné zadání, a karta ukazuje jen to, co vybraný
 * model opravdu použije — ACE-Step jazyk zpěvu, tempo a tóninu, YuE2 plán not.
 * YuE2 tyhle tři vstupy vůbec nemá (řídí si je sám ze stylu a textu), takže by
 * to byly knoflíky, které graf zahodí.
 */
@Composable
fun MusicSection(vm: MainViewModel) {
    val scene by vm.music.collectAsStateWithLifecycle()
    val yue2 = scene.motor == MusicMotor.YUE2

    SectionCard(
        title = t("Čím skládat"),
        subtitle = t("Dva různé modely — každý skládá jinak a bere jiné zadání")
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            PillRow(
                items = MusicMotor.entries.toList(),
                selected = scene.motor,
                label = { it.title },
                onSelect = { vm.setMusicMotor(it) },
            )
            Text(
                scene.motor.detail,
                style = MaterialTheme.typography.bodySmall, color = TextLow
            )
        }
    }

    if (yue2) {
        SectionCard(
            title = t("Odkud vzít melodii"),
            subtitle = t("Buď si ji model vymyslí, nebo ji vezme z nahrávky, kterou mu dáš")
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                PillRow(
                    items = MusicRezim.entries.toList(),
                    selected = scene.rezim,
                    label = { it.title },
                    onSelect = { vm.setMusicRezim(it) },
                )
                Text(
                    scene.rezim.detail,
                    style = MaterialTheme.typography.bodySmall, color = TextLow
                )
            }
        }
        if (scene.predelava) PredlohaSekce(vm, scene)
    }

    SectionCard(
        title = "Skladba",
        subtitle = if (yue2) t("YuE2 3B — nejdřív noty, pak zpěv")
        else t("ACE-Step 1.5 — celá píseň za pár desítek sekund")
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column {
                Text("Styl", style = MaterialTheme.typography.labelMedium, color = TextLow)
                Spacer(Modifier.height(8.dp))
                DarkTextField(
                    value = scene.styl,
                    onValueChange = { vm.setMusicStyl(it) },
                    placeholder = t("Žánr, nástroje, nálada, hlas zpěváka…"),
                    minHeight = 90.dp,
                    onClear = { vm.setMusicStyl("") },
                )
            }
            Column {
                Text(
                    t("Text písně (nepovinný)"),
                    style = MaterialTheme.typography.labelMedium, color = TextLow
                )
                Spacer(Modifier.height(8.dp))
                DarkTextField(
                    value = scene.text,
                    onValueChange = { vm.setMusicText(it) },
                    placeholder = if (yue2)
                        t("Sloky a refrén anglicky; prázdné = instrumentálka")
                    else t("Sloky a refrén; prázdné = instrumentálka"),
                    minHeight = 140.dp,
                    onClear = { vm.setMusicText("") },
                )
            }
        }
    }

    if (yue2) {
        // U YuE2 je délka jen strop: model dozpívá, kde má píseň konec, a
        // latent si podle toho sám určí. Slibovat přesnou délku by bylo lhaní.
        SectionCard(
            title = t("Nejvýše"),
            trailing = {
                Text(
                    "${scene.maxSeconds} s",
                    style = MaterialTheme.typography.headlineSmall, color = Cyan
                )
            }
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Slider(
                    value = scene.maxSeconds.toFloat(),
                    onValueChange = { vm.setMusicMaxSeconds(it.toInt()) },
                    valueRange = MusicScene.YUE2_MIN_SECONDS.toFloat()..
                        MusicScene.YUE2_MAX_SECONDS.toFloat(),
                    colors = sliderColors()
                )
                Text(
                    t("Strop délky. Kratší text = kratší píseň, model skončí sám."),
                    style = MaterialTheme.typography.bodySmall, color = TextLow
                )
            }
        }

        // Při předělávání noty přicházejí z nahrávky a uzel `YuE2GenerateABC`
        // v grafu vůbec není — plán by byl knoflík, který graf zahodí.
        if (!scene.predelava) SkladaciSekce(
            title = t("Plán skladby"),
            souhrn = scene.plan.title,
            klic = "plan-yue2",
        ) {
            SectionCard(
                title = t("Co si model napíše, než začne zpívat"),
                subtitle = t("Noty drží melodii pohromadě — bez nich se píseň rozvolní")
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    PillRow(
                        items = MusicPlan.entries.toList(),
                        selected = scene.plan,
                        label = { it.title },
                        onSelect = { vm.setMusicPlan(it) },
                    )
                    Text(
                        scene.plan.detail,
                        style = MaterialTheme.typography.bodySmall, color = TextLow
                    )
                }
            }
        }
    } else {
        SectionCard(
            title = t("Délka a jazyk"),
            trailing = {
                Text(
                    "${scene.seconds} s",
                    style = MaterialTheme.typography.headlineSmall, color = Cyan
                )
            }
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Slider(
                    value = scene.seconds.toFloat(),
                    onValueChange = { vm.setMusicSeconds(it.toInt()) },
                    valueRange = MusicScene.MIN_SECONDS.toFloat()..MusicScene.MAX_SECONDS.toFloat(),
                    colors = sliderColors()
                )
                Column {
                    Text(
                        t("Jazyk zpěvu"),
                        style = MaterialTheme.typography.labelMedium, color = TextLow
                    )
                    Spacer(Modifier.height(8.dp))
                    PillRow(
                        items = MusicScene.LANGUAGES,
                        selected = scene.language,
                        label = { it },
                        onSelect = { vm.setMusicLanguage(it) }
                    )
                }
            }
        }

        SkladaciSekce(
            title = t("Hudební detaily"),
            souhrn = "${scene.bpm} BPM · ${scene.keyscale}",
            klic = "hudebni-detaily",
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Column {
                    Row {
                        Text(
                            "Tempo",
                            style = MaterialTheme.typography.labelMedium, color = TextLow,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            "${scene.bpm} BPM",
                            style = MaterialTheme.typography.labelMedium, color = Cyan
                        )
                    }
                    Slider(
                        value = scene.bpm.toFloat(),
                        onValueChange = { vm.setMusicBpm(it.toInt()) },
                        valueRange = 60f..220f,
                        colors = sliderColors()
                    )
                }
                Dropdown(t("Tónina"), MusicScene.KEYSCALES, scene.keyscale, { it }) { v ->
                    vm.setMusicKeyscale(v)
                }
            }
        }
    }
}
