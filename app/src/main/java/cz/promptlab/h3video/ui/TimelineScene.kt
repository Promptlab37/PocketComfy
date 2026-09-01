package cz.promptlab.h3video.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cz.promptlab.h3video.MainViewModel
import cz.promptlab.h3video.data.SegmentMode
import cz.promptlab.h3video.data.TimelineScene
import cz.promptlab.h3video.data.TimelineSegment
import cz.promptlab.h3video.ui.theme.Amber
import cz.promptlab.h3video.ui.theme.Cyan
import cz.promptlab.h3video.ui.theme.Ok
import cz.promptlab.h3video.ui.theme.Outline1
import cz.promptlab.h3video.ui.theme.Surface2
import cz.promptlab.h3video.ui.theme.TextHi
import cz.promptlab.h3video.ui.theme.TextLow
import cz.promptlab.h3video.ui.theme.TextMid
import kotlin.math.roundToInt

/**
 * Časová osa tak, jak se dělá ve střihových aplikacích na mobilu.
 *
 * Zvyklosti, které tu platí (CapCut, InShot, KineMaster – shrnuto na img.ly
 * „Designing A Timeline For Mobile Video Editing"):
 *
 *  – klip je **široký podle své délky**, nad pásem běží **časové pravítko**,
 *    takže délka je vidět z rozměru, ne z čísla;
 *  – **ťuknutím se klip vybere**, dostane výrazný rámeček a na okrajích úchyty;
 *  – **tažením úchytu se mění délka**, se zacvakáváním po půl sekundě. Dotyková
 *    plocha úchytu je znatelně větší než ta grafická, jinak se netrefíš;
 *  – **úpravy se dělají v panelu POD pásem**, ne uvnitř klipu – do klipu se
 *    text ani volby nevejdou;
 *  – na konci pásu je dlaždice **+**.
 *
 * Playhead tu schválně není: nemáme co přehrávat, takže by to byla čára bez
 * funkce. Místo něj je u každého klipu čas, kdy ve videu začíná.
 */
private const val DP_PER_SECOND = 18f
private val TRACK_HEIGHT = 84.dp
private val HANDLE_WIDTH = 12.dp
private val HANDLE_TOUCH = 40.dp

