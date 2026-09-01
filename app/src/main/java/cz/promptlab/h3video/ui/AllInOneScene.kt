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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cz.promptlab.h3video.MainViewModel
import cz.promptlab.h3video.data.AioMode
import cz.promptlab.h3video.data.AioScene
import cz.promptlab.h3video.data.AioSlot
import cz.promptlab.h3video.data.Upscaler
import cz.promptlab.h3video.data.planExtend
import cz.promptlab.h3video.ui.theme.Cyan
import cz.promptlab.h3video.ui.theme.Outline1
import cz.promptlab.h3video.ui.theme.Surface2
import cz.promptlab.h3video.ui.theme.TextLow
import cz.promptlab.h3video.ui.theme.TextMid
import kotlin.math.roundToInt

/**
 * Karta **All in One**. Staví na balíku ComfyUI-ALLinONE-MinimaxH3, jehož hotové
 * šablony si appka stahuje přímo ze serveru – nic z nich není zabalené v APK,
 * takže po aktualizaci balíku se generuje podle nové verze.
 *
 * Karta záměrně nekopíruje to, co appka umí jinde. Přináší tři věci navíc:
 * klíčové snímky, prodloužení hotového videa a zvětšení.
 */
@Composable
fun AllInOneSection(vm: MainViewModel) {
    val scene by vm.aio.collectAsStateWithLifecycle()

    SectionCard(
        title = "Co se má udělat",
        subtitle = "Šablonu si appka stáhne z ComfyUI, z balíku All in One"
    ) {
        Column {
            PillRow(
                items = AioMode.entries.toList(),
                selected = scene.mode,
                label = { it.nazev },
                onSelect = { vm.setAioMode(it) },
            )
            Spacer(Modifier.height(8.dp))
            Text(scene.mode.popis, style = MaterialTheme.typography.bodySmall, color = TextLow)
        }
    }

    when (scene.mode) {
        AioMode.IMAGE -> ImageSekce(vm, scene)
        AioMode.REFERENCE -> ReferenceSekce(vm, scene)
        AioMode.KEYFRAMES -> KeyframeSekce(vm, scene)
        AioMode.EXTEND -> VideoSekce(
            vm, scene,
            titulek = "Video, které se má prodloužit",
            popis = "Naváže se na jeho konec – stejné rámování, žádný střih",
        )
        AioMode.UPSCALE -> {
            VideoSekce(
                vm, scene,
                titulek = "Video, které se má zvětšit",
                popis = "Nic se negeneruje znovu, jen se dopočítají detaily",
            )
            UpscaleSekce(vm, scene)
        }
        AioMode.CHARSHEET -> CharSheetSekce(vm, scene)
        AioMode.TEXT -> Unit
    }

    // List postavy má popis nepovinný, ale užitečný – říká, co z referencí
    // držet a co ignorovat. Proto se pole ukazuje i jemu.
    if (scene.mode.needsPrompt || scene.mode == AioMode.CHARSHEET) {
        SectionCard(
            title = when (scene.mode) {
                AioMode.EXTEND -> "Co se má dít dál"
                AioMode.CHARSHEET -> "Popis postavy (nepovinné)"
                else -> "Popis scény"
            },
            subtitle = when (scene.mode) {
                AioMode.EXTEND -> "Popiš, co se má stát po konci původního videa"
                AioMode.CHARSHEET -> "Co z fotek držet (obličej, účes, oblečení) a co vynechat"
                else -> "Anglicky to model chápe nejlíp, ale rozumí i česky"
            }
        ) {
            DarkTextField(
                value = scene.prompt,
                onValueChange = { vm.setAioPrompt(it) },
                placeholder = if (scene.mode == AioMode.CHARSHEET)
                    "Keep the face and hairstyle from Picture 1, the outfit from Picture 2"
                else "A woman walks through a neon-lit street, cinematic, static camera",
                minHeight = 130.dp,
                onClear = { vm.setAioPrompt("") },
            )
        }
    }

    // Délku nemá smysl nabízet u zvětšení (nic se negeneruje) ani u listu
    // postavy (choreografie kamery v šabloně je vyladěná na 124 snímků).
    if (scene.mode != AioMode.UPSCALE && scene.mode != AioMode.CHARSHEET) {
        SectionCard(
            title = if (scene.mode == AioMode.EXTEND) "O kolik prodloužit" else "Délka videa",
            subtitle = "Model počítá po blocích 17 snímků, délka se proto zaokrouhlí"
        ) {
            val strop = if (scene.mode == AioMode.EXTEND) AioScene.MAX_EXTEND_SECONDS else 15f
            LabeledSlider(
                label = "Sekundy",
                value = if (scene.mode == AioMode.EXTEND) {
                    val (_, _, nove) = planExtend(scene.seconds)
                    "%.1f s navíc".format(nove / 24f)
                } else "%.1f s (%d snímků)".format(scene.frames / 24f, scene.frames),
                position = scene.seconds,
                range = 2f..strop,
                onChange = { vm.setAioSeconds(it.roundToInt().toFloat()) },
            )
        }
    }
}

