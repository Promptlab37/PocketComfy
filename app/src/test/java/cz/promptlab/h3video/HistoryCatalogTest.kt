package cz.promptlab.h3video

import cz.promptlab.h3video.data.*
import org.junit.Assert.*
import org.junit.Test

class HistoryCatalogTest {
    private fun item(id: String, ext: String = "png", at: Long = 1) =
        VideoItem(id, "$id.$ext", "", at, 0f, "1024x1024", 42, false)

    @Test fun `stare zaznamy se nactou bez migrace`() {
        val decoded = HistoryCodec.decode("""[{"id":"a","file":"a.mp4","prompt":"test","at":1,"seed":7}]""")
        assertFalse(decoded.damaged)
        assertEquals(1, decoded.items.size)
        assertEquals("", decoded.items.single().title)
        assertFalse(decoded.items.single().favorite)
        assertEquals(7L, decoded.items.single().seed)
    }

    @Test fun `nazev a oblibene preziji ulozeni bez zmeny promptu a seedu`() {
        val original = item("a").copy(title = "Návrh", favorite = true, prompt = "původní zadání\ndruhý řádek", inGallery = true)
        assertEquals(original, HistoryCodec.decode(HistoryCodec.encode(listOf(original))).items.single())
    }

    @Test fun `poskozena polozka neznici zdrave polozky`() {
        val decoded = HistoryCodec.decode("""[null, 17, {"id":"ok","file":"ok.png"}, {"id":"bad"}]""")
        assertTrue(decoded.damaged)
        assertEquals(listOf("ok"), decoded.items.map { it.id })
    }

    @Test fun `neplatny json a nebezpecne cesty se odmitnou`() {
        assertTrue(HistoryCodec.decode("invalid").damaged)
        for (file in listOf("", ".", "..", "../secret", "dir/file", "C:secret", "dir\\file")) {
            val raw = org.json.JSONArray().put(org.json.JSONObject().put("id", "a").put("file", file)).toString()
            assertTrue(file, HistoryCodec.decode(raw).items.isEmpty())
        }
    }

    @Test fun `duplicitni id nezpusobi pad mrizky`() {
        val decoded = HistoryCodec.decode(HistoryCodec.encode(listOf(item("a"), item("a"))))
        assertTrue(decoded.damaged)
        assertEquals(1, decoded.items.size)
    }

    @Test fun `hleda bez diakritiky napric nazvem promptem souborem a seedem`() {
        val a = item("asset").copy(title = "Červený kabát", prompt = "Žena v zimě")
        assertEquals(listOf(a), filterHistory(listOf(a), "zena cerveny 42"))
        assertEquals(listOf(a), filterHistory(listOf(a), "ASSET.PNG"))
        assertTrue(filterHistory(listOf(a), "cerveny leto").isEmpty())
    }

    @Test fun `kombinace filtru je prunik a model neni video`() {
        val image = item("image").copy(favorite = true)
        val video = item("video", "mp4")
        val model = item("model", "GLB").copy(favorite = true)
        val all = listOf(image, video, model, item("audio", "wav"))
        assertEquals(listOf(video), filterHistory(all, kind = MediaKind.VIDEO))
        assertEquals(listOf(model), filterHistory(all, kind = MediaKind.MODEL3D, favoritesOnly = true))
        assertTrue(filterHistory(all, kind = MediaKind.VIDEO, favoritesOnly = true).isEmpty())
    }

    @Test fun `razeni ma stabilni poradi`() {
        val a = item("a", at = 2).copy(title = "Žlutá")
        val b = item("b", at = 3).copy(title = "Bílá")
        assertEquals(listOf(b, a), filterHistory(listOf(a, b)))
        assertEquals(listOf(a, b), filterHistory(listOf(a, b), order = HistoryOrder.OLDEST))
        assertEquals(listOf(b, a), filterHistory(listOf(a, b), order = HistoryOrder.NAME))
    }
}
