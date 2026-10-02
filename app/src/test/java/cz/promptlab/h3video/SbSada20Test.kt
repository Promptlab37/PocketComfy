package cz.promptlab.h3video

import cz.promptlab.h3video.data.SbCteni
import cz.promptlab.h3video.data.SbCteniTok
import cz.promptlab.h3video.data.SbFilmPlan
import cz.promptlab.h3video.data.SbFilmPrepis
import cz.promptlab.h3video.data.SbPrecteny
import cz.promptlab.h3video.data.SbUsek
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Opravy čtení ze sady 20 storyboardů a listu BYTY (5.69, 2. 10. 2026). Bez obrázků a bez serveru:
 * přepisy řádků jsou přesně to, co je na listech vytištěné (sb_korpus/byty, wellness).
 */
class SbSada20Test {

    /** Řádky BYTY tak, jak je čtení řádku opíše (uzel slučuje řádky do jednoho). */
    private val bytyRadek1 = "PANEL 1 1. 0–2 s | Úvodní záběr „Až dnes odejdeme, všichni koupíme co?“ PANEL 2 2. 2–6 s | " +
        "Odpověď a počet „BYTY! Ne jeden, ne dva — rovnou TŘI!“ PANEL 3 3. 6–9 s | Proč? „A proč? Protože byty rostou DO NEBES!“"
    private val bytyRadek2 = "PANEL 4 4. 9–12 s | Ukázka růstu (ukazuje po šipce nahoru) PANEL 5 5. 12–14 s | Říkanka (společně) " +
        "„A teď všichni: Kdo byty kupuje — kdo váhá a čeká, LITUJE!“ PANEL 6 6. 14–15 s | Závěr (vítězný postoj, tleskání třídy)"

    // ---------------------------------------------------------------- závorky: děj, nebo nálada

    @Test
    fun `samostatna zavorka je dej, ne nalada`() {
        val p = SbFilmPlan.prevedPrepis(bytyRadek2)
        assertEquals(mapOf(4 to "ukazuje po šipce nahoru", 6 to "vítězný postoj, tleskání třídy"), SbFilmPlan.prectiDeje(p))
        assertEquals(emptyMap<Int, String>(), SbFilmPlan.prectiNalady(p))
        // Bez nadpisů (jen text pod obrázkem) stejně.
        val bez = SbFilmPlan.prevedPrepis("PANEL 4 (ukazuje po šipce nahoru) PANEL 5 „A teď všichni: Kdo byty kupuje — kdo váhá a čeká, LITUJE!“ PANEL 6 (vítězný postoj, tleskání třídy)")
        assertEquals(setOf(4, 6), SbFilmPlan.prectiDeje(bez).keys)
        assertEquals(emptyMap<Int, String>(), SbFilmPlan.prectiNalady(bez))
    }

    @Test
    fun `zavorka za replikou zustava nalada - Wellness`() {
        val p = SbFilmPlan.prevedPrepis(
            "PANEL 1 DĚJ: Žena nadšeně kouká do telefonu, právě dostala zprávu. ŽENA: „Podívej, vyhráli jsme víkend v wellnessu!“ " +
                "(nadšená, radostná, široký úsměv) PANEL 2 DĚJ: Muž se zvědavě podívá na telefon. MUŽ: „Opravdu? A jak jsi to vyhrála?“ " +
                "(překvapený, skeptický, zvednuté obočí)",
        )
        assertEquals(mapOf(1 to "nadšená, radostná, široký úsměv", 2 to "překvapený, skeptický, zvednuté obočí"), SbFilmPlan.prectiNalady(p))
        assertEquals(mapOf(1 to "Žena nadšeně kouká do telefonu, právě dostala zprávu.", 2 to "Muž se zvědavě podívá na telefon."), SbFilmPlan.prectiDeje(p))
        // Podání mezi jménem a replikou je taky nálada.
        val q = SbFilmPlan.prevedPrepis("PANEL 1 (šeptem) „Je tu někdo?“")
        assertEquals(mapOf(1 to "šeptem"), SbFilmPlan.prectiNalady(q))
        assertEquals(emptyMap<Int, String>(), SbFilmPlan.prectiDeje(q))
    }

