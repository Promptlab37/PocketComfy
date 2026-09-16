package cz.promptlab.h3video

import cz.promptlab.h3video.update.UpdateChecker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

/**
 * Stažené APK se před instalací porovná s kontrolním součtem z poznámek
 * vydání, když tam je (audit 16. 9. 2026, L-4). Bez součtu se chová jako dřív.
 */
class UpdateSha256Test {

    private val hex = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"

    @Test
    fun `najde sha256 v poznamkach vydani v ruznych zapisech`() {
        assertEquals(hex, UpdateChecker.sha256ZPoznamek("## Co je nového\n- x\n\nSHA-256: `$hex`"))
        assertEquals(hex, UpdateChecker.sha256ZPoznamek("sha256=${hex.uppercase()}"))
        assertEquals(hex, UpdateChecker.sha256ZPoznamek("Kontrolní součet (SHA256) $hex konec"))
    }

    @Test
    fun `bez souctu vrati null a nic nekontroluje`() {
        assertNull(UpdateChecker.sha256ZPoznamek("jen poznámky bez součtu"))
        assertNull(UpdateChecker.sha256ZPoznamek("sha256: kratky"))
    }

    @Test
    fun `soucet souboru sedi se znamym vektorem`() {
        val f = File.createTempFile("h3sha", ".bin")
        try {
            f.writeText("abc")
            assertEquals(hex, UpdateChecker.sha256Souboru(f))
        } finally {
            f.delete()
        }
    }
}
