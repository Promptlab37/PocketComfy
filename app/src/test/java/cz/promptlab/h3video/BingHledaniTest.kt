package cz.promptlab.h3video

import cz.promptlab.h3video.comfy.BingHledani
import cz.promptlab.h3video.comfy.Katalog
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Hledání obrázků přes balík ComfyUI-BingImageSelector: čtení manifestu,
 * adresy náhledů a plných obrázků a pořadí výběru.
 */
class BingHledaniTest {

    private val session = "0123456789abcdef0123456789abcdef"
    private val id1 = "a".repeat(64)
    private val id2 = "b".repeat(64)
    private val id3 = "c".repeat(64)

    private fun manifest(vararg ids: String) = JSONObject()
        .put("session", session)
        .put("query", "kočka")
        .put("count", 24)
        .put("items", JSONArray().apply {
            ids.forEach { put(JSONObject().put("id", it).put("url", "https://x/$it").put("width", 800).put("height", 600)) }
        })

    @Test
    fun manifestSeCteCely() {
        val v = BingHledani.parsuj(manifest(id1, id2))
        assertEquals(session, v.session)
        assertEquals(listOf(id1, id2), v.obrazky.map { it.id })
        assertEquals(800, v.obrazky[0].sirka)
    }

    @Test
    fun neplatnaPolozkaSeVynecha() {
        val v = BingHledani.parsuj(manifest(id1, "../../etc", id2))
        assertEquals(listOf(id1, id2), v.obrazky.map { it.id })
    }

    @Test
    fun neplatnaSessionNeprojde() {
        assertThrows(IllegalArgumentException::class.java) {
            BingHledani.parsuj(manifest(id1).put("session", "../x"))
        }
    }

    @Test
    fun chybaZOdpovediBaliku() {
        assertEquals("Enter a search term", BingHledani.chyba("""{"error":"Enter a search term"}"""))
        assertNull(BingHledani.chyba("<html>"))
        assertNull(BingHledani.chyba("""{"session":"x"}"""))
    }

    @Test
    fun adresyNahleduAPlnehoObrazku() {
        assertEquals(
            "http://pc:8188/bing_image_selector/thumbnail/$session/$id1",
            BingHledani.nahledUrl("http://pc:8188/", session, id1),
        )
        // Plný obrázek leží ve vstupní složce ComfyUI: input/bing_image_selector/<session>/<id>.png
        val plny = okhttp3.HttpUrl.Companion.run { BingHledani.plnyUrl("http://pc:8188", session, id1).toHttpUrl() }
        assertEquals("/view", plny.encodedPath)
        assertEquals("$id1.png", plny.queryParameter("filename"))
        assertEquals("bing_image_selector/$session", plny.queryParameter("subfolder"))
        assertEquals("input", plny.queryParameter("type"))
    }

    @Test
    fun jedenObrazekKlepnutimNahradi() {
        var v = BingHledani.prepni(emptyList(), id1, max = 1)
        assertEquals(listOf(id1), v)
        v = BingHledani.prepni(v, id2, max = 1)
        assertEquals(listOf(id2), v)
        assertEquals(emptyList<String>(), BingHledani.prepni(v, id2, max = 1))
    }

    @Test
    fun viceObrazkuDrziPoradiAMez() {
        var v = BingHledani.prepni(emptyList(), id2, max = 2)
        v = BingHledani.prepni(v, id1, max = 2)
        assertEquals(listOf(id2, id1), v)
        // nad mez se nepřidává
        assertEquals(listOf(id2, id1), BingHledani.prepni(v, id3, max = 2))
        // odebrání uvolní místo
        assertEquals(listOf(id1), BingHledani.prepni(v, id2, max = 2))
    }

    @Test
    fun pocetVMezichBaliku() {
        assertTrue(BingHledani.POCET in 1..64)
    }

    @Test
    fun chybejiciBalikMaOdkazVKatalogu() {
        val b = Katalog.balikProUzel(BingHledani.UZEL)
        assertNotNull(b)
        assertEquals("https://github.com/concarne000/ComfyUI-BingImageSelector", b!!.odkaz)
    }

    /** Každý nový text obrazovky musí mít anglický překlad. */
    @Test
    fun textyObrazkyMajiPreklad() {
        val zdroj = listOf(
            "src/main/java/cz/promptlab/h3video/ui/VyberObrazkuActivity.kt",
            "src/main/java/cz/promptlab/h3video/comfy/BingHledani.kt",
        ).map { File(it).readText() }.joinToString("\n")
        // (?<![\w.]) — jinak by se chytlo i optInt("width")
        val texty = Regex("""(?<![\w.])t\("((?:[^"\\]|\\.)+)"\)""").findAll(zdroj).map { it.groupValues[1] }.toSet() +
            Regex("""Faze\.\w+|\("((?:[^"\\]|\\.)+)"\),""").findAll(zdroj.substringAfter("private enum class Faze").substringBefore("}"))
                .mapNotNull { it.groups[1]?.value }.toSet() +
            BingHledani.CHYBI_BALIK
        val chybi = texty.filter { cz.promptlab.h3video.data.Slovnik.EN[it] == null }
        assertEquals(emptyList<String>(), chybi)
    }
}
