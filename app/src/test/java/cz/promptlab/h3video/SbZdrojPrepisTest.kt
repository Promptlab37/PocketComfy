package cz.promptlab.h3video

import cz.promptlab.h3video.data.SbFilmScene
import cz.promptlab.h3video.data.SbKrok
import cz.promptlab.h3video.data.SbNalez
import cz.promptlab.h3video.data.SbPanel
import cz.promptlab.h3video.data.SbTextStrihu
import cz.promptlab.h3video.data.SbZdroj
import cz.promptlab.h3video.data.otiskScenare
import cz.promptlab.h3video.data.sbBezPlanuScenare
import cz.promptlab.h3video.data.sbFilmProblem
import cz.promptlab.h3video.data.sbHlavniKrok
import cz.promptlab.h3video.data.sbLzePripravit
import cz.promptlab.h3video.data.sbNastavScenar
import cz.promptlab.h3video.data.sbNormalizujZdroj
import cz.promptlab.h3video.data.sbPrechodSeScenarem
import cz.promptlab.h3video.data.sbPrepniZdroj
import cz.promptlab.h3video.data.sbVyberVolbu
import cz.promptlab.h3video.data.sbZdrojProText
import cz.promptlab.h3video.data.sbZdrojVolba
import cz.promptlab.h3video.data.sbZdrojVolby
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 5.69 (varianta A): přepis z ChatGPT je pole u „Mám storyboard“. Prázdné pole =
 * čtení obrázku (STORYBOARD), vložený přepis = zamčená cesta scénáře (SCENAR).
 * Přepisovač se nemění — mění se jen to, kdy je `zdroj` který.
 */
class SbZdrojPrepisTest {

    private val obrazek = File("storyboard.jpg")
    private val prepis = "Okno 1\nPepa: „Ahoj.“"

    /** Karta s hotovým plánem a vším, co přechod zdroje maže. */
    private fun plny(zdroj: SbZdroj, scenar: String = prepis) = SbFilmScene(
        storyboard = obrazek,
        zdroj = zdroj,
        dej = "Pepa potká Janu",
        scenar = scenar,
        panely = listOf(SbPanel(1, "panel 1", "medium", "static", 3.0, "Pepa: „Ahoj.“")),
        nazev = "Film",
        casyZeStoryboardu = true,
        zadaniUseku = listOf("prompt 1"),
        hlasy = mapOf("Pepa" to "deep voice"),
        vzhled = mapOf("Pepa" to "man in a coat"),
        kontinuita = "rainy night",
        strih = listOf(SbTextStrihu(1, "Titulek", SbTextStrihu.Druh.TEXT)),
        scenarOdhadem = true,
        panelyObrazku = 4,
        oknaScenare = 4,
        scenarPlanu = otiskScenare(scenar),
        nalezy = listOf(SbNalez(1, "nález")),
        radku = 2,
        sloupcu = 2,
        jmenaFotek = mapOf("/a.jpg" to "Pepa"),
    )

    /** Reset ze `setSbZdroj` před 5.69, doslova (bez VM: `bezPlanuScenare` = nalezy + [sbBezPlanuScenare]). */
    private fun puvodni(it: SbFilmScene, v: SbZdroj): SbFilmScene =
        if (it.zdroj == v) it
        else it.copy(zdroj = v, panely = emptyList(), nazev = "", casyZeStoryboardu = false, zadaniUseku = emptyList()).let { n ->
            if (v != SbZdroj.SCENAR && it.zdroj != SbZdroj.SCENAR) n
            else sbBezPlanuScenare(n.copy(zdroj = SbZdroj.SCENAR).copy(nalezy = emptyList()))
                .copy(zdroj = v, hlasy = emptyMap(), vzhled = emptyMap(), kontinuita = "", strih = emptyList())
        }

    @Test
    fun `reset je presunuty beze zmeny ucinku`() {
        for (z in SbZdroj.entries) for (v in SbZdroj.entries) {
            assertEquals("$z → $v", puvodni(plny(z), v), sbPrepniZdroj(plny(z), v))
        }
    }

    @Test
    fun `volby na karte jsou jen dve`() {
        assertEquals(listOf(SbZdroj.STORYBOARD, SbZdroj.DEJ), sbZdrojVolby)
        assertEquals(SbZdroj.STORYBOARD, sbZdrojVolba(plny(SbZdroj.SCENAR)))
        assertEquals(SbZdroj.STORYBOARD, sbZdrojVolba(plny(SbZdroj.STORYBOARD, "")))
        assertEquals(SbZdroj.DEJ, sbZdrojVolba(plny(SbZdroj.DEJ)))
    }