// --------------------------------------------------------------- jednotlivé režimy

@Composable
private fun ImageSekce(vm: MainViewModel, scene: AioScene) {
    SectionCard(
        title = "Snímky videa",
        subtitle = "První snímek určuje, čím video začne; poslední, kam dojede"
    ) {
        Column {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ObrazekSlot(
                    slot = scene.first,
                    popisek = "První snímek",
                    modifier = Modifier.weight(1f),
                    onPick = { uri -> vm.pickAioImage("first", 1, uri) },
                    onClear = { vm.clearAioImage("first", 1) },
                )
                ObrazekSlot(
                    slot = scene.last,
                    popisek = "Poslední snímek",
                    modifier = Modifier.weight(1f),
                    ztlumeny = !scene.useLastFrame,
                    onPick = { uri -> vm.pickAioImage("last", 2, uri) },
                    onClear = { vm.clearAioImage("last", 2) },
                )
            }
            Spacer(Modifier.height(12.dp))
            PrepinacRadek(
                titulek = "Zadat i poslední snímek",
                detail = if (scene.useLastFrame)
                    "Video půjde od prvního snímku k poslednímu"
                else "Video začne prvním snímkem a dál se rozvine samo",
                checked = scene.useLastFrame,
                onChange = { vm.setAioUseLastFrame(it) },
            )
        }
    }
}

/** Mřížka referenčních fotek – společná pro režimy Reference a List postavy. */
@Composable
private fun RefsMrizka(vm: MainViewModel, scene: AioScene) {
    Column {
        scene.refs.chunked(2).forEach { dvojice ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                dvojice.forEach { slot ->
                    ObrazekSlot(
                        slot = slot,
                        popisek = "Reference ${scene.refs.indexOf(slot) + 1}",
                        modifier = Modifier.weight(1f),
                        onPick = { uri -> vm.pickAioImage("ref", slot.key, uri) },
                        onClear = { vm.clearAioImage("ref", slot.key) },
                        onRemove = if (scene.refs.size > 1) {
                            { vm.removeAioRef(slot.key) }
                        } else null,
                    )
                }
                // Lichý počet: druhá polovina řádku zůstane prázdná, aby
                // dlaždice nebyly různě velké.
                if (dvojice.size == 1) Spacer(Modifier.weight(1f))
            }
            Spacer(Modifier.height(12.dp))
        }
        if (scene.canAddRef) {
            OutlineButton(
                "Přidat referenci",
                icon = { Icon(Icons.Default.Add, null, Modifier.size(18.dp), TextMid) },
                onClick = { vm.addAioRef() },
            )
        }
    }
}

@Composable
private fun ReferenceSekce(vm: MainViewModel, scene: AioScene) {
    SectionCard(
        title = "Reference",
        subtitle = "Podle nich model drží podobu postav, věcí i stylu"
    ) {
        RefsMrizka(vm, scene)
    }

    SectionCard(
        title = "Referenční video",
        subtitle = "Nepovinné – z videa se bere pohyb, podobu drží fotky"
    ) {
        ReferencniVideoObsah(vm, scene)
    }
}

/**
 * List postavy: fotky jedné postavy + dvě volby šablony (počet panelů a styl).
 * Vzorkování, délka i kamera jsou v šabloně balíku – tady se nenabízí nic,
 * co by je rozbilo.
 */
