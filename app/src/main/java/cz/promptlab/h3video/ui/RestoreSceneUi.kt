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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import cz.promptlab.h3video.ui.theme.TextLow
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
import cz.promptlab.h3video.ui.theme.Outline1
import cz.promptlab.h3video.ui.theme.Surface2
import cz.promptlab.h3video.ui.theme.TextMid

/**
 * Karta **Oprava fotky**. Ve výchozím stavu je to fotka a tlačítko —
 * opravovací zadání je vyladěné v předloze (škrábance, kolorizace,
 * doostření, potrhané okraje).
 *
 * Pod tím je nepovinné vlastní zadání pro cílenou opravu. Staré LoRA pro
 * LoRA pro starší architekturu nejsou s Qwen Image 2.1 kompatibilní, proto je karta nenabízí.
 */
@Composable
fun RestoreSection(vm: MainViewModel) {
    val scene by vm.restore.collectAsStateWithLifecycle()
    val nacita by vm.restoreNacita.collectAsStateWithLifecycle()
    val chyba by vm.restoreChyba.collectAsStateWithLifecycle()

    val imageOnly = PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
    val pick = rememberLauncherForActivityResult(
        cz.promptlab.h3video.ui.VyberMedii()
    ) { uri -> vm.pickRestoreImage(uri) }

    SectionCard(
        title = t("Stará nebo poškozená fotka"),
        subtitle = t("Škrábance, prach, vybledlé barvy i trhliny — appka opraví vše naráz")
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(4f / 3f)
                .clip(RoundedCornerShape(14.dp))
                .background(Surface2)
                .border(1.dp, Outline1, RoundedCornerShape(14.dp))
                .clickable(enabled = !nacita) { pick.launch(imageOnly) }
        ) {
            val thumb = scene.thumb
            if (nacita) {
                Column(
                    Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    androidx.compose.material3.CircularProgressIndicator(
                        Modifier.size(28.dp), color = cz.promptlab.h3video.ui.theme.Cyan, strokeWidth = 2.dp,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(t("Načítám fotku…"), style = MaterialTheme.typography.bodySmall, color = TextMid)
                }
            } else if (thumb != null) {
                Image(
                    bitmap = thumb.asImageBitmap(),
                    contentDescription = t("Fotka k opravě"),
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
                        .clickable { vm.clearRestoreImage() },
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
        chyba?.let {
            Spacer(Modifier.height(6.dp))
            Text(it, style = MaterialTheme.typography.bodySmall, color = cz.promptlab.h3video.ui.theme.Amber)
        }
    }

    SectionCard(
        title = t("Cílená oprava"),
        subtitle = t("Nepovinné. Prázdné = obecná záchrana staré fotky jako dosud")
    ) {
        Column {
            DarkTextField(
                value = scene.pokyn,
                onValueChange = { v -> vm.updateRestore { it.copy(pokyn = v) } },
                placeholder = t("Co se má opravit — anglicky, model je na ni trénovaný"),
                minHeight = 92.dp,
                onClear = { vm.updateRestore { it.copy(pokyn = "") } },
            )
            if (scene.pokyn.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    t("Vlastní zadání nahradí obecnou obnovu, Qwen 2.1 k němu přidá ochranu detailů."),
                    style = MaterialTheme.typography.bodySmall, color = TextLow
                )
            }
        }
    }

    SectionCard(
        title = t("Doostření RTX"),
        subtitle = t("NVIDIA DLSS 5 jako poslední krok — nic nedokresluje, jen doostří"),
        trailing = {
            androidx.compose.material3.Switch(
                checked = scene.doostrit,
                onCheckedChange = { v -> vm.updateRestore { it.copy(doostrit = v) } },
            )
        },
    ) {
        if (scene.doostrit) PillRow(
            items = listOf("1x", "2x"),
            selected = scene.doostritNasobek,
            label = { if (it == "2x") t("Doostřit a zvětšit 2×") else t("Jen doostřit") },
            onSelect = { v -> vm.updateRestore { it.copy(doostritNasobek = v) } },
        )
    }
}
