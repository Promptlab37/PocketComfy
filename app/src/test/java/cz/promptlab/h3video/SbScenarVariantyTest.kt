package cz.promptlab.h3video

import cz.promptlab.h3video.data.LongMmPomer
import cz.promptlab.h3video.data.SbFilmPlan
import cz.promptlab.h3video.data.SbFilmPrepis
import cz.promptlab.h3video.data.SbFilmScene
import cz.promptlab.h3video.data.SbScenar
import cz.promptlab.h3video.data.SbScenarCteni
import cz.promptlab.h3video.data.SbZdroj
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Storyboard + scénář (5.14): druhý scénář uživatele (svatební fotka, 30. 9.
 * 2026) a 24 zápisů, jak je píše ChatGPT (sestavil kritik).
 */
class SbScenarVariantyTest {

    private val varianty: Map<String, String> =
        File("src/test/resources/scenar_varianty.txt").readText().split("=====").map { it.trim() }
            .filter { it.isNotBlank() }.associate { it.lineSequence().first().substringBefore(' ') to it.substringAfter("\n") }

    private fun v(klic: String): SbScenarCteni = SbScenar.rozeber(varianty.getValue(klic))!!

    @Test
    fun `svatba - okna s nazvem, podani za jmenem, pokyn pro cely film`() {
        val s = SbScenar.rozeber(File("src/test/resources/scenar_svatba.txt").readText())!!
        assertEquals("", s.nazev)
        assertEquals(LongMmPomer.NAVYSKU, s.pomer)
        assertEquals(listOf(0.0, 4.0, 7.0, 11.0), s.okna.map { it.od })
        // Věty o fotografii z řádku Formát a „Pokyn pro převod…“ platí pro celý film.
        assertTrue(s.kontinuita.startsWith("Ve všech oknech je stejná svatební fotografie."))
        assertTrue(s.kontinuita.contains("Pohyb nevěsty probíhá výhradně uvnitř fotografie na displeji."))
        assertFalse(s.okna[3].obraz.contains("Pohyb nevěsty"))
        // Repliky: „Syn tiše:“ uprostřed řádku Zvuk, „Otec tiše:“ na vlastním řádku.
        val r = s.okna.flatMap { o -> o.repliky.map { o.cislo to it } }
        assertEquals(2, r.size)
        assertEquals(1 to "Syn", r[0].first to r[0].second.kdo)
        assertEquals("tiše", r[0].second.podani)
        assertEquals("Tati, podívej.", r[0].second.text)
        assertEquals(4 to "Otec", r[1].first to r[1].second.kdo)
        assertEquals("Na ten den si pamatuju.", r[1].second.text)
        assertEquals("šustění fotografií.", s.okna[0].zvuk)
        assertFalse(s.okna[0].obraz.contains("Tati"))
        // Detail rukou → typ; displej v okně 3.
        assertEquals("close-up", s.okna[1].typ)
        assertEquals(listOf(3), s.okna.filter { it.obrazovka }.map { it.cislo })
        assertEquals(listOf("Jedna fotka. Barvy a pohyb."), s.okna[2].texty)
        assertEquals("Oživte své vzpomínky. fotozije.cz", s.okna[3].vyzva)
        // Hlasy bez věku v hlavičce: podle role (otec vedle syna je starší).
        val h = SbScenar.hlasy(s)
        assertEquals("a man in his mid-30s with a warm, natural, medium-pitched voice", h["Syn"])
        assertEquals("a man in his late 60s with a soft, gentle, slightly husky voice", h["Otec"])
        // Plán: 4 + 3 + 4 + 4 s, dva úseky.
        val plan = SbFilmPlan.naplanuj(SbScenar.cteni(s, null))
        assertEquals(listOf(4.0, 3.0, 4.0, 4.0), plan.panely.map { it.sekundy })
        assertEquals(2, SbFilmPlan.rozdel(plan.panely).size)
        // Prompt: web ani výzva se do H3 nedostanou.
        val sc = SbFilmScene(
            storyboard = File("s.png"), zdroj = SbZdroj.SCENAR, panely = SbScenar.doplnPanely(plan.panely, s),
            hlasy = plan.hlasy, kontinuita = s.kontinuita,
        )
        sc.useky.forEachIndexed { k, u ->
            val t = SbFilmPrepis.hlidka(
                1, u, k, sc.useky.size, true, SbFilmPrepis.idMluvcich(sc.panely), SbFilmPrepis.jazykFilmu(sc.panely),
                sc.hlasy, kontinuita = sc.kontinuita,
            )
            assertFalse(t.contains("fotozije"))
            assertFalse(t.contains("Oživte"))
            assertFalse(t.contains("Jedna fotka"))
        }
    }

