package cz.promptlab.h3video

import cz.promptlab.h3video.data.SbScenar
import cz.promptlab.h3video.data.SbScenarPokryti
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Korpus scénářů (5.27, odborníci 30. 9. 2026): stejný obsah zapsaný
 * mnoha způsoby, jak je píše ChatGPT. Očekávaný výsledek je známý předem,
 * takže nový zápis se nemusí ladit ručně — test řekne, co nesedí.
 */
class SbScenarKorpusTest {

    data class Rep(val kdo: String, val jak: String, val text: String)
    data class Okno(val od: Int, val doS: Int, val nazev: String, val obraz: String, val zvuk: String?, val text: String?, val repliky: List<Rep>)
    data class Postava(val jmeno: String, val vek: Int, val popis: String)
    data class Film(val nazev: String, val postavy: List<Postava>, val okna: List<Okno>)

    private val filmy = listOf(
        Film("KAVÁRNA", listOf(Postava("Jana", 28, "krátké blond vlasy, zelený svetr"), Postava("Tomáš", 32, "tmavé vousy, modrá košile")), listOf(
            Okno(0, 3, "PŘÍCHOD", "Jana vejde do kavárny a rozhlédne se.", "Zvonek nade dveřmi.", null, emptyList()),
            Okno(3, 7, "SETKÁNÍ", "Tomáš vstane od stolu a mávne na ni.", null, null, listOf(Rep("Tomáš", "radostně", "Jano, tady jsem!"))),
            Okno(7, 11, "OBJEDNÁVKA", "Oba si sednou, číšnice přinese dvě kávy.", "Cinknutí šálků.", "Káva, která spojuje.", listOf(Rep("Jana", "s úsměvem", "Tohle místo miluju."))),
            Okno(11, 14, "ZÁVĚR", "Detail dvou šálků vedle sebe.", null, "Navštivte nás.", emptyList()),
        )),
        Film("DĚDEČKOVA DÍLNA", listOf(Postava("Děda", 78, "bílé vlasy, pracovní zástěra"), Postava("Vnuk", 10, "zrzavé vlasy, žluté tričko")), listOf(
            Okno(0, 4, "DÍLNA", "Děda brousí dřevěné auto u ponku.", "Šustění brusného papíru.", null, emptyList()),
            Okno(4, 8, "VNUK", "Vnuk nakoukne do dveří a oči mu zazáří.", null, null, listOf(Rep("Vnuk", "nadšeně", "Dědo, to je pro mě?"))),
            Okno(8, 12, "DÁREK", "Děda mu auto podá a pohladí ho po vlasech.", null, null, listOf(Rep("Děda", "tiše", "Pro tebe, kamaráde."), Rep("Vnuk", "", "Děkuju!"))),
        )),
        Film("RANNÍ BĚH", listOf(Postava("Petra", 40, "černé legíny, růžová bunda")), listOf(
            Okno(0, 3, "BUDÍK", "Petra vypne budík a podívá se z okna.", "Pípání budíku.", "Každé ráno začíná tady.", emptyList()),
            Okno(3, 6, "PARK", "Běží parkem mezi stromy, dech se jí páří.", "Kroky po štěrku.", null, emptyList()),
            Okno(6, 10, "CÍL", "Zastaví se u lavičky a napije se vody.", null, null, listOf(Rep("Petra", "zadýchaně", "Dneska jsem to dala."))),
            Okno(10, 13, "LOGO", "Láhev s vodou na lavičce v ranním světle.", null, "Běhej s námi.", emptyList()),
        )),
    )

    private fun q(t: String) = "„$t“"

