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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import cz.promptlab.h3video.data.DanceRozsah
import cz.promptlab.h3video.data.DanceScene
import cz.promptlab.h3video.data.DanceStyl
import cz.promptlab.h3video.data.t
import cz.promptlab.h3video.ui.theme.Amber
import cz.promptlab.h3video.ui.theme.Outline1
import cz.promptlab.h3video.ui.theme.Surface2
import cz.promptlab.h3video.ui.theme.TextLow
import cz.promptlab.h3video.ui.theme.TextMid

/**
 * Karta **Dance** — Wan-Dancer.
 *
 * Fotka + hudba → video, kde ten člověk tančí do rytmu. Vzorové video
 * s tancem se sem nedává, model ho nepřijímá; choreografii si vymyslí sám.
 *
 * Styly a rozsah pohybu jsou v nabídce česky. Model sám je vycvičený na
 * čínské zadání, ale překlad si drží appka — do karty se nedostane.
 */
@Composable
fun DanceSection(vm: MainViewModel) {
    val scene by vm.dance.collectAsStateWithLifecycle()
    val hudbaChyba by vm.danceHudbaChyba.collectAsStateWithLifecycle()

    val imageOnly = PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
    val vyberFotky = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> vm.pickDanceFotku(uri) }
    val vyberHudby = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> vm.pickDanceHudbu(uri) }

    SectionCard(
        title = t("Kdo bude tančit"),
        subtitle = t("Fotka člověka — z ní se bere podoba"),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(4f / 3f)
                .clip(RoundedCornerShape(14.dp))
                .background(Surface2)
                .border(1.dp, Outline1, RoundedCornerShape(14.dp))
                .clickable { vyberFotky.launch(imageOnly) }
        ) {
            val nahled = scene.nahled
            if (nahled != null) {
                Image(
                    bitmap = nahled.asImageBitmap(),
                    contentDescription = t("Fotka tanečníka"),
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
                        .clickable { vm.clearDanceFotku() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Close, t("Odebrat"), Modifier.size(16.dp), TextMid)
                }
            } else {
                Icon(
                    Icons.Default.AddPhotoAlternate, t("Vybrat fotku"),
                    Modifier.align(Alignment.Center).size(34.dp), TextMid
                )
            }
        }
    }

    SectionCard(
        title = t("Hudba"),
        subtitle = scene.hudba?.name ?: t("Na tuhle skladbu se bude tančit"),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlineButton(
                text = if (scene.hudba == null) t("Vybrat hudbu") else t("Vybrat jinou"),
                modifier = Modifier.fillMaxWidth(),
                onClick = { vyberHudby.launch("audio/*") },
            )
            if (scene.hudba != null) {
                OutlineButton(
                    text = t("Odebrat hudbu"),
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { vm.clearDanceHudbu() },
                )
                Text(
                    t("Délka skladby %.1f s").format(scene.hudbaSekund),
                    style = MaterialTheme.typography.bodySmall, color = Amber,
                )
            }
            if (hudbaChyba != null) {
                Text(
                    hudbaChyba!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }

    SectionCard(title = t("Rozlišení"), subtitle = t("Vyšší je 2,25× víc bodů a úměrně déle trvá")) {
        PillRow(
            items = cz.promptlab.h3video.data.DanceKvalita.entries.toList(),
            selected = scene.kvalita,
            label = { it.title },
            onSelect = { vm.setDanceKvalita(it) },
        )
    }

    SectionCard(title = t("Druh tance"), subtitle = t("Podle toho se řídí styl pohybu")) {
        PillRow(
            items = DanceStyl.entries.toList(),
            selected = scene.styl,
            label = { it.title },
            onSelect = { vm.setDanceStyl(it) },
        )
    }

    SectionCard(title = t("Rozsah pohybu"), subtitle = t("Jak velká gesta má dělat")) {
        PillRow(
            items = DanceRozsah.entries.toList(),
            selected = scene.rozsah,
            label = { it.title },
            onSelect = { vm.setDanceRozsah(it) },
        )
    }

    SectionCard(title = t("Délka"), subtitle = t("Video vzniká po pětisekundových úsecích")) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            PillRow(
                items = DanceScene.DELKY,
                selected = scene.sekundy,
                label = { "$it s" },
                onSelect = { vm.setDanceSekundy(it) },
            )
            Text(
                t("%d úseků · %d snímků při %d fps")
                    .format(scene.useku, scene.useku * DanceScene.SNIMKU_NA_USEK, DanceScene.FPS),
                style = MaterialTheme.typography.bodySmall, color = TextLow,
            )
        }
    }

    SectionCard(
        title = t("Doplnění zadání (nepovinné)"),
        subtitle = t("Prostředí nebo oblečení, když na nich záleží"),
    ) {
        DarkTextField(
            value = scene.popis,
            onValueChange = { vm.setDancePopis(it) },
            placeholder = t("na pódiu s barevnými světly"),
            minHeight = 80.dp,
            onClear = { vm.setDancePopis("") },
        )
    }
}