@Composable
private fun CharSheetSekce(vm: MainViewModel, scene: AioScene) {
    SectionCard(
        title = "Fotky postavy",
        subtitle = "První fotka určuje styl, další doplňují podobu"
    ) {
        RefsMrizka(vm, scene)
    }

    SectionCard(
        title = "Podoba listu",
        subtitle = "Vzorkování a kameru řídí šablona balíku"
    ) {
        Column {
            Text("Počet pohledů", style = MaterialTheme.typography.labelMedium, color = TextLow)
            Spacer(Modifier.height(8.dp))
            PillRow(
                items = listOf(6, 4),
                selected = scene.sheetPanels,
                label = { if (it == 6) "6 – plná otočka" else "4 – rychlejší" },
                onSelect = { vm.setAioSheetPanels(it) },
            )
            Spacer(Modifier.height(12.dp))
            PrepinacRadek(
                titulek = "Fotorealistický styl",
                detail = if (scene.sheetPhotoreal)
                    "Neretušovaná studiová fotografie, bez make-upu"
                else "Styl se převezme z první fotky",
                checked = scene.sheetPhotoreal,
                onChange = { vm.setAioSheetPhotoreal(it) },
            )
        }
    }
}

@Composable
private fun ReferencniVideoObsah(vm: MainViewModel, scene: AioScene) {
    Column {
        VideoRadek(
            soubor = scene.refVideo,
            prazdny = "Vybrat video z galerie",
            onPick = { uri -> vm.pickAioVideo("refvideo", uri) },
            onClear = { vm.clearAioVideo("refvideo") },
        )
        if (scene.refVideo != null) {
            Spacer(Modifier.height(12.dp))
            PrepinacRadek(
                titulek = "Použít i zvuk z videa",
                detail = if (scene.refVideoAudio)
                    "Model dostane zvukovou stopu videa jako referenci"
                else "Zvuk z videa se zahodí, model si vytvoří vlastní",
                checked = scene.refVideoAudio,
                onChange = { vm.setAioRefVideoAudio(it) },
            )
        }
    }
}

