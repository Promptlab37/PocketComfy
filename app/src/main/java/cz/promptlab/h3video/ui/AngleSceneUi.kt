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
import cz.promptlab.h3video.comfy.AngleBuilder
import cz.promptlab.h3video.data.t
import cz.promptlab.h3video.ui.theme.Outline1
import cz.promptlab.h3video.ui.theme.Surface2
import cz.promptlab.h3video.ui.theme.TextLow
import cz.promptlab.h3video.ui.theme.TextMid

/**
 * Karta **Úhel kamery**: fotka a grafický ovladač kamery — půdorys, kde se
 * kamera táhne kolem objektu, a bokorys na výšku. Žádné psaní ani posuvníky:
 * zadání pro model se skládá z polohy kamery ([AngleBuilder.prompt]).
 *
 * Kamera skáče jen na natrénované polohy (8 × 4 × 3 = 96 póz). Plynulý pohyb
 * by sliboval, co model neumí — mezipolohy by graf stejně zahodil.
 */
@Composable
fun AngleSection(vm: MainViewModel) {
    val scene by vm.angle.collectAsStateWithLifecycle()

    val imageOnly = PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
    val pick = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> vm.pickAngleImage(uri) }

    SectionCard(
        title = t("Fotka"),
        subtitle = t("Postava nebo předmět, na který se chceš podívat z jiné strany")
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
                    contentDescription = t("Fotka pro nový úhel"),
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
                        .clickable { vm.clearAngleImage() },
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
        title = t("Kde má být kamera"),
        subtitle = t("Táhni kamerou kolem objektu — blíž je detail, dál celek")
    ) {
        Column {
            PudorysKamery(
                smer = scene.azimut,
                odstup = scene.odstup,
            ) { smer, odstup ->
                vm.updateAngle { it.copy(azimut = smer, odstup = odstup) }
            }

            Spacer(Modifier.height(4.dp))
            Text(
                t("Objekt je čelem dolů, k tobě. Pohled zprava je proto vlevo — je to jeho pravá strana, ne tvoje."),
                style = MaterialTheme.typography.bodySmall, color = TextLow
            )

            Spacer(Modifier.height(16.dp))
            Text(
                t("Jak vysoko"),
                style = MaterialTheme.typography.labelMedium, color = TextLow
            )
            BokorysKamery(scene.vyska) { v -> vm.updateAngle { it.copy(vyska = v) } }

            Spacer(Modifier.height(8.dp))
            Text(
                scene.popis,
                style = MaterialTheme.typography.bodyMedium, color = TextMid
            )

            Spacer(Modifier.height(12.dp))
            SilaLory(scene.sila, t("Síla přesunu")) { v -> vm.updateAngle { it.copy(sila = v) } }
            Text(
                t("Níž než 1 posune pohled míň, ale drží líp podobu předlohy."),
                style = MaterialTheme.typography.bodySmall, color = TextLow
            )

            Spacer(Modifier.height(10.dp))
            Text(
                t("Zadání pro model") + ": " + scene.zadani,
                style = MaterialTheme.typography.bodySmall, color = TextLow
            )
        }
    }
}