    @Test
    fun `vyslovna emoce ma prednost pred zavorkou`() {
        val p = SbFilmPlan.prevedPrepis("PANEL 1 Obraz: Anna najde hodinky. Emoce: Zvědavost. Anna: „Komu ses ztratily?“ (tiše)")
        assertEquals(mapOf(1 to "Zvědavost."), SbFilmPlan.prectiNalady(p))
        assertEquals(mapOf(1 to "Anna najde hodinky."), SbFilmPlan.prectiDeje(p))
    }

    // ---------------------------------------------------------------- nadpis panelu

    @Test
    fun `nadpis za casem je titulek, ne dej`() {
        val p = SbFilmPlan.prevedPrepis(bytyRadek1)
        assertEquals(mapOf(1 to "Úvodní záběr", 2 to "Odpověď a počet", 3 to "Proč?"), SbFilmPlan.prectiNadpisy(p))
        // „Odpověď a počet“ má tři slova — do 5.68 z něj byl děj.
        assertEquals(emptyMap<Int, String>(), SbFilmPlan.prectiDeje(p))
        assertEquals(
            mapOf(1 to "\"Až dnes odejdeme, všichni koupíme co?\"", 2 to "\"BYTY! Ne jeden, ne dva — rovnou TŘI!\"",
                3 to "\"A proč? Protože byty rostou DO NEBES!\""),
            SbFilmPlan.prectiRepliky(p),
        )
        assertEquals("A man cheers. Title: Opening shot.", SbFilmPlan.doplnNadpis("A man cheers.", "Opening shot"))
    }

    @Test
    fun `cas a stitek Obraz nadpis nedelaji`() {
        // Robot hledá domov: „0–4 s | Obraz: …“ — za svislítkem je štítek, ne nadpis.
        val p = SbFilmPlan.prevedPrepis("PANEL 1 0–4 s | Obraz: Robot R7 stojí v dešti u vyřazené elektroniky. Emoce: Zvědavost. R7: „Úloha dokončena.“")
        assertEquals(emptyMap<Int, String>(), SbFilmPlan.prectiNadpisy(p))
        assertEquals(mapOf(1 to "Robot R7 stojí v dešti u vyřazené elektroniky."), SbFilmPlan.prectiDeje(p))
    }

    // ---------------------------------------------------------------- společně

    @Test
    fun `spolecne v nadpisu je sbor`() {
        val p = SbFilmPlan.prevedPrepis(bytyRadek2)
        assertEquals(setOf(5), SbFilmPlan.prectiSbor(p))
        assertEquals("Říkanka", SbFilmPlan.prectiNadpisy(p)[5])
        // Replika zůstane celá, „společně“ do ní ani do nálady nepatří.
        assertEquals("\"A teď všichni: Kdo byty kupuje — kdo váhá a čeká, LITUJE!\"", SbFilmPlan.prectiRepliky(p)[5])
        assertNull(SbFilmPlan.prectiNalady(p)[5])
    }

    // ---------------------------------------------------------------- mluvčí bez jména

    @Test
    fun `dvojtecka v uvozovkach neni mluvci`() {
        val r = "\"A teď všichni: Kdo byty kupuje — kdo váhá a čeká, LITUJE!\""
        assertEquals(emptyList<Pair<String, String>>(), SbFilmPrepis.repliky(r))
        assertEquals(
            listOf(SbFilmPlan.VYCHOZI_MLUVCI to "A teď všichni: Kdo byty kupuje — kdo váhá a čeká, LITUJE!"),
            SbFilmPrepis.repliky(SbFilmPrepis.sloucit(r, r)),
        )
        // Celé čtení bez jména: replika se nerozpadne na mluvčího „A teď všichni“.
        val c = SbFilmPlan.precti("TITLE: none | GRID: 2x3\nPANEL 5 | 12-14 s | medium | static | man chants with class | $r")
        assertTrue(SbFilmPrepis.repliky(c.panels5()).none { it.first == "A teď všichni" })
    }

    private fun SbCteni.panels5() = panely.first().repliky

