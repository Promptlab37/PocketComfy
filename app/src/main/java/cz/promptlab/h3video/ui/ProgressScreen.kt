package cz.promptlab.h3video.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cz.promptlab.h3video.comfy.Stage
import cz.promptlab.h3video.engine.GenState
import cz.promptlab.h3video.engine.GenerationService
import cz.promptlab.h3video.engine.RunKind
import cz.promptlab.h3video.engine.firstPhaseTitle
import cz.promptlab.h3video.engine.kind
import cz.promptlab.h3video.engine.mainPhaseTitle
import cz.promptlab.h3video.engine.stageDetailText
import cz.promptlab.h3video.engine.stageText
import cz.promptlab.h3video.util.Preview
import cz.promptlab.h3video.ui.theme.AccentSweep
import cz.promptlab.h3video.ui.theme.Amber
import cz.promptlab.h3video.ui.theme.Cyan
import cz.promptlab.h3video.ui.theme.Danger
import cz.promptlab.h3video.ui.theme.Ink
import cz.promptlab.h3video.ui.theme.Ok
import cz.promptlab.h3video.ui.theme.Outline1
import cz.promptlab.h3video.ui.theme.Surface1
import cz.promptlab.h3video.ui.theme.Surface2
import cz.promptlab.h3video.ui.theme.TextHi
import cz.promptlab.h3video.ui.theme.TextLow
import cz.promptlab.h3video.ui.theme.TextMid
import cz.promptlab.h3video.ui.theme.Violet
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/** Pět kroků, které uživatel na průběhu vidí. Uvnitř je jich víc, ale to ho nezajímá. */
private data class Phase(val title: String, val stages: Set<Stage>)

// Texty fází se berou z jednoho místa pro všechny druhy běhu:
// engine/RunTexts.kt (stageText, stageDetailText, mainPhaseTitle…).

private val PHASES = listOf(
    Phase("Spojení a odeslání referencí", setOf(Stage.STARTING, Stage.UPLOADING)),
    Phase("Fronta a modely", setOf(Stage.QUEUED, Stage.MODELS, Stage.REFERENCES)),
    Phase("Čtení zadání", setOf(Stage.ENCODING)),
    Phase("Generování obrazu a zvuku", setOf(Stage.SAMPLING)),
    Phase("Dokončení a přenos do aplikace", setOf(Stage.DECODING, Stage.MUXING, Stage.DOWNLOADING, Stage.FINISHING)),
)

/** Prstenec je jen ukazatel u textu, ne hlavní hrdina obrazovky. */
private val RING_COMPACT = 92.dp

/**
 * Obrazovka průběhu.
 *
 * ROZVRŽENÍ – jádro věci, protože předchozí pokusy ho měly špatně:
 *
 * Obsah zabere kolem 460 dp, telefon má přes 800. Zbylých ~340 dp se někam dát
 * musí. Když se rozdají pružnými mezerami mezi prvky, vznikne uvnitř obrazovky
 * díra – nejdřív zela nad prstencem, pak mezi popiskem a čísly. Vycentrování
 * celého bloku problém neřeší, jen ho rozpůlí na dvě menší díry.
 *
 * Řešení: volné místo dostane JEDEN prvek, který ho umí využít – plocha
 * s živým náhledem generovaného videa. Dokud náhled nedorazí, je v ní rámeček
 * s vysvětlením. Prstenec proto sedí malý nahoře vedle textu, nezabírá půl
 * obrazovky, a čísla s tlačítky drží dole.
 */
