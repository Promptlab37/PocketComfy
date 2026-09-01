package cz.promptlab.h3video.data

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.Immutable
import java.io.File

/**
 * Karta **Výměna tváře** — uživatelovo ACE++ workflow (Flux Fill inpaint
 * s portrétní LoRA). Cílová fotka nese ručně namalovanou masku obličeje
 * v průhlednosti (stejně jako maska z editoru ComfyUI): kde uživatel
 * v appce začmáral, tam je alfa 0 a uzel LoadImage z toho udělá masku.
 *
 * Dosazují se JEN dvě fotky a seed; celý inpaint řetěz je z předlohy.
 */
@Immutable
data class FaceSwapScene(
    /** Cílová fotka (PNG s vymaskovaným obličejem v alfě). */
    val target: File? = null,
    val targetThumb: Bitmap? = null,
    /** Je na cílové fotce namalovaná maska? Bez ní není co měnit. */
    val maskPainted: Boolean = false,
    /** Fotka s novou tváří. */
    val face: File? = null,
    val faceThumb: Bitmap? = null,
) {
    val uploadImages: List<File> get() = listOfNotNull(target, face)
}

/** Co kartě chybí, než se dá spustit. */
fun faceSwapProblem(s: FaceSwapScene): String? = when {
    s.target == null -> "Vyber fotku, ve které se má vyměnit tvář."
    !s.maskPainted -> "Začmárej prstem obličej, který se má vyměnit."
    s.face == null -> "Vyber fotku s novou tváří."
    else -> null
}

/** Upozornění, která nebrání spuštění. */
fun faceSwapHints(s: FaceSwapScene): List<String> {
    val out = mutableListOf<String>()
    if (s.face != null) {
        out += "Nejlíp funguje ostrá tvář zepředu, bez brýlí a bez stínů."
    }
    return out
}

/** Soubory karty ve vlastní složce; příznak masky v nastavení. */
class FaceSwapStore(private val ctx: Context) {

    fun dir(): File = File(ctx.filesDir, "faceswap").also { it.mkdirs() }

    /** Cílová fotka se drží v PNG kvůli alfa kanálu s maskou. */
    fun targetFile(): File = File(dir(), "cil.png")
    fun faceFile(): File = File(dir(), "tvar.png")

    private val sp = ctx.getSharedPreferences("h3video", Context.MODE_PRIVATE)

    fun load(): FaceSwapScene {
        val target = targetFile().takeIf { it.exists() && it.length() > 0 }
        val face = faceFile().takeIf { it.exists() && it.length() > 0 }
        return FaceSwapScene(
            target = target,
            maskPainted = target != null && sp.getBoolean(KEY_MASK, false),
            face = face,
        )
    }

    fun save(s: FaceSwapScene) {
        sp.edit().putBoolean(KEY_MASK, s.maskPainted).apply()
    }

    private companion object { const val KEY_MASK = "faceswapMask" }
}
