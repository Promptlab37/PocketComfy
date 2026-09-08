package cz.promptlab.h3video.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cz.promptlab.h3video.comfy.AngleBuilder
import cz.promptlab.h3video.data.t
import cz.promptlab.h3video.ui.theme.Cyan
import cz.promptlab.h3video.ui.theme.Outline1
import cz.promptlab.h3video.ui.theme.Surface1
import cz.promptlab.h3video.ui.theme.Surface2
import cz.promptlab.h3video.ui.theme.TextLow
import cz.promptlab.h3video.ui.theme.TextMid
import cz.promptlab.h3video.ui.theme.Violet
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Půdorys: objekt uprostřed, kamera se kolem něj **táhne prstem**.
 *
 * Úhel od středu je směr (osm poloh), vzdálenost od středu je odstup (tři
 * kruhy). Kamera skáče jen na natrénované polohy — mezipolohy model nezná,
 * takže by ukazovat plynulý pohyb znamenalo slibovat, co graf zahodí.
 *
 * Objekt je nakreslený čelem dolů, k divákovi. Proto pohled „zprava" leží
 * na obrazovce vlevo: je to pravá strana objektu, ne divákova.
 */
@Composable
fun PudorysKamery(
    smer: Int,
    odstup: Int,
    onZmena: (smer: Int, odstup: Int) -> Unit,
) {
    val mericTextu = rememberTextMeasurer()
    val hustota = LocalDensity.current

    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .pointerInput(Unit) {
                fun zPozice(p: Offset) {
                    val stred = Offset(size.width / 2f, size.height / 2f)
                    val polomer = minOf(size.width, size.height) / 2f - with(hustota) { 26.dp.toPx() }
                    val d = p - stred
                    // 0° dole, roste po směru hodinových ručiček (osa y míří dolů)
                    val stupne = Math.toDegrees(atan2(d.y, d.x).toDouble()).toFloat() - 90f
                    val pomer = (hypot(d.x, d.y) / polomer).coerceIn(0f, 1.2f)
                    onZmena(AngleBuilder.smerZUhlu(stupne), AngleBuilder.odstupZPomeru(pomer))
                }
                detectDragGestures { zmena, _ -> zPozice(zmena.position) }
            }
            .pointerInput(Unit) {
                detectTapGestures { p ->
                    val stred = Offset(size.width / 2f, size.height / 2f)
                    val polomer = minOf(size.width, size.height) / 2f - with(hustota) { 26.dp.toPx() }
                    val d = p - stred
                    val stupne = Math.toDegrees(atan2(d.y, d.x).toDouble()).toFloat() - 90f
                    val pomer = (hypot(d.x, d.y) / polomer).coerceIn(0f, 1.2f)
                    onZmena(AngleBuilder.smerZUhlu(stupne), AngleBuilder.odstupZPomeru(pomer))
                }
            }
    ) {
        Canvas(Modifier.fillMaxWidth().aspectRatio(1f)) {
            val stred = center
            val polomer = minOf(size.width, size.height) / 2f - 26.dp.toPx()

            // kruhy odstupu — vybraný svítí
            AngleBuilder.ODSTUP_POMER.forEachIndexed { i, pomer ->
                drawCircle(
                    color = if (i == odstup) Violet.copy(alpha = 0.55f) else Outline1,
                    radius = polomer * pomer,
                    center = stred,
                    style = Stroke(width = if (i == odstup) 2.5.dp.toPx() else 1.dp.toPx()),
                )
            }

            // paprsky do osmi směrů
            repeat(AngleBuilder.AZIMUTY.size) { i ->
                val bod = bodNaKruhu(stred, polomer * 1.0f, AngleBuilder.uhelProSmer(i))
                drawLine(
                    color = if (i == smer) Violet.copy(alpha = 0.35f) else Outline1.copy(alpha = 0.6f),
                    start = stred,
                    end = bod,
                    strokeWidth = 1.dp.toPx(),
                )
            }

            objektUprostred(stred, 20.dp.toPx())

            // popisky osmi směrů po obvodu
            AngleBuilder.AZIMUTY.forEachIndexed { i, (_, nazevCs) ->
                val bod = bodNaKruhu(stred, polomer + 14.dp.toPx(), AngleBuilder.uhelProSmer(i))
                popisek(
                    mericTextu, t(nazevCs), bod,
                    if (i == smer) Cyan else TextLow,
                    if (i == smer) 11.sp.value else 10.sp.value,
                )
            }

            // samotná kamera
            val kamera = bodNaKruhu(
                stred, polomer * AngleBuilder.pomerProOdstup(odstup),
                AngleBuilder.uhelProSmer(smer),
            )
            drawLine(Violet, stred, kamera, strokeWidth = 2.dp.toPx())
            drawCircle(Violet.copy(alpha = 0.25f), radius = 18.dp.toPx(), center = kamera)
            drawCircle(Violet, radius = 9.dp.toPx(), center = kamera)
            drawCircle(Surface1, radius = 4.dp.toPx(), center = kamera)
        }
    }
}

/**
 * Boční pohled: objekt vlevo, kamera se táhne po oblouku od podhledu
 * (−30°) po nadhled (60°). Čtyři polohy, které LoRA umí.
 */
