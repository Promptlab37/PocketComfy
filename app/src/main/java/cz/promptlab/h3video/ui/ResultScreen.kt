package cz.promptlab.h3video.ui

import android.content.Intent
import android.widget.Toast
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cz.promptlab.h3video.data.VideoItem
import cz.promptlab.h3video.ui.theme.Cyan
import cz.promptlab.h3video.ui.theme.Danger
import cz.promptlab.h3video.ui.theme.Amber
import cz.promptlab.h3video.ui.theme.Ink
import cz.promptlab.h3video.ui.theme.Ok
import cz.promptlab.h3video.ui.theme.Outline1
import cz.promptlab.h3video.ui.theme.Surface1
import cz.promptlab.h3video.ui.theme.TextHi
import cz.promptlab.h3video.ui.theme.TextLow
import cz.promptlab.h3video.ui.theme.TextMid

@Composable
fun ResultScreen(
    item: VideoItem,
    onClose: () -> Unit,
    onAgain: () -> Unit,
    onSaved: () -> Unit = {},
    /** Poslat hotový obrázek rovnou do karty Zvětšit (jen u obrázků). */
    onUpscale: (() -> Unit)? = null,
    /** Poslat hotový obrázek rovnou do karty Úprava obrázku (jen u obrázků). */
    onEdit: (() -> Unit)? = null,
    /** Rozhýbat obrázek — poslat do All in One → Z obrázku (jen u obrázků). */
    onAnimate: (() -> Unit)? = null,
    /**
     * Co k běhu řekly samotné uzly. Jinak to skončí jen v logu na počítači,
     * kam se z telefonu nedostaneš – a přitom jde často o věc, kterou z videa
     * nepoznáš (replika, která se neudělala jako dialog, prázdné povinné pole).
     */
    warnings: List<String> = emptyList(),
) {
    val ctx = LocalContext.current
    var saved by remember(item.id) { mutableStateOf(item.inGallery) }

    Column(
        Modifier
            .fillMaxSize()
            .background(Ink)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(26.dp))
        Icon(Icons.Default.CheckCircle, null, Modifier.size(40.dp), Ok)
        NodeWarnings(warnings)
        Spacer(Modifier.height(10.dp))
        Text(
            when {
                item.isImage -> "Obrázek je hotový"
                item.isAudio -> "Skladba je hotová"
                else -> "Video je hotové"
            },
            style = MaterialTheme.typography.headlineSmall, color = TextHi
        )
        Text(
            // U videa, na které aplikace navázala po restartu, délku neznáme –
            // pak se ukáže jen popis, ne matoucí "0,0 s".
            (if (item.seconds > 0f) "%.1f s · %s".format(item.seconds, item.resolution)
            else item.resolution) +
                if (item.tookSeconds > 0)
                    " · hotovo za " +
                        cz.promptlab.h3video.engine.GenerationService.formatEta(item.tookSeconds)
                else "",
            style = MaterialTheme.typography.bodySmall, color = TextLow
        )
        // Prompt se tu neopakuje – uživatel ho právě napsal a na výsledku ho
        // nezajímá, chce vidět video. Zůstává v Galerii aplikace u záznamu.
        // Ťuknutím na řádek se zkopíruje seed (a prompt, když nějaký je) –
        // profík si tak výsledek zreprodukuje nebo doladí jinde.
        Text(
            buildString {
                val m = runCatching { cz.promptlab.h3video.data.Mode.valueOf(item.mode) }.getOrNull()
                if (m != null) append(m.title).append(" · ")
                else if (item.twoImages) append("2 reference · ")
                append("seed ${item.seed}")
            },
            style = MaterialTheme.typography.bodySmall, color = TextLow,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable {
                    val cm = ctx.getSystemService(android.content.ClipboardManager::class.java)
                    val text = if (item.prompt.isNotBlank())
                        "${item.prompt}\nseed: ${item.seed}" else "${item.seed}"
                    cm.setPrimaryClip(android.content.ClipData.newPlainText("PocketComfy", text))
                    // Android 13+ ukazuje vlastní bublinu o zkopírování sám.
                    if (android.os.Build.VERSION.SDK_INT < 33) {
                        Toast.makeText(ctx, "Zkopírováno", Toast.LENGTH_SHORT).show()
                    }
                }
                .padding(horizontal = 6.dp, vertical = 2.dp)
        )

        Spacer(Modifier.height(18.dp))
        // Karta Úprava obrázku vrací PNG – přehrávač by na něm jen zčernal.
        if (item.isImage) {
            // Dekóduje se na pozadí a se stropem ~4096 px na hranu – gigapixel
            // ze Zvětšit by jinak zmrazil UI a shodil appku na paměti.
            var bmp by remember(item.id) { mutableStateOf<android.graphics.Bitmap?>(null) }
            LaunchedEffect(item.id) {
                bmp = withContext(kotlinx.coroutines.Dispatchers.IO) {
                    runCatching {
                        val cesta = item.file(ctx).absolutePath
                        val hranice = android.graphics.BitmapFactory.Options()
                            .apply { inJustDecodeBounds = true }
                        android.graphics.BitmapFactory.decodeFile(cesta, hranice)
                        var vzorek = 1
                        while (maxOf(hranice.outWidth, hranice.outHeight) / (vzorek * 2) >= 4096) vzorek *= 2
                        val opts = android.graphics.BitmapFactory.Options()
                            .apply { inSampleSize = vzorek }
                        android.graphics.BitmapFactory.decodeFile(cesta, opts)
                    }.getOrNull()
                }
            }
            var naCelou by remember(item.id) { mutableStateOf(false) }
            bmp?.let {
                androidx.compose.foundation.Image(
                    bitmap = it.asImageBitmap(),
                    contentDescription = "Upravený obrázek – klepnutím zvětšíš",
                    contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { naCelou = true },
                )
                Text(
                    "Klepni pro zvětšení",
                    style = MaterialTheme.typography.bodySmall, color = TextLow,
                    modifier = Modifier.padding(top = 4.dp)
                )
                if (naCelou) ZoomovaciObrazek(it) { naCelou = false }
            }
        } else if (item.isAudio) {
            // Skladba nemá obraz – přehrávač zvládne MP3 taky, jen by ukázal
            // černý obdélník. Vlastní řádek s play/pauzou je srozumitelnější.
            AudioPrehravac(item.file(ctx))
        } else {
            VideoPlayer(item.file(ctx), Modifier.fillMaxWidth())
        }

        Spacer(Modifier.height(18.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlineButton(
                if (saved) "V galerii telefonu" else "Uložit do galerie",
                modifier = Modifier.weight(1f),
                color = if (saved) Ok else TextMid,
                icon = {
                    Icon(
                        Icons.Default.Download, null, Modifier.size(18.dp),
                        if (saved) Ok else TextMid
                    )
                }
            ) {
                // Obrázek patří do Obrázků a s vlastní příponou – PNG uložené
                // jako .mp4 do Filmů by nešlo otevřít. Skladba jde do Hudby.
                val ok = if (item.isAudio) {
                    val ext = item.fileName.substringAfterLast('.', "mp3")
                    cz.promptlab.h3video.util.MediaSaver.saveAudioToGallery(
                        ctx, item.file(ctx), "H3_${item.createdAt}.$ext"
                    )
                } else if (item.isImage) {
                    val ext = item.fileName.substringAfterLast('.', "png")
                    cz.promptlab.h3video.util.MediaSaver.saveImageToGallery(
                        ctx, item.file(ctx), "H3_${item.createdAt}.$ext"
                    )
                } else {
                    cz.promptlab.h3video.util.MediaSaver.saveToGallery(
                        ctx, item.file(ctx), "H3_${item.createdAt}.mp4"
                    )
                }
                saved = ok
                if (ok) onSaved()
                Toast.makeText(
                    ctx,
                    when {
                        !ok -> "Uložení se nepovedlo"
                        item.isAudio -> "Uloženo do Hudba/H3 Video"
                        item.isImage -> "Uloženo do Obrázky/H3 Video"
                        else -> "Uloženo do Filmy/H3 Video"
                    },
                    Toast.LENGTH_SHORT
                ).show()
            }
            OutlineButton(
                "Sdílet",
                modifier = Modifier.weight(1f),
                icon = { Icon(Icons.Default.Share, null, Modifier.size(18.dp), TextMid) }
            ) {
                val intent = cz.promptlab.h3video.util.MediaSaver.shareIntent(ctx, item.file(ctx))
                ctx.startActivity(
                    Intent.createChooser(
                        intent,
                        when {
                            item.isAudio -> "Sdílet skladbu"
                            item.isImage -> "Sdílet obrázek"
                            else -> "Sdílet video"
                        }
                    )
                )
            }
        }

        // Rozcestník: z hotového obrázku se pokračuje jedním klepnutím —
        // rozhýbat do videa, upravit, nebo zvětšit. Bez stahování a
        // znovunahrávání.
        if (item.isImage && (onAnimate != null || onEdit != null || onUpscale != null)) {
            Spacer(Modifier.height(16.dp))
            Text(
                "Pokračuj s obrázkem",
                style = MaterialTheme.typography.labelMedium,
                color = TextLow,
                modifier = Modifier.fillMaxWidth(),
            )
            if (onAnimate != null) {
                Spacer(Modifier.height(8.dp))
                OutlineButton(
                    "Rozhýbat — video z obrázku",
                    modifier = Modifier.fillMaxWidth(),
                    color = Cyan,
                    onClick = onAnimate,
                )
            }
            if (onEdit != null) {
                Spacer(Modifier.height(8.dp))
                OutlineButton(
                    "Upravit (Krea 2 — popiš změnu)",
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onEdit,
                )
            }
            if (onUpscale != null) {
                Spacer(Modifier.height(8.dp))
                OutlineButton(
                    "Zvětšit (SeedVR2 gigapixel)",
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onUpscale,
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        GradientButton("Generovat další", onClick = onAgain)
        Spacer(Modifier.height(10.dp))
        OutlineButton("Zavřít", modifier = Modifier.fillMaxWidth(), onClick = onClose)
        Spacer(Modifier.height(26.dp))
    }
}

@Composable
fun FailureScreen(
    message: String,
    canRetryDownload: Boolean,
    onRetryDownload: () -> Unit,
    onClose: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(Ink)
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(60.dp))
        Icon(Icons.Default.ErrorOutline, null, Modifier.size(44.dp), Danger)
        Spacer(Modifier.height(12.dp))
        Text("Nepovedlo se", style = MaterialTheme.typography.headlineSmall, color = TextHi)
        Spacer(Modifier.height(12.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Surface1)
                .border(1.dp, Outline1, RoundedCornerShape(16.dp))
                .padding(16.dp)
        ) {
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = TextMid,
                textAlign = TextAlign.Start
            )
        }
        Spacer(Modifier.height(20.dp))
        if (canRetryDownload) {
            GradientButton(
                "Zkusit přenos znovu",
                icon = {
                    Icon(
                        Icons.Default.Refresh, null, Modifier.size(18.dp),
                        androidx.compose.ui.graphics.Color.White
                    )
                },
                onClick = onRetryDownload
            )
            Spacer(Modifier.height(10.dp))
        }
        OutlineButton("Zpět na zadání", modifier = Modifier.fillMaxWidth(), color = Cyan, onClick = onClose)
    }
}

