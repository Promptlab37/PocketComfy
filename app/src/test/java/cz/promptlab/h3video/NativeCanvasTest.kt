package cz.promptlab.h3video

import cz.promptlab.h3video.data.Aspect
import cz.promptlab.h3video.data.GenParams
import cz.promptlab.h3video.data.Resolution
import cz.promptlab.h3video.data.nativeCanvas
import cz.promptlab.h3video.data.nativeMegapixels
import cz.promptlab.h3video.data.sizeStepsFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Plátno modelu podle poměru stran.
 *
 * Dřív se to měřilo plochým pravidlem „kratší hrana nad 768 px". To u čtverce
 * a 21:9 dávalo jiný výsledek než uzel a hlavně to hlásilo „nad nativním
 * plátnem" u 832×1120 (0,90 MP), tedy u rozlišení, které je menší než nativní
 * 1344×768 u šestnáctky – lidem to logicky nedávalo smysl.
 *
 * Hodnoty jsou z uzlu: `adapt_canvas` v `comfy_extras/nodes_minimax_h3.py`,
 * `BASE_SHORT_EDGE = 768`, `MAX_PIXELS = 768 * 1344`.
 */
class NativeCanvasTest {

    @Test
    fun `nativni platno sedi s uzlem`() {
        // 1344×768 je zároveň výchozí hodnota uzlu
        assertEquals(Resolution(1344, 768), nativeCanvas(Aspect.LANDSCAPE_16_9))
        assertEquals(Resolution(768, 1344), nativeCanvas(Aspect.PORTRAIT_9_16))
        assertEquals(Resolution(768, 768), nativeCanvas(Aspect.SQUARE_1_1))
        assertEquals(Resolution(1024, 768), nativeCanvas(Aspect.LANDSCAPE_4_3))
        assertEquals(Resolution(768, 1024), nativeCanvas(Aspect.PORTRAIT_3_4))
        assertEquals(Resolution(1152, 768), nativeCanvas(Aspect.LANDSCAPE_3_2))
    }

    @Test
    fun `u sirokeho pomeru strop plochy stahne i kratsi hranu`() {
        // 21:9 by na 768 px kratší hraně mělo 1792×768 = 1,31 MP, což je nad
        // stropem 768×1344 – uzel proto plátno zmenší.
        val r = nativeCanvas(Aspect.ULTRAWIDE_21_9)
        assertTrue("kratší hrana ${r.shortEdge}", r.shortEdge < 768)
        assertTrue("plocha ${r.pixels}", r.pixels <= 768L * 1344)
        assertEquals(0, r.width % 32)
        assertEquals(0, r.height % 32)
    }

    @Test
    fun `nativni velikost je v nabidce a trefi se presne`() {
        Aspect.entries.forEach { a ->
            val kroky = sizeStepsFor(a)
            val nativni = nativeCanvas(a)
            val mp = kroky.firstOrNull { Resolution.of(a, it) == nativni }
            assertTrue("${a.label}: nativní ${nativni.label} chybí v nabídce", mp != null)
            // a nesmí tam být dvakrát v podobě dvou skoro stejných pilulek
            assertEquals(
                "${a.label}: duplicitní velikosti",
                kroky.size, kroky.distinct().size
            )
        }
    }

    @Test
    fun `832x1120 uz se nehlasi jako nad platnem`() {
        // přesně to, co uživatel viděl: 3:4, 0,9 MP
        val p = GenParams(aspect = Aspect.PORTRAIT_3_4, megapixels = 0.9f)
        assertEquals("832×1120", p.resolution.label)
        assertFalse(p.aboveNative)
        // je to jen o kousek nad plátnem 768×1024 (8 % na kratší hraně), to se toleruje
        assertTrue("přetečení ${p.nativeOverhead} %", p.nativeOverhead in 1..20)
    }

    @Test
    fun `hlaska se ukazuje jen u vetsich odchylek, ne skoro porad`() {
        // Kdyby se hlásilo všechno nad nativem, svítí oranžová u většiny voleb
        // a přestane něco znamenat.
        val hlasi = Aspect.entries.sumOf { a ->
            sizeStepsFor(a).count { GenParams(aspect = a, megapixels = it).aboveNative }
        }
        val vsech = Aspect.entries.sumOf { sizeStepsFor(it).size }
        assertTrue("hlásí se u $hlasi z $vsech voleb", hlasi < vsech / 2)
    }

    @Test
    fun `skutecne velke platno se hlasi porad`() {
        val p = GenParams(aspect = Aspect.LANDSCAPE_16_9, megapixels = 2.0f)
        assertEquals("1920×1088", p.resolution.label)
        assertTrue(p.aboveNative)
        assertTrue("přetečení ${p.nativeOverhead} %", p.nativeOverhead > 90)
    }

    @Test
    fun `na nativu se to pozna`() {
        val p = GenParams(aspect = Aspect.PORTRAIT_3_4, megapixels = nativeMegapixels(Aspect.PORTRAIT_3_4))
        assertTrue(p.isNativeResolution)
        assertFalse(p.aboveNative)
        assertEquals(0, p.nativeOverhead)
        assertEquals("768×1024", p.resolution.label)
    }

    @Test
    fun `pevna rada rozliseni zustava jak byla`() {
        // Změna se nesmí dotknout hodnot, na kterých je vyladěné workflow.
        assertEquals("864×480", Resolution.of(Aspect.LANDSCAPE_16_9, 0.4f).label)
        assertEquals("1344×768", Resolution.of(Aspect.LANDSCAPE_16_9, 0.98f).label)
        assertEquals("1920×1088", Resolution.of(Aspect.LANDSCAPE_16_9, 2.0f).label)
        assertEquals("576×1024", Resolution.of(Aspect.PORTRAIT_9_16, 0.56f).label)
    }
}
