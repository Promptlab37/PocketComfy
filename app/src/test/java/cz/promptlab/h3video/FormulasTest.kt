package cz.promptlab.h3video

import cz.promptlab.h3video.data.Aspect
import cz.promptlab.h3video.data.AppSettings
import cz.promptlab.h3video.data.Resolution
import cz.promptlab.h3video.data.TRAINED_MIN_FRAMES
import cz.promptlab.h3video.data.framesForSeconds
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Vzorce převzaté z workflow reprodukují chování ComfyUI uzlů do posledního
 * pixelu a snímku – tyhle testy hlídají, že je nikdo při úpravách nerozbije.
 */
class FormulasTest {

    // ---- H3 VALID FRAME LENGTH: max(5, round(a*24)) + (5 - (n % 17)) % 17

    @Test fun `5 sekund je presne trenovane minimum`() =
        assertEquals(TRAINED_MIN_FRAMES, framesForSeconds(5))

    @Test fun `8 sekund pada presne na mrizku`() =
        assertEquals(192, framesForSeconds(8))

    @Test fun `2 sekundy se zaokrouhli nahoru na platnou delku`() =
        assertEquals(56, framesForSeconds(2))

    @Test fun `vysledek je vzdy delka tvaru 17k plus 5`() {
        for (s in 2..15) {
            val frames = framesForSeconds(s)
            assertEquals("pro $s s vychazi $frames", 5, frames % 17)
        }
    }

    // ---- ResolutionSelector (multiple = 32) – tabulka z dokumentace uzlu

    @Test fun `0_4 MP na 16 ku 9 je 864x480`() =
        assertEquals(864 to 480, Resolution.calc(Aspect.LANDSCAPE_16_9, 0.4f))

    @Test fun `0_98 MP na 16 ku 9 je 1344x768`() =
        assertEquals(1344 to 768, Resolution.calc(Aspect.LANDSCAPE_16_9, 0.98f))

    @Test fun `2 MP na 16 ku 9 je 1920x1088`() =
        assertEquals(1920 to 1088, Resolution.calc(Aspect.LANDSCAPE_16_9, 2.0f))

    @Test fun `na vysku je to zrcadlove`() =
        assertEquals(480 to 864, Resolution.calc(Aspect.PORTRAIT_9_16, 0.4f))

    // ---- Normalizace adresy serveru

    @Test fun `prazdny vstup zustava prazdny - server nenastaven`() =
        assertEquals("", AppSettings.normalizeUrl("", default = "http://10.0.0.9:8189"))

    @Test fun `host vychoziho serveru bez portu prevezme jeho port`() =
        assertEquals(
            "http://10.0.0.9:8189",
            AppSettings.normalizeUrl("10.0.0.9", default = "http://10.0.0.9:8189")
        )

    @Test fun `jina IP bez portu dostane 8188`() =
        assertEquals(
            "http://192.168.0.7:8188",
            AppSettings.normalizeUrl("192.168.0.7", default = "http://10.0.0.9:8189")
        )

    @Test fun `bez vychoziho serveru se doplni 8188`() =
        assertEquals(
            "http://10.0.0.9:8188",
            AppSettings.normalizeUrl("10.0.0.9", default = "")
        )

    @Test fun `vlastni port zustava a lomitko se usekne`() =
        assertEquals(
            "http://10.0.2.2:9999",
            AppSettings.normalizeUrl("http://10.0.2.2:9999/", default = "")
        )

    // ---- Rychlé volby serveru ze sestavení

    @Test fun `rychle volby se ctou z retezce url-popisek`() =
        assertEquals(
            listOf("http://a:1" to "Doma", "http://b:2" to "http://b:2"),
            AppSettings.parsePresets("http://a:1|Doma;http://b:2")
        )

    @Test fun `prazdne rychle volby = zadne pilulky`() =
        assertEquals(emptyList<Pair<String, String>>(), AppSettings.parsePresets(""))
}