    /** Skutečné čtení obrázku ke svatebnímu scénáři (server 30. 9. 2026). */
    @Test
    fun `svatba - vzhled z obrazku pod jmeny ze scenare`() {
        val s = SbScenar.rozeber(File("src/test/resources/scenar_svatba.txt").readText())!!
        val obrazek = SbFilmPlan.precti(
            "TITLE: none | TOTAL: none | SHOTS: 4 | GRID: 2x2 | VOICES: Young Man = a man in his 30s with a warm, neutral voice; " +
                "Elderly Man = an elderly man with a gentle, soft voice | LOOKS: Young Man = brown hair, green shirt; Elderly Man = " +
                "white hair, red sweater | MUSIC: nostalgic, sentimental, piano and strings, 60 BPM PANEL 1 | none | medium | static | " +
                "A young man shows an elderly man a cracked wedding photo from a box. | none PANEL 2 | none | close-up | static | " +
                "The young man takes a photo of the cracked wedding photo with his phone. | none PANEL 3 | none | close-up | static | " +
                "The young man displays a restored version of the wedding photo on his phone. | none PANEL 4 | none | medium | static | " +
                "The young man shows the restored wedding photo to the elderly man on his phone. | none",
        )
        assertEquals(
            mapOf("Syn" to "brown hair, green shirt", "Otec" to "white hair, red sweater"),
            SbScenar.vzhledProMluvci(obrazek.vzhled, s),
        )
        // Počet panelů sedí → prompty se píšou hned.
        assertEquals(s.okna.size, obrazek.panely.size)
        // Typ ze scénáře (Detail rukou) přebije obrázek, jinak obrázek.
        val plan = SbFilmPlan.naplanuj(SbScenar.cteni(s, obrazek))
        assertEquals(listOf("medium", "close-up", "close-up", "medium"), plan.panely.map { it.typ })
    }

    /** Skutečný přepis úseku 2 svatebního filmu (30. 9. 2026): jazyk a replika mimo <d>. */
    @Test
    fun `svatba - cestina z celeho scenare a citace repliky pryc`() {
        val text = File("src/test/resources/scenar_svatba.txt").readText()
        val s = SbScenar.rozeber(text)!!
        val plan = SbFilmPlan.naplanuj(SbScenar.cteni(s, null))
        val sc = SbFilmScene(storyboard = File("s.png"), zdroj = SbZdroj.SCENAR, scenar = text,
            panely = SbScenar.doplnPanely(plan.panely, s))
        // Repliky samy češtinu neprozradí, scénář ano.
        assertNull(SbFilmPrepis.jazykFilmu(sc.panely))
        assertEquals("Czech", SbFilmPrepis.jazykFilmu(sc))
        // Jiné volby beze změny.
        assertNull(SbFilmPrepis.jazykFilmu(sc.copy(zdroj = SbZdroj.STORYBOARD)))
        val prepis = File("src/test/resources/prepis_svatba_usek2.txt").readText()
        val cisty = SbFilmPrepis.ocistiPrepis(prepis, 2)
        assertEquals(1, Regex("Na ten den si pamatuju").findAll(cisty).count())
        assertTrue(cisty.contains("<d>[Language] Na ten den si pamatuju.</d>"))
    }

    /** 5.27: kontrola po přepisovači — replika úseku musí být v promptu v <d>. */
    @Test
    fun `po prepisovaci - chybejici replika`() {
        val text = File("src/test/resources/scenar_svatba.txt").readText()
        val s = SbScenar.rozeber(text)!!
        val plan = SbFilmPlan.naplanuj(SbScenar.cteni(s, null))
        val useky = SbFilmPlan.rozdel(SbScenar.doplnPanely(plan.panely, s))
        val prepis = File("src/test/resources/prepis_svatba_usek2.txt").readText()
        assertEquals(emptyList<String>(), SbFilmPrepis.chybejiciRepliky(prepis, useky[1]))
        val bez = prepis.replace("<d>[Language] Na ten den si pamatuju.</d>", "")
        assertEquals(listOf("Na ten den si pamatuju."), SbFilmPrepis.chybejiciRepliky(bez, useky[1]))
    }