    @Test
    fun `zdroj podle textu`() {
        assertEquals(SbZdroj.STORYBOARD, sbZdrojProText(""))
        assertEquals(SbZdroj.STORYBOARD, sbZdrojProText("  \n "))
        assertEquals(SbZdroj.SCENAR, sbZdrojProText("x"))
    }

    @Test
    fun `prvni vlozeni prepne na prepis a smaze plan cteni`() {
        val s0 = plny(SbZdroj.STORYBOARD, "")
        val s = sbNastavScenar(s0, prepis)
        assertEquals(SbZdroj.SCENAR, s.zdroj)
        assertEquals(prepis, s.scenar)
        assertEquals(puvodni(s0, SbZdroj.SCENAR).copy(scenar = prepis), s)
        assertTrue(s.panely.isEmpty())
        assertTrue(s.hlasy.isEmpty() && s.vzhled.isEmpty() && s.nalezy.isEmpty())
        assertEquals("", s.kontinuita)
        assertTrue(sbPrechodSeScenarem(s0.zdroj, s.zdroj))
        // Obrázek, děj a jména fotek zůstanou.
        assertEquals(obrazek, s.storyboard)
        assertEquals(s0.dej, s.dej)
        assertEquals(s0.jmenaFotek, s.jmenaFotek)
    }

    @Test
    fun `vymazani prepisu vrati cteni obrazku`() {
        val s0 = plny(SbZdroj.SCENAR)
        val s = sbNastavScenar(s0, "")
        assertEquals(SbZdroj.STORYBOARD, s.zdroj)
        assertEquals(puvodni(s0, SbZdroj.STORYBOARD).copy(scenar = ""), s)
        assertTrue(s.panely.isEmpty())
        assertEquals(0, s.scenarPlanu)
    }

    @Test
    fun `uprava textu plan nemaze, hlida ho otisk`() {
        val s0 = plny(SbZdroj.SCENAR)
        assertNull(sbHlavniKrok(s0))
        // Psaní znak po znaku: zdroj ani plán se nemění.
        var s = s0
        for (c in " Jana: „Čau.“") {
            s = sbNastavScenar(s, s.scenar + c)
            assertEquals(SbZdroj.SCENAR, s.zdroj)
            assertEquals(s0.panely, s.panely)
            assertEquals(s0.hlasy, s.hlasy)
            assertEquals(s0.kontinuita, s.kontinuita)
            assertEquals(s0.zadaniUseku, s.zadaniUseku)
            assertFalse(sbPrechodSeScenarem(s0.zdroj, s.zdroj))
        }
        assertEquals(s0.copy(scenar = s.scenar), s)
        // Změněný text: Natočit blokuje otisk, nabídne se Připravit film.
        assertEquals(SbKrok.PRIPRAVIT, sbHlavniKrok(s))
        assertEquals("Scénář se změnil. Připrav film znovu.", sbFilmProblem(s))
        // Jen bílé znaky na krajích otisk nemění.
        assertNull(sbHlavniKrok(sbNastavScenar(s0, s0.scenar + "\n  ")))
    }

    @Test
    fun `stejny prazdny stav nic nemaze`() {
        val s0 = plny(SbZdroj.STORYBOARD, "")
        assertEquals(s0.copy(scenar = "  "), sbNastavScenar(s0, "  "))
        assertEquals(SbZdroj.STORYBOARD, sbNastavScenar(s0, "  ").zdroj)
    }

    @Test
    fun `z deje text schova, ale nesmaze`() {
        val s = sbVyberVolbu(plny(SbZdroj.SCENAR), SbZdroj.DEJ)
        assertEquals(SbZdroj.DEJ, s.zdroj)
        assertEquals(prepis, s.scenar)
        assertEquals(puvodni(plny(SbZdroj.SCENAR), SbZdroj.DEJ), s)
        // Text uložený pod „z děje“ (např. ze souboru) zdroj nepřepne.
        val d = sbNastavScenar(s, "jiný")
        assertEquals(SbZdroj.DEJ, d.zdroj)
        assertEquals("jiný", d.scenar)
        assertEquals(s.copy(scenar = "jiný"), d)
    }

