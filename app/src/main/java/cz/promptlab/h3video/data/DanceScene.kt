package cz.promptlab.h3video.data

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.Immutable
import java.io.File

/**
 * Druh tance. V kartě je česky, do grafu jde čínsky.
 *
 * **Proč čínsky:** Wan-Dancer je na čínské zadání vycvičený a oficiální
 * předloha skládá prompt ze šablony „一个人正在跳舞，舞蹈种类是…" (člověk tančí,
 * druh tance je…). Přeložit ji do češtiny by znamenalo psát modelu jazykem,
 * na kterém trénovaný není. Uživatel ty znaky nikde nevidí — vybírá
 * z českých názvů a překlad si drží appka.
 */
enum class DanceStyl(
    /** Slovo, které se dosadí do čínské šablony promptu. */
    val zadani: String,
    private val titleCs: String,
) {
    LATINA("拉丁舞", "Latina"),
    STREET("街舞", "Street dance"),
    KPOP("韩舞", "K-pop"),
    KLASIKA("古典舞", "Klasický tanec"),
    STEP("踢踏舞", "Step");

    val title: String get() = t(titleCs)
}

/** Jak velké pohyby má tanečník dělat. */
enum class DanceRozsah(
    val zadani: String,
    private val titleCs: String,
) {
    MALY("低", "Drobné pohyby"),
    STREDNI("中等", "Střední"),
    VELKY("高", "Výrazné"),
    MAXIMALNI("最大", "Maximální");

    val title: String get() = t(titleCs)
}

/**
 * Rozlišení videa.
 *
 * Oficiální předloha jede na 720 × 1280. To je ale **2,25× víc bodů** než
 * nižší volba, a protože model vzorkuje 149 snímků naráz, roste čas úměrně.
 * Naměřeno 22. 9. 2026 na RTX 4060 Ti: první fáze na 480 × 832 trvala
 * 10 min 48 s, takže plné rozlišení je na jedno pětisekundové video dlouhá
 * záležitost. Volba je proto na kartě, ne napevno.
 */
enum class DanceKvalita(
    val sirka: Int,
    val vyska: Int,
    private val titleCs: String,
    /** Kolik trvalo pět sekund videa při měření na RTX 4060 Ti. */
    private val odhadCs: String,
) {
    RYCHLE(480, 832, "480 × 832 — rychlejší", "22 minut"),
    PLNE(720, 1280, "720 × 1280 — jako v předloze", "kolem hodiny");

    val odhad: String get() = t(odhadCs)

    val title: String get() = t(titleCs)
}

/**
 * Karta **Dance** — Wan-Dancer 14B.
 *
 * Z fotky člověka a hudby udělá video, kde ten člověk tančí **do rytmu**.
 * Uzel `WanDancerEncodeAudio` si z hudby spočítá obálku nástupů, MFCC,
 * chroma a hlavně pozice dob, a ty vstřikuje do video modelu — choreografii
 * si tedy model vymýšlí sám podle hudby, žádné vzorové video se nedává.
 *
 * Běh má dvě fáze: globální model rozvrhne pohyb (náhled v 8 fps) a lokální
 * ho dopiluje na 30 fps. Model vzorkuje **149 snímků najednou**, což je
 * zhruba 5 sekund; delší video vzniká po úsecích téhle délky.
 */
@Immutable
data class DanceScene(
    /** Fotka tanečníka — z ní se bere podoba i první snímek. */
    val fotka: File? = null,
    /** Náhled vybrané fotky; do uloženého zadání nepatří, jen na obrazovku. */
    val nahled: Bitmap? = null,
    /**
     * Hudba, na kterou se tančí. Kopie u sebe: odkaz do galerie telefonu
     * může vypršet dřív, než se úloha dostane z fronty na řadu.
     */
    val hudba: File? = null,
    /** Délka nahrávky v sekundách, zjištěná při výběru; 0 = neznámá. */
    val hudbaSekund: Float = 0f,
    val kvalita: DanceKvalita = DanceKvalita.RYCHLE,
    val styl: DanceStyl = DanceStyl.LATINA,
    val rozsah: DanceRozsah = DanceRozsah.STREDNI,
    /** Kolik sekund videa vyrobit. Násobky délky úseku — viz [SEKUND_NA_USEK]. */
    val sekundy: Int = SEKUND_NA_USEK,
    /** Nepovinný vlastní dovětek k zadání (česky i anglicky, model si poradí). */
    val popis: String = "",
) {
    /** Kolik úseků po 149 snímcích se z té délky vyrobí. */
    val useku: Int get() = (sekundy / SEKUND_NA_USEK).coerceAtLeast(1)

    /** Pořadí je závazné — stavitel čte [fotka, hudba]. */
    val uploadImages: List<File> get() = listOfNotNull(fotka)

    companion object {
        /** Model vzorkuje 149 snímků najednou, to je při 30 fps ~5 s. */
        const val SNIMKU_NA_USEK = 149
        const val FPS = 30
        const val SEKUND_NA_USEK = 5

        /** Nabízené délky. Delší = víc úseků a úměrně delší běh. */
        val DELKY = listOf(5, 10, 15, 20, 25, 30)
    }
}

