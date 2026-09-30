package cz.promptlab.h3video

import cz.promptlab.h3video.data.SbFilmPlan
import cz.promptlab.h3video.data.SbFilmPrepis
import cz.promptlab.h3video.data.SbFilmScene
import cz.promptlab.h3video.data.SbScenar
import cz.promptlab.h3video.data.SbScenarModel
import cz.promptlab.h3video.data.SbScenarPokryti
import cz.promptlab.h3video.data.SbZdroj
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * „Špatný stůl“ (uživatel 30. 9. 2026): scénář z ChatGPT s mluvčími S1–S5
 * („S1 – mladý muž v tmavě zeleném saku“). Dřív: postavy „Černé“ a „Jediná“,
 * hlas jen pro tři, S2 se značkou (S1). Obě cesty — rozbor appky i čtení
 * jazykovým modelem (skutečná odpověď ze serveru) — musí dát totéž.
 */
class SbScenarSpatnyStulTest {

    private val text = File("src/test/resources/scenar_spatny_stul.txt").readText()
    private val stitky = File("src/test/resources/scenar_spatny_stul_stitky.txt").readText()
    private val obrazek = SbFilmPlan.precti(File("src/test/resources/scenar_spatny_stul_obraz.txt").readText())

    private val repliky = listOf(
        1 to "S2", 1 to "S1", 2 to "S1", 4 to "S1", 5 to "S4", 5 to "S5", 6 to "S4",
        7 to "S3", 7 to "S1", 9 to "S1", 10 to "S2", 11 to "S1", 12 to "S2",
    )

    private fun zkontroluj(nazev: String, s: cz.promptlab.h3video.data.SbScenarCteni) {
        assertEquals(nazev, 12, s.okna.size)
        assertEquals(nazev, listOf("S1", "S2", "S3", "S4", "S5"), s.postavy.keys.filter { it.startsWith("S") })
        assertTrue(nazev, s.postavy.getValue("S2").contains("přítelkyně"))
        assertEquals(nazev, repliky, s.okna.flatMap { o -> o.repliky.map { o.cislo to it.kdo } })
        val plan = SbFilmPlan.naplanuj(SbScenar.cteni(s, obrazek))
        // Každý mluvčí má hlas, ženy ženský, muži mužský; rekvizita nemluví.
        for (k in listOf("S1", "S3", "S5")) assertFalse("$nazev $k", plan.hlasy.getValue(k).contains("woman"))
        for (k in listOf("S2", "S4")) assertTrue("$nazev $k", plan.hlasy.getValue(k).contains("woman"))
        assertTrue(nazev, plan.hlasy.getValue("S4").contains("elderly"))
        assertFalse(nazev, plan.hlasy.keys.any { it.startsWith("Rekvizit") || it == "Jediná" || it == "Černé" })
        // Značky mluvčích: S2 zůstane (S2).
        val sc = SbFilmScene(storyboard = File("s.png"), zdroj = SbZdroj.SCENAR, scenar = text,
            panely = SbScenar.doplnPanely(plan.panely, s), hlasy = plan.hlasy, vzhled = plan.vzhled, kontinuita = s.kontinuita)
        val id = SbFilmPrepis.idMluvcich(sc.panely)
        assertEquals(nazev, mapOf("S1" to "S1", "S2" to "S2", "S3" to "S3", "S4" to "S4", "S5" to "S5"), id.toSortedMap())
        val vse = sc.useky.mapIndexed { k, u ->
            SbFilmPrepis.hlidka(1, u, k, sc.useky.size, true, id, SbFilmPrepis.jazykFilmu(sc),
                sc.hlasy, predchozi = sc.useky.getOrNull(k - 1)?.panely?.lastOrNull(), vzhled = sc.vzhled, kontinuita = sc.kontinuita)
        }.joinToString("\n")
        assertTrue(nazev, vse.contains("S2 (S2), s úsměvem, says <d>[Czech] Jsi dneska nějaký nervózní.</d>") ||
            vse.contains("S2 (S2), with a smile, says <d>[Czech] Jsi dneska nějaký nervózní.</d>"))
        assertFalse(nazev, vse.contains("S2 (S1)"))
    }

    @Test
    fun `rozbor appky`() {
        val s = SbScenar.rozeber(text)!!
        zkontroluj("rozbor", s)
        assertEquals(emptyList<String>(), SbScenarPokryti.nepokryte(text, s))
    }

    @Test
    fun `cteni jazykovym modelem`() {
        val j = SbScenarModel.jednotky(text)
        val st = SbScenarModel.stitky(stitky, j)!!
        zkontroluj("model", SbScenarModel.cteni(j, st))
    }
}
