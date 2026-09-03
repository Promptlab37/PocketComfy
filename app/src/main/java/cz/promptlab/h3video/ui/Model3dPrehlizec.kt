package cz.promptlab.h3video.ui

import android.annotation.SuppressLint
import android.webkit.WebView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import cz.promptlab.h3video.ui.theme.Surface2
import java.io.File

/**
 * Prohlížeč hotového 3D modelu — otáčení tahem prstu, přiblížení štípnutím.
 *
 * Kreslí se ve WebView přes `<model-viewer>` od Googlu, který je zabalený
 * v APK (`assets/model-viewer.min.js`), takže funguje i bez internetu.
 * Nativní vykreslovač (Filament) by uměl víc, jenže přibalit ho znamená
 * desítky megabajtů — celá appka má přitom kolem dvou.
 *
 * Model se načítá přes `file://` z privátní složky appky, proto se WebView
 * povoluje čtení souborů. Nic jiného než vlastní soubory tam nevede: stránka
 * se nikam nepřipojuje a JavaScript v ní je jen ten zabalený.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun Model3dPrehlizec(soubor: File, modifier: Modifier = Modifier) {
    val html = """
        <!doctype html><html><head><meta charset="utf-8">
        <meta name="viewport" content="width=device-width,initial-scale=1,user-scalable=no">
        <style>
          html,body{margin:0;height:100%;background:#12141a;overflow:hidden}
          model-viewer{width:100%;height:100%;--poster-color:transparent}
        </style>
        <script src="model-viewer.min.js"></script>
        </head><body>
        <model-viewer
            src="file://${soubor.absolutePath}"
            camera-controls
            touch-action="none"
            interaction-prompt="none"
            shadow-intensity="1"
            exposure="1"
            environment-image="neutral"
            autoplay></model-viewer>
        </body></html>
    """.trimIndent()

    Box(
        modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(14.dp))
            .background(Surface2)
    ) {
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    // Model leží v privátní složce appky; bez tohohle by ho
                    // stránka s adresou file:// nesměla načíst.
                    settings.allowFileAccess = true
                    @Suppress("DEPRECATION")
                    settings.allowFileAccessFromFileURLs = true
                    setBackgroundColor(android.graphics.Color.TRANSPARENT)
                    loadDataWithBaseURL(
                        "file:///android_asset/", html, "text/html", "utf-8", null
                    )
                }
            },
            update = { it.loadDataWithBaseURL("file:///android_asset/", html, "text/html", "utf-8", null) },
            modifier = Modifier.fillMaxSize(),
        )
    }
}