    @Test
    fun `radek bez jmena se nezahodi kvuli celemu cteni`() {
        // Celé čtení si jméno vymyslelo a text přečetlo hůř; řádek má text přesně, jen bez jména.
        val s = SbFilmPrepis.sloucit("Muž: \"Až dnes odejdem, všichni koupíme co?\"", "\"Až dnes odejdeme, všichni koupíme co?\"")
        assertEquals(listOf("Muž" to "Až dnes odejdeme, všichni koupíme co?"), SbFilmPrepis.repliky(s))
        // Pojmenované čtení řádku zůstává jako dřív.
        val t = SbFilmPrepis.sloucit("AI: \"Objednávám obývací stůl.\"", "Al: \"Objednávám obývací stůl.\"")
        assertEquals(listOf("AI" to "Objednávám obývací stůl."), SbFilmPrepis.repliky(t))
    }

    @Test
    fun `jedina postava bez jmena ma jednoho mluvciho`() {
        val radky = mapOf(1 to "\"Až dnes…\"", 2 to "\"BYTY!\"", 4 to "")
        fun cteni(jmena: List<String>, hlasy: Map<String, String> = emptyMap(), vzhled: Map<String, String> = emptyMap()) =
            SbCteni(null, null, null, jmena.mapIndexed { i, j -> SbPrecteny(i + 1, null, null, "", "", "x", "$j: \"r$i\"") }, hlasy = hlasy, vzhled = vzhled)
        // Jediné vymyšlené jméno, nebo víc jmen a jediný vzhled.
        assertTrue(SbCteniTok.jedenMluvciBezJmena(cteni(listOf("Muž", "Muž")), radky))
        assertTrue(SbCteniTok.jedenMluvciBezJmena(cteni(listOf("Muž", "Učitel"), vzhled = mapOf("Muž" to "glasses")), radky))
        // Víc postav (Praotec: Čech, Lech) — jména z celého čtení zůstanou.
        assertFalse(SbCteniTok.jedenMluvciBezJmena(cteni(listOf("Čech", "Lech"), mapOf("Čech" to "a", "Lech" to "b"), mapOf("Čech" to "a", "Lech" to "b")), radky))
        // Jméno vytištěné u repliky → nic se nemění.
        assertFalse(SbCteniTok.jedenMluvciBezJmena(cteni(listOf("Muž")), radky + (3 to "Pepa: \"Ahoj\"")))
    }

    @Test
    fun `prejmenovani hlasu a vzhledu vymysleneho jmena`() {
        val t = "TITLE: none | VOICES: Muž = a man in his 40s; Učitel = a cheerful man | LOOKS: Muž = glasses; Třída = students | MUSIC: pop"
        val n = SbFilmPlan.prejmenujMluvciho(t, setOf("Muž", "Učitel"), SbFilmPlan.VYCHOZI_MLUVCI)
        val c = SbFilmPlan.precti(n)
        assertEquals(mapOf(SbFilmPlan.VYCHOZI_MLUVCI to "a man in his 40s"), c.hlasy)
        assertEquals(mapOf(SbFilmPlan.VYCHOZI_MLUVCI to "glasses", "Třída" to "students"), c.vzhled)
        assertEquals("pop", c.hudbaStyl)
    }

    // ---------------------------------------------------------------- celý tok BYTY bez serveru

