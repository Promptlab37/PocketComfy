package cz.promptlab.h3video.data

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.Immutable
import java.io.File

/**
 * Karta **Výměna tváře** — uživatelovo ACE++ workflow (Flux Fill inpaint
 * s portrétní LoRA). Maska je od 2.89 SAMOSTATNÝ soubor (bílá = vyměnit,
 * černá = nechat) a cílová fotka zůstává netknutá — dřívější gumování do
 * alfa kanálu zároveň černilo pixely fotky a černé okraje se pak
 * přimíchávaly do prolnutí (tmavý šev kolem masky) a braly modelu kontext.
 *
 * Dosazují se JEN tři obrázky a seed; celý inpaint řetěz je z předlohy.
 */
@Immutable
data class FaceSwapScene(
    /** Cílová fotka — čistá, bez zásahů. */
    val target: File? = null,
    val targetThumb: Bitmap? = null,
    /** Maska štětcem: bílá = vyměnit, černá = nechat. Bez ní není co měnit. */
    val mask: File? = null,
    /** Fotka s novou tváří. */
    val face: File? = null,
    val faceThumb: Bitmap? = null,
) {
    val maskPainted: Boolean get() = mask != null

    /** Pořadí je závazné — stavitel čte [cíl, tvář, maska]. */
    val uploadImages: List<File> get() = listOfNotNull(target, face, mask)
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

/** Soubory karty ve vlastní složce. */
class FaceSwapStore(private val ctx: Context) {

    fun dir(): File = File(ctx.filesDir, "faceswap").also { it.mkdirs() }

    fun targetFile(): File = File(dir(), "cil.png")
    fun faceFile(): File = File(dir(), "tvar.png")

    /** Maska ve vlastním souboru — fotka se malováním nemění. */
    fun maskFile(): File = File(dir(), "maska.png")

    fun load(): FaceSwapScene {
        val target = targetFile().takeIf { it.exists() && it.length() > 0 }
        val face = faceFile().takeIf { it.exists() && it.length() > 0 }
        // Maska z verzí ≤2.88 žila v alfa kanálu cílové fotky — nový soubor
        // neexistuje, takže se stará maska automaticky neuzná a appka si
        // řekne o novou. Přesně to chceme.
        val mask = if (target != null) {
            maskFile().takeIf { it.exists() && it.length() > 0 }
        } else null
        return FaceSwapScene(target = target, mask = mask, face = face)
    }

    fun save(@Suppress("UNUSED_PARAMETER") s: FaceSwapScene) {
        // Všechno podstatné žije v souborech — není co zapisovat.
    }
}
