package cz.promptlab.h3video.data

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.Immutable
import java.io.File

/**
 * Čím se tvář mění. Qwen 2.1 je první — v obrázkových kartách je to nejlepší
 * model, který appka má (přání uživatele).
 */
enum class SwapMotor(private val titleCs: String) {
    /**
     * Qwen Image 2.1 + LoRA BFS Head v1.1 (Alissonerdx/BFS-Best-Face-Swap),
     * autorovo workflow 1:1. Bez masky; mění celou hlavu včetně vlasů.
     */
    QWEN21("Qwen Image 2.1"),

    /** Uživatelovo ACE++ workflow (Flux Fill), maska prstem. */
    FLUX("Flux Fill");

    val title: String get() = t(titleCs)
}

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
    val motor: SwapMotor = SwapMotor.QWEN21,
) {
    val maskPainted: Boolean get() = mask != null

    /** Chce motor masku? Qwen s BFS ne — hlavu najde sám. */
    val chceMasku: Boolean get() = motor == SwapMotor.FLUX

    /** Pořadí je závazné — stavitel čte [cíl, tvář, maska]; Qwen masku nebere. */
    val uploadImages: List<File>
        get() = if (chceMasku) listOfNotNull(target, face, mask) else listOfNotNull(target, face)
}

/** Co kartě chybí, než se dá spustit. */
fun faceSwapProblem(s: FaceSwapScene): String? = when {
    s.target == null -> t("Vyber fotku, ve které se má vyměnit tvář.")
    s.chceMasku && !s.maskPainted -> t("Začmárej prstem obličej, který se má vyměnit.")
    s.face == null -> t("Vyber fotku s novou tváří.")
    else -> null
}

/** Upozornění, která nebrání spuštění. */
fun faceSwapHints(s: FaceSwapScene): List<String> {
    val out = mutableListOf<String>()
    if (s.face != null) {
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
        val motor = runCatching { SwapMotor.valueOf(sp.getString(K_MOTOR, "")!!) }
            .getOrDefault(SwapMotor.QWEN21)
        return FaceSwapScene(target = target, mask = mask, face = face, motor = motor)
    }

    /** Fotky žijí v souborech; ukládá se jen volba motoru. */
    fun save(s: FaceSwapScene) {
        sp.edit().putString(K_MOTOR, s.motor.name).apply()
    }

    private val sp get() = ctx.getSharedPreferences("h3video", Context.MODE_PRIVATE)

    private companion object { const val K_MOTOR = "swapMotor" }
}