    /** Způsoby zápisu, jak je píše ChatGPT. */
    private val styly: List<Pair<String, (Film) -> String>> = listOf(
        "hranate" to { f ->
            "STORYBOARD – ${f.nazev}\nFormát: 9:16, přibližně ${f.okna.last().doS} sekund.\n\nPostavy: " +
                f.postavy.joinToString(" a ") { "${it.jmeno} (${it.vek} let, ${it.popis})" } + ".\n\n" +
                f.okna.mapIndexed { i, o ->
                    "[OKNO ${i + 1} | ${o.od}–${o.doS} s] ${o.nazev}\nObraz: ${o.obraz}\n" +
                        (o.zvuk?.let { "Zvuk: $it\n" } ?: "") +
                        o.repliky.joinToString("") { r -> "Dialog – ${r.kdo.lowercase()}${if (r.jak.isNotBlank()) ", ${r.jak}" else ""}: ${q(r.text)}\n" } +
                        (o.text?.let { "Text na videu: ${q(it)}\n" } ?: "")
                }.joinToString("\n")
        },
        "okno_pomlcka" to { f ->
            "Scénář k obrázkovému storyboardu\nFormát: realistická reklama, 9:16.\nPostavy:\n" +
                f.postavy.joinToString("\n") { "${it.jmeno} – ${it.vek} let (${it.popis})" } + "\n" +
                f.okna.mapIndexed { i, o ->
                    "Okno ${i + 1} — ${o.nazev.lowercase().replaceFirstChar { it.uppercase() }} (${o.od}–${o.doS} s)\n${o.obraz}" +
                        (o.zvuk?.let { "\nZvuk: $it" } ?: "") +
                        o.repliky.joinToString("") { r -> " ${r.kdo}${if (r.jak.isNotBlank()) " ${r.jak}" else ""}: ${q(r.text)}" } +
                        (o.text?.let { "\nText ve videu: ${q(it)}" } ?: "")
                }.joinToString("\n")
        },
        "markdown" to { f ->
            "# ${f.nazev}\n**Formát:** 9:16\n**Postavy:** " + f.postavy.joinToString(", ") { "**${it.jmeno}** (${it.vek} let, ${it.popis})" } + "\n\n" +
                f.okna.mapIndexed { i, o ->
                    "**ZÁBĚR ${i + 1} – ${o.od}–${o.doS} s**\n**Obraz:** ${o.obraz}\n" +
                        (o.zvuk?.let { "**Zvuk:** $it\n" } ?: "") +
                        o.repliky.joinToString("") { r -> "**${r.kdo}**${if (r.jak.isNotBlank()) " (${r.jak})" else ""}: ${q(r.text)}\n" } +
                        (o.text?.let { "**Titulek:** ${q(it)}\n" } ?: "")
                }.joinToString("\n")
        },
        "filmovy" to { f ->
            "${f.nazev}\nPOSTAVY\n" + f.postavy.joinToString("\n") { "${it.jmeno}: ${it.vek} let, ${it.popis}" } + "\n\n" +
                f.okna.mapIndexed { i, o ->
                    "SCÉNA ${i + 1}\n${o.obraz}\n" +
                        (o.zvuk?.let { "ZVUK: $it\n" } ?: "") +
                        o.repliky.joinToString("") { r -> "${r.kdo.uppercase()}${if (r.jak.isNotBlank()) " (${r.jak})" else ""}: ${r.text}\n" } +
                        (o.text?.let { "TEXT NA OBRAZOVCE: $it\n" } ?: "")
                }.joinToString("\n")
        },
        "cislovany" to { f ->
            "${f.nazev}\nPostavy: " + f.postavy.joinToString("; ") { "${it.jmeno}, ${it.vek} let, ${it.popis}" } + "\n\n" +
                f.okna.mapIndexed { i, o ->
                    "${i + 1}. ${o.obraz}" + (o.zvuk?.let { " Zvuk: $it" } ?: "") +
                        o.repliky.joinToString("") { r -> " ${r.kdo}: ${q(r.text)}" } +
                        (o.text?.let { " Text na videu: ${q(it)}" } ?: "")
                }.joinToString("\n")
        },
        "anglicke_stitky" to { f ->
            "${f.nazev}\nFormat: vertical 9:16\nCharacters: " + f.postavy.joinToString("; ") { "${it.jmeno} (${it.vek} years, ${it.popis})" } + "\n\n" +
                f.okna.mapIndexed { i, o ->
                    "Scene ${i + 1} (${o.od}-${o.doS}s)\nVisual: ${o.obraz}\n" +
                        (o.zvuk?.let { "Sound: $it\n" } ?: "") +
                        o.repliky.joinToString("") { r -> "Dialogue: ${r.kdo.uppercase()}${if (r.jak.isNotBlank()) " (${r.jak})" else ""}: \"${r.text}\"\n" } +
                        (o.text?.let { "On-screen text: \"$it\"\n" } ?: "")
                }.joinToString("\n")
        },
        "emoji_nadpisy" to { f ->
            "🎬 ${f.nazev}\n📐 Formát: 9:16\n👥 Postavy: " + f.postavy.joinToString(" a ") { "${it.jmeno.lowercase()} (${it.vek} let, ${it.popis})" } + "\n\n" +
                f.okna.mapIndexed { i, o ->
                    "### ${i + 1}. ${o.nazev.lowercase().replaceFirstChar { it.uppercase() }} (${o.od}–${o.doS} s)\n📷 Obraz: ${o.obraz}\n" +
                        (o.zvuk?.let { "🔊 Zvuk: $it\n" } ?: "") +
                        o.repliky.joinToString("") { r -> "🗣️ ${r.kdo}${if (r.jak.isNotBlank()) ", ${r.jak}" else ""}: ${q(r.text)}\n" } +
                        (o.text?.let { "📝 Text na videu: ${q(it)}\n" } ?: "")
                }.joinToString("\n")
        },
        "jeden_radek" to { f ->
            "${f.nazev}. Postavy: " + f.postavy.joinToString(" a ") { "${it.jmeno} (${it.vek} let, ${it.popis})" } + ". " +
                f.okna.mapIndexed { i, o ->
                    "ZÁBĚR ${i + 1} (${o.od}–${o.doS} s): Obraz: ${o.obraz}" + (o.zvuk?.let { " Zvuk: $it" } ?: "") +
                        o.repliky.joinToString("") { r -> " ${r.kdo}: ${q(r.text)}" } +
                        (o.text?.let { " Text na videu: ${q(it)}" } ?: "")
                }.joinToString(" ")
        },
    )