@Composable
fun TimelineSceneSection(vm: MainViewModel) {
    val scene by vm.timeline.collectAsStateWithLifecycle()
    var vybrany by remember { mutableIntStateOf(scene.segments.firstOrNull()?.key ?: 1) }

    var pickFor by remember { mutableStateOf<Int?>(null) }
    val imageOnly = PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
    val pick = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> pickFor?.let { vm.pickSegmentImage(it, uri) }; pickFor = null }

    // Segment mohl mezitím zmizet (odebrání) – výběr musí zůstat platný.
    val vybranySegment = scene.segments.firstOrNull { it.key == vybrany }
        ?: scene.segments.first().also { vybrany = it.key }

    SectionCard(
        title = "Časová osa",
        subtitle = "Ťukni na klip a uprav ho dole. Délku táhni za okraj klipu."
    ) {
        Column {
            val scroll = rememberScrollState()

            // ---- pravítko + pás v jednom rolování, ať se nerozjedou
            Column(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(scroll)
            ) {
                Ruler(scene.totalSeconds)
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    var zacatek = 0f
                    scene.segments.forEachIndexed { i, seg ->
                        val start = zacatek
                        Clip(
                            seg = seg,
                            index = i,
                            startSeconds = start,
                            vybrany = seg.key == vybrany,
                            onSelect = { vybrany = seg.key },
                            onResize = { delta ->
                                val nova = (seg.seconds + delta)
                                    .coerceIn(2f, TimelineScene.MAX_SEGMENT_SECONDS)
                                // Zacvakávání po půl sekundě – přesnější tažení
                                // stejně nemá smysl, model si délku zaokrouhlí.
                                vm.setSegmentSeconds(seg.key, (nova * 2).roundToInt() / 2f)
                            },
                        )
                        zacatek = start + seg.seconds
                    }
                    if (scene.canAdd) {
                        Box(
                            Modifier
                                .width(64.dp)
                                .height(TRACK_HEIGHT)
                                .clip(RoundedCornerShape(10.dp))
                                .border(1.dp, Outline1, RoundedCornerShape(10.dp))
                                .clickable { vm.addSegment() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Movie, "Přidat segment", Modifier.size(20.dp), TextMid)
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            Text(
                "%.1f s celkem · %d segmentů".format(scene.totalSeconds, scene.segments.size),
                style = MaterialTheme.typography.bodySmall, color = TextLow
            )

            Spacer(Modifier.height(14.dp))
            SegmentPanel(
                vm = vm,
                scene = scene,
                seg = vybranySegment,
                index = scene.segments.indexOf(vybranySegment),
                onPick = { pickFor = vybranySegment.key; pick.launch(imageOnly) },
            )

            // Globální prompt: uzel ho přidává ke každému segmentu. Do 2.62 byl
            // natvrdo v kódu a nešel změnit, přestože se ukládal i posílal.
            Spacer(Modifier.height(14.dp))
            Text(
                "Styl celého filmu",
                style = MaterialTheme.typography.labelMedium, color = TextLow
            )
            Spacer(Modifier.height(8.dp))
            DarkTextField(
                value = scene.globalPrompt,
                onValueChange = { vm.setTimelineGlobal(it) },
                placeholder = TimelineScene.DEFAULT_GLOBAL,
                minHeight = 64.dp,
                onClear = { vm.setTimelineGlobal("") },
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Přidá se ke každému segmentu – drží jednotný vzhled, světlo a barvy.",
                style = MaterialTheme.typography.bodySmall, color = TextLow
            )
        }
    }
}

/** Pravítko po sekundách; popisek po pěti, aby se to nesešlo. */
@Composable
private fun Ruler(totalSeconds: Float) {
    val density = LocalDensity.current
    val sekund = (totalSeconds.toInt() + 2)
    Box(Modifier.width((sekund * DP_PER_SECOND).dp).height(18.dp)) {
        Canvas(Modifier.fillMaxSize()) {
            val krok = with(density) { DP_PER_SECOND.dp.toPx() }
            for (s in 0..sekund) {
                val x = s * krok
                val vyska = if (s % 5 == 0) size.height * 0.75f else size.height * 0.35f
                drawLine(
                    color = Outline1,
                    start = Offset(x, size.height - vyska),
                    end = Offset(x, size.height),
                    strokeWidth = 1f,
                )
            }
        }
        Row(Modifier.fillMaxSize()) {
            for (s in 0..sekund step 5) {
                if (s > 0) Spacer(Modifier.width((5 * DP_PER_SECOND).dp - 18.dp))
                else Spacer(Modifier.width(2.dp))
                Text(
                    "${s}s",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextLow,
                    modifier = Modifier.width(16.dp)
                )
            }
        }
    }
}

/**
 * Jeden klip. Šířka odpovídá délce, takže je poměr vidět na první pohled.
 * Úchyt na pravém okraji mění délku; levý okraj je konec předchozího klipu,
 * takže se za něj netáhne.
 */
@Composable
private fun Clip(
    seg: TimelineSegment,
    index: Int,
    startSeconds: Float,
    vybrany: Boolean,
    onSelect: () -> Unit,
    onResize: (Float) -> Unit,
) {
    val density = LocalDensity.current
    val sirka = (seg.seconds * DP_PER_SECOND).dp.coerceAtLeast(56.dp)

    Box(
        Modifier
            .width(sirka)
            .height(TRACK_HEIGHT)
            .clip(RoundedCornerShape(10.dp))
            .background(if (vybrany) Cyan.copy(alpha = .14f) else Surface2)
            .border(
                if (vybrany) 2.dp else 1.dp,
                if (vybrany) Cyan else Outline1,
                RoundedCornerShape(10.dp)
            )
            .clickable { onSelect() }
    ) {
        if (seg.thumb != null) {
            Image(
                bitmap = seg.thumb.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(10.dp))
            )
        }
        Column(Modifier.padding(6.dp)) {
            Text(
                "${index + 1}",
                style = MaterialTheme.typography.labelMedium,
                color = if (vybrany) Cyan else TextMid,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                "%.1f s".format(seg.seconds),
                style = MaterialTheme.typography.labelSmall, color = TextLow
            )
            if (seg.inheritPrevious) {
                Icon(Icons.Default.Link, null, Modifier.size(13.dp), Ok)
            }
        }
        Text(
            "%.1f".format(startSeconds),
            style = MaterialTheme.typography.labelSmall,
            color = TextLow,
            modifier = Modifier.align(Alignment.BottomStart).padding(6.dp)
        )

        // Úchyt délky. Grafika je úzká, dotyková plocha široká – jinak se do ní
        // na mobilu netrefíš.
        if (vybrany) {
            Box(
                Modifier
                    .align(Alignment.CenterEnd)
                    .width(HANDLE_TOUCH)
                    .fillMaxHeight()
                    .pointerInput(seg.key) {
                        detectHorizontalDragGestures { _, drag ->
                            onResize(with(density) { drag.toDp().value } / DP_PER_SECOND)
                        }
                    },
                contentAlignment = Alignment.CenterEnd
            ) {
                Box(
                    Modifier
                        .width(HANDLE_WIDTH)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(topEnd = 10.dp, bottomEnd = 10.dp))
                        .background(Cyan)
                )
            }
        }
    }
}

