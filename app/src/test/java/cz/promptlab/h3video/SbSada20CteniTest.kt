package cz.promptlab.h3video

import cz.promptlab.h3video.data.SbCteniTok
import cz.promptlab.h3video.data.SbFilmPlan
import cz.promptlab.h3video.data.SbFilmPrepis
import cz.promptlab.h3video.data.SbOcrShoda
import cz.promptlab.h3video.data.SbPanelyObrazu
import cz.promptlab.h3video.data.SbPrecteny
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Opravy po živém čtení sady 20 (5.69, 2. 10. 2026): kamera v ději, zdvojená replika v tichém panelu,
 * zvukový efekt jako replika, tiché chyby v bublinách, OCR bez diakritiky nad verzálkou.
 */
class SbSada20CteniTest {

    // ---------------------------------------------------------------- Kamera: velikost záběru

    @Test
    fun `kamera za emoci na stejnem radku neni dej`() {
        val p = SbFilmPlan.prevedPrepis(
            "PANEL 1 0-4 s Obraz: Anna najde mosazné hodinky na zastávce. Emoce: Zvědavost. Kamera: Detail. Anna: „Komu ses ztratily?“ " +
                "PANEL 2 4-8 s Obraz: Recepční mu podá klíč. Emoce: Soustředění. Kamera: Pololek. Recepční: „Snídaně od sedmi.“ " +
                "PANEL 3 8-12 s Obraz: Radek se objeví vedle lampy. Emoce: Soustředění. Kamera: Celé.",
        )
        assertEquals(
            mapOf(1 to "Anna najde mosazné hodinky na zastávce.", 2 to "Recepční mu podá klíč.", 3 to "Radek se objeví vedle lampy."),
            SbFilmPlan.prectiDeje(p),
        )
        assertEquals(mapOf(1 to "close-up", 2 to "medium shot", 3 to "wide shot"), SbFilmPlan.prectiZabery(p))
        assertEquals(mapOf(1 to "Zvědavost.", 2 to "Soustředění.", 3 to "Soustředění."), SbFilmPlan.prectiNalady(p))
        assertEquals("Anna: \"Komu ses ztratily?\"", SbFilmPlan.prectiRepliky(p)[1])
    }

    @Test
    fun `pohyb kamery zustava v deji`() {
        val p = SbFilmPlan.prevedPrepis("PANEL 1 Obraz: Babiš se otočí. Kamera: Pomalý nájezd kamery.")
        assertEquals(emptyMap<Int, String>(), SbFilmPlan.prectiZabery(p))
        assertEquals("Babiš se otočí. Pomalý nájezd kamery.", SbFilmPlan.prectiDeje(p)[1])
        assertNull(SbFilmPlan.velikostZaberu("pomalý nájezd kamery"))
        assertEquals("extreme wide shot", SbFilmPlan.velikostZaberu("Velký celek."))
        assertEquals("medium close-up", SbFilmPlan.velikostZaberu("Polodetail"))
    }

    @Test
    fun `stitek s carkou navic je stale stitek`() {
        // Soused z Marsu: „Obráz:“ — děj se jménem štítku.
        val p = SbFilmPlan.prevedPrepis("PANEL 3 3 | 8-12 s Obráz: Pavel ukáže na hodiny 22:30. Emoce: Překvapení. Kamera: Celek. Pavel: „Po desátý v tichu.“")
        assertEquals("Pavel ukáže na hodiny 22:30.", SbFilmPlan.prectiDeje(p)[3])
        assertEquals("Pavel: \"Po desátý v tichu.\"", SbFilmPlan.prectiRepliky(p)[3])
    }

    // ---------------------------------------------------------------- zdvojená replika