@Composable
private fun KeyframeSekce(vm: MainViewModel, scene: AioScene) {
    SectionCard(
        title = "Klíčové snímky",
        subtitle = "Obrázek se připne na konkrétní snímek a video jimi projde po řadě"
    ) {
        Column {
            scene.keys.forEachIndexed { i, slot ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ObrazekSlot(
                        slot = slot,
                        popisek = "Snímek ${i + 1}",
                        modifier = Modifier.width(110.dp),
                        onPick = { uri -> vm.pickAioImage("key", slot.key, uri) },
                        onClear = { vm.clearAioImage("key", slot.key) },
                        onRemove = if (scene.keys.size > 1) {
                            { vm.removeAioKey(slot.key) }
                        } else null,
                    )
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        LabeledSlider(
                            label = "Kde ve videu",
                            value = "snímek ${slot.position} · %.1f s".format(slot.position / 24f),
                            position = slot.position.toFloat(),
                            range = 1f..scene.frames.toFloat(),
                            onChange = { vm.setAioKeyPosition(slot.key, it.roundToInt()) },
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
            }
            if (scene.canAddKey) {
                OutlineButton(
                    "Přidat klíčový snímek",
                    icon = { Icon(Icons.Default.Add, null, Modifier.size(18.dp), TextMid) },
                    onClick = { vm.addAioKey() },
                )
            }
        }
    }
}

@Composable
private fun VideoSekce(vm: MainViewModel, scene: AioScene, titulek: String, popis: String) {
    SectionCard(title = titulek, subtitle = popis) {
        VideoRadek(
            soubor = scene.sourceVideo,
            prazdny = "Vybrat video z galerie",
            onPick = { uri -> vm.pickAioVideo("source", uri) },
            onClear = { vm.clearAioVideo("source") },
        )
    }
}

@Composable
private fun UpscaleSekce(vm: MainViewModel, scene: AioScene) {
    SectionCard(
        title = "Zvětšovač",
        subtitle = "SeedVR2 dopočítává detaily, RTX jen rychle zvětší"
    ) {
        Column {
            PillRow(
                items = Upscaler.entries.toList(),
                selected = scene.upscaler,
                label = { it.nazev },
                onSelect = { vm.setAioUpscaler(it) },
            )
            Spacer(Modifier.height(8.dp))
            Text(scene.upscaler.popis, style = MaterialTheme.typography.bodySmall, color = TextLow)
            Spacer(Modifier.height(14.dp))
            when (scene.upscaler) {
                Upscaler.SEEDVR2 -> LabeledSlider(
                    label = "Kratší hrana výsledku",
                    value = "${scene.upscaleResolution} px",
                    position = scene.upscaleResolution.toFloat(),
                    range = 720f..2160f,
                    onChange = { vm.setAioUpscaleResolution((it / 120f).roundToInt() * 120) },
                    note = "Čím víc, tím déle to trvá a tím víc paměti to sní.",
                )

                Upscaler.RTX -> LabeledSlider(
                    label = "Kolikrát zvětšit",
                    value = "${scene.upscaleMultiplier}×",
                    position = scene.upscaleMultiplier.toFloat(),
                    range = 2f..4f,
                    onChange = { vm.setAioUpscaleMultiplier(it.roundToInt()) },
                    note = "Jede na grafice NVIDIA přes ovladač, model se nespouští.",
                )
            }
        }
    }
}

// ------------------------------------------------------------------ stavební prvky

/** Dlaždice s obrázkem: ťuknutím se vybere, křížkem odebere. */
@Composable
private fun ObrazekSlot(
    slot: AioSlot,
    popisek: String,
    modifier: Modifier = Modifier,
    ztlumeny: Boolean = false,
    onPick: (android.net.Uri?) -> Unit,
    onClear: () -> Unit,
    onRemove: (() -> Unit)? = null,
) {
    val imageOnly = PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
    val pick = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> onPick(uri) }

    Column(modifier) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(0.85f)
                .clip(RoundedCornerShape(16.dp))
                .background(Surface2)
                .border(1.dp, if (ztlumeny) Outline1 else Outline1, RoundedCornerShape(16.dp))
                .clickable { pick.launch(imageOnly) },
            contentAlignment = Alignment.Center
        ) {
            val thumb = slot.thumb
            if (thumb != null) {
                Image(
                    thumb.asImageBitmap(),
                    contentDescription = popisek,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(26.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Surface2)
                        .clickable(onClick = onClear),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Close, "Odebrat obrázek", Modifier.size(15.dp), TextMid)
                }
            } else {
                Icon(
                    Icons.Default.AddPhotoAlternate, popisek,
                    Modifier.size(28.dp), if (ztlumeny) TextLow else TextMid
                )
            }
        }
        Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                popisek,
                style = MaterialTheme.typography.labelMedium,
                color = if (ztlumeny) TextLow else TextMid,
                modifier = Modifier.weight(1f),
            )
            if (onRemove != null) {
                Text(
                    "Odebrat",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextLow,
                    modifier = Modifier.clickable(onClick = onRemove),
                )
            }
        }
    }
}

/** Řádek pro výběr videa z galerie. */
@Composable
private fun VideoRadek(
    soubor: java.io.File?,
    prazdny: String,
    onPick: (android.net.Uri?) -> Unit,
    onClear: () -> Unit,
) {
    val videoOnly = PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
    val pick = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> onPick(uri) }

    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Surface2)
            .border(1.dp, Outline1, RoundedCornerShape(14.dp))
            .clickable { pick.launch(videoOnly) }
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.Movie, null, Modifier.size(22.dp), if (soubor == null) TextMid else Cyan)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                soubor?.name ?: prazdny,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (soubor != null) FontWeight.Medium else FontWeight.Normal,
                maxLines = 1,
            )
            if (soubor != null) {
                Text(
                    "%.1f MB".format(soubor.length() / 1024f / 1024f),
                    style = MaterialTheme.typography.bodySmall, color = TextLow,
                )
            }
        }
        if (soubor != null) {
            Box(
                Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(50))
                    .clickable(onClick = onClear),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Close, "Odebrat video", Modifier.size(17.dp), TextMid)
            }
        }
    }
}

@Composable
private fun PrepinacRadek(
    titulek: String,
    detail: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(titulek, style = MaterialTheme.typography.bodyMedium)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = TextLow)
        }
        androidx.compose.material3.Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = switchColors(),
        )
    }
}
