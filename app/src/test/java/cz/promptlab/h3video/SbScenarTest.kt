package cz.promptlab.h3video

import cz.promptlab.h3video.data.LongMmPomer
import cz.promptlab.h3video.data.SbFilmPlan
import cz.promptlab.h3video.data.SbFilmPrepis
import cz.promptlab.h3video.data.SbFilmScene
import cz.promptlab.h3video.data.SbScenar
import cz.promptlab.h3video.data.SbTextStrihu
import cz.promptlab.h3video.data.SbZdroj
import cz.promptlab.h3video.data.sbFilmProblem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Storyboard + scénář (5.14). Hlavní vzor je doslovný scénář uživatele
 * „FOTOŽIJE.CZ“ (30. 9. 2026): 8 oken, dvě repliky, texty na videu, výzva.
 */
class SbScenarTest {

    private val fotozije = File("src/test/resources/scenar_fotozije.txt").readText()

    /** Čtení obrázku bez textu: 8 panelů, typy záběrů (skutečný tvar odpovědi). */
    private val obrazek = SbFilmPlan.precti(
        "TITLE: none | TOTAL: none | SHOTS: none | GRID: 4x2 | VOICES: none | LOOKS: Daughter = dark bob, cream sweater | " +
            "MUSIC: warm, emotional, soft piano and strings, 80 BPM " +
            (1..8).joinToString(" ") { "PANEL $it | none | ${if (it in 4..6) "close-up" else "medium"} | static | shot $it | none" },
    )

    @Test
    fun `scenar uzivatele - okna, repliky, texty`() {
        val s = SbScenar.rozeber(fotozije)!!
        assertEquals("FOTOŽIJE.CZ", s.nazev)
        assertEquals(LongMmPomer.NAVYSKU, s.pomer)
        assertEquals(26.0, s.celkem!!, 1e-9)
        assertEquals(listOf("Dcera", "Maminka"), s.postavy.keys.toList())
        assertEquals("35 let, tmavé mikádo, krémový svetr", s.postavy["Dcera"])
        assertTrue(s.kontinuita.startsWith("Ve všech záběrech je jediná a tatáž stará fotografie"))
        assertEquals((1..8).toList(), s.okna.map { it.cislo })
        assertEquals(listOf(0.0, 2.0, 5.0, 8.0, 11.0, 15.0, 19.0, 23.0), s.okna.map { it.od })
        assertEquals(26.0, s.okna.last().doS!!, 1e-9)
        // Repliky: přesně dvě, správný mluvčí a podání, v okně 2 a 7.
        val repl = s.okna.flatMap { o -> o.repliky.map { o.cislo to it } }
        assertEquals(2, repl.size)
        assertEquals(2, repl[0].first)
        assertEquals("Dcera", repl[0].second.kdo)
        assertEquals("překvapeně", repl[0].second.podani)
        assertEquals("To jsi vážně ty?", repl[0].second.text)
        assertEquals(7, repl[1].first)
        assertEquals("Maminka", repl[1].second.kdo)
        assertEquals("tiše a dojatě", repl[1].second.podani)
        assertEquals("To jsem já…", repl[1].second.text)
        // Zvuk; „Hudba se na okamžik ztiší“ je pokyn pro mix, ne zvuk záběru.
        assertEquals("Šustění stránek.", s.okna[0].zvuk)
        assertEquals("Cvaknutí fotoaparátu.", s.okna[2].zvuk)
        assertEquals("", s.okna[5].zvuk)
        assertTrue(s.okna[5].poznamky.any { it.contains("Hudba se na okamžik ztiší") })
        // Texty na videu a výzva — bez uvozovek, do střihu.
        assertEquals(listOf("Našla jsem mámu."), s.okna[0].texty)
        assertEquals("Oživte svou vzpomínku na fotozije.cz", s.okna[7].vyzva)
        val strih = SbScenar.strih(s)
        assertEquals(5, strih.count { it.druh == SbTextStrihu.Druh.TEXT })
        assertEquals(1, strih.count { it.druh == SbTextStrihu.Druh.VYZVA })
        // Poznámka pro střih z popisu okna 4 do H3 nejde.
        assertFalse(s.okna[3].obraz.contains("záznam obrazovky"))
        assertTrue(s.okna[3].poznamky.any { it.contains("záznam obrazovky") })
        // Detaily telefonu (4–6) jsou obrazovky, okno 7 (ukáže telefon mamince) ne.
        assertEquals(listOf(4, 5, 6), s.okna.filter { it.obrazovka }.map { it.cislo })
        // Emoce ze „Akce a emoce“ zůstávají v popisu.
        assertTrue(SbScenar.popis(s.okna[0]).contains("Zvědavost, náhlé rozpoznání."))
    }