@Composable
fun ProgressScreen(
    state: GenState.Running,
    onMinimize: () -> Unit,
    onCancel: () -> Unit,
    queueCount: Int = 0,
) {
    val progress by animateFloatAsState(
        targetValue = state.overall,
        animationSpec = tween(600, easing = FastOutSlowInEasing),
        label = "progress"
    )
    val infinite = rememberInfiniteTransition(label = "spin")
    val spin by infinite.animateFloat(
        0f, 360f,
        infiniteRepeatable(tween(9000, easing = LinearEasing), RepeatMode.Restart),
        label = "spin"
    )
    val breathe by infinite.animateFloat(
        0.94f, 1.06f,
        infiniteRepeatable(tween(2400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "breathe"
    )

    // vlastní vteřinový tik, aby čas běžel i ve fázích, kdy server zrovna nic neposílá
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }
    val elapsed = ((now - state.startedAt) / 1000).toInt().coerceAtLeast(0)
    val activePhase = PHASES.indexOfFirst { state.stage in it.stages }.coerceAtLeast(0)

    val preview = state.preview
    val note = state.note

    Column(
        Modifier
            .fillMaxSize()
            .background(Ink)
            .padding(horizontal = 20.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // ---------------------------------------------------------- záhlaví
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "GENERUJI",
                style = MaterialTheme.typography.labelMedium,
                color = TextLow,
                letterSpacing = 4.sp
            )
            Spacer(Modifier.weight(1f))
            if (state.label.isNotEmpty()) {
                Text(
                    state.label,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextLow,
                    maxLines = 1
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        // ------------------------------ prstenec vedle textu, jeden řádek
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            ProgressRing(progress, spin, breathe, RING_COMPACT)
            Column(Modifier.weight(1f)) {
                Text(
                    if (state.preparing) "Načítám model"
                    else stageText(state.stage, state.kind),
                    style = MaterialTheme.typography.titleLarge,
                    color = TextHi,
                    maxLines = 2
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    when {
                        state.preparing -> "Model se nahrává do grafické karty, chvíli to trvá."
                        state.stage == Stage.QUEUED && state.queuePosition > 0 ->
                            "Před tebou je ${state.queuePosition} úloha ve frontě"
                        else -> stageDetailText(state.stage, state.kind)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMid,
                    maxLines = 3
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        // ---------------------------------------------------- živý náhled
        // Tenhle prvek dostane všechno zbylé místo (weight). Proto na obrazovce
        // nikde nezeje prázdno – a když náhled dorazí, je konečně pořádně vidět.
        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(16.dp))
                .background(Surface1)
                .border(1.dp, Outline1, RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (preview != null) {
                LivePreview(preview, Modifier.fillMaxSize())
            } else {
                Text(
                    if (state.stage == Stage.SAMPLING)
                        "Náhled se objeví, jakmile model vykreslí první snímek"
                    else "Náhled se objeví, až model začne kreslit",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextLow,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        // ---------------------------------------------------------- čísla
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StatTile("Uplynulo", formatClock(elapsed), Modifier.weight(1f))
            StatTile(
                "Zbývá",
                state.etaSeconds?.let { GenerationService.formatEta(it) } ?: "počítám",
                Modifier.weight(1f)
            )
            // Během přenosu videa ukazuje stejná dlaždice, kolik už je staženo –
            // u větších souborů to trvá a bez čísel to vypadá zaseknutě.
            if (state.stage == Stage.DOWNLOADING && state.transferTotal > 0) {
                StatTile(
                    "Přeneseno",
                    "%.1f MB".format(state.transferDone / 1_048_576f),
                    Modifier.weight(1f),
                    hint = "z %.1f MB".format(state.transferTotal / 1_048_576f)
                )
            } else {
                StatTile(
                    "Krok",
                    if (state.stage == Stage.SAMPLING && state.totalSteps > 0)
                        "${state.step}/${state.totalSteps}" else "—",
                    Modifier.weight(1f),
                    // ať je vidět, z čeho odhad vychází
                    hint = if (state.secondsPerStep > 0)
                        "%.0f s / krok".format(state.secondsPerStep) else null
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        // ----------------------------------------------------- pásek fází
        PhaseStrip(activePhase, state.kind)

        // ------------------------------------------------ klidná poznámka
        if (note != null) {
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Amber.copy(alpha = .08f))
                    .border(1.dp, Amber.copy(alpha = .22f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 11.dp, vertical = 9.dp),
                horizontalArrangement = Arrangement.spacedBy(9.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.CloudOff, null, Modifier.size(16.dp), Amber)
                Text(
                    note,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMid,
                    maxLines = 3
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // ------------------------------------------- tlačítka vedle sebe
        // Zrušit chce potvrzení druhým klepnutím – omylem zabitý dlouhý běh
        // je drahý. Po pár vteřinách bez potvrzení se tlačítko samo vrátí.
        var potvrditZruseni by remember { mutableStateOf(false) }
        LaunchedEffect(potvrditZruseni) {
            if (potvrditZruseni) {
                kotlinx.coroutines.delay(4000)
                potvrditZruseni = false
            }
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlineButton(
                "Skrýt",
                modifier = Modifier.weight(1f),
                color = Cyan,
                onClick = onMinimize
            )
            OutlineButton(
                if (potvrditZruseni) "Opravdu zrušit?" else "Zrušit",
                modifier = Modifier.weight(1f),
                color = if (potvrditZruseni) Danger else TextMid,
                onClick = {
                    if (potvrditZruseni) onCancel() else potvrditZruseni = true
                }
            )
        }

        Spacer(Modifier.height(10.dp))
        Text(
            if (queueCount > 0)
                "Ve frontě čeká ${queueCount}× další běh – naskočí sám."
            else
                "Telefon můžeš zamknout, generování běží na počítači dál.",
            style = MaterialTheme.typography.bodySmall,
            color = TextLow,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
        }
}

/** Prstenec s procenty. Velikost se předává, aby se vešel i vedle živého náhledu. */
@Composable
private fun ProgressRing(progress: Float, spin: Float, breathe: Float, size: Dp) {
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        // rozostřená aura
        Box(
            Modifier
                .size(size * 0.84f * breathe)
                .clip(RoundedCornerShape(50))
                .background(
                    Brush.radialGradient(listOf(Violet.copy(alpha = .18f), Color.Transparent))
                )
        )
        androidx.compose.foundation.Canvas(
            Modifier
                .fillMaxSize()
                .rotate(spin)
        ) {
            // Tloušťka roste s prstencem, ať to vypadá stejně na velkém i malém.
            val stroke = (this.size.minDimension * 0.068f).coerceAtLeast(7f)
            val inset = stroke / 2
            val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
            drawArc(
                color = Surface2,
                startAngle = 0f, sweepAngle = 360f, useCenter = false,
                topLeft = Offset(inset, inset), size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
            drawArc(
                brush = AccentSweep,
                startAngle = -90f,
                sweepAngle = 360f * progress.coerceIn(0f, 1f),
                useCenter = false,
                topLeft = Offset(inset, inset), size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "${(progress * 100).roundToInt()}",
                fontSize = (size.value * 0.26f).sp,
                fontWeight = FontWeight.Light,
                color = TextHi,
                maxLines = 1
            )
            Text("%", style = MaterialTheme.typography.labelMedium, color = TextLow)
        }
    }
}

/**
 * Pět fází jako vodorovný pásek. Sloupec pěti řádků zabíral přes 200 dp a kvůli
 * němu se muselo rolovat; tady je stejná informace na dvou řádcích.
 */
@Composable
private fun PhaseStrip(activePhase: Int, kind: RunKind = RunKind.VIDEO) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            PHASES.forEachIndexed { i, _ ->
                Box(
                    Modifier
                        .weight(1f)
                        .height(5.dp)
                        .clip(RoundedCornerShape(50))
                        .background(
                            when {
                                i < activePhase -> Ok
                                i == activePhase -> Cyan
                                else -> Outline1
                            }
                        )
                )
            }
        }
        Spacer(Modifier.height(5.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Fáze ${activePhase + 1} z ${PHASES.size}",
                style = MaterialTheme.typography.bodySmall,
                color = TextLow
            )
            Spacer(Modifier.weight(1f))
            Text(
                when (activePhase) {
                    0 -> firstPhaseTitle(kind)
                    3 -> mainPhaseTitle(kind)
                    else -> PHASES[activePhase].title
                },
                style = MaterialTheme.typography.bodySmall,
                color = TextMid,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun StatTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    hint: String? = null,
) {
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Surface1)
            .border(1.dp, Outline1, RoundedCornerShape(14.dp))
            .padding(vertical = 9.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(value, style = MaterialTheme.typography.titleMedium, color = TextHi, maxLines = 1)
        Text(label, style = MaterialTheme.typography.bodySmall, color = TextLow, maxLines = 1)
        if (hint != null) {
            Text(hint, style = MaterialTheme.typography.bodySmall, color = Cyan)
        }
    }
}

fun formatClock(seconds: Int): String {
    val m = seconds / 60
    val s = seconds % 60
    return "%d:%02d".format(m, s)
}

/**
 * Živý náhled. Uzel ModelPreviewOverrideKJ posílá animaci, ne jeden obrázek –
 * podle toho, co karta umí, buď MP4 (rozebrané na snímky), nebo animovaný WebP
 * (ten se přehrává sám, jen potřebuje ImageView kvůli překreslovacím voláním).
 */
@androidx.compose.runtime.Composable
private fun LivePreview(preview: Preview, modifier: Modifier = Modifier) {
    when (preview) {
        is Preview.Frames -> {
            val frames = preview.frames
            var index by androidx.compose.runtime.remember(frames) {
                androidx.compose.runtime.mutableIntStateOf(0)
            }
            // Jediný snímek se nepřehrává – jinak by tikal naprázdno na pozadí.
            if (frames.size > 1) {
                LaunchedEffect(frames) {
                    val step = (1000L / preview.fps).coerceAtLeast(40L)
                    while (true) {
                        kotlinx.coroutines.delay(step)
                        index = (index + 1) % frames.size
                    }
                }
            }
            Image(
                bitmap = frames[index.coerceIn(0, frames.lastIndex)].asImageBitmap(),
                contentDescription = null,
                modifier = modifier,
                contentScale = ContentScale.Fit
            )
        }

        is Preview.Animated -> androidx.compose.ui.viewinterop.AndroidView(
            factory = { ctx ->
                android.widget.ImageView(ctx).apply {
                    scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
                }
            },
            update = { view ->
                view.setImageDrawable(preview.drawable)
                (preview.drawable as? android.graphics.drawable.AnimatedImageDrawable)?.start()
            },
            modifier = modifier
        )
    }
}
