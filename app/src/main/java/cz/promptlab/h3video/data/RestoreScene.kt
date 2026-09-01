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
) {
    val uploadImages: List<File> get() = listOfNotNull(source)
}

/** Co kartě chybí, než se dá spustit. */
fun restoreProblem(s: RestoreScene): String? =
    if (s.source == null) "Vyber fotku, kterou chceš opravit." else null

/** Soubor opravované fotky — bajt po bajtu, ve vlastní složce. */
class RestoreStore(private val ctx: Context) {

    fun dir(): File = File(ctx.filesDir, "restore").also { it.mkdirs() }

    private val sp = ctx.getSharedPreferences("h3video", Context.MODE_PRIVATE)

    fun load(): RestoreScene {
        val name = sp.getString(KEY, null) ?: return RestoreScene()
        val f = File(dir(), name)
        return if (f.exists() && f.length() > 0) RestoreScene(source = f) else RestoreScene()
    }

    fun save(s: RestoreScene) {
        sp.edit().putString(KEY, s.source?.name).apply()
    }

    private companion object { const val KEY = "restoreScene" }
}
