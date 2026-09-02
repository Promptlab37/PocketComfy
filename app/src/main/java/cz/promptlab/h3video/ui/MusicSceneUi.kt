package cz.promptlab.h3video.ui

import cz.promptlab.h3video.data.t

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cz.promptlab.h3video.MainViewModel
import cz.promptlab.h3video.data.MusicScene
import cz.promptlab.h3video.ui.theme.Cyan
import cz.promptlab.h3video.ui.theme.TextLow

/**
 * Karta Hudba: styl, text písně, délka a jazyk. Hudební detaily (BPM, tónina)
 * jsou sbalené — mají rozumné výchozí hodnoty a většinou se neřeší.
 */
@Composable
fun MusicSection(vm: MainViewModel) {
    val scene by vm.music.collectAsStateWithLifecycle()

    SectionCard(
        title = "Skladba",
        subtitle = t("ACE-Step 1.5 — celá píseň za pár desítek sekund")
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
                    placeholder = t("Sloky a refrén; prázdné = instrumentálka"),
                    minHeight = 140.dp,
                    onClear = { vm.setMusicText("") },
                )
            }
        }
    }

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
                Text(t("Jazyk zpěvu"), style = MaterialTheme.typography.labelMedium, color = TextLow)
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
