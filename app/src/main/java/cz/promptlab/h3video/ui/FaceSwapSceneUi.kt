package cz.promptlab.h3video.ui

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Face
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cz.promptlab.h3video.MainViewModel
import cz.promptlab.h3video.ui.theme.Cyan
import cz.promptlab.h3video.ui.theme.Ink
import cz.promptlab.h3video.ui.theme.Ok
import cz.promptlab.h3video.ui.theme.Outline1
import cz.promptlab.h3video.ui.theme.Surface2
import cz.promptlab.h3video.ui.theme.TextHi
import cz.promptlab.h3video.ui.theme.TextLow
import cz.promptlab.h3video.ui.theme.TextMid

/**
 * Karta **Výměna tváře** — cílová fotka (s prstem namalovanou maskou
 * obličeje), nová tvář, Generovat. Maska se maluje ve vlastním editoru
 * přes celou obrazovku a ukládá se do alfa kanálu PNG — přesně tak, jak
 * ji čte uzel LoadImage v ComfyUI.
 */
@Composable
fun FaceSwapSection(vm: MainViewModel) {
    val scene by vm.swap.collectAsStateWithLifecycle()

    val imageOnly = PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
    val pickTarget = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> vm.pickSwapTarget(uri) }
    val pickFace = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> vm.pickSwapFace(uri) }

    var maluje by remember { mutableStateOf(false) }

    SectionCard(
        title = "Fotka, kde se mění tvář",
        subtitle = if (scene.maskPainted)
            "Maska je namalovaná — klepnutím na štětec ji předěláš"
        else "Vyber fotku a pak prstem začmárej obličej, který se má vyměnit"
    ) {
        Column {
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(4f / 3f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Surface2)
                    .border(
                        1.dp,
                        if (scene.maskPainted) Ok.copy(alpha = .5f) else Outline1,
                        RoundedCornerShape(14.dp)
                    )
                    .clickable {
                        if (scene.target == null) pickTarget.launch(imageOnly) else maluje = true
                    }
            ) {
                val thumb = scene.targetThumb
                if (thumb != null) {
                    Image(
                        bitmap = thumb.asImageBitmap(),
                        contentDescription = "Cílová fotka",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                    )
                    Row(
                        Modifier.align(Alignment.TopEnd).padding(6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            Modifier
                                .size(28.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Surface2)
                                .clickable { maluje = true },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Brush, "Malovat masku",
                                Modifier.size(16.dp),
                                if (scene.maskPainted) Ok else Cyan
                            )
                        }
                        Box(
                            Modifier
                                .size(28.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Surface2)
                                .clickable { vm.clearSwap("target") },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Close, "Odebrat", Modifier.size(16.dp), TextMid)
                        }
                    }
                } else {
                    Icon(
                        Icons.Default.AddPhotoAlternate, "Vybrat fotku",
                        Modifier.align(Alignment.Center).size(34.dp), TextMid
                    )
                }
            }
        }
    }

    SectionCard(
        title = "Nová tvář",
        subtitle = "Nejlíp ostrá fotka zepředu"
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(4f / 3f)
                .clip(RoundedCornerShape(14.dp))
                .background(Surface2)
                .border(1.dp, Outline1, RoundedCornerShape(14.dp))
                .clickable { pickFace.launch(imageOnly) }
        ) {
            val thumb = scene.faceThumb
            if (thumb != null) {
                Image(
                    bitmap = thumb.asImageBitmap(),
                    contentDescription = "Nová tvář",
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
                        .clickable { vm.clearSwap("face") },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Close, "Odebrat", Modifier.size(16.dp), TextMid)
                }
            } else {
                Icon(
                    Icons.Default.Face, "Vybrat tvář",
                    Modifier.align(Alignment.Center).size(34.dp), TextMid
                )
            }
        }
    }

    if (maluje) {
        val file = scene.target
        val bmp = remember(file?.path, maluje) {
            file?.let {
                runCatching { android.graphics.BitmapFactory.decodeFile(it.absolutePath) }
                    .getOrNull()
            }
        }
        if (bmp != null) {
            MaskEditor(
                bitmap = bmp,
                onDone = { vysledek ->
                    vm.ulozSwapMasku(vysledek)
                    maluje = false
                },
                onClose = { maluje = false },
            )
        }
    }
}

/** Jeden tah štětcem v souřadnicích bitmapy. */
private class Tah(val brushPx: Float) {
    val body = mutableStateListOf<Offset>()
}

/**
 * Malování masky přes celou obrazovku. Kde uživatel čmárá, tam se při
 * uložení vymaže alfa kanál — LoadImage v ComfyUI z toho udělá masku,
 * stejně jako u masky namalované v editoru ComfyUI.
 */