@Composable
fun BokorysKamery(vyska: Int, onZmena: (Int) -> Unit) {
    val mericTextu = rememberTextMeasurer()
    val hustota = LocalDensity.current

    Box(
        Modifier
            .fillMaxWidth()
            .height(150.dp)
            .pointerInput(Unit) {
                fun zPozice(p: Offset) {
                    val zaklad = Offset(
                        with(hustota) { 44.dp.toPx() },
                        size.height - with(hustota) { 44.dp.toPx() },
                    )
                    val d = p - zaklad
                    // y míří dolů, takže nahoru je záporné → otočit znaménko
                    val stupne = Math.toDegrees(atan2(-d.y, abs(d.x)).toDouble()).toFloat()
                    onZmena(AngleBuilder.vyskaZUhlu(stupne))
                }
                detectDragGestures { zmena, _ -> zPozice(zmena.position) }
            }
            .pointerInput(Unit) {
                detectTapGestures { p ->
                    val zaklad = Offset(
                        with(hustota) { 44.dp.toPx() },
                        size.height - with(hustota) { 44.dp.toPx() },
                    )
                    val d = p - zaklad
                    val stupne = Math.toDegrees(atan2(-d.y, abs(d.x)).toDouble()).toFloat()
                    onZmena(AngleBuilder.vyskaZUhlu(stupne))
                }
            }
    ) {
        Canvas(Modifier.fillMaxWidth().height(150.dp)) {
            val zaklad = Offset(44.dp.toPx(), size.height - 44.dp.toPx())
            val delka = size.width - 96.dp.toPx()

            // obzor
            drawLine(
                Outline1, Offset(20.dp.toPx(), zaklad.y),
                Offset(size.width - 20.dp.toPx(), zaklad.y), 1.dp.toPx(),
            )

            // objekt jako postavička na zemi
            drawCircle(TextMid, 6.dp.toPx(), Offset(zaklad.x, zaklad.y - 26.dp.toPx()))
            drawLine(
                TextMid, Offset(zaklad.x, zaklad.y - 20.dp.toPx()),
                Offset(zaklad.x, zaklad.y), 3.dp.toPx(),
            )

            // oblouk s možnými výškami
            AngleBuilder.VYSKA_STUPNE.forEachIndexed { i, stupen ->
                val bod = bodVBokorysu(zaklad, delka, stupen)
                drawLine(
                    color = if (i == vyska) Violet.copy(alpha = 0.4f) else Outline1.copy(alpha = 0.7f),
                    start = Offset(zaklad.x, zaklad.y - 20.dp.toPx()),
                    end = bod,
                    strokeWidth = 1.dp.toPx(),
                )
                if (i != vyska) drawCircle(Outline1, 5.dp.toPx(), bod)
            }

            val kamera = bodVBokorysu(zaklad, delka, AngleBuilder.uhelProVysku(vyska))
            drawCircle(Violet.copy(alpha = 0.25f), 17.dp.toPx(), kamera)
            drawCircle(Violet, 9.dp.toPx(), kamera)
            drawCircle(Surface1, 4.dp.toPx(), kamera)

            popisek(
                mericTextu, t(AngleBuilder.VYSKY[vyska].second),
                Offset(kamera.x, kamera.y - 22.dp.toPx()), Cyan, 11f,
            )
        }
    }
}

// ------------------------------------------------------------------ kreslení

private fun bodNaKruhu(stred: Offset, polomer: Float, stupne: Float): Offset {
    val rad = Math.toRadians((stupne + 90f).toDouble())
    return Offset(
        stred.x + polomer * cos(rad).toFloat(),
        stred.y + polomer * sin(rad).toFloat(),
    )
}

private fun bodVBokorysu(zaklad: Offset, delka: Float, stupne: Float): Offset {
    val rad = Math.toRadians(stupne.toDouble())
    return Offset(
        zaklad.x + delka * cos(rad).toFloat(),
        zaklad.y - 20f - delka * sin(rad).toFloat(),
    )
}

/** Objekt v půdorysu: kolečko s klínem, který ukazuje, kam je čelem. */
private fun DrawScope.objektUprostred(stred: Offset, r: Float) {
    drawCircle(Surface2, r, stred)
    drawCircle(TextMid, r, stred, style = Stroke(1.5.dp.toPx()))
    // klín míří dolů — tam je „zepředu"
    val klin = Path().apply {
        moveTo(stred.x - r * 0.45f, stred.y + r * 0.35f)
        lineTo(stred.x + r * 0.45f, stred.y + r * 0.35f)
        lineTo(stred.x, stred.y + r * 1.05f)
        close()
    }
    drawPath(klin, TextMid)
}

private fun DrawScope.popisek(
    meric: TextMeasurer,
    text: String,
    stred: Offset,
    barva: Color,
    velikost: Float,
) {
    val vysledek = meric.measure(text, TextStyle(color = barva, fontSize = velikost.sp))
    drawText(
        vysledek,
        topLeft = Offset(
            stred.x - vysledek.size.width / 2f,
            stred.y - vysledek.size.height / 2f,
        ),
    )
}
