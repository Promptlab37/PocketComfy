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
import cz.promptlab.h3video.data.UpscaleScene
import cz.promptlab.h3video.ui.theme.Outline1
import cz.promptlab.h3video.ui.theme.Surface2
import cz.promptlab.h3video.ui.theme.TextLow
import cz.promptlab.h3video.ui.theme.TextMid

/**
 * Karta **Zvětšit** — SeedVR2 gigapixel podle uživatelova workflow. Jediné
 * dvě volby: fotka a mřížka dlaždic; všechno ostatní je vyladěné v předloze.
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
}