    @Test
    fun `replika zdvojena do vzdaleneho tichého panelu`() {
        // Recepce pro duchy: celé čtení dalo repliku panelu 7 i tichému panelu 5; řádky ji mají jen u 7.
        val r = "Duch: \"Ale poplatky mě dorazily až teď.\""
        val panely = listOf(5, 6, 7).map { SbPrecteny(it, null, null, "", "", "x", if (it == 6) "Host: \"On už je po smrti?\"" else r) }
        val radky = mapOf(5 to "", 6 to "Host: \"On už je po smrti?\"", 7 to r)
        val s = SbFilmPrepis.slucCteni(panely, radky)
        assertEquals(emptyList<Pair<String, String>>(), SbFilmPrepis.repliky(s[0].repliky))
        assertEquals(1, SbFilmPrepis.repliky(s[2].repliky).size)
    }

    // ---------------------------------------------------------------- zvukový efekt

    @Test
    fun `zvukovy efekt v obraze neni replika`() {
        assertTrue(SbCteniTok.zvukovyEfekt("HAPČÍ!"))
        assertTrue(SbCteniTok.zvukovyEfekt("BANG!"))
        assertFalse(SbCteniTok.zvukovyEfekt("BYTY! Ne jeden, ne dva — rovnou TŘI!"))
        assertFalse(SbCteniTok.zvukovyEfekt("Tebe."))
        assertFalse(SbCteniTok.zvukovyEfekt("Hapčí!"))
        assertFalse(SbCteniTok.zvukovyEfekt("AI!"))
    }

    // ---------------------------------------------------------------- OCR

    @Test
    fun `OCR neubere diakritiku nad verzalkou`() {
        // Robot hledá domov: Tesseract četl „R7:„Uloha“, model správně „Úloha“.
        assertEquals("Úloha dokončena." to emptyList<SbOcrShoda.Oprava>(), SbOcrShoda.oprav("Úloha dokončena.", "R7:„Uloha dokončena.“"))
        // Malé písmeno dál opraví (vytištěný překlep — Pepa).
        assertEquals("A nabiječku ovládá taky AI?", SbOcrShoda.oprav("A nabíječku ovládá taky AI?", "Kamarád: „A nabiječku ovládá taky AI?“").first)
    }

    @Test
    fun `rozdil ktery OCR neopravi jde ke kontrole`() {
        val p = SbOcrShoda.porovnej("Pět let chrnění ohně.", "Drak: „Pět let chrlení ohně.“")
        assertEquals("Pět let chrnění ohně.", p.text)
        assertEquals(listOf(SbOcrShoda.Oprava("chrnění", "chrlení")), p.neprijate)
        // Děj: jen diakritika.
        assertEquals("ukazuje po šipce nahoru", SbOcrShoda.porovnej("ukazuje po šípce nahoru", "(ukazuje po šipce nahoru)", jenDiakritika = true).text)
        assertEquals("Viktor ukáže pod kotel s polévku.", SbOcrShoda.porovnej("Viktor ukáže pod kotel s polévku.", "Obraz: Viktor ukáže pod kotel s polévkou.", jenDiakritika = true).text)
    }

    // ---------------------------------------------------------------- bubliny celým tokem

    /** List 2 × 2, repliky jen v bublinách (Čtyři přání, Drak na pohovoru), popisky bez replik. */
    private val bunky = listOf(
        SbPanelyObrazu.Obdelnik(0, 0, 500, 400), SbPanelyObrazu.Obdelnik(500, 0, 1000, 400),
        SbPanelyObrazu.Obdelnik(0, 400, 500, 800), SbPanelyObrazu.Obdelnik(500, 400, 1000, 800),
    )
    private val ocrPanelu = mapOf(
        bunky[0] to "Běla: „Každý jedno přání.“ 0-4 s Obraz: Tři přátelé objeví lampu.",
        bunky[1] to "4-8 s Obraz: Radek se objeví vedle lampy.",
        bunky[2] to "Drak: „Pět let chrlení ohně.“ 8-12 s Obraz: Drak vyfoukne plamen.",
        bunky[3] to "HAPČÍ! 12-16 s Obraz: Drak kýchne.",
    )

