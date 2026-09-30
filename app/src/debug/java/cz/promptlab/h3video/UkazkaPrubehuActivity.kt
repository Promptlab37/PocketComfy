package cz.promptlab.h3video

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.Modifier
import cz.promptlab.h3video.comfy.Stage
import cz.promptlab.h3video.engine.GenState
import cz.promptlab.h3video.ui.ProgressScreen
import cz.promptlab.h3video.ui.theme.H3Theme
import cz.promptlab.h3video.ui.theme.Ink
import cz.promptlab.h3video.util.Preview

/**
 * JEN LADICÍ VERZE: obrazovka průběhu s vymyšlenými údaji, aby šla vyfotit na
 * emulátoru bez skutečného generování. `adb shell am start -n
 * cz.promptlab.h3video.debug/cz.promptlab.h3video.UkazkaPrubehuActivity --es stav vzorkovani`
 * (stav: fronta | model | vzorkovani | prenos | obrazek).
 */
class UkazkaPrubehuActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val od = System.currentTimeMillis()
        val nahled = Bitmap.createBitmap(640, 360, Bitmap.Config.ARGB_8888).also { b ->
            val p = Paint().apply { shader = LinearGradient(0f, 0f, 640f, 360f, 0xFF3A4A5C.toInt(), 0xFFB08A5A.toInt(), Shader.TileMode.CLAMP) }
            Canvas(b).drawRect(0f, 0f, 640f, 360f, p)
        }
        val stav = when (intent.getStringExtra("stav")) {
            "fronta" -> GenState.Running(null, Stage.QUEUED, 0.06f, queuePosition = 1, startedAt = od - 12_000, label = "Film ze storyboardu")
            "model" -> GenState.Running(null, Stage.MODELS, 0.14f, startedAt = od - 48_000, preparing = true, label = "Film ze storyboardu")
            "prenos" -> GenState.Running(null, Stage.DOWNLOADING, 0.96f, startedAt = od - 402_000, transferDone = 18_400_000, transferTotal = 31_000_000, etaSeconds = 6, preview = Preview.Frames(listOf(nahled), 1), label = "Film ze storyboardu")
            "obrazek" -> GenState.Running(null, Stage.SAMPLING, 0.52f, step = 13, totalSteps = 25, startedAt = od - 41_000, etaSeconds = 38, secondsPerStep = 3.0, isImage = true, isT2i = true, preview = Preview.Frames(listOf(nahled), 1), label = "Obrázek")
            else -> GenState.Running(null, Stage.SAMPLING, 0.58f, step = 7, totalSteps = 12, startedAt = od - 215_000, etaSeconds = 164, secondsPerStep = 23.0, preview = Preview.Frames(listOf(nahled), 1), label = "Film ze storyboardu")
        }
        setContent {
            H3Theme {
                Box(Modifier.fillMaxSize().background(Ink).statusBarsPadding().navigationBarsPadding()) {
                    ProgressScreen(state = stav, onMinimize = {}, onCancel = {})
                }
            }
        }
    }
}
