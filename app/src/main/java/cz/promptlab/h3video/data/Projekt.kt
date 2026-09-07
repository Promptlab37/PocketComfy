package cz.promptlab.h3video.data

import android.content.Context
import androidx.compose.runtime.Immutable
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Jeden **záběr** filmu.
 *
 * Appka do 3.31 uměla vyrobit jedno video. Kdo z toho skládal delší věc,
 * musel si pořadí, popisy i to, který soubor kam patří, držet v hlavě nebo
 * v poznámkách. Záběr je nejmenší jednotka, která tohle drží za něj.
 *
 * Výsledek se **neukládá znovu** — odkazuje se na položku galerie přes její
 * `id`. Když ji uživatel smaže, záběr o něj přijde, ale nic se nerozbije;
 * jen se znovu ukáže jako nevyrobený.
 */
@Immutable
data class Zaber(
    val id: Long,
    /** Krátké označení, třeba „1A — Anna vejde do haly". */
    val nazev: String = "",
    /** Co se v záběru děje. Odsud se předvyplní zadání v cílové kartě. */
    val popis: String = "",
    /** Kterou kartou se má vyrobit. Null = ještě nerozhodnuto. */
    val karta: Mode? = null,
    /** `id` položky v galerii, když je záběr hotový. */
    val vysledek: String? = null,
    /** Poznámka pro člověka — kamera, světlo, co je potřeba dotočit. */
    val poznamka: String = "",
) {
    val jeHotovy: Boolean get() = vysledek != null
}

/** Film, klip nebo reklama — prostě to, co se skládá z víc záběrů. */
@Immutable
data class Projekt(
    val id: Long,
    val nazev: String = "",
    val zabery: List<Zaber> = emptyList(),
) {
    val hotovych: Int get() = zabery.count { it.jeHotovy }

    /** Přesune záběr o [kam] míst; mimo rozsah nedělá nic. */
    fun presun(zaberId: Long, kam: Int): Projekt {
        val i = zabery.indexOfFirst { it.id == zaberId }
        if (i < 0) return this
        val j = i + kam
        if (j !in zabery.indices) return this
        val novy = zabery.toMutableList()
        novy.add(j, novy.removeAt(i))
        return copy(zabery = novy)
    }

    fun uprav(zaberId: Long, zmena: (Zaber) -> Zaber): Projekt =
        copy(zabery = zabery.map { if (it.id == zaberId) zmena(it) else it })

    fun smaz(zaberId: Long): Projekt = copy(zabery = zabery.filterNot { it.id == zaberId })
}

/** Stav karty Projekt: všechny projekty a ten, který je zrovna otevřený. */
@Immutable
data class ProjektScene(
    val projekty: List<Projekt> = emptyList(),
    val otevreny: Long? = null,
    /**
     * Záběr, pro který se právě něco generuje. Jakmile běh doběhne, výsledek
     * se k němu připojí — proto to musí přežít i přepnutí karty a restart.
     */
    val cekaZaber: Long? = null,
) {
    val projekt: Projekt? get() = projekty.firstOrNull { it.id == otevreny }
}

/** Co kartě chybí. Projekt se nespouští, takže tu nikdy nic nebrání. */
fun projektProblem(@Suppress("UNUSED_PARAMETER") s: ProjektScene): String? = null

/** Uložení projektů. Je to jen text, takže se vejdou do jednoho souboru. */
class ProjektStore(private val ctx: Context) {

    private fun soubor(): File = File(ctx.filesDir, "projekty.json")

    fun load(): ProjektScene = runCatching {
        val f = soubor()
        if (!f.exists()) return ProjektScene()
        val root = JSONObject(f.readText())
        val pole = root.optJSONArray("projekty") ?: JSONArray()
        val projekty = (0 until pole.length()).map { i ->
            val p = pole.getJSONObject(i)
            val zab = p.optJSONArray("zabery") ?: JSONArray()
            Projekt(
                id = p.optLong("id"),
                nazev = p.optString("nazev"),
                zabery = (0 until zab.length()).map { j ->
                    val z = zab.getJSONObject(j)
                    Zaber(
                        id = z.optLong("id"),
                        nazev = z.optString("nazev"),
                        popis = z.optString("popis"),
                        // Neznámý název karty (starší nebo novější verze appky)
                        // se tiše zapomene — lepší než spadnout na výčtu.
                        karta = Mode.entries.firstOrNull { it.name == z.optString("karta") },
                        vysledek = z.optString("vysledek").takeIf { it.isNotBlank() },
                        poznamka = z.optString("poznamka"),
                    )
                },
            )
        }
        ProjektScene(
            projekty = projekty,
            otevreny = root.optLong("otevreny", -1L).takeIf { it > 0 },
            cekaZaber = root.optLong("cekaZaber", -1L).takeIf { it > 0 },
        )
    }.getOrDefault(ProjektScene())

    fun save(s: ProjektScene) {
        runCatching {
            val pole = JSONArray()
            s.projekty.forEach { p ->
                val zab = JSONArray()
                p.zabery.forEach { z ->
                    zab.put(
                        JSONObject()
                            .put("id", z.id)
                            .put("nazev", z.nazev)
                            .put("popis", z.popis)
                            .put("karta", z.karta?.name ?: "")
                            .put("vysledek", z.vysledek ?: "")
                            .put("poznamka", z.poznamka)
                    )
                }
                pole.put(
                    JSONObject().put("id", p.id).put("nazev", p.nazev).put("zabery", zab)
                )
            }
            soubor().writeText(
                JSONObject()
                    .put("projekty", pole)
                    .put("otevreny", s.otevreny ?: -1L)
                    .put("cekaZaber", s.cekaZaber ?: -1L)
                    .toString()
            )
        }
    }
}

/**
 * Karty, které umí vyrobit záběr.
 *
 * Nabízet mezi nimi Projekt by byl kruh a Nastavení tam nepatří vůbec —
 * karta nemá ukazovat volby, které nikam nevedou.
 */
val KARTY_PRO_ZABER: List<Mode> get() = Mode.entries.filter { it != Mode.PROJEKT }
