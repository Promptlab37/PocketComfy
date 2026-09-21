package cz.promptlab.h3video.data

import android.content.Context
import androidx.compose.runtime.Immutable
import org.json.JSONObject

/**
 * Co se na kartě **LTX 2.5** dělá. Jeden model, tři způsoby zadání —
 * lišící se jen tím, co do grafu přijde a odkud se bere délka.
 *
 * Zvuk vzniká u všech tří: LTX 2.5 je model obrazu **i zvuku**. U [TEXT]
 * a [OBRAZEK] si ho vymyslí (prázdný zvukový latent, do zadání patří i popis
 * zvuku), u [ZVUK] dostane hotový soubor a napasuje na něj obraz.
 */
enum class LtxRezim(
    /** Předloha v `res/raw`; každý režim má vlastní, ověřenou proti serveru. */
    val sablona: String,
    private val titleCs: String,
    private val detailCs: String,
) {
    TEXT(
        sablona = "workflow_ltx25_t2v",
        titleCs = "Z textu",
        detailCs = "Jen z popisu. Zvuk k tomu model vymyslí sám",
    ),
    OBRAZEK(
        sablona = "workflow_ltx25_i2v",
        titleCs = "Z obrázku",
        detailCs = "Rozhýbe fotku a dodá k ní zvuk",
    ),
    ZVUK(
        sablona = "workflow_ltx25_audio",
        titleCs = "Ze zvuku",
        detailCs = "Fotka mluví na tvůj zvuk — délka sedí přesně na něj",
    );

    val title: String get() = t(titleCs)
    val detail: String get() = t(detailCs)

    /** Bere tenhle režim fotku prvního snímku? */
    val chceObrazek: Boolean get() = this != TEXT

    /** Zadává se délka ručně? U [ZVUK] ji určuje nahraný soubor. */
    val zadavaSeDelka: Boolean get() = this != ZVUK
}

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
    /** Způsob zadání — určuje předlohu i to, která pole karty platí. */
    val rezim: LtxRezim = LtxRezim.OBRAZEK,
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
    /**
     * Délka v sekundách u režimů [LtxRezim.TEXT] a [LtxRezim.OBRAZEK].
     * U [LtxRezim.ZVUK] se nepoužije — tam ji určuje nahraný soubor.
     */
    val sekundy: Float = 5f,
    /**
     * Doplňková LoRA na transformer. Sedí celá rodina LTX — soubory pro 2.3
     * i pro 2.5 mají stejný tvar (48 bloků, šířka 4096), viz [LtxLoras].
     */
    val lora: EditLora = EditLora(),
) {
    /**
     * Kolik snímků z toho vyjde při 25 fps — jen pro popisek v kartě.
     * Zaokrouhluje se: 8,4 s je v plovoucí čárce o kousek míň než 210 snímků
     * a useknutím by karta hlásila o snímek míň, než graf opravdu spočítá.
     */
    val snimku: Int get() {
        val delka = if (rezim == LtxRezim.ZVUK) zvukSekund else sekundy
        return if (delka > 0f) Math.round(FPS * delka) + 1 else 0
    }

    companion object {
        /** Snímková frekvence předlohy; graf z ní počítá délku latentu. */
        const val FPS = 25

        /**
         * Nad tuhle délku se 22B model na 16GB kartě dostává do přelévání
         * do RAM a běh roste do desítek minut. Není to zákaz, jen mez, od
         * které karta varuje.
         */
        const val ROZUMNY_STROP_S = 20f

        /** Meze ručně zadávané délky. */
        const val MIN_SEKUND = 2f
        const val MAX_SEKUND = 20f
    }
}

/** Co kartě chybí, než se dá spustit. */
fun ltxProblem(s: LtxScene): String? = when {
    s.rezim.chceObrazek && s.obrazek?.exists() != true ->
        t("Vyber fotku, ze které video začne.")
    s.rezim == LtxRezim.ZVUK && s.zvuk?.exists() != true ->
        t("Vyber zvuk, na který se bude mluvit.")
    s.popis.isBlank() -> t("Popiš scénu — kdo je v záběru a co dělá.")
    else -> null
}

/** Upozornění, která nebrání spuštění. */
fun ltxHints(s: LtxScene): List<String> {
    val out = mutableListOf<String>()
    if (s.rezim == LtxRezim.ZVUK) {
        out += t("Délku videa určuje zvuk — model ji nemá jak useknout.")
        if (s.zvukSekund > LtxScene.ROZUMNY_STROP_S) {
            out += t("Zvuk je delší než 20 s. Běh poroste do desítek minut, ") +
                t("protože se model nevejde do paměti grafiky celý.")
        }
    } else {
        // LTX 2.5 skládá obraz i zvuk najednou. Kdo do zadání napíše jen to,
        // co je vidět, dostane zvuk odhadnutý ze scény — a bývá to šum.
        out += t("Napiš i to, co má být slyšet — hlas, ruch, hudbu. ") +
            t("Model skládá obraz a zvuk zároveň.")
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
            rezim = runCatching { LtxRezim.valueOf(j.optString("rezim")) }
                .getOrDefault(LtxRezim.OBRAZEK),
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
            sekundy = j.optDouble("sekundy", 5.0).toFloat()
                .coerceIn(LtxScene.MIN_SEKUND, LtxScene.MAX_SEKUND),
            lora = EditLora(
                name = j.optString("lora"),
                strength = j.optDouble("loraSila", 0.8).toFloat().coerceIn(0f, 2f),
            ),
        )
    }.getOrDefault(LtxScene())

    fun save(s: LtxScene) {
        sp.edit().putString(
            KEY,
            JSONObject()
                .put("rezim", s.rezim.name)
                .put("sekundy", s.sekundy.toDouble())
                .put("obrazek", s.obrazek?.absolutePath ?: "")
                .put("zvuk", s.zvuk?.absolutePath ?: "")
                .put("zvukSekund", s.zvukSekund.toDouble())
                .put("popis", s.popis)
                .put("pomer", s.pomer.name)
                .put("lora", s.lora.name)
                .put("loraSila", s.lora.strength.toDouble())
                .toString()
        ).apply()
    }

    private companion object { const val KEY = "ltxScene" }
}
