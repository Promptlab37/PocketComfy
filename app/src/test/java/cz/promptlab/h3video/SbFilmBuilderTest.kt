package cz.promptlab.h3video

import cz.promptlab.h3video.comfy.H3RefWriteBuilder
import cz.promptlab.h3video.comfy.SbFilmBuilder
import cz.promptlab.h3video.data.LongMmPomer
import cz.promptlab.h3video.data.SbFilmPlan
import cz.promptlab.h3video.data.SbFilmPrepis
import cz.promptlab.h3video.data.SbFilmScene
import cz.promptlab.h3video.data.SbPanel
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Grafy Filmu ze storyboardu. Test je i vypíše do `build/sbfilm-grafy/`, kde je
 * prověří nástroj proti `/object_info` (jména uzlů, nabídky, soubory na disku).
 */
class SbFilmBuilderTest {

    private val panely = listOf(4.0, 3.0, 4.0, 3.0, 4.0, 5.0, 4.0, 3.0).mapIndexed { i, s ->
        SbPanel(i + 1, "panel ${i + 1}", "wide", "static", s)
    }
    private val scene = SbFilmScene(panely = panely, pomer = LongMmPomer.NASIRKU)
    private val useky = SbFilmPlan.rozdel(panely)
    private val zadani = useky.mapIndexed { k, _ -> "[Shot 1] part $k <Picture 1> <Picture 2>. [Shot 2] At 00:03.000, x" }

    private fun graf() = SbFilmBuilder.buildFilm(scene, useky, zadani, listOf("sb.png", "p1.png"), "16:9", 7L)

    private fun JSONObject.vstupy(id: String) = getJSONObject(id).getJSONObject("inputs")

    @Test
    fun `retez useku - setup jen u prvniho, dalsi na predchozim, zadani v override`() {
        val wf = graf()
        val kroky = wf.keys().asSequence().filter { wf.getJSONObject(it).getString("class_type") == "MiniMaxH3EasySegmentStep_SatoDive" }
            .map { it.toInt() }.sorted().toList()
        assertEquals(useky.size, kroky.size)
        assertTrue(wf.vstupy(kroky[0].toString()).has("sample_setup"))
        for (i in 1 until kroky.size) {
            val v = wf.vstupy(kroky[i].toString())
            assertFalse(v.has("sample_setup"))
            assertEquals(kroky[i - 1].toString(), v.getJSONArray("previous_segment").getString(0))
            assertEquals(zadani[i], v.getString("prompt_override"))
        }
        assertEquals(kroky.last().toString(),
            wf.vstupy(SbFilmBuilder.N_COLLECT).getJSONArray("final_segment").getString(0))
    }

    @Test
    fun `plan urcuje delky - celkem a znacky zacatku useku`() {
        val k = graf().vstupy(SbFilmBuilder.N_KONTEXT)
        assertEquals(30.0, k.getDouble("seconds"), 0.001)
        val casti = k.getString("prompt").split("\n---\n")
        assertEquals(useky.size, casti.size)
        assertTrue(casti[1].startsWith("[Shot 1] At " + SbFilmPlan.casH3(useky[0].sekundy)))
        // Každý úsek označí storyboard i postavu — jinak by je nedostal.
        assertTrue(casti.all { it.contains("<Picture 1>") && it.contains("<Picture 2>") })
        assertEquals("native_guide", k.getString("continuity_mode"))
    }

    @Test
    fun `storyboard je prvni obrazek v referencich`() {
        val wf = graf()
        val media = wf.vstupy(SbFilmBuilder.N_MEDIA)
        assertEquals(2, media.getInt("image_count"))
        assertEquals("sb.png", wf.vstupy(media.getJSONArray("image_1").getString(0)).getString("image"))
    }

    @Test
    fun `kroky se pocitaji pres cely film`() {
        val tridy = SbFilmBuilder.nodeClasses(graf())
        val useky = tridy.filterValues { it == "MiniMaxH3EasySegmentStep_SatoDive" }.keys.sortedBy { it.toInt() }
        assertEquals(0 to 24, SbFilmBuilder.globalniKrok(useky[0], tridy, 0, 8))
        assertEquals(11 to 24, SbFilmBuilder.globalniKrok(useky[1], tridy, 3, 8))
        assertEquals(24 to 24, SbFilmBuilder.globalniKrok(useky[2], tridy, 8, 8))
        // Uzel mimo úseky: beze změny.
        assertEquals(3 to 8, SbFilmBuilder.globalniKrok("999", tridy, 3, 8))
    }

    @Test
    fun `graf nema visici odkazy`() {
        val wf = graf()
        wf.keys().forEach { id ->
            val ins = wf.vstupy(id)
            ins.keys().forEach { k ->
                val v = ins.opt(k)
                if (v is JSONArray && v.length() == 2 && v.opt(0) is String) {
                    assertTrue("uzel $id → ${v.getString(0)}", wf.has(v.getString(0)))
                }
            }
        }
    }

    @Test
    fun `hlidka useku - zaznamy s casy od nuly a role obrazku`() {
        val h = SbFilmPrepis.hlidka(2, useky[1], 1, useky.size)
        assertTrue(h.contains("<Picture 1> is a storyboard reference"))
        assertTrue(h.contains("<Picture 2> show the characters"))
        assertTrue(h.contains("continues directly from the previous part"))
        assertTrue(h.contains("[Shot 1] storyboard panel ${useky[1].panely[0].cislo}"))
        if (useky[1].panely.size > 1) assertTrue(h.contains("[Shot 2] At " + SbFilmPlan.casH3(useky[1].panely[0].sekundy)))
        // Závorka pokynu na vlastním řádku (jinak ji model opíše).
        assertTrue(h.endsWith("\n]"))
    }

    @Test
    fun `cteni - otazka nahrazuje roli a vystup je popis`() {
        val wf = SbFilmBuilder.buildCteni("sb.png", H3RefWriteBuilder.CAPTIONER_ODVAZANY, 3L)
        assertEquals(SbFilmPlan.OTAZKA_CTENI, wf.vstupy(SbFilmBuilder.N_CTENI).getString("instruction"))
        assertEquals(1, wf.vstupy(SbFilmBuilder.N_CTENI_VYSTUP).getJSONArray("source").getInt(1))
    }

    @Test
    fun `vypis grafu pro kontrolu proti serveru`() {
        val dir = File("build/sbfilm-grafy").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        File(dir, "film.json").writeText(graf().toString(2))
        File(dir, "cteni.json").writeText(
            SbFilmBuilder.buildCteni("sb.png", H3RefWriteBuilder.CAPTIONER_ODVAZANY, 3L).toString(2),
        )
    }
}
