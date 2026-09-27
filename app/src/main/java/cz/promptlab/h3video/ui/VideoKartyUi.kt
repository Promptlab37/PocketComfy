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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import cz.promptlab.h3video.data.AioScene
import cz.promptlab.h3video.data.PohybRezim
import cz.promptlab.h3video.data.UpravaRezim
import cz.promptlab.h3video.data.Upscaler
import cz.promptlab.h3video.data.VylepseniRezim
import cz.promptlab.h3video.data.VylepseniScene
import cz.promptlab.h3video.data.t
import cz.promptlab.h3video.ui.theme.Cyan
import cz.promptlab.h3video.ui.theme.Outline1
import cz.promptlab.h3video.ui.theme.Surface2
import cz.promptlab.h3video.ui.theme.TextLow
import cz.promptlab.h3video.ui.theme.TextMid
import java.io.File
import kotlin.math.roundToInt

/** Karta **Pohyb postavy** — rozcestník mezi Dance a Wan Animate. */
@Composable
fun PohybSection(vm: MainViewModel) {
    val rezim by vm.pohyb.collectAsStateWithLifecycle()
    SectionCard(title = t("Podle čeho se hýbe")) {
        PillRow(
            items = PohybRezim.entries.toList(),
            selected = rezim,
            label = { it.title },
            onSelect = { vm.setPohybRezim(it) },
        )
    }
    when (rezim) {
        PohybRezim.HUDBA -> DanceSection(vm)
        PohybRezim.VIDEO -> AnimateSection(vm)
    }
}

