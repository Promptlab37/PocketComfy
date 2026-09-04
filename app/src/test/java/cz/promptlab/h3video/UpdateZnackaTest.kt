package cz.promptlab.h3video

import cz.promptlab.h3video.update.UpdateChecker.jeNovejsiVydani
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Kontrola aktualizací nesmí kvůli tvaru značky vydání tiše tvrdit, že je
 * aplikace aktuální.
 *
 * Přesně to se stalo u vydání `v3.18` a `v3.19`: brávalo se jen to, co je před
 * tečkou, takže z „v3.19" vyšlo 3, proti sestavení 129 to bylo méně a nové
 * verze se do telefonu nikdy nedostaly.
 */
class UpdateZnackaTest {

    @Test
    fun `znacka s cislem sestaveni`() {
        assertTrue(jeNovejsiVydani("v132", kodTed = 131, jmenoTed = "3.19"))
        assertFalse(jeNovejsiVydani("v131", kodTed = 131, jmenoTed = "3.19"))
        assertFalse(jeNovejsiVydani("v130", kodTed = 131, jmenoTed = "3.19"))
    }

    @Test
    fun `znacka se jmenem verze`() {
        assertTrue(jeNovejsiVydani("v3.19", kodTed = 129, jmenoTed = "3.17"))
        assertTrue(jeNovejsiVydani("v3.18", kodTed = 129, jmenoTed = "3.17"))
        assertFalse(jeNovejsiVydani("v3.17", kodTed = 129, jmenoTed = "3.17"))
        assertFalse(jeNovejsiVydani("v3.09", kodTed = 129, jmenoTed = "3.17"))
    }

    @Test
    fun `vyssi desitky nejsou mensi nez jednotky`() {
        // Textovým porovnáním by „3.9" vyšlo výš než „3.10".
        assertTrue(jeNovejsiVydani("v3.10", kodTed = 121, jmenoTed = "3.9"))
        assertFalse(jeNovejsiVydani("v3.9", kodTed = 123, jmenoTed = "3.10"))
        assertTrue(jeNovejsiVydani("v4.0", kodTed = 131, jmenoTed = "3.19"))
    }

    @Test
    fun `nesrozumitelna znacka spadne, netvari se jako aktualni`() {
        assertThrows(IllegalStateException::class.java) {
            jeNovejsiVydani("nightly", kodTed = 131, jmenoTed = "3.19")
        }
    }
}
