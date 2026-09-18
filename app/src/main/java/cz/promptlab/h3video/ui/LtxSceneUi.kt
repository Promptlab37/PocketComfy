package cz.promptlab.h3video.ui

import cz.promptlab.h3video.data.t

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
import cz.promptlab.h3video.data.LtxPomer
import cz.promptlab.h3video.data.LtxScene
import cz.promptlab.h3video.ui.theme.Amber
import cz.promptlab.h3video.ui.theme.Outline1
import cz.promptlab.h3video.ui.theme.Surface2
import cz.promptlab.h3video.ui.theme.TextLow
import cz.promptlab.h3video.ui.theme.TextMid

/**
 * Karta **Video ze zvuku** (LTX 2.5).
 *
 * Proti ostatním video kartám tu schválně **není délka**. Počet snímků si
 * spočítá graf z délky nahraného zvuku (`fps × délka + 1`), takže nejde
 * zadat kratší video, než je řeč — a přesně kvůli tomu karta vznikla.
 * Ukazuje se proto jen to, co z toho vyjde.
 */
@Composable
fun LtxSection(vm: MainViewModel) {
    val scene by vm.ltx.collectAsStateWithLifecycle()
    val zvukChyba by vm.ltxZvukChyba.collectAsStateWithLifecycle()

    val imageOnly = PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
    val vyberFotky = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> vm.pickLtxObrazek(uri) }
    val vyberZvuku = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> vm.pickLtxZvuk(uri) }

    SectionCard(
        title = t("Fotka"),
        subtitle = t("První snímek videa — z něj se bere podoba i prostředí")
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
                    contentDescription = t("Fotka prvního snímku"),
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
                        .clickable { vm.clearLtxObrazek() },
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
        title = t("Zvuk"),
        subtitle = scene.zvuk?.name ?: t("Řeč nebo zpěv — video se na něj napasuje")
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlineButton(
                text = if (scene.zvuk == null) t("Vybrat zvuk") else t("Vybrat jiný"),
                modifier = Modifier.fillMaxWidth(),
                onClick = { vyberZvuku.launch("audio/*") },
            )
            if (scene.zvuk != null) {
                OutlineButton(
                    text = t("Odebrat zvuk"),
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { vm.clearLtxZvuk() },
                )
                Text(
                    t("Délka %.1f s → %d snímků při %d fps")
                        .format(scene.zvukSekund, scene.snimku, LtxScene.FPS),
                    style = MaterialTheme.typography.bodySmall,
                    color = Amber,
                )
            }
            if (zvukChyba != null) {
                Text(
                    zvukChyba!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Text(
                t("Délku videa určuje tenhle soubor — model ji nemá jak useknout."),
                style = MaterialTheme.typography.bodySmall,
                color = TextLow,
            )
        }
    }

    SectionCard(
        title = t("Scéna"),
        subtitle = t("Co je v záběru a co se děje — anglicky")
    ) {
        DarkTextField(
            value = scene.popis,
            onValueChange = { vm.setLtxPopis(it) },
            placeholder = t("A news anchor in a dark blue studio speaks to the camera…"),
            minHeight = 110.dp,
            onClear = { vm.setLtxPopis("") },
        )
    }

    SectionCard(title = t("Tvar obrazu"), subtitle = t("Poměr stran hotového videa")) {
        PillRow(
            items = LtxPomer.entries.toList(),
            selected = scene.pomer,
            label = { it.title },
            onSelect = { vm.setLtxPomer(it) },
        )
    }
}