    private inner class BublinyModel : SbCteniTok.Model {
        val ocrDotazy = mutableListOf<List<SbPanelyObrazu.Obdelnik>>()
        override suspend fun nahraj(png: ByteArray, nazev: String) = nazev
        override suspend fun ocr(jmeno: String, oblasti: List<SbPanelyObrazu.Obdelnik>, krok: Int): List<String> {
            ocrDotazy += oblasti
            return oblasti.map { ocrPanelu[it].orEmpty() }
        }
        override suspend fun precti(jmeno: String, otazka: String, krok: Int): String = when {
            otazka == SbFilmPlan.OTAZKA_CTENI ->
                "TITLE: none | TOTAL: 16 | SHOTS: 4 | GRID: 2x2 | VOICES: Běla = a young woman; Drak = a small dragon | " +
                    "LOOKS: Běla = crown; Drak = green dragon | MUSIC: none | CONTINUITY: none " +
                    "PANEL 1 | 0-4 s | medium | static | Friends find a lamp | Běla: \"Každý jedno přání.\" " +
                    "PANEL 2 | 4-8 s | medium | static | Radek appears | Drak: \"Pět let chrnění ohně.\" " +
                    "PANEL 3 | 8-12 s | wide | static | The dragon breathes fire | Drak: \"Pět let chrnění ohně.\" " +
                    "PANEL 4 | 12-16 s | close-up | static | The dragon sneezes | Drak: \"HAPČÍ!\""
            "numbered 1 to 2" in otazka -> "PANEL 1 0-4 s Obraz: Tři přátelé objeví lampu. PANEL 2 4-8 s Obraz: Radek se objeví vedle lampy."
            "numbered 3 to 4" in otazka -> "PANEL 3 8-12 s Obraz: Drak vyfoukne plamen. PANEL 4 12-16 s Obraz: Drak kýchne."
            otazka.startsWith("This image is a film storyboard. Translate") ->
                "ACTION 1 = Three friends find the lamp. ACTION 2 = Radek appears beside the lamp. ACTION 3 = The dragon breathes out a flame. " +
                    "ACTION 4 = The dragon sneezes. SOUND 4 = A loud sneeze."
            else -> ""
        }
    }

    private inner class BublinyObraz : SbCteniTok.Obraz {
        override val sirka = 1000
        override val vyska = 800
        override fun original() = ByteArray(0)
        override fun vyrezPng(x0: Int, y0: Int, x1: Int, y1: Int, meritko: Float) = ByteArray(0)
        override fun bunky() = this@SbSada20CteniTest.bunky.map { SbPanelyObrazu.Bunka(it, false) }
    }

    @Test
    fun `repliky z bublin overi OCR celeho panelu`() {
        val model = BublinyModel()
        val v = runBlocking { SbCteniTok.precti(BublinyObraz(), model, object : SbCteniTok.Prubeh { override fun krokNavic() = 0 }) }
        val plan = SbCteniTok.sestav(v).plan.panely
        val repl = plan.map { SbFilmPrepis.repliky(it.repliky) }
        // Panel 1 potvrzený, panel 2 zdvojený z panelu 3 → pryč, panel 4 je zvuk, ne replika.
        assertEquals(listOf("Běla" to "Každý jedno přání."), repl[0])
        assertEquals(emptyList<Pair<String, String>>(), repl[1])
        assertEquals(listOf("Drak" to "Pět let chrnění ohně."), repl[2])
        assertEquals(emptyList<Pair<String, String>>(), repl[3])
        // „chrnění“ × „chrlení“: nic tichého — slovo jde ke kontrole.
        assertEquals("chrnění / chrlení", v.sporne[3])
        assertTrue(plan[3].popis, plan[3].popis.endsWith("Sound: A loud sneeze."))
        // Druhé OCR jen pro panely s replikami z bublin (1–3), čtvrtý už je zvuk.
        assertEquals(listOf(bunky[0], bunky[1], bunky[2]), model.ocrDotazy.last())
    }
}