    @Test
    fun `markdown, emoji, pomlcky a nadpisy`() {
        v("V1").let {
            assertEquals("Dcera listuje albem.", it.okna[0].obraz)
            assertEquals(listOf("Našla jsem mámu."), it.okna[0].texty)
            assertEquals("To jsi ty?", it.okna[0].repliky.single().text)
        }
        v("V6").let { assertEquals("Dcera listuje albem.", it.okna[0].obraz); assertEquals("", it.okna[1].zvuk) }
        v("V5").let { assertEquals("tiše", it.okna[1].repliky.single().podani); assertEquals("Maminka se usměje.", it.okna[1].obraz) }
        v("V7").let { assertEquals(3.0, it.okna[1].od!!, 1e-9); assertEquals("Dcera listuje albem.", it.okna[0].obraz) }
        v("W2").let { assertEquals(2.0, it.okna[0].doS!!, 1e-9); assertEquals("Dcera listuje albem.", it.okna[0].obraz) }
        v("W3").let { assertEquals(2, it.okna.size) }
        v("W6").let { assertEquals(2.5, it.okna[1].od!!, 1e-9) }
        v("W7").let { assertEquals("A.", it.okna[0].obraz) }
        v("W10").let { assertEquals(listOf("close-up", "medium"), it.okna.map { o -> o.typ }); assertEquals("Dcera listuje albem.", it.okna[0].obraz) }
        v("V12").let { assertEquals(listOf(0.0, 3.0), it.okna.map { o -> o.od }) }
    }

    @Test
    fun `repliky ve vsech tvarech`() {
        // Filmový zápis bez uvozovek.
        v("V3").let {
            assertEquals("To jsi ty?", it.okna[0].repliky.single().text)
            assertEquals("dojatě", it.okna[1].repliky.single().podani)
            assertFalse(it.okna[0].obraz.contains("To jsi"))
        }
        // Dva řádky pod jedním štítkem Dialog se neslijí.
        v("V9").okna[0].repliky.let { assertEquals(listOf("Dcera", "Maminka"), it.map { r -> r.kdo }); assertEquals("Ano, to jsem já.", it[1].text) }
        v("V10").let {
            assertEquals(2, it.okna[0].repliky.size)
            assertEquals("off-screen voice", it.okna[1].repliky.single().podani)
        }
        v("W1").okna[0].repliky.single().let { assertEquals("Dcera", it.kdo) }
        v("W4").let {
            assertEquals("Vypravěč", it.okna[0].repliky.single().kdo)
            assertEquals("Dcera", it.okna[1].repliky.single().kdo)
            assertEquals("klidně, off-screen voice", it.okna[2].repliky.single().podani)
        }
        v("W5").let {
            assertEquals("Dcera", it.okna[0].repliky.single().kdo)
            assertEquals("", it.okna[0].repliky.single().podani)
            assertEquals("Maminka", it.okna[1].repliky.single().kdo)
        }
        v("W8").okna[1].repliky.single().let { assertEquals("Říkali mi \"Květa\".", it.text) }
        v("V4").let { assertEquals("Woman", it.okna[0].repliky.single().kdo); assertEquals(listOf("I found mom."), it.okna[0].texty) }
        v("V13").let { assertEquals(3, it.okna.size); assertEquals(listOf("Oživte vzpomínky"), it.okna[2].texty) }
    }

    @Test
    fun `texty a loga nejsou repliky ani obraz`() {
        v("W9").let {
            assertTrue(it.okna.all { o -> o.repliky.isEmpty() })
            assertEquals(listOf("Našla jsem mámu"), it.okna[0].texty)
            assertEquals("fotozije.cz", it.okna[1].vyzva)
            assertEquals(listOf("Vraťte fotkám život"), it.okna[1].texty)
            assertEquals("", it.okna[1].obraz)
        }
        v("V11").let {
            assertTrue(it.okna[2].repliky.isEmpty())
            assertEquals(listOf("fotozije.cz"), it.okna[2].texty)
            assertEquals("", it.okna[2].obraz)
            assertTrue(it.okna[2].poznamky.contains("Logo."))
        }
        v("W11").let { assertEquals("A.", it.okna[0].obraz); assertEquals(listOf("Našla jsem mámu"), it.okna[0].texty) }
        // Štítek uprostřed věty štítkem není; „Dialog – žádný“ zmizí.
        v("V8").let {
            assertTrue(it.okna[1].obraz.contains("že je to super: vypadá mladě."))
            assertTrue(it.okna[0].obraz.contains("na obrazovce je text: nápis v aplikaci."))
            assertFalse(it.okna[0].obraz.contains("Dialog"))
            assertTrue(it.okna[1].poznamky.contains("logo vpravo."))
            assertEquals("Maminka se směje.", it.okna[1].akce)
        }
    }

