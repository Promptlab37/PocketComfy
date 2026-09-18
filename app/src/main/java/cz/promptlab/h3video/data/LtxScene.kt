package cz.promptlab.h3video.data

import android.content.Context
import androidx.compose.runtime.Immutable
import org.json.JSONObject

/**
 * Tvar obrazu u karty **Video ze zvuku**. Hodnoty jsou přesně ty, které zná
 * uzel `ResolutionSelector` — jiný řetězec by uzel neuměl přečíst.
 */
enum class LtxPomer(
    /** Hodnota do `aspect_ratio`; musí sedět na nabídku uzlu. */
    val hodnota: String,
    private val titleCs: String,
) {
    NA_VYSKU("9:16 (Portrait Widescreen)", "Na výšku (9:16)"),
    NA_SIRKU("16:9 (Widescreen)", "Na šířku (16:9)"),
    CTVEREC("1:1 (Square)", "Čtverec (1:1)"),
    PORTRET("3:4 (Portrait Standard)", "Na výšku (3:4)");

    val title: String get() = t(titleCs)
}

/**
 * Karta **Video ze zvuku** — LTX 2.5, fotka + zvukový soubor → video, které
 * na ten zvuk mluví (nebo zpívá).
 *
 * Proč vznikla: u MiniMax H3 se délka záběru zadává ve snímcích dopředu a řeč
 * vzniká až při generování. Když se netrefíš, model větu nedořekne a poslední
 * slabika chybí — a pozná se to až na hotovém videu. Tady jde řeč dovnitř jako
 * hotový soubor a **počet snímků si graf spočítá z jeho délky**
 * (`fps × délka + 1`), takže se useknout nemůže.
 *
 * Zadání je proto krátké: fotka, zvuk, popis scény a tvar obrazu. Kroky,
 * sigmy, dvouprůchodové zvětšení i sampler zůstávají z předlohy.
 */
@Immutable
data class LtxScene(
    /** První snímek — z něj se bere podoba i prostředí. */
    val obrazek: java.io.File? = null,
    /** Náhled vybrané fotky; do uloženého zadání nepatří, jen na obrazovku. */
    val nahled: android.graphics.Bitmap? = null,
    /**
     * Zvuk, na který se bude mluvit. Kopie u sebe: odkaz do galerie telefonu
     * může vypršet dřív, než se úloha dostane z fronty na řadu.
     */
    val zvuk: java.io.File? = null,
    /** Délka zvuku v sekundách, zjištěná při výběru; 0 = neznámá. */
    val zvukSekund: Float = 0f,
    /** Popis scény — anglicky, model je učený na anglické popisky. */
    val popis: String = "",
    /** Tvar obrazu. */
    val pomer: LtxPomer = LtxPomer.NA_VYSKU,
) {
    /**
     * Kolik snímků z toho vyjde při 25 fps — jen pro popisek v kartě.
     * Zaokrouhluje se: 8,4 s je v plovoucí čárce o kousek míň než 210 snímků
     * a useknutím by karta hlásila o snímek míň, než graf opravdu spočítá.
     */
    val snimku: Int get() =
        if (zvukSekund > 0f) Math.round(FPS * zvukSekund) + 1 else 0

    companion object {
        /** Snímková frekvence předlohy; graf z ní počítá délku latentu. */
        const val FPS = 25

        /**
         * Nad tuhle délku se 22B model na 16GB kartě dostává do přelévání
         * do RAM a běh roste do desítek minut. Není to zákaz, jen mez, od
         * které karta varuje.
         */
        const val ROZUMNY_STROP_S = 20f
    }
}

/** Co kartě chybí, než se dá spustit. */
fun ltxProblem(s: LtxScene): String? = when {
    s.obrazek?.exists() != true -> t("Vyber fotku, ze které video začne.")
    s.zvuk?.exists() != true -> t("Vyber zvuk, na který se bude mluvit.")
    s.popis.isBlank() -> t("Popiš scénu — kdo je v záběru a co dělá.")
    else -> null
}

/** Upozornění, která nebrání spuštění. */
fun ltxHints(s: LtxScene): List<String> {
    val out = mutableListOf<String>()
    out += t("Délku videa určuje zvuk — model ji nemá jak useknout.")
    if (s.zvukSekund > LtxScene.ROZUMNY_STROP_S) {
        out += t("Zvuk je delší než 20 s. Běh poroste do desítek minut, ") +
            t("protože se model nevejde do paměti grafiky celý.")
    }
    out += t("Popis piš anglicky — model je učený na anglické popisky.")
    return out
}

/** Uložené zadání karty Video ze zvuku — přežije zavření aplikace. */
class LtxStore(ctx: Context) {

    private val sp = ctx.getSharedPreferences("h3video", Context.MODE_PRIVATE)

    fun load(): LtxScene = runCatching {
        val j = JSONObject(sp.getString(KEY, "{}")!!)
        LtxScene(
            // Soubor mohl mezitím zmizet (úklid cache, přeinstalace) — pak
            // je to, jako by vybraný nebyl.
            obrazek = j.optString("obrazek").takeIf { it.isNotBlank() }
                ?.let { java.io.File(it) }?.takeIf { it.exists() },
            zvuk = j.optString("zvuk").takeIf { it.isNotBlank() }
                ?.let { java.io.File(it) }?.takeIf { it.exists() },
            zvukSekund = j.optDouble("zvukSekund", 0.0).toFloat(),
            popis = j.optString("popis"),
            pomer = runCatching { LtxPomer.valueOf(j.optString("pomer")) }
                .getOrDefault(LtxPomer.NA_VYSKU),
        )
    }.getOrDefault(LtxScene())

    fun save(s: LtxScene) {
        sp.edit().putString(
            KEY,
            JSONObject()
                .put("obrazek", s.obrazek?.absolutePath ?: "")
                .put("zvuk", s.zvuk?.absolutePath ?: "")
                .put("zvukSekund", s.zvukSekund.toDouble())
                .put("popis", s.popis)
                .put("pomer", s.pomer.name)
                .toString()
        ).apply()
    }

    private companion object { const val KEY = "ltxScene" }
}
