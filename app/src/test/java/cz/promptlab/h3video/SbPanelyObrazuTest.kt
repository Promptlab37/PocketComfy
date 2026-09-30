package cz.promptlab.h3video

import cz.promptlab.h3video.data.SbPanelyObrazu
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import kotlin.math.abs

/** Hledání panelů na storyboardu (5.38): různě velké panely, prázdné políčko, popisky. */
class SbPanelyObrazuTest {

    private val dir = File("src/test/resources/panely")

    // Obrázky (i skutečné storyboardy uživatele) do repa nejdou — .gitignore *.jpg.
    // Bez nich se tyhle testy přeskočí (GitHub); lokálně běží. Vyrobené listy
    // v kódu (zátěž) běží všude.
    private fun obrazky() = assumeTrue(File(dir, "real_iron.jpg").exists())

    // javax.imageio je v JDK, ale ne v android.jar, proti kterému se testy překládají — přes reflexi.
    private fun najdi(jmeno: String): Pair<List<SbPanelyObrazu.Obdelnik>?, Pair<Int, Int>> {
        val io = Class.forName("javax.imageio.ImageIO")
        val im = io.getMethod("read", File::class.java).invoke(null, File(dir, jmeno))
        val w = im.javaClass.getMethod("getWidth").invoke(im) as Int
        val h = im.javaClass.getMethod("getHeight").invoke(im) as Int
        val px = im.javaClass.getMethod(
            "getRGB", Int::class.java, Int::class.java, Int::class.java, Int::class.java,
            IntArray::class.java, Int::class.java, Int::class.java,
        ).invoke(im, 0, 0, w, h, null, 0, w) as IntArray
        return SbPanelyObrazu.najdi(px, w, h) to (w to h)
    }

    @Test
    fun `vyrobene listy - presne hranice a poradi`() {
        obrazky()
        val ocek = JSONObject(File(dir, "ocekavane.json").readText())
        val out = StringBuilder()
        for (jm in ocek.keys()) {
            val (p, rozmer) = najdi(jm)
            val vzor = ocek.getJSONArray(jm)
            val tol = maxOf(rozmer.first, rozmer.second) * 0.02
            if (p == null || p.size != vzor.length()) { out.append("$jm: ${p?.size} místo ${vzor.length()} $p\n"); continue }
            for (i in 0 until vzor.length()) {
                val v = vzor.getJSONArray(i)
                val q = p[i]
                val sedi = abs(q.x0 - v.getInt(0)) <= tol && abs(q.y0 - v.getInt(1)) <= tol &&
                    abs(q.x1 - v.getInt(2)) <= tol && abs(q.y1 - v.getInt(3)) <= tol
                if (!sedi) out.append("$jm panel ${i + 1}: $q místo $v\n")
            }
        }
        assertEquals(out.toString(), "", out.toString())
    }

    @Test
    fun `skutecne storyboardy`() {
        obrazky()
        // Otevřené dveře: 2 sloupce × 4 řádky, panely až k okraji.
        assertEquals(8, najdi("real_storyboard.jpg").first?.size)
        // Iron: 3 × 4 s popisky pod obrázky — popisek patří k panelu.
        val iron = najdi("real_iron.jpg").first
        assertNotNull(iron)
        assertEquals(12, iron!!.size)
        assertEquals(3, SbPanelyObrazu.radky(iron).size)
        // Mřížka 2 × 2 s rámečky a šedým vnitřkem.
        assertEquals(4, najdi("real_grid.jpg").first?.size)
        // Jedna fotka bez panelů → nejisté, appka jede postaru.
        assertNull(najdi("real_fox.jpg").first)
    }

    @Test
    fun `radky - vysoky panel drzi radek`() {
        obrazky()
        val (p, _) = najdi("vnorene_ramy.jpg")
        val r = SbPanelyObrazu.radky(p!!)
        assertEquals(listOf(3, 3), r.map { it.size })
        assertTrue(r[0][0].vyska > r[0][1].vyska)
    }