    /** Druhý svatební scénář (30. 9. 2026): „Text ve videu:“ zůstal v popisu jako „Text ve videu:.“. */
    @Test
    fun `text ve videu je text do strihu`() {
        val s = SbScenar.rozeber(
            "[OKNO 1 | 0–4 s]\nObraz: Telefon leží vedle fotografie. Text ve videu: „Barvy zpátky.“\n" +
                "[OKNO 2 | 4–7 s]\nObraz: Detail displeje.\nText ve videu: „A malý okamžik pohybu.“\n" +
                "[OKNO 3 | 7–9 s]\nObraz: Nápis na zdi „Sláva“. Titulek na konci: „Ahoj“.",
        )!!
        assertEquals("Telefon leží vedle fotografie.", s.okna[0].obraz)
        assertEquals(listOf("Barvy zpátky."), s.okna[0].texty)
        assertEquals("Detail displeje.", s.okna[1].obraz)
        assertEquals(listOf("A malý okamžik pohybu."), s.okna[1].texty)
        assertFalse(s.okna.any { it.obraz.contains("Text ve videu") || it.obraz.endsWith(":.") })
        assertFalse(s.okna[2].obraz.contains("Titulek na konci"))
    }

    @Test
    fun `web, logo a text v uvozovkach z popisu pryc`() {
        val s = SbScenar.rozeber(
            "[OKNO 1 | 0–3 s]\nObraz: Detail telefonu. Dcera nahraje fotku na fotozije.cz a na displeji svítí nápis „Hotovo“.\n" +
                "[OKNO 2 | 3–6 s]\nAkce: Klidný závěrečný záběr s prostorem pro logo.",
        )!!
        assertEquals("Detail telefonu. Dcera nahraje fotku na web a na displeji svítí nápis.", s.okna[0].obraz)
        assertTrue(s.okna[0].poznamky.contains("Hotovo"))
        assertEquals("Klidný závěrečný záběr s volným místem v horní části obrazu.", s.okna[1].akce)
        assertTrue(s.okna[1].poznamky.any { it.contains("logo") })
    }

    @Test
    fun `postavy v ruznych zapisech a hlasy`() {
        fun p(t: String) = SbScenar.postavyZ(t).map { it.jmeno to it.zena }
        assertEquals(listOf("Dcera" to true, "Maminka" to true), p("Dcera – 35 let (tmavé mikádo, svetr)\nMaminka – 72 let (šedé vlasy)"))
        assertEquals(listOf("Dcera" to true, "Maminka" to true), p("- **Dcera** (35 let, mikádo)\n- **Maminka** (72 let)"))
        assertEquals(listOf("Honza" to false, "Kuba" to false), p("Honza (40 let, vousy) a jeho syn Kuba (8 let)"))
        assertEquals(listOf("Jana" to true, "Věra" to true), p("Jana, 35 let, mikádo; Věra, 72 let, šedé vlasy"))
        assertEquals("35 let, tmavé mikádo, svetr", SbScenar.postavyZ("Dcera – 35 let (tmavé mikádo, svetr)").single().popis)
    }

    @Test
    fun `bez oken rozdeli model`() {
        assertNull(SbScenar.rozeber("Dcera najde starou fotku a ukáže ji mamince."))
        // Filmový zápis bez oken: repliky se najdou, model musí mít všechny celé.
        val puvodni = "Dcera listuje albem.\nDCERA: To jsi vážně ty?\nMaminka se usměje.\nMAMINKA (dojatě): To jsem já…"
        val dobra = "SHOT 1 | none | Dcera listuje albem. | none | Dcera: \"To jsi vážně ty?\" | none | none\n" +
            "SHOT 2 | none | Maminka se usměje. | none | Maminka (dojatě): \"To jsem já…\" | none | none"
        assertEquals(2, SbScenar.zOdpovedi(dobra, puvodni)!!.okna.size)
        // Zkrácená replika neprojde.
        assertNull(SbScenar.zOdpovedi(dobra.replace("To jsi vážně ty?", "To jsi"), puvodni))
        // Chybějící replika neprojde.
        assertNull(SbScenar.zOdpovedi(dobra.replace("Maminka (dojatě): \"To jsem já…\"", "none"), puvodni))
    }
}
