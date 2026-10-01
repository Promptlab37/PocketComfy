package cz.promptlab.h3video

import cz.promptlab.h3video.comfy.H3RefWriteBuilder
import cz.promptlab.h3video.data.SbFilmPrepis
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 5.52: „Z fotky jen tvář“ — skutečná tvář na postavu ze storyboardu, tělo a chlupy zůstanou. */
class SbJenTvarTest {

    @Test
    fun `jen tvar prida vyjimku jen pro zvolenou fotku`() {
        val v = SbFilmPrepis.jenTvar(listOf(false, true), 2, listOf("Táta", "Příšera"))
        assertTrue(v.contains("Exception for <Picture 3>: take only the face"))
        assertTrue(v.contains("Příšera's body, build, skin and its texture, hair, fur and clothing look exactly as in the storyboard"))
        assertTrue(!v.contains("<Picture 2>"))
    }

    @Test
    fun `bez volby se zadani nemeni`() {
        assertEquals("", SbFilmPrepis.jenTvar(listOf(false, false), 2, listOf("A", "B")))
        assertEquals("", SbFilmPrepis.jenTvar(emptyList(), 2, emptyList()))
    }

    @Test
    fun `vidouci model u fotky jen tvar popise jen oblicej`() {
        val o = JSONObject(H3RefWriteBuilder.otazky(3, true, setOf(2)))
        assertEquals(H3RefWriteBuilder.OTAZKA_POSTAVA, o.getJSONObject("ref_1").getString("text"))
        assertEquals(H3RefWriteBuilder.OTAZKA_TVAR, o.getJSONObject("ref_2").getString("text"))
    }
}