    @Test
    fun `plan - casy ze scenare, rec se nezkrati, tri useky`() {
        val s = SbScenar.rozeber(fotozije)!!
        val plan = SbFilmPlan.naplanuj(SbScenar.cteni(s, obrazek))
        val panely = SbScenar.doplnPanely(plan.panely, s)
        assertTrue(plan.zeStoryboardu)
        assertEquals(listOf(2.0, 3.0, 3.0, 3.0, 4.0, 4.0, 4.0, 3.0), panely.map { it.sekundy })
        // Okno 1 říká „Detail fotografie.“ — typ ze scénáře má přednost; okno 2 typ neuvádí → z obrázku.
        assertEquals("close-up", panely[0].typ)
        assertEquals("medium", panely[1].typ)
        assertEquals("close-up", panely[4].typ)
        assertEquals("Dcera: „To jsi vážně ty?“", panely[1].repliky)
        assertEquals("překvapeně", panely[1].podani)
        // Hlasy podle věku z hlavičky, klíčované jmény ze scénáře.
        assertEquals("a woman in her mid-30s with a warm, natural, medium-pitched voice", plan.hlasy["Dcera"])
        assertEquals("a woman in her early 70s with a soft, gentle, slightly husky voice", plan.hlasy["Maminka"])
        assertEquals("35 let, tmavé mikádo, krémový svetr", plan.vzhled["Dcera"])
        val useky = SbFilmPlan.rozdel(panely)
        assertEquals(3, useky.size)
        assertTrue(useky.all { it.sekundy <= 15.0 })
    }

    private fun scena(): SbFilmScene {
        val s = SbScenar.rozeber(fotozije)!!
        val plan = SbFilmPlan.naplanuj(SbScenar.cteni(s, obrazek))
        return SbFilmScene(
            storyboard = File("storyboard.png"), zdroj = SbZdroj.SCENAR, scenar = fotozije,
            dej = "Zbytek textu z jiné volby.", panely = SbScenar.doplnPanely(plan.panely, s),
            hlasy = plan.hlasy, vzhled = plan.vzhled, kontinuita = s.kontinuita, strih = SbScenar.strih(s),
            scenarPlanu = cz.promptlab.h3video.data.otiskScenare(fotozije), oknaScenare = s.okna.size,
        )
    }

    @Test
    fun `hlidka - podani, zvuk, displej, kontinuita, nic ze strihu`() {
        val sc = scena()
        assertTrue(sc.seStoryboardem)
        assertEquals(listOf("storyboard.png"), sc.uploadImages.map { it.name })
        val sp = SbFilmPrepis
        val useky = sc.useky
        val out = StringBuilder()
        useky.forEachIndexed { k, u ->
            val z = sp.zadani(sc, k, useky.size)
            // Děj z jiné volby do scénáře nepatří.
            assertFalse(z.contains("Zbytek textu"))
            val h = sp.hlidka(
                sc.uploadImages.size, u, k, useky.size, sc.seStoryboardem,
                sp.idMluvcich(sc.panely), sp.jazykFilmu(sc.panely), sc.hlasy,
                predchozi = useky.getOrNull(k - 1)?.panely?.lastOrNull(), vzhled = sc.vzhled, kontinuita = sc.kontinuita,
            )
            assertTrue(h.contains("Continuity for the whole film, keep it in every shot: Ve všech záběrech"))
            // Texty na videu, výzva a poznámky pro střih nikde.
            for (x in listOf("Našla jsem mámu", "Stačí jedna fotka", "Barvy zpátky", "Oživte", "záznam obrazovky", "Hudba se"))
                assertFalse(x, h.contains(x))
            out.append("=== ZADANI $k\n$z\n=== HLIDKA $k$h\n")
        }
        val vse = out.toString()
        assertTrue(vse.contains("Dcera (S1), překvapeně, says <d>[Czech] To jsi vážně ty?</d>"))
        assertTrue(vse.contains("Maminka (S2), tiše a dojatě, says <d>[Czech] To jsem já…</d>"))
        assertTrue(vse.contains("sound in this shot: Šustění stránek."))
        assertTrue(vse.contains("the phone screen shows only pictures, soft colour blocks"))
        assertTrue(vse.contains("Dcera (S1) — a woman in her mid-30s"))
        // Tichý záběr nemá žádnou repliku.
        vse.split("\n[Shot").drop(1).map { z -> z.lines().takeWhile { it == z.lines().first() || it.startsWith("    ") } }
            .filter { z -> z.any { "SILENT SHOT" in it } }.forEach { z -> assertFalse(z.any { "<d>" in it }) }
        File(File("build/sbscenar").also { it.mkdirs() }, "hlidka.txt").writeText(vse)
    }

    @Test
    fun `srt z hotovych delek`() {
        val sc = scena()
        val srt = SbScenar.srt(sc.panely, sc.strih)
        assertTrue(srt.startsWith("1\n00:00:00,000 --> 00:00:02,000\nNašla jsem mámu.\n"))
        assertTrue(srt.contains("Oživte svou vzpomínku na fotozije.cz"))
        assertFalse(srt.contains("záznam obrazovky"))
        assertEquals(6, Regex("""-->""").findAll(srt).count())
    }