    @Test
    fun `navrat na storyboard podle textu`() {
        val dej = plny(SbZdroj.DEJ)
        assertEquals(SbZdroj.SCENAR, sbVyberVolbu(dej, SbZdroj.STORYBOARD).zdroj)
        assertEquals(puvodni(dej, SbZdroj.SCENAR), sbVyberVolbu(dej, SbZdroj.STORYBOARD))
        val dejBez = plny(SbZdroj.DEJ, "")
        assertEquals(SbZdroj.STORYBOARD, sbVyberVolbu(dejBez, SbZdroj.STORYBOARD).zdroj)
        assertEquals(puvodni(dejBez, SbZdroj.STORYBOARD), sbVyberVolbu(dejBez, SbZdroj.STORYBOARD))
        // Klepnutí na už vybranou volbu nic nemaže.
        val sc = plny(SbZdroj.SCENAR)
        assertSame(sc, sbVyberVolbu(sc, SbZdroj.STORYBOARD))
        val sb = plny(SbZdroj.STORYBOARD, "")
        assertEquals(sb, sbVyberVolbu(sb, SbZdroj.STORYBOARD))
        val d = plny(SbZdroj.DEJ)
        assertEquals(d, sbVyberVolbu(d, SbZdroj.DEJ))
    }

    @Test
    fun `nacteni - ulozeny prepis je mam storyboard s textem`() {
        val sc = plny(SbZdroj.SCENAR)
        assertEquals(sc, sbNormalizujZdroj(sc))
        assertEquals(SbZdroj.STORYBOARD, sbZdrojVolba(sbNormalizujZdroj(sc)))
        val sb = plny(SbZdroj.STORYBOARD, "")
        assertEquals(sb, sbNormalizujZdroj(sb))
        val dej = plny(SbZdroj.DEJ)
        assertEquals(dej, sbNormalizujZdroj(dej))
        // Starý stav: STORYBOARD se zbytkem textu z dřívější volby → text pryč, plán i cesta zůstanou.
        val stary = plny(SbZdroj.STORYBOARD)
        assertEquals(stary.copy(scenar = ""), sbNormalizujZdroj(stary))
        // SCENAR bez textu → čtení obrázku.
        val prazdny = plny(SbZdroj.SCENAR, "")
        assertEquals(puvodni(prazdny, SbZdroj.STORYBOARD), sbNormalizujZdroj(prazdny))
    }

    @Test
    fun `lze pripravit a hlavni krok - bez prepisu`() {
        val bez = SbFilmScene(zdroj = SbZdroj.STORYBOARD)
        assertFalse(sbLzePripravit(bez))
        assertEquals("Vyber obrázek se storyboardem.", sbFilmProblem(bez))
        val s = sbNastavScenar(bez.copy(storyboard = obrazek), "")
        assertEquals(SbZdroj.STORYBOARD, s.zdroj)
        assertTrue(sbLzePripravit(s))
        assertEquals(SbKrok.PRIPRAVIT, sbHlavniKrok(s))
        assertEquals("Nejdřív připrav film.", sbFilmProblem(s))
        val hotovy = s.copy(panely = listOf(SbPanel(1, "panel 1", "medium", "static", 3.0)), zadaniUseku = listOf("p"))
        assertNull(sbHlavniKrok(hotovy))
        assertNull(sbFilmProblem(hotovy))
    }

    @Test
    fun `lze pripravit a hlavni krok - s prepisem`() {
        val bez = sbNastavScenar(SbFilmScene(zdroj = SbZdroj.STORYBOARD), prepis)
        assertEquals(SbZdroj.SCENAR, bez.zdroj)
        assertFalse(sbLzePripravit(bez))
        assertEquals("Vyber obrázek se storyboardem.", sbFilmProblem(bez))
        val s = bez.copy(storyboard = obrazek)
        assertTrue(sbLzePripravit(s))
        assertEquals(SbKrok.PRIPRAVIT, sbHlavniKrok(s))
        assertEquals("Nejdřív připrav film.", sbFilmProblem(s))
        val hotovy = s.copy(panely = listOf(SbPanel(1, "panel 1", "medium", "static", 3.0)), zadaniUseku = listOf("p"),
            scenarPlanu = otiskScenare(prepis))
        assertNull(sbHlavniKrok(hotovy))
        assertNull(sbFilmProblem(hotovy))
        // Bez promptů jen dopsat.
        assertEquals(SbKrok.NAPSAT, sbHlavniKrok(hotovy.copy(zadaniUseku = emptyList())))
    }

    @Test
    fun `kontinuita - storyboard do deje se meni az navrhem`() {
        // Přepnutí STORYBOARD → DEJ maže jen plán (beze změny proti 5.68);
        // kontinuitu z patičky přepíše na "" až navrhnoutSbZabery (5.67).
        val s = sbVyberVolbu(plny(SbZdroj.STORYBOARD, ""), SbZdroj.DEJ)
        assertEquals(SbZdroj.DEJ, s.zdroj)
        assertTrue(s.panely.isEmpty())
        assertEquals(puvodni(plny(SbZdroj.STORYBOARD, ""), SbZdroj.DEJ), s)
    }
}
