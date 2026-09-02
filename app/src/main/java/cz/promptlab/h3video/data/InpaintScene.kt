package cz.promptlab.h3video.data

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.Immutable
import java.io.File

/**
 * Model, kterým se domalovává. Texty jsou v `*Cs` polích a překládají se až
 * při čtení — hodnoty enumu vzniknou jednou při startu, takže by přepnutí
 * jazyka na uložený překlad nedosáhlo.
 */
enum class InpaintModel(
    private val titleCs: String,
    private val detailCs: String,
) {
    /** Destilovaný editační model: 4 kroky, rozumí větě, drží okolí. */
    KLEIN(
        titleCs = "FLUX.2 Klein",
        detailCs = "Novější a rychlý (4 kroky), nejlíp rozumí zadání",
    ),

    /** Model trénovaný přímo na díry v obraze — stejný jako u výměny tváře. */
    FILL(
        titleCs = "Flux Fill",
        detailCs = "Klasika na maskování, věrnější textury, ale pomalejší",
    );

    val title: String get() = t(titleCs)
    val detail: String get() = t(detailCs)
}

/**
 * Karta **Domalovat** (inpainting) — zamaskovaná část fotky se přemaluje
 * podle věty, zbytek zůstane netknutý.
 *
 * Maska je samostatný černobílý soubor (bílá = přemalovat), stejně jako
 * u výměny tváře od 2.89: gumování do alfa kanálu zároveň černí pixely
 * fotky a černá se pak přimíchává do prolnutí (tmavý šev kolem masky).
 */
@Immutable
data class InpaintScene(
    /** Fotka, do které se maluje — čistá, bez zásahů. */
    val source: File? = null,
    val thumb: Bitmap? = null,
    /** Maska štětcem: bílá = přemalovat, černá = nechat. */
    val mask: File? = null,
    /** Co má na zamaskovaném místě být. */
    val prompt: String = "",
    val model: InpaintModel = InpaintModel.KLEIN,
) {
    val maskPainted: Boolean get() = mask != null

    /** Pořadí je závazné — stavitel čte [fotka, maska]. */
    val uploadImages: List<File> get() = listOfNotNull(source, mask)
}

/** Co kartě chybí, než se dá spustit. */
fun inpaintProblem(s: InpaintScene): String? = when {
    s.source == null -> t("Vyber fotku, do které se má domalovávat.")
    !s.maskPainted -> t("Začmárej prstem místo, které se má přemalovat.")
    s.prompt.isBlank() -> t("Napiš, co má na zamaskovaném místě být.")
    else -> null
}

/** Upozornění, která nebrání spuštění. */
fun inpaintHints(s: InpaintScene): List<String> {
    val out = mutableListOf<String>()
    if (s.maskPainted) {
        out += t("Popiš celé místo i s okolím („muž v černé bundě na lavičce“), " +
            "ne jen samotnou věc — model píše obraz, ne příkaz.")
    }
    return out
}

/**
 * Uložení karty — stejný vzor jako u ostatních scén: fotka a maska jako
 * soubory ve složce aplikace, volby jako JSON v nastavení.
 */
class InpaintStore(private val ctx: Context) {

    private val sp = ctx.getSharedPreferences("h3video", Context.MODE_PRIVATE)

    fun dir(): File = File(ctx.filesDir, "inpaint").also { it.mkdirs() }

    fun sourceFile(): File = File(dir(), "fotka.png")

    /** Maska ve vlastním souboru — fotka se malováním nemění. */
    fun maskFile(): File = File(dir(), "maska.png")

    fun load(): InpaintScene {
        val source = sourceFile().takeIf { it.exists() && it.length() > 0 }
        val mask = if (source != null) {
            maskFile().takeIf { it.exists() && it.length() > 0 }
        } else null
        val raw = sp.getString("inpaintScene", "") ?: ""
        val ulozene = runCatching { org.json.JSONObject(raw) }.getOrNull()
        return InpaintScene(
            source = source,
            mask = mask,
            prompt = ulozene?.optString("prompt").orEmpty(),
            model = runCatching { InpaintModel.valueOf(ulozene?.optString("model").orEmpty()) }
                .getOrDefault(InpaintModel.KLEIN),
        )
    }

    fun save(s: InpaintScene) {
        sp.edit().putString(
            "inpaintScene",
            org.json.JSONObject()
                .put("prompt", s.prompt)
                .put("model", s.model.name)
                .toString()
        ).apply()
    }
}
