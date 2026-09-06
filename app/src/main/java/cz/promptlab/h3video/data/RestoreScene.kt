package cz.promptlab.h3video.data

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.Immutable
import java.io.File

/**
 * Karta **Oprava fotky** — uživatelovo Qwen Image Edit 2511 workflow na
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
     * Cílená LoRA nad rámec předlohy (prázdné = žádná). Předloha už tři LoRA
     * řetězí (Lightning, upscale, realismus), tahle se přivěsí na konec.
     */
    val lora: String = "",
    /** Síla cílené LoRA. */
    val loraSila: Float = 1f,
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
            lora = sp.getString(KEY_LORA, "") ?: "",
            loraSila = sp.getFloat(KEY_SILA, 1f),
        )
        val name = sp.getString(KEY, null) ?: return zaklad
        val f = File(dir(), name)
        return if (f.exists() && f.length() > 0) zaklad.copy(source = f) else zaklad
    }

    fun save(s: RestoreScene) {
        sp.edit()
            .putString(KEY, s.source?.name)
            .putString(KEY_POKYN, s.pokyn)
            .putString(KEY_LORA, s.lora)
            .putFloat(KEY_SILA, s.loraSila)
            .apply()
    }

    private companion object {
        const val KEY = "restoreScene"
        const val KEY_POKYN = "restorePokyn"
        const val KEY_LORA = "restoreLora"
        const val KEY_SILA = "restoreLoraSila"
    }
}
