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
import androidx.compose.runtime.LaunchedEffect
import cz.promptlab.h3video.ui.theme.Amber
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
 * Pod tím je nepovinné vlastní zadání a cílená LoRA, pro případ, kdy nejde
 * o starou fotku, ale o opravu konkrétního kusu hotového obrázku. Vlastní
 * zadání předlohové NAHRAZUJE: v tom předlohovém stojí „no shape
 * deformation", takže by cílenou opravu tvaru rovnou popřelo.
 */
@Composable
fun RestoreSection(vm: MainViewModel) {
    val scene by vm.restore.collectAsStateWithLifecycle()

    val imageOnly = PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
    val pick = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
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
                .clickable { pick.launch(imageOnly) }
        ) {
            val thumb = scene.thumb
            if (thumb != null) {
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
    }

    val loras by vm.restoreLoras.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.refreshRestoreLoras() }

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
                    t("Vlastní zadání nahradí opravovací zadání předlohy."),
                    style = MaterialTheme.typography.bodySmall, color = Amber
                )
            }

            if (loras.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                LoraSeznam(
                    nadpis = t("Cílená LoRA (nepovinná)"),
                    seznam = loras,
                    vybrana = scene.lora,
                    prazdna = t("Žádná"),
                    onVybrat = { lora -> vm.updateRestore { it.copy(lora = lora) } },
                )
                if (scene.lora.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    SilaLory(scene.loraSila) { v ->
                        vm.updateRestore { it.copy(loraSila = v) }
                    }
                    Text(
                        t("Řadí se za tři LoRA předlohy. Některé chtějí v zadání spouštěcí slovo."),
                        style = MaterialTheme.typography.bodySmall, color = TextLow
                    )
                }
            }
        }
    }
}