    @Test
    fun `co chybi`() {
        val sc = scena()
        assertEquals("Vlož scénář.", sbFilmProblem(sc.copy(scenar = "")))
        assertEquals("Vyber obrázek se storyboardem.", sbFilmProblem(sc.copy(storyboard = null)))
        assertEquals("Nejdřív připrav film.", sbFilmProblem(sc.copy(panely = emptyList())))
        assertEquals("Nejdřív napiš prompty.", sbFilmProblem(sc))
        // Scénář upravený po přečtení: plán je ze starého.
        assertEquals("Scénář se změnil. Připrav film znovu.", sbFilmProblem(sc.copy(scenar = fotozije.replace("To jsi vážně ty?", "Jsi to ty?"))))
        assertEquals("Nejdřív napiš prompty.", sbFilmProblem(sc.copy(scenar = fotozije + "\n\n")))
    }

    @Test
    fun `jine zapisy scenare`() {
        // Anglické štítky, mluvčí velkými písmeny s podáním v závorce, bez časů.
        val en = """
            SCRIPT: Morning
            Format: vertical 9:16, 12 seconds
            Characters: Anna (30 years, red coat); Tom (40 years, grey suit)
            SHOT 1
            Image: Anna opens the door.
            ANNA (smiling): "Good morning!"
            SHOT 2
            Visual: Tom looks up from the newspaper.
            Sound: a cup clinks
            TOM (O.S.): "Morning."
            On-screen text: "Every day"
        """.trimIndent()
        val s = SbScenar.rozeber(en)!!
        assertEquals(2, s.okna.size)
        assertNull(s.okna[0].od)
        assertEquals("Anna", s.okna[0].repliky.single().kdo)
        assertEquals("smiling", s.okna[0].repliky.single().podani)
        assertEquals("Good morning!", s.okna[0].repliky.single().text)
        assertEquals("off-screen voice", s.okna[1].repliky.single().podani)
        assertEquals("a cup clinks", s.okna[1].zvuk)
        assertEquals(listOf("Every day"), s.okna[1].texty)
        assertEquals("a man in his early 40s with a warm, natural, medium-pitched voice", SbScenar.hlasy(s)["Tom"])

        // Záběry na jednom řádku a mluvčí v jiném tvaru než v hlavičce.
        val radek = "Postavy: Dcera (35 let) a maminka (72 let). ZÁBĚR 1 (0–3 s): Obraz: Dcera sedí. Dialog – dcery: „Ahoj.“ " +
            "ZÁBĚR 2 (3–6 s): Obraz: Maminka se otočí. MAMINKA: „Ahoj, holčičko.“"
        val r = SbScenar.rozeber(radek)!!
        assertEquals(2, r.okna.size)
        assertEquals(3.0, r.okna[1].od!!, 1e-9)
        assertEquals("Dcera", r.okna[0].repliky.single().kdo)
        assertEquals("Maminka", r.okna[1].repliky.single().kdo)
        assertEquals("Ahoj, holčičko.", r.okna[1].repliky.single().text)
        assertFalse(r.okna[1].obraz.contains("holčičko"))

        // Číslované odstavce bez štítku okna.
        val cisla = "1. Dcera otevře album.\n2. Najde fotku. Dcera: „To jsi ty?“\n3. Usměje se."
        val c = SbScenar.rozeber(cisla)!!
        assertEquals(3, c.okna.size)
        assertEquals("To jsi ty?", c.okna[1].repliky.single().text)

        // Bez oken → null (rozdělí model).
        assertNull(SbScenar.rozeber("Dcera najde starou fotku a ukáže ji mamince."))
    }

    @Test
    fun `odpoved modelu - repliky jen doslova`() {
        val puvodni = "Dcera listuje albem a řekne: Dcera: „To jsi vážně ty?“ Pak ukáže fotku mamince. Maminka: „To jsem já…“"
        val dobra = "SHOT 1 | none | Dcera listuje albem. | curiosity | Dcera (překvapeně): \"To jsi vážně ty?\" | none | none " +
            "SHOT 2 | none | Ukáže fotku mamince. | none | Maminka: \"To jsem já…\" | none | none"
        val s = SbScenar.zOdpovedi(dobra, puvodni)
        assertNotNull(s)
        assertTrue(s!!.odhadem)
        assertEquals("překvapeně", s.okna[0].repliky.single().podani)
        // Přeložená replika → odmítnout.
        assertNull(SbScenar.zOdpovedi(dobra.replace("To jsi vážně ty?", "Is that really you?"), puvodni))
        // Vynechaná replika → odmítnout.
        assertNull(SbScenar.zOdpovedi(dobra.replace("Maminka: \"To jsem já…\"", "none"), puvodni))
    }

    @Test
    fun `hlasy podle veku`() {
        assertEquals("a girl of about 8 with a light, high child's voice", SbScenar.hlas(true, 8))
        assertEquals("a man in his late 50s with a warm, mature voice", SbScenar.hlas(false, 58))
        assertEquals(true, SbScenar.zena("Maminka", "72 let"))
        assertEquals(false, SbScenar.zena("Děda", "80 let"))
    }
}
