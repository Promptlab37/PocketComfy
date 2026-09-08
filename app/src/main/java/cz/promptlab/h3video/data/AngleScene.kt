package cz.promptlab.h3video.data

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.Immutable
import cz.promptlab.h3video.comfy.AngleBuilder
import java.io.File

/**
 * Karta **Úhel kamery** — z jedné fotky tentýž objekt z jiného místa
 * (Qwen Image Edit 2511 + LoRA `multiple-angles`).
 *
 * Uživatel nezadává text: nastaví, odkud se má kamera dívat, jak vysoko
 * a jak daleko. Z toho se složí zadání ve tvaru, na kterém je LoRA
 * natrénovaná — viz [AngleBuilder.prompt].
 *
 * Fotka se ukládá bez zmenšení a překódování (jako u karty Oprava fotky).
 */
@Immutable
data class AngleScene(
    val source: File? = null,
    val thumb: Bitmap? = null,
    /** Směr dokola kolem objektu, index do [AngleBuilder.AZIMUTY]. */
    val azimut: Int = 0,
    /** Výška kamery, index do [AngleBuilder.VYSKY]. Výchozí je úroveň očí. */
    val vyska: Int = 1,
    /** Odstup od objektu, index do [AngleBuilder.ODSTUPY]. Výchozí polocelek. */
    val odstup: Int = 1,
    /**
     * Síla LoRA. Jednička je hodnota z předlohy; níž se pohled posune míň,
     * ale drží se líp podoba předlohy.
     */
    val sila: Float = 1f,
) {
    val uploadImages: List<File> get() = listOfNotNull(source)

    /** Zadání, které půjde do grafu — ukazuje se i v kartě, ať je vidět co se posílá. */
    val zadani: String get() = AngleBuilder.prompt(azimut, vyska, odstup)

    /** Lidský popis pro historii („zprava, nadhled, detail"). */
    val popis: String get() = AngleBuilder.popis(azimut, vyska, odstup)
}

/** Co kartě chybí, než se dá spustit. */
fun angleProblem(s: AngleScene): String? =
    if (s.source == null) t("Vyber fotku, ze které se má udělat jiný úhel.") else null

/** Soubor fotky — bajt po bajtu, ve vlastní složce. */
class AngleStore(private val ctx: Context) {

    fun dir(): File = File(ctx.filesDir, "angle").also { it.mkdirs() }

    private val sp = ctx.getSharedPreferences("h3video", Context.MODE_PRIVATE)

    fun load(): AngleScene {
        val zaklad = AngleScene(
            azimut = sp.getInt(KEY_AZIMUT, 0),
            vyska = sp.getInt(KEY_VYSKA, 1),
            odstup = sp.getInt(KEY_ODSTUP, 1),
            sila = sp.getFloat(KEY_SILA, 1f),
        )
        val name = sp.getString(KEY, null) ?: return zaklad
        val f = File(dir(), name)
        return if (f.exists() && f.length() > 0) zaklad.copy(source = f) else zaklad
    }

    fun save(s: AngleScene) {
        sp.edit()
            .putString(KEY, s.source?.name)
            .putInt(KEY_AZIMUT, s.azimut)
            .putInt(KEY_VYSKA, s.vyska)
            .putInt(KEY_ODSTUP, s.odstup)
            .putFloat(KEY_SILA, s.sila)
            .apply()
    }

    private companion object {
        const val KEY = "angleScene"
        const val KEY_AZIMUT = "angleAzimut"
        const val KEY_VYSKA = "angleVyska"
        const val KEY_ODSTUP = "angleOdstup"
        const val KEY_SILA = "angleSila"
    }
}