/** Co kartě chybí, než se dá spustit. */
fun danceProblem(s: DanceScene): String? = when {
    // Stačí, že je soubor vybraný — na chybějící soubory myslí už DanceStore,
    // který je při načtení zahodí. Stejně to má zbytek karet.
    s.fotka == null -> t("Vyber fotku člověka, který má tančit.")
    s.hudba == null -> t("Vyber hudbu, na kterou se bude tančit.")
    s.hudbaSekund > 0f && s.hudbaSekund < s.sekundy ->
        t("Hudba je kratší než zvolená délka videa. Zkrať video nebo vyber delší skladbu.")
    else -> null
}

/** Upozornění, která nebrání spuštění. */
fun danceHints(s: DanceScene): List<String> {
    val out = mutableListOf<String>()
    // Bez tohohle to vypadá jako chyba appky. Není — model si choreografii
    // vymýšlí a vzorové video vůbec nepřijímá.
    out += t("Pohyb si model vymyslí sám podle rytmu. Video se vzorovým tancem se sem nedává.")
    // Změřeno 22. 9. 2026 na RTX 4060 Ti: 5 s videa v 480 × 832 = 22 min 20 s
    // (10:48 první fáze, 11:32 druhá). Číslo patří do karty, protože bez něj
    // to vypadá jako každý jiný běh a člověk si pustí třicet sekund.
    out += t("Pět sekund v %d × %d trvalo při měření %s. ")
        .format(s.kvalita.sirka, s.kvalita.vyska, s.kvalita.odhad) +
        t("Vyšší rozlišení i delší video čas úměrně násobí.")
    if (s.sekundy > DanceScene.SEKUND_NA_USEK) {
        out += t("Zvolená délka je %d úseků po %d s a každý se počítá zvlášť. ")
            .format(s.useku, DanceScene.SEKUND_NA_USEK) +
            t("Napoprvé zkus jeden, ať víš, jak dlouho to u tebe trvá.")
    }
    return out
}

/** Uložené zadání karty — přežije zavření aplikace. */
class DanceStore(private val ctx: Context) {

    private val sp = ctx.getSharedPreferences("h3video", Context.MODE_PRIVATE)

    fun dir(): File = File(ctx.filesDir, "dance").also { it.mkdirs() }

    fun fotkaFile(): File = File(dir(), "fotka.png")

    fun load(): DanceScene = runCatching {
        val j = org.json.JSONObject(sp.getString(KEY, "{}")!!)
        DanceScene(
            // Soubor mohl mezitím zmizet (úklid cache, přeinstalace) — pak
            // je to, jako by vybraný nebyl.
            fotka = j.optString("fotka").takeIf { it.isNotBlank() }
                ?.let { File(it) }?.takeIf { it.exists() },
            hudba = j.optString("hudba").takeIf { it.isNotBlank() }
                ?.let { File(it) }?.takeIf { it.exists() },
            hudbaSekund = j.optDouble("hudbaSekund", 0.0).toFloat(),
            kvalita = runCatching { DanceKvalita.valueOf(j.optString("kvalita")) }
                .getOrDefault(DanceKvalita.RYCHLE),
            styl = runCatching { DanceStyl.valueOf(j.optString("styl")) }
                .getOrDefault(DanceStyl.LATINA),
            rozsah = runCatching { DanceRozsah.valueOf(j.optString("rozsah")) }
                .getOrDefault(DanceRozsah.STREDNI),
            sekundy = j.optInt("sekundy", DanceScene.SEKUND_NA_USEK)
                .coerceIn(DanceScene.DELKY.first(), DanceScene.DELKY.last()),
            popis = j.optString("popis"),
        )
    }.getOrDefault(DanceScene())

    fun save(s: DanceScene) {
        sp.edit().putString(
            KEY,
            org.json.JSONObject()
                .put("fotka", s.fotka?.absolutePath ?: "")
                .put("hudba", s.hudba?.absolutePath ?: "")
                .put("hudbaSekund", s.hudbaSekund.toDouble())
                .put("kvalita", s.kvalita.name)
                .put("styl", s.styl.name)
                .put("rozsah", s.rozsah.name)
                .put("sekundy", s.sekundy)
                .put("popis", s.popis)
                .toString()
        ).apply()
    }

    private companion object { const val KEY = "danceScene" }
}
