package cz.promptlab.h3video.data

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.Immutable
import java.io.File

/**
 * Karta **Oprava fotky** — Qwen Image 2.1 na
 * záchranu starých fotek: škrábance, prach, roztrhané okraje, kolorizace,
 * doostření. Zadání je pevné (vyladěný opravovací prompt v předloze),
 * dosazuje se JEN fotka a seed — proto je karta tak jednoduchá.
 *
 * Fotka se ukládá bez zmenšení a překódování (jako u karty Zvětšit).
 */
@Immutable
data class RestoreScene(
    val source: File? = null,
    val thumb: Bitmap? = null,
    /**
     * Co se má opravit. Prázdné = pevné zadání předlohy, tedy obecná záchrana
     * staré fotky. Vyplněné zadání předlohu NAHRADÍ — nedá se skládat, protože
     * v ní stojí „no shape deformation", což je pravý opak cílené opravy.
     */
    val pokyn: String = "",
    /**
     * Doostření NVIDIA DLSS 5 jako poslední krok (od 4.54). Qwen vrací fotku
     * kolem 1 MPx a měkčí; DLSS ji bez dokreslování doostří (1×), případně
     * rovnou zvětší (2×). Uživatel 26. 9. 2026: „aby výstup byl hotový ostrý".
     */
    val doostrit: Boolean = true,
    /** "1x" = jen doostření, "2x" = doostření + zvětšení (viz DlssBuilder). */
    val doostritNasobek: String = "1x",
) {
    val uploadImages: List<File> get() = listOfNotNull(source)
}

/** Co kartě chybí, než se dá spustit. */
fun restoreProblem(s: RestoreScene): String? =
    if (s.source == null) t("Vyber fotku, kterou chceš opravit.") else null

/** Soubor opravované fotky — bajt po bajtu, ve vlastní složce. */
class RestoreStore(private val ctx: Context) {

    fun dir(): File = File(ctx.filesDir, "restore").also { it.mkdirs() }

    private val sp = ctx.getSharedPreferences("h3video", Context.MODE_PRIVATE)

    fun load(): RestoreScene {
        val zaklad = RestoreScene(
            pokyn = sp.getString(KEY_POKYN, "") ?: "",
            doostrit = sp.getBoolean(KEY_DOOSTRIT, true),
            doostritNasobek = sp.getString(KEY_NASOBEK, "1x") ?: "1x",
        )
        val name = sp.getString(KEY, null) ?: return zaklad
        val f = File(dir(), name)
        return if (f.exists() && f.length() > 0) zaklad.copy(source = f) else zaklad
    }

    fun save(s: RestoreScene) {
        sp.edit()
            .putString(KEY, s.source?.name)
            .putString(KEY_POKYN, s.pokyn)
            .putBoolean(KEY_DOOSTRIT, s.doostrit)
            .putString(KEY_NASOBEK, s.doostritNasobek)
            .apply()
    }

    private companion object {
        const val KEY = "restoreScene"
        const val KEY_POKYN = "restorePokyn"
        const val KEY_DOOSTRIT = "restoreDoostrit"
        const val KEY_NASOBEK = "restoreDoostritNasobek"
    }
}
