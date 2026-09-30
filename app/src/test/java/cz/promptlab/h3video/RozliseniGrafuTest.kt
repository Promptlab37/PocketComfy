package cz.promptlab.h3video

import cz.promptlab.h3video.comfy.RozliseniGrafu
import cz.promptlab.h3video.comfy.SbFilmBuilder
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

/** Rozlišení na obrazovce průběhu = přesně to, co spočítá balík / co je v grafu. */
class RozliseniGrafuTest {
    @Test
    fun `stitky balicku jako balicek`() {
        assertEquals(864 to 480, RozliseniGrafu.zeStitku("480P", "16:9"))
        assertEquals(960 to 544, RozliseniGrafu.zeStitku("540P", "16:9"))
        assertEquals(1280 to 736, RozliseniGrafu.zeStitku("720P", "16:9"))
        assertEquals(544 to 960, RozliseniGrafu.zeStitku("540P", "9:16"))
        assertEquals(1344 to 768, RozliseniGrafu.zeStitku("768P", "Custom", 1344, 768))
    }

    @Test
    fun `z grafu - stitek ma prednost pred vychozimi width height`() {
        val wf = JSONObject("""{"328":{"class_type":"MiniMaxH3EasyContextSegments_SatoDive","inputs":{"resolution":"540P","aspect_ratio":"16:9","width":1344,"height":768}}}""")
        assertEquals("960×544", RozliseniGrafu.text(wf))
        val px = JSONObject("""{"1":{"class_type":"EmptyLatentImage","inputs":{"width":864,"height":480}},"2":{"class_type":"X","inputs":{"width":1376,"height":768}}}""")
        assertEquals("1376×768", RozliseniGrafu.text(px))
    }
}