/** Hlášky uzlů z běhu – žlutě, ale bez dramatu: video existuje, tohle je rada. */
@Composable
private fun NodeWarnings(warnings: List<String>) {
    if (warnings.isEmpty()) return
    Spacer(Modifier.height(14.dp))
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Surface1)
            .border(1.dp, Amber.copy(alpha = .45f), RoundedCornerShape(14.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            "Co k tomu řekly uzly",
            style = MaterialTheme.typography.labelMedium,
            color = Amber
        )
        warnings.forEach {
            Text(it, style = MaterialTheme.typography.bodySmall, color = TextMid)
        }
    }
}

/**
 * Celoobrazovkový prohlížeč obrázku v Dialogu přes celé okno — uvnitř
 * rolovací obrazovky by fillMaxSize skončil vložený mezi kartami, ne PŘES
 * nimi. Pinch zoom 1–5×, posun omezený na okraje (jinak jde obrázek
 * „vytáhnout" z obrazovky a ztratí se), dvojklep přepíná zoom, klep při
 * nezvětšeném obrázku a tlačítko zavírají; Zpět řeší Dialog sám.
 */
@Composable
private fun ZoomovaciObrazek(bmp: android.graphics.Bitmap, onClose: () -> Unit) {
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        var scale by remember { mutableFloatStateOf(1f) }
        var offset by remember { mutableStateOf(Offset.Zero) }
        var box by remember { mutableStateOf(IntSize.Zero) }

        fun srovnej(s: Float, o: Offset): Offset {
            if (s <= 1f) return Offset.Zero
            val mx = box.width * (s - 1f) / 2f
            val my = box.height * (s - 1f) / 2f
            return Offset(o.x.coerceIn(-mx, mx), o.y.coerceIn(-my, my))
        }

        val stav = rememberTransformableState { zoom, pan, _ ->
            scale = (scale * zoom).coerceIn(1f, 5f)
            offset = srovnej(scale, offset + pan)
        }

        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black)
                .onSizeChanged { box = it }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { if (scale <= 1f) onClose() },
                        onDoubleTap = {
                            if (scale > 1f) {
                                scale = 1f; offset = Offset.Zero
                            } else scale = 2.5f
                        }
                    )
                }
        ) {
            androidx.compose.foundation.Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = "Obrázek na celou obrazovku",
                contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = scale; scaleY = scale
                        translationX = offset.x; translationY = offset.y
                    }
                    .transformable(stav),
            )
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 40.dp, end = 16.dp)
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Surface1.copy(alpha = 0.8f))
                    .clickable { onClose() },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Close, "Zavřít", Modifier.size(20.dp), TextHi)
            }
        }
    }
}