    private fun norm(t: String) = t.lowercase().replace(Regex("""[„“”"«».,!?…]"""), "").replace(Regex("""\s+"""), " ").trim()

    @Test
    fun `korpus - kazdy zapis dava stejny obsah`() {
        val chyby = mutableListOf<String>()
        for (f in filmy) for ((styl, render) in styly) {
            val text = render(f)
            val id = "${f.nazev}/$styl"
            val s = SbScenar.rozeber(text)
            if (s == null) { chyby += "$id: nerozebráno"; continue }
            if (s.okna.size != f.okna.size) { chyby += "$id: oken ${s.okna.size} ≠ ${f.okna.size}"; continue }
            // Repliky: stejné okno, text i mluvčí (podle kmene).
            f.okna.forEachIndexed { i, o ->
                val ma = s.okna[i].repliky.map { norm(it.kdo).take(4) to norm(it.text) }
                val cekam = o.repliky.map { norm(it.kdo).take(4) to norm(it.text) }
                if (ma != cekam) chyby += "$id okno ${i + 1}: repliky $ma ≠ $cekam"
                o.text?.let { t -> if (s.okna[i].texty.none { norm(it) == norm(t) } && norm(s.okna[i].vyzva) != norm(t)) chyby += "$id okno ${i + 1}: text „$t“ chybí" }
                if (norm(s.okna[i].obraz + " " + s.okna[i].akce).contains(norm(o.text ?: "§§§"))) chyby += "$id okno ${i + 1}: text na videu v popisu"
                o.repliky.forEach { r -> if (norm(s.okna[i].obraz + " " + s.okna[i].akce).contains(norm(r.text))) chyby += "$id okno ${i + 1}: replika v popisu" }
                // Název okna nesmí předcházet popisu (popis začíná tím, co je ve scénáři pod „Obraz“).
                if (!norm(s.okna[i].obraz + " " + s.okna[i].akce).startsWith(norm(o.obraz).take(20))) chyby += "$id okno ${i + 1}: popis „${s.okna[i].obraz.take(50)}“"
            }
            // Postavy se jmény a popisem.
            f.postavy.forEach { p -> if (s.postavy.keys.none { norm(it).take(4) == norm(p.jmeno).take(4) }) chyby += "$id: postava ${p.jmeno} chybí" }
            // Nic nezbylo.
            SbScenarPokryti.nepokryte(text, s).forEach { chyby += "$id: nezařazeno „$it“" }
        }
        File(File("build/sbscenar").also { it.mkdirs() }, "korpus.txt").writeText(chyby.joinToString("\n"))
        // Zadání pro model a porovnání s nahranými odpověďmi (test/resources/korpus/model_k_*.txt).
        val chybyModelu = mutableListOf<String>()
        val dir = File("build/sbscenar/model").also { it.mkdirs() }
        for (f in filmy) for ((styl, render) in styly) {
            val text = render(f)
            val id = (f.nazev.take(6) + "_" + styl).replace(Regex("""[^A-Za-z0-9_]"""), "")
            val j = cz.promptlab.h3video.data.SbScenarModel.jednotky(text)
            File(dir, "zadani_k_$id.txt").writeText(cz.promptlab.h3video.data.SbScenarModel.zadani(j))
            val odp = File("src/test/resources/korpus/model_k_$id.txt")
            if (!odp.exists()) continue
            val st = cz.promptlab.h3video.data.SbScenarModel.stitky(odp.readText(), j)
            if (st == null) { chybyModelu += "$id: odpověď modelu nečitelná"; continue }
            val b = cz.promptlab.h3video.data.SbScenarModel.cteni(j, st)
            val a = SbScenar.rozeber(text)
            if (a != null) cz.promptlab.h3video.data.SbScenarModel.rozdily(a, b).forEach { chybyModelu += "$id: $it" }
            SbScenarPokryti.nepokryte(text, b).forEach { chybyModelu += "$id: model nezařadil „$it“" }
        }
        File("build/sbscenar/korpus_model.txt").writeText(chybyModelu.joinToString("\n"))
        assertTrue("model: " + chybyModelu.take(20).joinToString("\n"), chybyModelu.isEmpty())
        assertTrue("${chyby.size} chyb:\n" + chyby.take(40).joinToString("\n"), chyby.isEmpty())
    }
}
