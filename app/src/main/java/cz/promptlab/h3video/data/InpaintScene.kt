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
    /**
     * Model trénovaný přímo na díry v obraze. Výchozí, protože dělá přesně to,
     * co karta slibuje: co napíšeš, to do masky namaluje.
     */
    FILL(
        titleCs = "Flux Fill",
        detailCs = "Domaluje do masky to, co popíšeš — na tohle je trénovaný",
    ),

    /**
     * Destilovaný editační model (4 kroky). Zadání ale bere jako **příkaz
     * k úpravě** a původní výřez drží jako referenci — na pouhý popis („muž
     * s břichem") často odpoví tím, že nechá všechno být. Proto se jeho
     * zadání posílá jako instrukce a v kartě je popsaný jinak.
     */
    KLEIN(
        titleCs = "FLUX.2 Klein",
        detailCs = "Rychlejší (4 kroky), ale poslouchá příkazy — „dej mu plnovous“",
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
    val model: InpaintModel = InpaintModel.FILL,
    /**
     * Doplňková LoRA do přemalování (prázdné = žádná). Základní modely mají
     * o některých motivech — hlavně anatomii — jen mlhavou představu; LoRA
     * trénovaná na to konkrétní je jediné, co s tím spolehlivě hne.
     */
    val lora: String = "",
    val loraSila: Float = 0.9f,
    /**
     * Síla přemalování (denoise) u Flux Fillu. 1,0 = pod maskou vzniká všechno
     * znovu, nižší hodnota nechá tvar i pózu z předlohy a jen je dokreslí —
     * na dolaďování detailu je 0,5–0,7 obvykle lepší než plný přepis.
     */
    val sila: Float = 1.0f,
) {
    val maskPainted: Boolean get() = mask != null

    /** Pořadí je závazné — stavitel čte [fotka, maska]. */
    val uploadImages: List<File> get() = listOfNotNull(source, mask)
}

/**
 * Nabídka LoRA pro daný model. Adaptér patří vždycky k jedné rodině vah:
 * LoRA pro FLUX.1 se na FLUX.2 Klein nenasadí (jiné tvary) a naopak, takže
 * míchat je nemá smysl ani nabízet. Rodina se pozná ze jména souboru — je to
 * jediné, co server o LoRA prozradí.
 */
fun loryProModel(model: InpaintModel, vse: List<String>): List<String> {
    val flux2Znaky = listOf("klein", "flux2", "flux.2", "f2k")
    return when (model) {
        InpaintModel.KLEIN -> vse.filter { jmeno ->
            flux2Znaky.any { jmeno.contains(it, ignoreCase = true) }
        }
        InpaintModel.FILL -> vse.filter { jmeno ->
            val flux = jmeno.contains("flux", true) || jmeno.contains("-f1", true)
            flux && flux2Znaky.none { jmeno.contains(it, ignoreCase = true) }
        }
    }.sorted()
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
        // Verze 2.97 měla jako výchozí Klein. Ten ale na popisné zadání často
        // nezměnil nic (drží původní výřez jako referenci), takže výchozí je
        // od 2.98 Flux Fill — a jednou se přepíše i uložená volba z 2.97,
        // kterou si nikdo vědomě nevybral.
        if (!sp.getBoolean("inpaintMig298", false)) {
            val stare = sp.getString("inpaintScene", "") ?: ""
            val opravene = runCatching {
                org.json.JSONObject(stare).put("model", InpaintModel.FILL.name).toString()
            }.getOrDefault(stare)
            // Zadání zůstává, mění se jen model.
            sp.edit().putBoolean("inpaintMig298", true)
                .putString("inpaintScene", opravene).apply()
        }
        val raw = sp.getString("inpaintScene", "") ?: ""
        val ulozene = runCatching { org.json.JSONObject(raw) }.getOrNull()
        return InpaintScene(
            source = source,
            mask = mask,
            prompt = ulozene?.optString("prompt").orEmpty(),
            model = runCatching { InpaintModel.valueOf(ulozene?.optString("model").orEmpty()) }
                .getOrDefault(InpaintModel.FILL),
            lora = ulozene?.optString("lora").orEmpty(),
            loraSila = (ulozene?.optDouble("loraSila", 0.9) ?: 0.9).toFloat(),
            sila = (ulozene?.optDouble("sila", 1.0) ?: 1.0).toFloat(),
        )
    }

    fun save(s: InpaintScene) {
        sp.edit().putString(
            "inpaintScene",
            org.json.JSONObject()
                .put("prompt", s.prompt)
                .put("model", s.model.name)
                .put("lora", s.lora)
                .put("loraSila", s.loraSila.toDouble())
                .put("sila", s.sila.toDouble())
                .toString()
        ).apply()
    }
}