@Composable
fun MaskEditor(bitmap: Bitmap, onDone: (Bitmap) -> Unit, onClose: () -> Unit) {
    val tahy = remember { mutableStateListOf<Tah>() }
    var brushDp by remember { mutableFloatStateOf(42f) }
    var box by remember { mutableStateOf(IntSize.Zero) }

    // Fit umístění bitmapy do plátna: měřítko a posun pro převod souřadnic.
    fun scale(): Float = if (box == IntSize.Zero) 1f else minOf(
        box.width.toFloat() / bitmap.width, box.height.toFloat() / bitmap.height
    )
    fun offset(): Offset {
        val s = scale()
        return Offset(
            (box.width - bitmap.width * s) / 2f,
            (box.height - bitmap.height * s) / 2f,
        )
    }
    fun naBitmapu(p: Offset): Offset {
        val s = scale(); val o = offset()
        return Offset(
            ((p.x - o.x) / s).coerceIn(0f, bitmap.width.toFloat()),
            ((p.y - o.y) / s).coerceIn(0f, bitmap.height.toFloat()),
        )
    }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .background(Ink)
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                Text(
                    "Začmárej obličej, který se vymění",
                    style = MaterialTheme.typography.titleMedium, color = TextHi
                )
                Text(
                    "Klidně s přesahem přes okraje tváře — přechod se změkčí sám.",
                    style = MaterialTheme.typography.bodySmall, color = TextLow
                )
            }

            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .onSizeChanged { box = it }
                    .pointerInput(bitmap) {
                        detectDragGestures(
                            onDragStart = { p ->
                                val density = box.width / 360f
                                val t = Tah(brushDp * density / scale())
                                t.body += naBitmapu(p)
                                tahy += t
                            },
                            onDrag = { change, _ ->
                                tahy.lastOrNull()?.body?.add(naBitmapu(change.position))
                            },
                        )
                    }
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    val s = scale(); val o = offset()
                    drawImage(
                        image = bitmap.asImageBitmap(),
                        dstOffset = androidx.compose.ui.unit.IntOffset(
                            o.x.toInt(), o.y.toInt()
                        ),
                        dstSize = IntSize(
                            (bitmap.width * s).toInt(), (bitmap.height * s).toInt()
                        ),
                    )
                    tahy.forEach { tah ->
                        if (tah.body.isEmpty()) return@forEach
                        val path = Path()
                        val prvni = tah.body.first()
                        path.moveTo(o.x + prvni.x * s, o.y + prvni.y * s)
                        tah.body.drop(1).forEach { b ->
                            path.lineTo(o.x + b.x * s, o.y + b.y * s)
                        }
                        // I ťuknutí bez tahu musí nakreslit tečku.
                        if (tah.body.size == 1) {
                            path.lineTo(o.x + prvni.x * s + 0.1f, o.y + prvni.y * s)
                        }
                        drawPath(
                            path,
                            color = Color(0xFFE91E8C).copy(alpha = .55f),
                            style = Stroke(
                                width = tah.brushPx * s,
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round,
                            )
                        )
                    }
                }
            }

            Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Štětec", style = MaterialTheme.typography.labelMedium, color = TextLow)
                    Slider(
                        value = brushDp,
                        onValueChange = { brushDp = it },
                        valueRange = 16f..90f,
                        modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                        colors = sliderColors()
                    )
                    Text(
                        "${brushDp.toInt()}",
                        style = MaterialTheme.typography.labelMedium, color = TextMid
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlineButton("Krok zpět", modifier = Modifier.weight(1f)) {
                        tahy.removeLastOrNull()
                    }
                    OutlineButton("Smazat vše", modifier = Modifier.weight(1f)) {
                        tahy.clear()
                    }
                }
                Spacer(Modifier.height(8.dp))
                GradientButton(
                    if (tahy.isEmpty()) "Nejdřív začmárej obličej" else "Hotovo — použít masku",
                    enabled = tahy.isNotEmpty(),
                    onClick = {
                        val out = bitmap.copy(Bitmap.Config.ARGB_8888, true)
                        val canvas = android.graphics.Canvas(out)
                        val paint = android.graphics.Paint().apply {
                            isAntiAlias = true
                            style = android.graphics.Paint.Style.STROKE
                            strokeCap = android.graphics.Paint.Cap.ROUND
                            strokeJoin = android.graphics.Paint.Join.ROUND
                            xfermode = android.graphics.PorterDuffXfermode(
                                android.graphics.PorterDuff.Mode.CLEAR
                            )
                        }
                        tahy.forEach { tah ->
                            if (tah.body.isEmpty()) return@forEach
                            paint.strokeWidth = tah.brushPx
                            val p = android.graphics.Path()
                            val prvni = tah.body.first()
                            p.moveTo(prvni.x, prvni.y)
                            tah.body.drop(1).forEach { b -> p.lineTo(b.x, b.y) }
                            if (tah.body.size == 1) p.lineTo(prvni.x + 0.1f, prvni.y)
                            canvas.drawPath(p, paint)
                        }
                        onDone(out)
                    }
                )
                Spacer(Modifier.height(8.dp))
                OutlineButton("Zavřít bez uložení", modifier = Modifier.fillMaxWidth()) {
                    onClose()
                }
            }
        }
    }
}