    /** Model, který vrací to, co je na listu BYTY vytištěné; celé čtení si jména vymyslí. */
    private inner class BytyModel : SbCteniTok.Model {
        val otazky = mutableListOf<String>()
        override suspend fun nahraj(png: ByteArray, nazev: String) = nazev
        override suspend fun precti(jmeno: String, otazka: String, krok: Int): String {
            otazky += otazka
            return when {
                otazka == SbFilmPlan.OTAZKA_CTENI ->
                    "TITLE: none | TOTAL: 15 | SHOTS: 6 | GRID: 2x3 | VOICES: Muž = a man in his 40s with a loud voice; " +
                        "Učitel = a cheerful man | LOOKS: Muž = short brown hair, glasses, blue and yellow cheerleader outfit | " +
                        "MUSIC: upbeat pop | CONTINUITY: none " +
                        "PANEL 1 | 0-2 s | wide | static | A man in a cheerleader outfit at a whiteboard | Muž: \"Až dnes odejdem, všichni koupíme co?\" " +
                        "PANEL 2 | 2-6 s | medium | static | He shows three fingers | Učitel: \"BYTY! Ne jeden, ne dva — rovnou TŘI!\" " +
                        "PANEL 3 | 6-9 s | medium | static | He points the pompom up | Muž: \"A proč? Protože byty rostou DO NEBES!\" " +
                        "PANEL 4 | 9-12 s | medium | static | He points along the arrow | none " +
                        "PANEL 5 | 12-14 s | close-up | static | He chants | \"A teď všichni: Kdo byty kupuje — kdo váhá a čeká, LITUJE!\" " +
                        "PANEL 6 | 14-15 s | medium | static | He raises the pompom | none"
                "numbered 1 to 3" in otazka -> bytyRadek1
                "numbered 4 to 6" in otazka -> bytyRadek2
                otazka.startsWith("This image is a film storyboard. Translate") ->
                    "ACTION 4 = He points up along the arrow. CAMERA 4 = none ACTION 6 = A victorious pose, the class applauds. " +
                        "CAMERA 6 = none TITLE 1 = Opening shot TITLE 2 = The answer and the count TITLE 3 = Why? " +
                        "TITLE 4 = Showing the growth TITLE 5 = The chant TITLE 6 = Ending"
                else -> ""
            }
        }
    }

    private object BytyObraz : SbCteniTok.Obraz {
        override val sirka = 1536
        override val vyska = 1024
        override fun original() = ByteArray(0)
        override fun vyrezPng(x0: Int, y0: Int, x1: Int, y1: Int, meritko: Float) = ByteArray(0)
        override fun bunky(): List<cz.promptlab.h3video.data.SbPanelyObrazu.Bunka>? = null
    }

    @Test
    fun `BYTY celym tokem cteni`() {
        val model = BytyModel()
        val v = runBlocking { SbCteniTok.precti(BytyObraz, model, object : SbCteniTok.Prubeh { override fun krokNavic() = 0 }) }
        assertTrue(v.jedenMluvci)
        // Hlas byl u vymyšleného jména — nový dotaz na hlas není potřeba.
        assertTrue(model.otazky.none { it.contains("These characters speak in it") })
        val s = SbCteniTok.sestav(v)
        val panely = s.plan.panely
        // Jeden stálý mluvčí, text replik z řádků (celé čtení mělo „odejdem“).
        assertEquals(
            listOf(
                listOf(SbFilmPlan.VYCHOZI_MLUVCI to "Až dnes odejdeme, všichni koupíme co?"),
                listOf(SbFilmPlan.VYCHOZI_MLUVCI to "BYTY! Ne jeden, ne dva — rovnou TŘI!"),
                listOf(SbFilmPlan.VYCHOZI_MLUVCI to "A proč? Protože byty rostou DO NEBES!"),
                emptyList(),
                listOf(SbFilmPlan.VYCHOZI_MLUVCI to "A teď všichni: Kdo byty kupuje — kdo váhá a čeká, LITUJE!"),
                emptyList(),
            ),
            panely.map { SbFilmPrepis.repliky(it.repliky) },
        )
        assertEquals(mapOf(SbFilmPlan.VYCHOZI_MLUVCI to "a man in his 40s with a loud voice"), s.plan.hlasy)
        assertEquals(mapOf(SbFilmPlan.VYCHOZI_MLUVCI to "short brown hair, glasses, blue and yellow cheerleader outfit"), s.plan.vzhled)
        // Závorka = děj, nadpis = nápověda, ne děj.
        assertEquals(mapOf(4 to "ukazuje po šipce nahoru", 6 to "vítězný postoj, tleskání třídy"), v.dejeOpis)
        assertEquals(emptyMap<Int, String>(), v.naladyOpis)
        assertEquals("He points up along the arrow. Title: Showing the growth.", panely[3].popis)
        assertEquals("A man in a cheerleader outfit at a whiteboard Title: Opening shot.", panely[0].popis)
        // Říkanka společně: podání u repliky, replika beze změny.
        assertEquals(SbFilmPlan.PODANI_SBOR, panely[4].podani)
        assertTrue(panely.filter { it.cislo != 5 }.all { it.podani.isEmpty() })
        // Vytištěné časy; prodloužené jen panely 1 a 5, kam se replika nevejde (12 a 19 slabik do 2 s).
        assertEquals(listOf(3.2, 4.0, 3.0, 3.0, 4.5, 1.0), panely.map { it.sekundy })
    }