/** Karta **Upravit video**. Video nahoře je společné všem režimům. */
@Composable
fun UpravaVideaSection(vm: MainViewModel) {
    val s by vm.uprava.collectAsStateWithLifecycle()
    val chyba by vm.videoKartyChyba.collectAsStateWithLifecycle()

    SectionCard(title = t("Video")) {
        VideoVyber(s.video, chyba, { vm.pickUpravaVideo(it) }, { vm.clearUpravaVideo() })
    }
    if (UpravaRezim.entries.size > 1) {
        SectionCard(title = t("Co udělat")) {
            PillRow(
                items = UpravaRezim.entries.toList(),
                selected = s.rezim,
                label = { it.title },
                onSelect = { vm.setUpravaRezim(it) },
            )
        }
    }
    when (s.rezim) {
        UpravaRezim.PREMALOVAT -> {
            SectionCard(title = t("Co ve videu sledovat")) {
                Column {
                    DarkTextField(
                        value = s.maskTarget,
                        onValueChange = { vm.setUpravaMaskTarget(it) },
                        placeholder = "head",
                        minHeight = 52.dp,
                        singleLine = true,
                        onClear = { vm.setUpravaMaskTarget("") },
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(t("Kolik objektů"), style = MaterialTheme.typography.labelMedium, color = TextLow)
                    Spacer(Modifier.height(6.dp))
                    PillRow(
                        items = listOf(1, 2, 3),
                        selected = s.maskObjects,
                        label = { it.toString() },
                        onSelect = { vm.setUpravaMaskObjects(it) },
                    )
                }
            }
            SectionCard(title = t("Čím to nahradit (nepovinné)")) {
                RefsRada(vm, s.refs.filter { it.image != null }, s.canAddRef || s.refs.any { it.image == null })
            }
            SectionCard(title = t("Popis scény")) {
                DarkTextField(
                    value = s.popis,
                    onValueChange = { vm.setUpravaPopis(it) },
                    placeholder = "A man wearing a shiny silver astronaut helmet",
                    minHeight = 100.dp,
                    onClear = { vm.setUpravaPopis("") },
                )
            }
            SectionCard(title = t("Kolik videa zpracovat")) {
                LabeledSlider(
                    label = t("Sekundy"),
                    value = t("prvních %.0f s videa").format(s.sekundy),
                    position = s.sekundy,
                    range = 2f..15f,
                    onChange = { vm.setUpravaSekundy(it.roundToInt().toFloat()) },
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RefsRada(vm: MainViewModel, plne: List<cz.promptlab.h3video.data.AioSlot>, lzePridat: Boolean) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        plne.forEach { slot ->
            Box(
                Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Surface2)
                    .border(1.dp, Outline1, RoundedCornerShape(12.dp))
            ) {
                slot.thumb?.let {
                    Image(
                        it.asImageBitmap(), null,
                        contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(),
                    )
                }
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(22.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Surface2)
                        .clickable { vm.clearUpravaRef(slot.key) },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Default.Close, t("Odebrat"), Modifier.size(14.dp), TextMid) }
            }
        }
        if (lzePridat) {
            RefPridat(zbyva = AioScene.MAX_REFS - plne.size) { vm.pickUpravaRefs(it) }
        }
    }
}

/** Karta **Vylepšit video**. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VylepseniVideaSection(vm: MainViewModel) {
    val s by vm.vylepseni.collectAsStateWithLifecycle()
    val chyba by vm.videoKartyChyba.collectAsStateWithLifecycle()

    SectionCard(title = t("Video")) {
        VideoVyber(s.video, chyba, { vm.pickVylepseniVideo(it) }, { vm.clearVylepseniVideo() })
    }
    SectionCard(title = t("Co udělat")) {
        PillRow(
            items = VylepseniRezim.entries.toList(),
            selected = s.rezim,
            label = { it.title },
            onSelect = { vm.setVylepseniRezim(it) },
        )
    }
    when (s.rezim) {
        VylepseniRezim.ZVETSIT -> SectionCard(title = t("Zvětšovač")) {
            Column {
                PillRow(
                    items = Upscaler.entries.toList(),
                    selected = s.upscaler,
                    label = { it.nazev },
                    onSelect = { vm.setVylepseniUpscaler(it) },
                )
                Spacer(Modifier.height(14.dp))
                when (s.upscaler) {
                    Upscaler.SEEDVR2 -> LabeledSlider(
                        label = t("Kratší hrana výsledku"),
                        value = "${s.upscaleResolution} px",
                        position = s.upscaleResolution.toFloat(),
                        range = 720f..2160f,
                        onChange = { vm.setVylepseniRozliseni((it / 120f).roundToInt() * 120) },
                    )
                    Upscaler.RTX -> LabeledSlider(
                        label = t("Kolikrát zvětšit"),
                        value = "${s.upscaleMultiplier}×",
                        position = s.upscaleMultiplier.toFloat(),
                        range = 2f..4f,
                        onChange = { vm.setVylepseniNasobekRtx(it.roundToInt()) },
                    )
                }
            }
        }
        VylepseniRezim.ZPLYNULIT -> SectionCard(title = t("Plynulost")) {
            Column {
                PillRow(
                    items = s.nasobkyKtereSeVejdou,
                    selected = s.nasobek,
                    label = { "$it×" },
                    onSelect = { vm.setVylepseniNasobek(it) },
                )
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        t("Zpomalit"),
                        style = MaterialTheme.typography.bodyMedium, color = TextMid,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(checked = s.zpomalit, onCheckedChange = { vm.setVylepseniZpomalit(it) })
                }
            }
        }
    }
}

/** Výběr zdrojového videa z telefonu; název souboru a křížek, když je vybrané. */
@Composable
internal fun VideoVyber(
    video: File?,
    chyba: String?,
    onPick: (android.net.Uri?) -> Unit,
    onClear: () -> Unit,
) {
    val videoOnly = PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
    val vyber = rememberLauncherForActivityResult(VyberMedii()) { onPick(it) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Surface2)
                .border(1.dp, Outline1, RoundedCornerShape(12.dp))
                .clickable { vyber.launch(videoOnly) }
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.Movie, null, Modifier.size(22.dp), if (video != null) Cyan else TextMid)
            Spacer(Modifier.width(12.dp))
            Text(
                video?.name ?: t("Vybrat video z telefonu"),
                style = MaterialTheme.typography.bodyMedium,
                color = if (video != null) TextMid else TextLow,
                modifier = Modifier.weight(1f),
            )
            if (video != null) {
                Icon(
                    Icons.Default.Close, t("Odebrat"),
                    Modifier
                        .size(20.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { onClear() },
                    TextMid
                )
            }
        }
        if (chyba != null) {
            Text(chyba, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
    }
}
