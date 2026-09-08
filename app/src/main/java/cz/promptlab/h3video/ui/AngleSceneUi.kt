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
import kotlin.math.roundToInt

/**
 * Karta **Úhel kamery**: fotka a tři posuvníky — odkud se kamera dívá,
 * jak vysoko a jak daleko. Žádné psaní: zadání pro model se skládá
 * z těch tří voleb ([AngleBuilder.prompt]).
 *
 * Posuvníky mají tolik poloh, kolik jich LoRA umí (8 × 4 × 3 = 96 póz).
 * Mezipolohy se nenabízejí schválně — model jiné než natrénované pózy nezná
 * a karta nemá ukazovat volbu, kterou graf zahodí.
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
        subtitle = t("Osm směrů dokola, čtyři výšky, tři odstupy")
    ) {
        Column {
            VolbaPosuvnik(
                popisek = t("Směr"),
                volby = AngleBuilder.AZIMUTY.map { t(it.second) },
                index = scene.azimut,
            ) { v -> vm.updateAngle { it.copy(azimut = v) } }

            Spacer(Modifier.height(8.dp))
            VolbaPosuvnik(
                popisek = t("Výška"),
                volby = AngleBuilder.VYSKY.map { t(it.second) },
                index = scene.vyska,
            ) { v -> vm.updateAngle { it.copy(vyska = v) } }

            Spacer(Modifier.height(8.dp))
            VolbaPosuvnik(
                popisek = t("Odstup"),
                volby = AngleBuilder.ODSTUPY.map { t(it.second) },
                index = scene.odstup,
            ) { v -> vm.updateAngle { it.copy(odstup = v) } }

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

/**
 * Posuvník s pevnými polohami. Popisek vpravo ukazuje vybranou volbu slovem,
 * ať je jasné, co která poloha znamená.
 */
@Composable
private fun VolbaPosuvnik(
    popisek: String,
    volby: List<String>,
    index: Int,
    onZmena: (Int) -> Unit,
) {
    Column {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(popisek, style = MaterialTheme.typography.labelMedium, color = TextLow)
            Text(
                volby.getOrElse(index) { "" },
                style = MaterialTheme.typography.labelMedium, color = TextMid
            )
        }
        Slider(
            value = index.toFloat(),
            onValueChange = { v -> onZmena(v.roundToInt().coerceIn(volby.indices)) },
            valueRange = 0f..(volby.size - 1).toFloat(),
            // Zarážky mezi krajními polohami, aby posuvník skákal jen po volbách.
            steps = (volby.size - 2).coerceAtLeast(0),
            colors = sliderColors(),
        )
    }
}
