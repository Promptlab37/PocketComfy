package cz.promptlab.h3video

import cz.promptlab.h3video.comfy.SbHudbaBuilder
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/** Pomocník pro ruční ověřovací běh: graf hudby k filmu. Běží jen s SB_HUDBA_DO=<soubor>. */
class SbHudbaGrafVypisTest {
    @Test
    fun vypis() {
        val cil = System.getenv("SB_HUDBA_DO")
        assumeTrue(cil != null)
        val wf = SbHudbaBuilder.build(
            SbHudbaBuilder.zVystupu("PocketSbFilm_DigitalHuman_00001_.mp4", ""),
            "upbeat, comedic, electronic beat with horns, 120 BPM", 18.17, SbHudbaBuilder.HLASITOST_VYCHOZI, 4242L,
        )
        File(cil).writeText(wf.toString(1))
    }
}
