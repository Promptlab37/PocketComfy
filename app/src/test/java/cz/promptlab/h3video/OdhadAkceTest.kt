package cz.promptlab.h3video

import cz.promptlab.h3video.comfy.H3RefWriteBuilder
import cz.promptlab.h3video.comfy.ImagePromptBuilder
import cz.promptlab.h3video.comfy.SbFilmBuilder
import cz.promptlab.h3video.data.KrokAkce
import cz.promptlab.h3video.data.OdhadAkce
import cz.promptlab.h3video.data.Ocekavani
import cz.promptlab.h3video.data.TypKroku
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Odhad času vícekrokových akcí Filmu ze storyboardu (5.09). Čísla jsou
 * naměřená na serveru 29. 9. 2026: celé čtení 17 s teplé / 96 s studené,
 * řádek 8,5 s, přepis úseku 50 / 117 s.
 */
class OdhadAkceTest {

    private val vychozi: (TypKroku) -> Ocekavani = { Ocekavani(it.teplyS, it.studenyS) }
    private val cteni = listOf(
        KrokAkce(TypKroku.CTENI_CELE, studeny = true),
        KrokAkce(TypKroku.CTENI_RADEK), KrokAkce(TypKroku.CTENI_RADEK),
    )

    @Test
    fun `studene cteni se dvema radky na zacatku`() {
        // Ve frontě se neodpočítává: celá akce 96 + 2 × 8,5 = 113 s.
        val r = OdhadAkce.spocitej(cteni, vychozi, 0, vKrokuS = null, ubehloS = 0.0)
        assertEquals(113L, r.zbyvaS)
        // Po 40 s běhu prvního kroku zbývá 56 + 17.
        val b = OdhadAkce.spocitej(cteni, vychozi, 0, vKrokuS = 40.0, ubehloS = 40.0)
        assertEquals(73L, b.zbyvaS)
        assertTrue(b.podil > 0.3f && b.podil < 0.4f)
    }

    @Test
    fun `tepla akce ukazuje celkovy cas vsech kroku`() {
        val tepla = cteni.map { it.copy(studeny = false) }
        assertEquals(34L, OdhadAkce.spocitej(tepla, vychozi, 0, null, 0.0).zbyvaS)
        // Druhý krok (řádek 1), běží 3 s: 5,5 + 8,5.
        assertEquals(14L, OdhadAkce.spocitej(tepla, vychozi, 1, 3.0, 20.0).zbyvaS)
        // Příprava tří úseků, teple: 3 × 57 s.
        val useky = List(3) { KrokAkce(TypKroku.PREPIS_USEKU) }
        assertEquals(171L, OdhadAkce.spocitej(useky, vychozi, 0, null, 0.0).zbyvaS)
    }

    @Test
    fun `protazeny krok cas neprida a odhad neskoci zpet`() {
        val tepla = listOf(KrokAkce(TypKroku.CTENI_CELE), KrokAkce(TypKroku.CTENI_RADEK))
        // 30 s > 17: krok se protáhl, zbývá jen další řádek (8,5 s) — žádný skok na studený.
        assertEquals(9L, OdhadAkce.spocitej(tepla, vychozi, 0, 30.0, 30.0).zbyvaS)
        // Poslední krok přetáhl: žádné číslo, bar stojí.
        val r = OdhadAkce.spocitej(tepla, vychozi, 1, 20.0, 50.0, predchoziPodil = 0.6f)
        assertNull(r.zbyvaS)
        assertEquals(0.6f, r.podil, 0f)
    }

    /** Příšera 1. 10. 2026: druhý úsek 87 s místo 57 s — zbývající čas se nesmí zvýšit. */
    @Test
    fun `zbyvajici cas po kroku jen klesa`() {
        val kroky = listOf(KrokAkce(TypKroku.PREPIS_USEKU), KrokAkce(TypKroku.PREPIS_USEKU))
        var minule = Long.MAX_VALUE
        for (s in 0..87) {
            val z = OdhadAkce.spocitej(kroky, vychozi, 1, s.toDouble(), 52.0 + s).zbyvaS ?: 0L
            assertTrue("v ${s}. s zbývá $z, předtím $minule", z <= minule)
            minule = z
        }
    }

    @Test
    fun `bar necouvne kdyz pribudou kroky a zastavi se pred koncem`() {
        val jeden = listOf(KrokAkce(TypKroku.CTENI_CELE))
        val a = OdhadAkce.spocitej(jeden, vychozi, 0, 16.0, 16.0)
        // Po prvním čtení se ukáže, že řádků je 3: podíl by spadl, ale nesmí.
        val vic = jeden + List(3) { KrokAkce(TypKroku.CTENI_RADEK) }
        val b = OdhadAkce.spocitej(vic, vychozi, 1, 0.0, 17.0, predchoziPodil = a.podil)
        assertTrue(b.podil >= a.podil)
        assertTrue(a.podil <= OdhadAkce.STROP_PODILU)
    }

