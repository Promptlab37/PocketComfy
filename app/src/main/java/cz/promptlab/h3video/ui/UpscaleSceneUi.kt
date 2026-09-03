package cz.promptlab.h3video.ui

import cz.promptlab.h3video.data.t

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cz.promptlab.h3video.MainViewModel
import cz.promptlab.h3video.data.DlssStyl
import cz.promptlab.h3video.data.UpscaleMetoda
import cz.promptlab.h3video.data.UpscaleScene
import cz.promptlab.h3video.data.upscaleHints
import cz.promptlab.h3video.ui.theme.Outline1
import cz.promptlab.h3video.ui.theme.Surface2
import cz.promptlab.h3video.ui.theme.TextLow
import cz.promptlab.h3video.ui.theme.TextMid
import kotlin.math.roundToInt

/**
 * Karta **Zvětšit** — dvě cesty k větší nebo ostřejší fotce.
 *
 * *SeedVR2 gigapixel* jede podle uživatelova workflow převzatého 1:1; volí se
 * jen fotka a mřížka dlaždic, zbytek je vyladěný v předloze. *DLSS 5* je od
 * 3.02 rychlá alternativa: nedokresluje, jen rekonstruuje, co ve fotce je,
 * zato v sekundách. Nastavení má proto vlastní — míru zvětšení, styl, sílu
 * a rekonstrukci pleti.
 */
@Composable
fun UpscaleSection(vm: MainViewModel) {
    val scene by vm.upscale.collectAsStateWithLifecycle()

    val imageOnly = PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
    val pick = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> vm.pickUpscaleImage(uri) }

    SectionCard(
        title = t("Fotka ke zvětšení"),
        subtitle = t("Vezme se v plném rozlišení, bez překódování")
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(4f / 3f)
                .clip(RoundedCornerShape(14.dp))
                .background(Surface2)
                .border(1.dp, Outline1, RoundedCornerShape(14.dp))
                .clickable { pick.launch(imageOnly) }
        ) {
            val thumb = scene.thumb
            if (thumb != null) {
                Image(
                    bitmap = thumb.asImageBitmap(),
                    contentDescription = t("Fotka ke zvětšení"),
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(28.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Surface2)
                        .clickable { vm.clearUpscaleImage() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Close, "Odebrat", Modifier.size(16.dp), TextMid)
                }
            } else {
                Icon(
                    Icons.Default.AddPhotoAlternate, "Vybrat fotku",
                    Modifier.align(Alignment.Center).size(34.dp), TextMid
                )
            }
        }
    }

    SectionCard(
        title = t("Čím zvětšit"),
        subtitle = t("Dvě různé cesty — jedna dokresluje, druhá rekonstruuje")
    ) {
        Column {
            PillRow(
                items = UpscaleMetoda.entries.toList(),
                selected = scene.metoda,
                label = { t(it.stitek) },
                onSelect = { vm.setUpscaleMetoda(it) },
            )
            Spacer(Modifier.height(6.dp))
            Text(
                t(scene.metoda.popis),
                style = MaterialTheme.typography.bodySmall, color = TextLow
            )
        }
    }

    if (scene.metoda == UpscaleMetoda.SEEDVR2) SectionCard(
        title = t("Velikost zvětšení"),
        subtitle = t("Fotka se rozdělí na dlaždice, každá se zvětší na 3200 px a slepí se")
    ) {
        Column {
            PillRow(
                items = UpscaleScene.GRIDS,
                selected = scene.grid,
                label = {
                    val n = it.substringBefore('x').toIntOrNull() ?: 2
                    "$it · ~${n * 3} tis. px"
                },
                onSelect = { vm.setUpscaleGrid(it) },
            )
            Spacer(Modifier.height(6.dp))
            Text(
                t("Víc dlaždic = větší výsledek, ale úměrně delší běh. 2×2 je vyladěné výchozí."),
                style = MaterialTheme.typography.bodySmall, color = TextLow
            )
        }
    }

    if (scene.metoda == UpscaleMetoda.DLSS) SectionCard(
        title = t("Nastavení DLSS 5"),
        subtitle = t("Neural Rendering na grafické kartě, výsledek za pár sekund")
    ) {
        Column {
            Text(t("Zvětšení"), style = MaterialTheme.typography.labelMedium, color = TextLow)
            Spacer(Modifier.height(8.dp))
            PillRow(
                items = UpscaleScene.DLSS_NASOBKY,
                selected = scene.dlssNasobek,
                label = { if (it == "1x") t("1× jen doostřit") else it },
                onSelect = { vm.setDlssNasobek(it) },
            )

            Spacer(Modifier.height(14.dp))
            Text(t("Styl"), style = MaterialTheme.typography.labelMedium, color = TextLow)
            Spacer(Modifier.height(8.dp))
            PillRow(
                items = DlssStyl.entries.toList(),
                selected = scene.dlssStyl,
                label = { t(it.stitek) },
                onSelect = { vm.setDlssStyl(it) },
            )

            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(t("Síla"), style = MaterialTheme.typography.labelMedium, color = TextLow)
                Slider(
                    value = scene.dlssSila,
                    onValueChange = { v -> vm.setDlssSila((v * 20).roundToInt() / 20f) },
                    valueRange = 0f..1f,
                    modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                    colors = sliderColors()
                )
                Text(
                    "%.2f".format(scene.dlssSila),
                    style = MaterialTheme.typography.labelMedium, color = TextMid
                )
            }
            Text(
                t("Nad 1.00 už runtime nic nepřidá; níž se výsledek přimíchává zpátky k předloze."),
                style = MaterialTheme.typography.bodySmall, color = TextLow
            )

            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(t("Rekonstruovat pleť"), style = MaterialTheme.typography.bodyMedium)
                    Text(
                        t("Model si sám najde kůži a dopočítá póry. Na fotky bez lidí to vypni."),
                        style = MaterialTheme.typography.bodySmall, color = TextLow
                    )
                }
                Switch(
                    checked = scene.dlssPlet,
                    onCheckedChange = { vm.setDlssPlet(it) },
                    colors = switchColors(),
                )
            }

            upscaleHints(scene).forEach { hint ->
                Spacer(Modifier.height(6.dp))
                Text(hint, style = MaterialTheme.typography.bodySmall, color = TextLow)
            }
        }
    }
}
