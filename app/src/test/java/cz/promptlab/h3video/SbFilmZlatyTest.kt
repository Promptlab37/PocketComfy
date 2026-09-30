package cz.promptlab.h3video

import cz.promptlab.h3video.data.SbFilmPlan
import cz.promptlab.h3video.data.SbFilmPrepis
import cz.promptlab.h3video.data.SbFilmScene
import cz.promptlab.h3video.data.SbPanel
import cz.promptlab.h3video.data.SbZdroj
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * Zlaté výstupy stávajících cest (5.14): nová volba „Storyboard + scénář“
 * nesmí změnit ani znak v tom, co dostane přepisovač u „Mám storyboard“
 * a „Vytvořit z děje“. Vzory vznikly z kódu 5.13 před změnou.
 */
class SbFilmZlatyTest {

    private val cteni =
        "TITLE: Iron Mask | TOTAL: none | SHOTS: 5 | GRID: 2x3 | VOICES: Guard = a man in his 40s with a rough, low voice; " +
            "King = a man in his 30s with a calm voice | LOOKS: Guard = steel helmet, red tunic; King = iron mask, grey rags | " +
            "MUSIC: cinematic, tense, low strings, 80 BPM " +
            "PANEL 1 | none | wide | slow push-in | A guard walks down a dark corridor. | none " +
            "PANEL 2 | none | close-up | static | A hand turns a key in the lock. | Guard: \"Vstávej.\" " +
            "PANEL 3 | none | medium | static | The prisoner lifts his head. | King: „Konečně.“; Guard: „Ticho!“ " +
            "PANEL 4 | none | medium | handheld | The guard pulls the prisoner up. DĚJ: Stráž spěchá. | none " +
            "PANEL 5 | none | close-up | static | The mask falls and reveals the king. | King: „Jsem tvůj král.“"

    private fun sceny(): Map<String, SbFilmScene> {
        val plan = SbFilmPlan.naplanuj(SbFilmPlan.precti(cteni))
        val sb = SbFilmScene(
            storyboard = File("storyboard.png"), zdroj = SbZdroj.STORYBOARD, panely = plan.panely,
            hlasy = plan.hlasy, vzhled = plan.vzhled, nazev = "Iron Mask",
        )
        val dej = SbFilmScene(
            zdroj = SbZdroj.DEJ, dej = "Vězeň v železné masce je osvobozen a ukáže se, že je to král.",
            postavy = listOf(cz.promptlab.h3video.data.LongMmRef(File("a.png")), cz.promptlab.h3video.data.LongMmRef(File("b.png"))),
            panely = plan.panely, hlasy = plan.hlasy,
        )
        // Dlouhý film: tři úseky, poslední s replikou (rezerva konce).
        val dlouhe = sb.copy(panely = plan.panely + plan.panely.map { it.copy(cislo = it.cislo + 5) } +
            listOf(SbPanel(11, "The king walks out into the light.", "wide", "static", 4.0, "King: „Jdeme.“")),
            dej = "Osvobození krále.")
        return mapOf("storyboard" to sb, "dej" to dej, "dlouhe" to dlouhe)
    }

    private fun vypis(s: SbFilmScene): String {
        val sp = SbFilmPrepis
        val useky = s.useky
        val out = StringBuilder()
        // Nová pole panelu (5.14) mají výchozí hodnoty; do srovnání nepatří.
        out.append("PLAN\n").append(SbFilmPlan.naplanuj(SbFilmPlan.precti(cteni)).toString()
            .replace(", podani=, zvuk=, obrazovka=false", "")).append("\n")
        out.append("PLANOVACI\n").append(SbFilmPlan.planovaciText(useky, "<Picture 1>")).append("\n")
        out.append("UPLOAD ").append(s.uploadImages.map { it.name }).append(" SB ").append(s.seStoryboardem).append("\n")
        useky.forEachIndexed { k, u ->
            out.append("ZADANI $k\n").append(sp.zadani(s, k, useky.size)).append("\n")
            out.append("HLIDKA $k").append(
                sp.hlidka(
                    s.uploadImages.size, u, k, useky.size, s.seStoryboardem,
                    sp.idMluvcich(s.panely), sp.jazykFilmu(s.panely), s.hlasy,
                    predchozi = useky.getOrNull(k - 1)?.panely?.lastOrNull(),
                    vzhled = s.vzhled,
                ),
            ).append("\n")
        }
        return out.toString()
    }

    @Test
    fun `stavajici cesty beze zmeny`() {
        val dir = File("src/test/resources/sbfilm_golden").also { it.mkdirs() }
        sceny().forEach { (jmeno, s) ->
            val f = File(dir, "$jmeno.txt")
            val ted = vypis(s)
            if (!f.exists()) f.writeText(ted)
            // Git na Windows může vzor uložit s CRLF.
            assertEquals(jmeno, f.readText().replace("\r\n", "\n"), ted)
        }
    }
}