    @Test
    fun `zivy odhad z tokenu ma prednost u prepisu useku`() {
        val useky = List(2) { KrokAkce(TypKroku.PREPIS_USEKU) }
        assertEquals(67L, OdhadAkce.spocitej(useky, vychozi, 0, 20.0, 20.0, tokenZbyvaS = 10.0).zbyvaS)
    }

    @Test
    fun `uceni prumeruje omezuje skok a spatne odhadnuty teply krok jde do studenych`() {
        val o = Ocekavani(17.0, 96.0)
        assertEquals(false to 18.0, OdhadAkce.nauc(o, false, 19.0))
        // 95 s u „teplého“ kroku = byl studený.
        assertEquals(true to 95.5, OdhadAkce.nauc(o, false, 95.0))
        // Skok nejvýš 3×: 1000 s u studeného → x = 288.
        assertEquals(true to 192.0, OdhadAkce.nauc(o, true, 1000.0))
    }

    private fun historie(vararg tridy: String): JSONObject {
        val graf = JSONObject()
        tridy.forEachIndexed { i, c -> graf.put("$i", JSONObject().put("class_type", c).put("inputs", JSONObject())) }
        return JSONObject().put("abc", JSONObject().put("prompt", JSONArray().put(7).put("abc").put(graf)))
    }

    @Test
    fun `studeny model po videu po restartu a pri chybe`() {
        assertTrue(OdhadAkce.jeStudeny(null))
        assertTrue(OdhadAkce.jeStudeny(JSONObject()))
        assertTrue(OdhadAkce.jeStudeny(historie("UNETLoader", "MiniMaxH3EasySegmentStep_SatoDive", "SaveVideo")))
        assertFalse(OdhadAkce.jeStudeny(historie("LoadImage", "MiniMaxH3ReferenceCaption", "PreviewAny")))
        // Nejnovější rozhoduje (číslo ve frontě), ne pořadí v odpovědi.
        val dve = historie("LoadImage", "MiniMaxH3ReferenceCaption", "PreviewAny")
        dve.put("xyz", JSONObject().put("prompt", JSONArray().put(9).put("xyz").put(
            JSONObject().put("1", JSONObject().put("class_type", "SaveVideo")))))
        assertTrue(OdhadAkce.jeStudeny(dve))
    }

    private fun tridy(g: JSONObject) = g.keys().asSequence().map { g.getJSONObject(it).getString("class_type") }.toSet()

    @Test
    fun `textove grafy appky maji jen textove tridy`() {
        val grafy = listOf(
            SbFilmBuilder.buildCteni("sb.png", "m.gguf", 1L),
            H3RefWriteBuilder.build("x", listOf("a.png"), 5.0, "16:9", "c.gguf", "w.gguf", 1L, storyboard = true, hlidka = "h"),
            ImagePromptBuilder.graf("x", "m.gguf", 1L, "s", 100, 0.7),
            ImagePromptBuilder.graf("x", "m.gguf", 1L, "s", 100, 0.7, mmproj = "p.gguf", obrazky = listOf("a.png")),
        )
        grafy.forEach { g ->
            assertTrue(tridy(g).toString(), OdhadAkce.TEXTOVE_TRIDY.containsAll(tridy(g)))
        }
    }

    /** 5.12: odvázaný vylepšovač a MiniMax Writer se odhadují podle času (tokeny nehlásí). */
    @Test
    fun `samostatne vylepsovace se poznaji podle grafu`() {
        assertEquals(TypKroku.ODVAZANY, TypKroku.proGraf(ImagePromptBuilder.graf("x", "m.gguf", 1L, "s", 100, 0.7)))
        assertEquals(TypKroku.ODVAZANY_FOTO, TypKroku.proGraf(
            ImagePromptBuilder.graf("x", "m.gguf", 1L, "s", 100, 0.7, mmproj = "p.gguf", obrazky = listOf("a.png"))))
        assertEquals(TypKroku.VYLEPSENI_H3, TypKroku.proGraf(
            H3RefWriteBuilder.build("x", listOf("a.png"), 5.0, "16:9", "c.gguf", "w.gguf", 1L)))
        assertNull(TypKroku.proGraf(SbFilmBuilder.buildCteni("sb.png", "m.gguf", 1L)))
        // Studená fotka po videu 77 s, teplá bez fotky 12 s.
        val k = listOf(KrokAkce(TypKroku.ODVAZANY_FOTO, studeny = true))
        assertEquals(77L, OdhadAkce.spocitej(k, vychozi, 0, null, 0.0).zbyvaS)
    }
}
