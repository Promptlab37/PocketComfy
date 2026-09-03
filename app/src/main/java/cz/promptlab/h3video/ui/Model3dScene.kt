package cz.promptlab.h3video.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import cz.promptlab.h3video.data.Model3dKvalita
import cz.promptlab.h3video.data.Model3dScene
import cz.promptlab.h3video.data.model3dHints
import cz.promptlab.h3video.data.t
import cz.promptlab.h3video.ui.theme.Amber
import cz.promptlab.h3video.ui.theme.Outline1
import cz.promptlab.h3video.ui.theme.Surface2
import cz.promptlab.h3video.ui.theme.TextLow
import cz.promptlab.h3video.ui.theme.TextMid

/**
 * Karta **3D model** — TRELLIS.2 z jedné fotky.
 *
 * Karta záměrně nenabízí nic z toho, co má video: poměr stran, délku ani
 * profil. Model si rozlišení tvaru řídí sám a výsledkem není snímek, ale
 * soubor `.glb`.
 */
@Composable
fun Model3dSection(vm: MainViewModel) {
    val scene by vm.model3d.collectAsStateWithLifecycle()

    val imageOnly = PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
    val pick = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> vm.pickModel3dImage(uri) }

    SectionCard(
        title = t("Fotka předmětu"),
        subtitle = t("Pozadí odstraní server sám — stačí běžná fotka z telefonu")
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
                    contentDescription = t("Fotka předmětu"),
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
                        .clickable { vm.clearModel3dImage() },
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
        title = t("Co má z modelu vylézt"),
        subtitle = t("Rozdíl není v tvaru, ale v tom, co se s modelem dá dělat dál")
    ) {
        Column {
            PillRow(
                items = Model3dKvalita.entries.toList(),
                selected = scene.kvalita,
                label = { it.nazev },
                onSelect = { vm.setModel3dKvalita(it) },
            )
            Spacer(Modifier.height(8.dp))
            Text(scene.kvalita.popis, style = MaterialTheme.typography.bodySmall, color = TextLow)
        }
    }

    SectionCard(
        title = t("Detail"),
        subtitle = t("Jemnost sítě a velikost textury")
    ) {
        Column {
            // Jemnost tvaru se nabízí, jen když je z čeho vybírat. Na 16GB
            // kartě projde jediná hodnota, takže přepínač s jednou pilulkou
            // by jen mátl.
            if (Model3dScene.DETAILY.size > 1) {
                Text(t("Jemnost tvaru"), style = MaterialTheme.typography.labelMedium, color = TextLow)
                Spacer(Modifier.height(8.dp))
                PillRow(
                    items = Model3dScene.DETAILY,
                    selected = scene.detail,
                    label = { it.toString() },
                    onSelect = { vm.setModel3dDetail(it) },
                )
            }

            if (scene.kvalita.jePbr) {
                Spacer(Modifier.height(14.dp))
                Text(t("Textura"), style = MaterialTheme.typography.labelMedium, color = TextLow)
                Spacer(Modifier.height(8.dp))
                PillRow(
                    items = Model3dScene.TEXTURY,
                    selected = scene.textura,
                    label = { "${it}×$it" },
                    onSelect = { vm.setModel3dTextura(it) },
                )
            }

            model3dHints(scene).forEach { hint ->
                Spacer(Modifier.height(8.dp))
                Text(hint, style = MaterialTheme.typography.bodySmall, color = Amber)
            }
        }
    }
}
