package cz.promptlab.h3video

import cz.promptlab.h3video.data.OdhadPrepisu
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Odhad času přepisovače. Čísla jsou z logu ComfyUI 27. 9. 2026:
 * Qwen 3.5 9B, strop 640 tokenů, skutečně napsáno 280, psaní ~3,5 tokenu/s.
 */
class OdhadPrepisuTest {

    private val t0 = 1_000_000L

    @Test fun `pri psani se pocita z zive rychlosti a minule delky`() {
        // 20 s od prvního tokenu, napsáno 70 → 3,5 tokenu/s; minule 280 tokenů.
        val v = OdhadPrepisu.spocitej(
            ted = t0 + 20_000, beziOd = t0 - 1_000, prvniTokenOd = t0, prvniHodnota = 0,
            napsano = 70, strop = 640, obvykleTokeny = 280, obvyklaRychlost = 0.0, obvykleNacitaniS = 0,
        )
        // (280 − 70) / 3,5 = 60 s
        assertEquals(60L, v.zbyvaS)
        assertEquals(0.25f, v.podil!!, 0.001f)
    }

    @Test fun `strop neni cilem kdyz vime kolik to obvykle napise`() {
        assertEquals(280, OdhadPrepisu.cilTokenu(napsano = 10, strop = 640, obvykleTokeny = 280))
        // bez minulé délky se musí počítat se stropem
        assertEquals(640, OdhadPrepisu.cilTokenu(napsano = 10, strop = 640, obvykleTokeny = 0))
    }

    @Test fun `kdyz se pise dele nez minule, cil se posune, nejvys ke stropu`() {
        assertEquals(320, OdhadPrepisu.cilTokenu(napsano = 300, strop = 640, obvykleTokeny = 280))
        assertEquals(640, OdhadPrepisu.cilTokenu(napsano = 635, strop = 640, obvykleTokeny = 280))
    }

    @Test fun `maly vzorek bere rychlost z minula`() {
        // 1 s a 3 tokeny — živá rychlost by skákala; použije se minulých 3,5/s.
        val v = OdhadPrepisu.spocitej(
            ted = t0 + 1_000, beziOd = t0, prvniTokenOd = t0, prvniHodnota = 0,
            napsano = 3, strop = 640, obvykleTokeny = 280, obvyklaRychlost = 3.5, obvykleNacitaniS = 0,
        )
        assertEquals(((280 - 3) / 3.5).toLong(), v.zbyvaS)
    }

    @Test fun `pri nacitani se pricte minule nacitani a cele psani`() {
        // převzato před 2 s, minule načítání 5 s → zbývá 3 s + 280/3,5 = 80 s
        val v = OdhadPrepisu.spocitej(
            ted = t0 + 2_000, beziOd = t0, prvniTokenOd = 0L, prvniHodnota = 0,
            napsano = 0, strop = 0, obvykleTokeny = 280, obvyklaRychlost = 3.5, obvykleNacitaniS = 5,
        )
        assertEquals(83L, v.zbyvaS)
        assertNull(v.podil)
    }

    @Test fun `prvni pouziti bez podkladu nic neslibuje`() {
        val v = OdhadPrepisu.spocitej(
            ted = t0 + 2_000, beziOd = t0, prvniTokenOd = 0L, prvniHodnota = 0,
            napsano = 0, strop = 0, obvykleTokeny = 0, obvyklaRychlost = 0.0, obvykleNacitaniS = 0,
        )
        assertNull(v.zbyvaS)
        assertNull(v.podil)
    }

    @Test fun `podil nikdy nedojede na sto procent pred koncem`() {
        val v = OdhadPrepisu.spocitej(
            ted = t0 + 60_000, beziOd = t0, prvniTokenOd = t0, prvniHodnota = 0,
            napsano = 400, strop = 640, obvykleTokeny = 280, obvyklaRychlost = 0.0, obvykleNacitaniS = 0,
        )
        assertNotNull(v.podil)
        assertTrue(v.podil!! < 1f)
    }

    @Test fun `prepisovac bez hlaseni tokenu bere minulou delku`() {
        // llama.cpp: server tokeny nehlásí, minule to trvalo 35 s, teď běží 10 s
        val v = OdhadPrepisu.spocitej(
            ted = t0 + 10_000, beziOd = t0, prvniTokenOd = 0L, prvniHodnota = 0,
            napsano = 0, strop = 0, obvykleTokeny = 0, obvyklaRychlost = 0.0, obvykleNacitaniS = 0,
            obvykleCelkemS = 35,
        )
        assertEquals(25L, v.zbyvaS)
    }
}
