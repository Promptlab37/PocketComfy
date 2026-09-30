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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cz.promptlab.h3video.MainViewModel
import cz.promptlab.h3video.data.t
import cz.promptlab.h3video.ui.theme.Amber
import cz.promptlab.h3video.ui.theme.Cyan
import cz.promptlab.h3video.ui.theme.Outline1
import cz.promptlab.h3video.ui.theme.Surface2
import cz.promptlab.h3video.ui.theme.TextLow
import cz.promptlab.h3video.ui.theme.TextMid

/** Karta **Wan Animate** — fotka postavy + video s pohybem. */
@Composable
fun AnimateSection(vm: MainViewModel) {
    val scene by vm.animate.collectAsStateWithLifecycle()
    val videoChyba by vm.animateVideoChyba.collectAsStateWithLifecycle()

    val imageOnly = PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
    val videoOnly = PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
    val vyberFotky = rememberLauncherForActivityResult(VyberMedii()) { uri -> vm.pickAnimateFotku(uri) }
    val vyberVidea = rememberLauncherForActivityResult(VyberMedii()) { uri -> vm.pickAnimateVideo(uri) }

    SectionCard(title = t("Postava")) {
        Box(
            Modifier
                .fillMaxWidth()
                .plochaFotky(prazdna = scene.nahled == null)
                .clip(RoundedCornerShape(14.dp))
                .background(Surface2)
                .border(1.dp, Outline1, RoundedCornerShape(14.dp))
                .clickable { vyberFotky.launch(imageOnly) }
        ) {
            val nahled = scene.nahled
            if (nahled != null) {
                Image(
                    bitmap = nahled.asImageBitmap(),
                    contentDescription = t("Postava"),
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
                KrizekOdebrat(onClick = { vm.clearAnimateFotku() }, popis = t("Odebrat"), modifier = Modifier.align(Alignment.TopEnd).padding(6.dp), velikost = 28.dp, ikona = 16.dp, tvar = RoundedCornerShape(8.dp), pozadi = Surface2, barva = TextMid, roh = Alignment.TopEnd)
            } else {
                Icon(
                    Icons.Default.AddPhotoAlternate, t("Vybrat fotku"),
                    Modifier.align(Alignment.Center).size(34.dp), TextMid
                )
            }
        }
    }

    SectionCard(title = t("Video s pohybem")) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            val maVideo = scene.video != null
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Surface2)
                    .border(1.dp, Outline1, RoundedCornerShape(12.dp))
                    .clickable { vyberVidea.launch(videoOnly) }
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.Movie, null, Modifier.size(22.dp), if (maVideo) Cyan else TextMid)
                Spacer(Modifier.width(12.dp))
                Text(
                    if (maVideo) scene.video?.name.orEmpty() else t("Vybrat video z telefonu"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (maVideo) TextMid else TextLow,
                    modifier = Modifier.weight(1f),
                )
                if (maVideo) {
                    Icon(
                        Icons.Default.Close, t("Odebrat"),
                        Modifier
                            .size(20.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { vm.clearAnimateVideo() },
                        TextMid
                    )
                }
            }
            if (maVideo && scene.videoSekund > 0f) {
                Text(
                    t("%.1f s · %d × %d · %d úseků")
                        .format(scene.videoSekund, scene.sirka, scene.vyska, scene.useku),
                    style = MaterialTheme.typography.bodySmall, color = Amber,
                )
            }
            if (videoChyba != null) {
                Text(
                    videoChyba!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }

    // Tři pole jedné věci (zadání) v jedné sekci — dřív tři karty po jednom poli.
    SectionCard(title = t("Popis")) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(t("Vzhled postavy"), style = MaterialTheme.typography.labelMedium, color = TextLow)
            DarkTextField(
                value = scene.postava,
                onValueChange = { vm.setAnimatePostava(it) },
                placeholder = t("young woman with short red hair, black leather jacket"),
                minHeight = 64.dp,
                onClear = { vm.setAnimatePostava("") },
            )
            Text(t("Prostředí"), style = MaterialTheme.typography.labelMedium, color = TextLow)
            DarkTextField(
                value = scene.prostredi,
                onValueChange = { vm.setAnimateProstredi(it) },
                placeholder = t("night city street with neon signs"),
                minHeight = 64.dp,
                onClear = { vm.setAnimateProstredi("") },
            )
            Text(t("Pohyb"), style = MaterialTheme.typography.labelMedium, color = TextLow)
            DarkTextField(
                value = scene.pohyb,
                onValueChange = { vm.setAnimatePohyb(it) },
                placeholder = t("a person dancing"),
                minHeight = 52.dp,
                onClear = { vm.setAnimatePohyb("") },
            )
        }
    }
}