/** Panel vybraného klipu – tady se dělají všechny úpravy. */
@Composable
private fun SegmentPanel(
    vm: MainViewModel,
    scene: TimelineScene,
    seg: TimelineSegment,
    index: Int,
    onPick: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Surface2)
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Segment ${index + 1}",
                style = MaterialTheme.typography.bodyMedium,
                color = TextHi, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            IconAction(Icons.Default.ChevronLeft, "Posunout doleva", index > 0) {
                vm.moveSegment(seg.key, -1)
            }
            IconAction(
                Icons.Default.ChevronRight, "Posunout doprava",
                index < scene.segments.lastIndex
            ) { vm.moveSegment(seg.key, +1) }
            IconAction(Icons.Default.ContentCopy, "Duplikovat", scene.canAdd) {
                vm.duplicateSegment(seg.key)
            }
            IconAction(Icons.Default.Delete, "Odebrat", scene.segments.size > 1) {
                vm.removeSegment(seg.key)
            }
        }

        Spacer(Modifier.height(10.dp))
        DarkTextField(
            value = seg.prompt,
            onValueChange = { vm.setSegmentPrompt(seg.key, it) },
            placeholder = if (index == 0) "Muž jde po molu, kamera ho sleduje zezadu…"
            else "Pokračuje dál, kamera se stáčí k moři…",
            minHeight = 84.dp,
            onClear = { vm.setSegmentPrompt(seg.key, "") },
        )

        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (index > 0) {
                TagChip(
                    text = "Navázat na předchozí",
                    active = seg.inheritPrevious,
                ) {
                    vm.setSegmentInherit(seg.key, !seg.inheritPrevious)
                    if (!seg.inheritPrevious) vm.setSegmentMode(seg.key, SegmentMode.IMAGE)
                }
            }
            if (!seg.inheritPrevious) {
                TagChip(
                    text = if (seg.thumb != null) "Vyměnit snímek" else "Začít snímkem",
                    active = seg.thumb != null,
                ) { onPick() }
            }
        }

        if (!seg.inheritPrevious && seg.thumb == null && seg.mode == SegmentMode.IMAGE) {
            Spacer(Modifier.height(8.dp))
            Text(
                "Segment čeká na snímek, ze kterého má vyjít.",
                style = MaterialTheme.typography.bodySmall, color = Amber
            )
        }

        Spacer(Modifier.height(10.dp))
        LabeledSlider(
            label = "Délka",
            value = "%.1f s".format(seg.seconds),
            position = seg.seconds,
            range = 2f..TimelineScene.MAX_SEGMENT_SECONDS,
            onChange = { vm.setSegmentSeconds(seg.key, it) },
            note = "Nebo táhni za pravý okraj klipu v ose.",
        )

        // Uzel si hotové segmenty drží v mezipaměti projektu, takže jeden
        // upravený záběr nemusí znamenat přepočítání celé osy.
        Spacer(Modifier.height(10.dp))
        TagChip(
            text = "Přegenerovat jen tento segment",
            active = scene.onlySegment == index + 1,
        ) {
            vm.setTimelineOnlySegment(if (scene.onlySegment == index + 1) 0 else index + 1)
        }
        if (scene.onlySegment > 0) {
            Spacer(Modifier.height(6.dp))
            Text(
                "Generovat přepočítá jen segment ${scene.onlySegment}, ostatní se vezmou " +
                    "z mezipaměti. Po dokončení se volba sama vypne.",
                style = MaterialTheme.typography.bodySmall, color = Amber
            )
        }
    }
}

@Composable
private fun IconAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    popis: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .size(38.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, popis, Modifier.size(19.dp), if (enabled) TextMid else Outline1)
    }
}
