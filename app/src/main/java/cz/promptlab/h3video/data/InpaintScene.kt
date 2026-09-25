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
    ),

    /**
     * Jednotný model Qwen Image 2.1. Vlastní uzel na masku nemá — maskuje se
     * tak, že místo prázdného latentu jde do vzorkování zdrojový výřez přes
     * `VAEEncode` + `SetLatentNoiseMask`. Výřez přitom zůstává i **referencí**
     * v `TextEncodeQwenImage21`, takže model vidí okolí masky a drží podobu
     * líp než oba flux modely. Platí za to časem: 25 kroků proti 4 u Kleina.
     */
    QWEN21(
        titleCs = "Qwen Image 2.1",
        detailCs = "Nejlíp drží podobu lidí, ale je nejpomalejší (25 kroků)",
    );

    val title: String get() = t(titleCs)
    val detail: String get() = t(detailCs)
}

/**
 * Co karta dělá. Obojí je tentýž graf — mění se jen to, odkud vznikne maska.
 */
enum class InpaintRezim(
    private val titleCs: String,
    private val detailCs: String,
) {
    /** Maska je to, co uživatel začmáral prstem. */
    MASKA(
        titleCs = "Domalovat do masky",
        detailCs = "Začmáráš místo a model ho přemaluje podle věty",
    ),

    /**
     * Maska vzniká sama: `InpaintCropImproved` s `extend_for_outpainting`
     * přilepí k fotce nové místo a označí ho. Štětec se proto nepoužívá.
     */
    ROZSIRIT(
        titleCs = "Rozšířit obrázek",
        detailCs = "Přilepí k fotce nové místo a domaluje, co tam chybí",
    );

    val title: String get() = t(titleCs)
    val detail: String get() = t(detailCs)

    /** Maluje se štětcem? U rozšíření ne — masku vyrobí graf. */
    val chceMasku: Boolean get() = this == MASKA
}

/** Kam se fotka rozšiřuje. Víc směrů zároveň je v pořádku. */
enum class Smer(private val titleCs: String) {
    DOLU("Dolů"),
    NAHORU("Nahoru"),
    VLEVO("Vlevo"),
    VPRAVO("Vpravo");

    val title: String get() = t(titleCs)
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
    /** Co se dělá — domalování do masky, nebo rozšíření plátna. */
    val rezim: InpaintRezim = InpaintRezim.MASKA,
    /** Směry rozšíření. Prázdné = nic, karta pak nepustí start. */
    val smery: Set<Smer> = setOf(Smer.DOLU),
    /**
     * O kolik procent plochy se v každém zvoleném směru přidá. Qwen ve své
     * příručce doporučuje 30–50 %, proto je výchozí 50 a strop 100:
     * nad tím už model nemá z čeho vycházet a dokresluje si scénu od nuly.
     */
    val procent: Int = 50,
) {
    val maskPainted: Boolean get() = mask != null

    /**
     * Pořadí je závazné — stavitel čte [fotka, maska].
     *
     * U rozšíření se maska neposílá vůbec: `InpaintCropImproved` ji má jako
     * nepovinný vstup a bez ní si ji z přilepeného místa vyrobí sám. Poslat
     * tam starou masku z druhého režimu by přemalovalo i místo uvnitř fotky.
     */
    val uploadImages: List<File> get() =
        if (rezim.chceMasku) listOfNotNull(source, mask) else listOfNotNull(source)

    /** Faktor pro daný směr tak, jak ho čte uzel: 1,0 = neměnit. */
    fun faktor(smer: Smer): Double =
        if (smer in smery) 1.0 + procent / 100.0 else 1.0
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
        // Qwen Image 2.1 je nová architektura — LoRA pro starší Qwen Image
        // na ni nesedí, nabízí se jen soubory označené pro 2.1.
        InpaintModel.QWEN21 -> vse.filter { Qwen21Lora.soubor(it) }
    }.sorted()
}

/** Co kartě chybí, než se dá spustit. */
fun inpaintProblem(s: InpaintScene): String? = when {
    s.source == null -> if (s.rezim.chceMasku)
        t("Vyber fotku, do které se má domalovávat.")
    else t("Vyber fotku, kterou chceš rozšířit.")
    s.rezim.chceMasku && !s.maskPainted ->
        t("Začmárej prstem místo, které se má přemalovat.")
    !s.rezim.chceMasku && s.smery.isEmpty() ->
        t("Vyber aspoň jeden směr, kam se má fotka rozšířit.")
    // U rozšíření je zadání nepovinné: model má původní fotku v grafu jako
    // druhou referenci, takže si scénu dotáhne i bez věty.
    s.rezim.chceMasku && s.prompt.isBlank() -> t("Napiš, co má na zamaskovaném místě být.")
    else -> null
}

/** Upozornění, která nebrání spuštění. */
fun inpaintHints(s: InpaintScene): List<String> {
    val out = mutableListOf<String>()
    if (!s.rezim.chceMasku) {
        // Původní fotka se vlepí zpátky nezměněná (InpaintStitchImproved),
        // takže se není čeho bát u tváří — ale je dobré to říct, protože
        // u ostatních modelů appky to takhle nefunguje.
        out += t("Původní fotka se nepřekresluje — model maluje jen to přilepené místo.")
        if (s.prompt.isBlank()) {
            out += t("Zadání můžeš nechat prázdné — model scénu dotáhne podle fotky. " +
                "Napiš ho, jen když tam má být něco konkrétního.")
        }
        if (s.procent > 60) {
            out += t("Nad 60 % už model nemá z čeho vycházet a scénu si vymýšlí. " +
                "Spolehlivější je rozšířit dvakrát po menším kusu.")
        }
        if (s.smery.size > 2) {
            out += t("Čím víc směrů naráz, tím víc si model domýšlí. " +
                "Po jednom směru to bývá přesnější.")
        }
        return out
    }
    if (s.maskPainted) {
        out += t("Popiš celé místo i s okolím („muž v černé bundě na lavičce“), " +
            "ne jen samotnou věc — model píše obraz, ne příkaz.")
    }
    // Bez tohohle to vypadá, že model „neposlechl". Neposlechl proto, že to
    // neumí: základní FLUX má nahotu i podrobnou anatomii vytrénovanou pryč
    // a bez adaptéru ji nenamaluje, ať se zadání napíše jakkoli. Volba LoRA
    // je ve sbaleném oddílu, takže o ní člověk nemusí vůbec vědět.
    if (s.lora.isBlank()) {
        out += t("Základní model neumí nahotu ani podrobnou anatomii — ty z něj " +
            "byly vytrénované pryč. Rozbal „Model a doladění“ a přimíchej LoRA, " +
            "jinak to zadání nesplní, ať ho napíšeš jakkoli.")
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
            rezim = runCatching { InpaintRezim.valueOf(ulozene?.optString("rezim").orEmpty()) }
                .getOrDefault(InpaintRezim.MASKA),
            smery = ulozene?.optJSONArray("smery")?.let { pole ->
                (0 until pole.length()).mapNotNull { i ->
                    runCatching { Smer.valueOf(pole.getString(i)) }.getOrNull()
                }.toSet()
            } ?: setOf(Smer.DOLU),
            procent = (ulozene?.optInt("procent", 50) ?: 50)
                .coerceIn(10, cz.promptlab.h3video.comfy.InpaintBuilder.ROZSIRENI_MAX),
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
                .put("rezim", s.rezim.name)
                .put("smery", org.json.JSONArray(s.smery.map { it.name }))
                .put("procent", s.procent)
                .toString()
        ).apply()
    }
}