    /** Vyrobený list: panely s šumem na pozadí [pozadi]; mezera [mezera] px. */
    private fun list(sl: Int, rd: Int, w: Int, h: Int, mezera: Int, pozadi: Int = 0xFFFFFFFF.toInt(),
                     uprav: (IntArray) -> Unit = {}): IntArray {
        val px = IntArray(w * h) { pozadi }
        val rnd = java.util.Random(3)
        val pw = (w - mezera * (sl + 1)) / sl
        val ph = (h - mezera * (rd + 1)) / rd
        for (r in 0 until rd) for (c in 0 until sl) {
            val x0 = mezera + c * (pw + mezera); val y0 = mezera + r * (ph + mezera)
            val zaklad = rnd.nextInt(0xFFFFFF)
            for (y in y0 until y0 + ph) for (x in x0 until x0 + pw) {
                px[y * w + x] = (0xFF shl 24) or ((zaklad + (x * 7 + y * 13 + rnd.nextInt(40))) and 0x7F7F7F)
            }
        }
        uprav(px)
        return px
    }

    @Test
    fun `zatez - husta mrizka a uzke mezery`() {
        val a = SbPanelyObrazu.najdi(list(5, 5, 1000, 1000, 8), 1000, 1000); assertEquals(SbPanelyObrazu.duvod, 25, a?.size)
        assertEquals(6, SbPanelyObrazu.najdi(list(3, 2, 1200, 700, 4), 1200, 700)?.size)
        // Černé pozadí.
        assertEquals(6, SbPanelyObrazu.najdi(list(3, 2, 900, 600, 10, 0xFF000000.toInt()), 900, 600)?.size)
    }

    @Test
    fun `zatez - postava na jednobarevnem pozadi neni storyboard`() {
        val w = 800; val h = 1000
        val px = IntArray(w * h) { 0xFFF0F0F0.toInt() }
        // Postava: velká souvislá skvrna uprostřed + drobnost vedle.
        for (y in 150 until 950) for (x in 250 until 550) if ((x - 400) * (x - 400) / 150 + (y - 550) * (y - 550) / 400 < 200) px[y * w + x] = 0xFF553322.toInt()
        for (y in 100 until 140) for (x in 600 until 640) px[y * w + x] = 0xFF223355.toInt()
        assertNull(SbPanelyObrazu.najdi(px, w, h))
    }

    @Test
    fun `zatez - prepalena obloha v panelu`() {
        val w = 900; val h = 600
        // Horní třetina prvního panelu bílá jako mezera, ale panel ji obklopuje ze stran obsahem.
        val px = list(3, 2, w, h, 12) { p ->
            for (y in 12 until 90) for (x in 40 until 260) p[y * w + x] = 0xFFFFFFFF.toInt()
        }
        val a = SbPanelyObrazu.najdi(px, w, h); assertEquals(SbPanelyObrazu.duvod, 6, a?.size)
    }

    @Test
    fun `prazdne policko se vraci zvlast - model ho muze zapocitat`() {
        obrazky()
        val io = Class.forName("javax.imageio.ImageIO")
        val im = io.getMethod("read", File::class.java).invoke(null, File(dir, "lichy_prazdny_ram.jpg"))
        val w = im.javaClass.getMethod("getWidth").invoke(im) as Int
        val h = im.javaClass.getMethod("getHeight").invoke(im) as Int
        val px = im.javaClass.getMethod("getRGB", Int::class.java, Int::class.java, Int::class.java, Int::class.java,
            IntArray::class.java, Int::class.java, Int::class.java).invoke(im, 0, 0, w, h, null, 0, w) as IntArray
        val b = SbPanelyObrazu.najdiBunky(px, w, h)!!
        // 7 panelů + 1 prázdné políčko v rámečku, prázdné je poslední (vpravo dole).
        assertEquals(8, b.size)
        assertEquals(listOf(false, false, false, false, false, false, false, true), b.map { it.prazdna })
    }
}
