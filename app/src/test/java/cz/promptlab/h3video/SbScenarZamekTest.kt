package cz.promptlab.h3video

import cz.promptlab.h3video.data.SbFilmPlan
import cz.promptlab.h3video.data.SbFilmPrepis
import cz.promptlab.h3video.data.SbFilmScene
import cz.promptlab.h3video.data.SbScenar
import cz.promptlab.h3video.data.SbZdroj
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * ZÁMEK nastavení přepisovače pro „Storyboard + scénář“ (5.32, uživatel 30. 9. 2026:
 * „teď to funguje dobře, zamkni to nastavení, ať se to zase nerozhodí“).
 *
 * Vzory jsou přesně to, co appka 5.32 poslala přepisovači na server (Dar mudrců,
 * 4 úseky), a to, co přepisovač vrátil. Test skládá zadání stejně jako
 * MainViewModel (pouzijScenar → napisPromptySb) a musí vyjít znak po znaku.
 * Když tenhle test spadne, změna mění funkční nastavení — NEMĚNIT vzory bez
 * výslovného souhlasu uživatele.
 */
class SbScenarZamekTest {

    private val dir = File("src/test/resources/zlaty_scenar")

    private fun scena(): SbFilmScene {
        val text = File("src/test/resources/scenar_dar_mudrcu_532.txt").readText().replace("
", "
")
        val scenar = SbScenar.rozeber(text)!!
        val obrazek = SbFilmPlan.precti(File("src/test/resources/cteni_dar_mudrcu_532.txt").readText().replace("
", "
"))
        val plan = SbFilmPlan.naplanuj(SbScenar.cteni(scenar, obrazek))
        return SbFilmScene(
            storyboard = File("sbfilm_storyboard.png"), zdroj = SbZdroj.SCENAR, scenar = text,
            panely = SbScenar.doplnPanely(plan.panely, scenar), vzhled = plan.vzhled, hlasy = plan.hlasy,
            kontinuita = scenar.kontinuita,
        )
    }

    @Test
    fun `zadani prepisovace se nemeni`() {
        val s = scena()
        val sp = SbFilmPrepis
        val useky = s.useky
        assertEquals(4, useky.size)
        useky.forEachIndexed { k, u ->
            val hlidka = sp.hlidka(
                s.uploadImages.size, u, k, useky.size, s.seStoryboardem,
                sp.idMluvcich(s.panely), sp.jazykFilmu(s), s.hlasy,
                predchozi = useky.getOrNull(k - 1)?.panely?.lastOrNull(),
                vzhled = s.vzhled, kontinuita = s.kontinuita,
                vzhledSeMeni = SbScenar.vzhledSeMeni(s.vzhled),
                zeScenare = true,
            )
            assertEquals("úsek ${k + 1}", File(dir, "prompt_$k.txt").readText().replace("
", "
"), // Stejně jako MainViewModel.spustPrepisAPockejRef (hlidatDialogy = false).
                sp.zadani(s, k, useky.size).trimEnd().trimEnd('.') + ". Do not add any on-screen text or captions unless explicitly requested." + hlidka)
        }
    }

    @Test
    fun `uprava vystupu prepisovace se nemeni`() {
        val s = scena()
        val sp = SbFilmPrepis
        val out = StringBuilder()
        s.useky.forEachIndexed { k, u ->
            val surovy = File(dir, "vystup_$k.txt").readText().replace("
", "
")
            val hotovy = sp.replikaNaZacatek(SbScenar.opravZnacky(sp.opravObrazky(sp.ocistiPrepis(surovy, u.panely.size), s.uploadImages.size)))
            out.append("===== $k\n").append(hotovy).append("\n")
        }
        val vzor = File(dir, "hotove.txt")
        if (!vzor.exists()) vzor.writeText(out.toString())
        assertEquals(vzor.readText().replace("
", "
"), out.toString())
    }
}
