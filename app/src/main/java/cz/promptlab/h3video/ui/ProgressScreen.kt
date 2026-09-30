package cz.promptlab.h3video.ui

import cz.promptlab.h3video.data.t

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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
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

/**
 * Fáze se skládají FUNKCÍ, ne konstantou: hodnota na úrovni souboru vznikne
 * jednou při zavedení třídy a texty by zůstaly v jazyce, který platil při
 * startu. Takhle se přeloží při každém čtení.
 */
private fun phases() = listOf(
    Phase(t("Spojení a odeslání referencí"), setOf(Stage.STARTING, Stage.UPLOADING)),
    Phase(t("Fronta a modely"), setOf(Stage.QUEUED, Stage.MODELS, Stage.REFERENCES)),
    Phase(t("Čtení zadání"), setOf(Stage.ENCODING)),
    Phase(t("Generování obrazu a zvuku"), setOf(Stage.SAMPLING)),
    Phase(t("Dokončení a přenos do aplikace"), setOf(Stage.DECODING, Stage.MUXING, Stage.DOWNLOADING, Stage.FINISHING)),
)

/** Prstenec je jen ukazatel u textu, ne hlavní hrdina obrazovky. */
private val RING_COMPACT = 108.dp

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
    /** Fronta čekajících běhů — ukazuje se rovnou tady, ne až po zavření. */
    fronta: List<cz.promptlab.h3video.engine.QueuedRun> = emptyList(),
    onRemoveFromQueue: (Long) -> Unit = {},
) {
    val progress by animateFloatAsState(
        targetValue = state.overall,
        animationSpec = tween(600, easing = FastOutSlowInEasing),
        label = "progress"
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
    val activePhase = phases().indexOfFirst { state.stage in it.stages }.coerceAtLeast(0)

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
                t("GENERUJI"),
                style = MaterialTheme.typography.labelMedium,
                color = TextLow,
                letterSpacing = 4.sp
            )
            // Karta a pod ní rozlišení, na které se opravdu generuje (z grafu, 5.45).
            Column(Modifier.weight(1f).padding(start = 12.dp), horizontalAlignment = Alignment.End) {
                if (state.label.isNotEmpty()) Text(
                    state.label, style = MaterialTheme.typography.bodySmall, color = TextLow, maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
                if (state.rozliseni.isNotEmpty()) Text(
                    state.rozliseni,
                    style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
                    color = Cyan, maxLines = 1,
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
            ProgressRing(
                progress,
                indeterminate = state.preparing || state.stage in setOf(Stage.STARTING, Stage.QUEUED, Stage.MODELS),
                size = RING_COMPACT,
            )
            Column(Modifier.weight(1f)) {
                Text(
                    if (state.preparing) t("Načítám model")
                    else stageText(state.stage, state.kind, state.model),
                    style = MaterialTheme.typography.titleLarge,
                    color = TextHi,
                    maxLines = 2
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    when {
                        state.preparing -> t("Model se nahrává do grafické karty, chvíli to trvá.")
                        state.stage == Stage.QUEUED && state.queuePosition > 0 ->
                            t("Před tebou je %d úloha ve frontě").format(state.queuePosition)
                        else -> stageDetailText(state.stage, state.kind, state.modelSoubor)
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
                        t("Náhled se objeví, jakmile model vykreslí první snímek")
                    else t("Náhled se objeví, až model začne kreslit"),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextLow,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        // ------------------------------- panel průběhu: čísla + souvislý pruh fází
        // Jedna karta místo tří dlaždic a samostatného pásku (5.34, grafik + UX).
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Brush.verticalGradient(listOf(Surface2, Surface1)))
                .border(1.dp, Outline1, RoundedCornerShape(16.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
        Row(
            Modifier.fillMaxWidth().height(androidx.compose.foundation.layout.IntrinsicSize.Min),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StatTile(t("Uplynulo"), formatClock(elapsed), Modifier.weight(1f))
            Oddelovac()
            StatTile(
                t("Zbývá"),
                state.etaSeconds?.let { GenerationService.formatEta(it) } ?: t("počítám"),
                Modifier.weight(1f)
            )
            Oddelovac()
            // Během přenosu videa ukazuje stejná dlaždice, kolik už je staženo –
            // u větších souborů to trvá a bez čísel to vypadá zaseknutě.
            if (state.stage == Stage.DOWNLOADING && state.transferTotal > 0) {
                StatTile(
                    t("Přeneseno"),
                    "%.1f MB".format(state.transferDone / 1_048_576f),
                    Modifier.weight(1f),
                    hint = t("z %.1f MB").format(state.transferTotal / 1_048_576f)
                )
            } else {
                StatTile(
                    t("Krok"),
                    if (state.stage == Stage.SAMPLING && state.totalSteps > 0)
                        "${state.step}/${state.totalSteps}" else "—",
                    Modifier.weight(1f),
                    // ať je vidět, z čeho odhad vychází
                    hint = if (state.secondsPerStep > 0)
                        t("%.0f s / krok").format(state.secondsPerStep) else null
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        PhaseStrip(
            activePhase, state.kind,
            kroku = if (state.stage == Stage.SAMPLING) state.totalSteps else 0,
            // Postup uvnitř běžící fáze: kroky vzorkování, bajty přenosu; jinak neznámý.
            uvnitr = when {
                state.stage == Stage.SAMPLING && state.totalSteps > 0 -> state.step.toFloat() / state.totalSteps
                state.stage == Stage.DOWNLOADING && state.transferTotal > 0 -> state.transferDone.toFloat() / state.transferTotal
                else -> null
            },
        )
        }

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
                t("Skrýt"),
                modifier = Modifier.weight(1f),
                color = Cyan,
                onClick = onMinimize
            )
            OutlineButton(
                if (potvrditZruseni) t("Opravdu zrušit?") else t("Zrušit"),
                modifier = Modifier.weight(1f),
                color = if (potvrditZruseni) Danger else TextMid,
                onClick = {
                    if (potvrditZruseni) onCancel() else potvrditZruseni = true
                }
            )
        }

        Spacer(Modifier.height(10.dp))
        if (fronta.isNotEmpty()) {
            // Celá fronta rovnou tady: dřív se tu ukazoval jen počet a seznam
            // byl schovaný pod obrazovkou průběhu, kam se za běhu nedalo.
            FrontaPruh(
                fronta,
                onRemove = onRemoveFromQueue,
                modifier = Modifier.padding(horizontal = 4.dp),
                prvni = state.label.ifBlank { null },
            )
        } else {
            TextVesel(
                t("Telefon můžeš zamknout, generování běží na počítači dál."),
                style = MaterialTheme.typography.bodySmall,
                color = TextLow,
            )
        }
        }
}

/** Barvy ukazatele: přechod z hlavní barvy motivu do akcentu — sedí ve všech vzhledech. */
private fun barvyPrubehu(): List<Color> = listOf(Violet, Cyan)

/**
 * Prstenec s procenty (5.34, grafik + UX + vývojář): oblouk vždy začíná nahoře
 * a neotáčí se (dřív se točil celý a 14 % viselo na jedenácté hodině). Přechod
 * z barev motivu, jemná záře pod obloukem, na špičce tepající tečka. Když
 * procenta stojí (fronta, načítání modelu), obíhá po dráze krátký světelný úsek.
 */
@Composable
private fun ProgressRing(progress: Float, indeterminate: Boolean, size: Dp) {
    val anim = rememberInfiniteTransition(label = "prstenec")
    val tep by anim.animateFloat(
        0f, 1f, infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "tep"
    )
    // Točící se světelný prstenec kolem kroužku — běží pořád (uživatel 30. 9. 2026:
    // „jak se točilo to kolečko, bylo to dobrý“). Oblouk průběhu se přitom netočí.
    val toceni by anim.animateFloat(
        0f, 360f, infiniteRepeatable(tween(2600, easing = LinearEasing), RepeatMode.Restart), label = "toceni"
    )
    val barvy = barvyPrubehu()
    val p = progress.coerceIn(0f, 1f)
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
            val tloustka = 5.dp.toPx()
            val tecka = 3.5.dp.toPx()
            // Místo pro vnější točící se prstenec — kroužek průběhu má pořád ~76 dp jako dřív.
            val okraj = tecka * 1.4f + 10.dp.toPx()
            val prumer = this.size.minDimension - okraj * 2
            val tl = Offset(okraj, okraj)
            val rozmer = Size(prumer, prumer)
            // Vnější točící se prstenec: kometa z barev motivu, dokola.
            run {
                val stredR = Offset(this.size.width / 2, this.size.height / 2)
                // Dost daleko od průběhu, ať nevypadá jako druhý ukazatel (odborníci 30. 9.).
                val vnejsi = prumer / 2 + 8.dp.toPx()
                val tlV = Offset(stredR.x - vnejsi, stredR.y - vnejsi)
                val kometa = Brush.sweepGradient(
                    0f to Color.Transparent, 0.55f to barvy[0].copy(alpha = .15f),
                    // Když procenta stojí (fronta, načítání), je kometa jasnější — je vidět, že se pracuje.
                    0.92f to barvy[1].copy(alpha = if (indeterminate) .85f else .6f), 1f to Color.Transparent, center = stredR,
                )
                rotate(toceni, stredR) {
                    // Záře pod kometou, pak ostrá kometa a zářivá hlava na jejím konci.
                    drawArc(kometa, 0f, 360f, false, tlV, Size(vnejsi * 2, vnejsi * 2), alpha = .3f,
                        style = Stroke(5.dp.toPx(), cap = StrokeCap.Round))
                    drawArc(kometa, 0f, 360f, false, tlV, Size(vnejsi * 2, vnejsi * 2),
                        style = Stroke(1.5.dp.toPx(), cap = StrokeCap.Round))
                    val hlava = Offset(stredR.x + vnejsi * kotlin.math.cos(Math.toRadians(-8.0)).toFloat(),
                        stredR.y + vnejsi * kotlin.math.sin(Math.toRadians(-8.0)).toFloat())
                    drawCircle(barvy[1].copy(alpha = .25f), 4.dp.toPx(), hlava)
                    drawCircle(barvy[1].copy(alpha = .8f), 1.8.dp.toPx(), hlava)
                }
            }
            // Dráha — celý kruh, ať je vidět, kolik zbývá.
            drawArc(Outline1, 0f, 360f, false, tl, rozmer, style = Stroke(tloustka))
            // Přechod po obvodu otočený tak, aby začínal nahoře (šev pod začátkem oblouku).
            val stred = Offset(this.size.width / 2, this.size.height / 2)
            val prechod = Brush.sweepGradient(0f to barvy[0], 1f to barvy[1], center = stred)
            rotate(-90f, stred) {
                if (p > 0f) {
                    // Záře: týž oblouk širší a průsvitný.
                    drawArc(prechod, 0f, 360f * p, false, tl, rozmer, alpha = .12f,
                        style = Stroke(tloustka * 1.8f, cap = StrokeCap.Round))
                    drawArc(prechod, 0f, 360f * p, false, tl, rozmer,
                        style = Stroke(tloustka, cap = StrokeCap.Round))
                }
            }
            // Tečka na špičce oblouku (nahoře, dokud je 0 %).
            val uhel = Math.toRadians((-90.0 + 360.0 * p))
            val r = prumer / 2
            val bod = Offset(stred.x + (r * kotlin.math.cos(uhel)).toFloat(), stred.y + (r * kotlin.math.sin(uhel)).toFloat())
            // U začátku a konce kruhu by se tečka slepila s kulatým koncem oblouku.
            if (p <= 0.001f || p in 0.02f..0.92f) {
                drawCircle(barvy[1].copy(alpha = .25f * (1f - tep) + .1f), tecka * (1.6f + tep), bod)
                drawCircle(barvy[1], tecka * (1f + .3f * tep), bod)
            }
        }
        val hustota = androidx.compose.ui.platform.LocalDensity.current
        // Pevné velikosti z dp: prstenec má pevný rozměr, „100 %“ se musí vejít i při velkém písmu.
        val velke = with(hustota) { (size * 0.27f).toSp() }
        val male = with(hustota) { (size * 0.14f).toSp() }
        Text(
            androidx.compose.ui.text.buildAnnotatedString {
                append("${(p * 100).roundToInt()}")
                pushStyle(androidx.compose.ui.text.SpanStyle(fontSize = male, color = TextLow, fontWeight = FontWeight.Medium))
                append("%")
                pop()
            },
            fontSize = velke,
            fontWeight = FontWeight.SemiBold,
            color = TextHi,
            maxLines = 1,
            style = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "tnum"),
        )
    }
}

/**
 * Jeden souvislý ukazatel průběhu rozdělený na fáze (5.34, uživatel: „ten
 * proužek s průběhem a krokama, ať to vypadá profi“). Hotové fáze jsou plné
 * přechodem z barev motivu, běžící fáze se plní podle kroků (nebo přenosu)
 * a po naplněné části jede světelný odlesk. Když postup uvnitř fáze není
 * znám, běží po ní pomalé světlo. Pod pruhem fáze vlevo, kroky vpravo.
 */
@Composable
private fun PhaseStrip(activePhase: Int, kind: RunKind = RunKind.VIDEO, uvnitr: Float? = null, kroku: Int = 0) {
    val anim = rememberInfiniteTransition(label = "pruh")
    val odlesk by anim.animateFloat(
        -0.4f, 1.4f, infiniteRepeatable(tween(1800, easing = LinearEasing), RepeatMode.Restart), label = "odlesk"
    )
    val plneni by animateFloatAsState((uvnitr ?: 0f).coerceIn(0f, 1f), tween(600, easing = FastOutSlowInEasing), label = "plneni")
    val barvy = barvyPrubehu()
    val pocet = phases().size
    Column(Modifier.fillMaxWidth()) {
        androidx.compose.foundation.Canvas(Modifier.fillMaxWidth().height(14.dp)) {
            val vyska = 8.dp.toPx()
            val mezera = 4.dp.toPx()
            val y = (size.height - vyska) / 2
            val sirka = (size.width - mezera * (pocet - 1)) / pocet
            val roh = androidx.compose.ui.geometry.CornerRadius(vyska / 2)
            // Přechod přes CELOU šířku — každý dílek má svůj kus, pruh působí jako jeden.
            val prechod = Brush.horizontalGradient(barvy, startX = 0f, endX = size.width)
            for (i in 0 until pocet) {
                val x = i * (sirka + mezera)
                drawRoundRect(Outline1, Offset(x, y), Size(sirka, vyska), roh)
                // Běžící fáze má barevný podklad — hned je vidět, kde se právě pracuje.
                if (i == activePhase) drawRoundRect(barvy[0].copy(alpha = .22f), Offset(x, y), Size(sirka, vyska), roh)
                val podil = when {
                    i < activePhase -> 1f
                    i == activePhase -> plneni
                    else -> 0f
                }
                if (podil > 0f) {
                    val w = (sirka * podil).coerceAtLeast(vyska)
                    // Záře pod naplněnou částí.
                    drawRoundRect(prechod, Offset(x, y - 2.dp.toPx()), Size(w, vyska + 4.dp.toPx()),
                        androidx.compose.ui.geometry.CornerRadius(vyska), alpha = .18f)
                    drawRoundRect(prechod, Offset(x, y), Size(w, vyska), roh)
                }
                if (i == activePhase && kroku in 2..8) {
                    // Pár kroků: hotové dílky oddělené mezerou (víc zářezů by vypadalo jako čárový kód).
                    for (k in 1 until kroku) {
                        val zx = x + sirka * k / kroku
                        if (zx < x + sirka * podil) drawLine(Surface1, Offset(zx, y), Offset(zx, y + vyska), 1.5.dp.toPx())
                    }
                }
                if (i == activePhase) {
                    // Světelný odlesk: po naplněné části, a když postup neznáme, po celém dílku.
                    val rozsah = if (uvnitr != null) (sirka * podil).coerceAtLeast(vyska) else sirka
                    val stredOdlesku = x + rozsah * odlesk
                    val polomer = sirka * 0.35f
                    clipRect(x, y, x + rozsah, y + vyska) {
                        drawRoundRect(
                            Brush.horizontalGradient(
                                // S postupem bílý lesk po výplni, bez něj barevné světlo po celé fázi.
                                if (uvnitr != null) listOf(Color.Transparent, Color.White.copy(alpha = .45f), Color.Transparent)
                                else listOf(Color.Transparent, barvy[1].copy(alpha = .95f), Color.Transparent),
                                startX = stredOdlesku - polomer, endX = stredOdlesku + polomer,
                            ),
                            Offset(x, y), Size(rozsah, vyska), roh,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                t("Fáze %d z %d").format(activePhase + 1, pocet),
                style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                color = TextLow
            )
            Spacer(Modifier.width(12.dp))
            // Velké písmo / úzký displej: zalomí se na druhý řádek, neuřízne.
            Text(
                when (activePhase) {
                    0 -> firstPhaseTitle(kind)
                    3 -> mainPhaseTitle(kind)
                    else -> phases()[activePhase].title
                },
                style = MaterialTheme.typography.bodySmall,
                color = TextMid,
                maxLines = 2,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** Svislá čára mezi čísly v panelu průběhu. */
@Composable
private fun Oddelovac() {
    Box(Modifier.width(1.dp).fillMaxHeight(.7f).background(Outline1))
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
            .fillMaxHeight()
            .padding(horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top,
    ) {
        // Tři dlaždice vedle sebe — na úzkém telefonu se hodnota zmenší, neuřízne.
        // Pevná šířka číslic: čas „3:38“ při tikání neposkakuje.
        TextVesel(value, style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"), color = TextHi)
        TextVesel(label, style = MaterialTheme.typography.bodySmall, color = TextLow)
        if (hint != null) {
            Text(
                hint, style = MaterialTheme.typography.bodySmall, color = Cyan, maxLines = 2,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
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
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                    (preview.drawable as? android.graphics.drawable.AnimatedImageDrawable)?.start()
                }
            },
            modifier = modifier
        )
    }
}