    @Test
    fun `spolecna replika v zadani prepisovace a kontrole`() {
        val v = runBlocking { SbCteniTok.precti(BytyObraz, BytyModel(), object : SbCteniTok.Prubeh { override fun krokNavic() = 0 }) }
        val panely = SbCteniTok.sestav(v).plan.panely
        val u = SbUsek(panely.filter { it.cislo == 5 })
        val hlidka = SbFilmPrepis.hlidka(1, u, 0, 1, jazykFilmu = SbFilmPrepis.jazykFilmu(panely))
        assertTrue(hlidka, hlidka.contains(
            "Mluvčí (S1), ${SbFilmPlan.PODANI_SBOR}, says <d>[Czech] A teď všichni: Kdo byty kupuje — kdo váhá a čeká, LITUJE!</d>",
        ))
        // Přepisovač napíše repliku jednou do <d> s podáním mimo ni — kontrola zadání ji přijme.
        val prompt = "subject_definitions: <Subject 1> a man.\nsummary: A chant.\nretention_analysis: none.\n" +
            "detailed_description: [Shot 1] <Subject 1> (S1), together with the whole class, all in unison, chants " +
            "<d>[Czech] A teď všichni: Kdo byty kupuje — kdo váhá a čeká, LITUJE!</d>\n" +
            "overall_soundscape: classroom.\nnon_diegetic_music: none."
        assertEquals(emptyList<String>(), cz.promptlab.h3video.data.SbKontrolaPromptu.zkontroluj(prompt, u))
        // Vynucení replik ji nechá jednou a doslova.
        val vynuceno = cz.promptlab.h3video.data.SbVynuceni.vynut(prompt, u, "Czech", mapOf(SbFilmPlan.VYCHOZI_MLUVCI to "S1"))
        assertEquals(1, Regex("<d>").findAll(vynuceno).count())
        assertTrue(vynuceno.contains("all in unison, chants <d>[Czech] A teď všichni: Kdo byty kupuje — kdo váhá a čeká, LITUJE!</d>"))
    }

    // ---------------------------------------------------------------- časy

    @Test
    fun `vytistene casy nad 45 s se nekrati`() {
        // Start za minutu: 12 × 4 s = 48 s; tiché panely dřív spadly na 2,5 s.
        val panely = (1..12).map { i ->
            SbPrecteny(i, (i - 1) * 4.0, i * 4.0, "medium", "static", "x", if (i % 3 == 0) "" else "Ada: \"Start za minutu.\"")
        }
        val plan = SbFilmPlan.naplanuj(SbCteni(null, 48.0, 12, panely))
        assertTrue(plan.zeStoryboardu)
        assertEquals(List(12) { 4.0 }, plan.panely.map { it.sekundy })
        // Úseky dál nejvýš po 14 s (mez modelu), na hranicích panelů.
        val useky = SbFilmPlan.rozdel(plan.panely)
        assertTrue(useky.all { u -> u.panely.dropLast(1).sumOf { it.sekundy } <= SbFilmPlan.MAX_USEK_S })
        assertEquals(12, useky.sumOf { it.panely.size })
    }

    @Test
    fun `odhad bez casu nad 45 s se dal zkrati`() {
        val panely = (1..12).map { SbPrecteny(it, null, null, "wide", "tracking", "x", "Anna: \"Tohle je opravdu hodně dlouhá replika, kterou postava říká.\"") }
        val plan = SbFilmPlan.naplanuj(SbCteni(null, null, null, panely))
        assertFalse(plan.zeStoryboardu)
        assertTrue(plan.panely.sumOf { it.sekundy } <= SbFilmPlan.MAX_CELKEM_S + 1e-9)
    }
}
