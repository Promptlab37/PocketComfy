package cz.promptlab.h3video

import cz.promptlab.h3video.data.Mode
import cz.promptlab.h3video.data.NABIZENE_KARTY
import cz.promptlab.h3video.data.Skupina
import cz.promptlab.h3video.data.lzeSkryt
import cz.promptlab.h3video.data.prvniViditelna
import cz.promptlab.h3video.data.viditelne
import cz.promptlab.h3video.data.viditelneSkupiny
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Nastavení → Karty v aplikaci: co se po skrytí karet ukazuje. */
class KartyVAplikaciTest {

    @Test fun `bez skrytych karet se ukazuje vse`() {
        assertEquals(Skupina.entries, viditelneSkupiny(emptySet()))
        Skupina.entries.forEach { assertEquals(it.karty, it.viditelne(emptySet())) }
    }

    @Test fun `skryta karta zmizi, skupina bez karet taky`() {
        val skryte = setOf(Mode.MUSIC, Mode.EDIT)
        assertFalse(Mode.EDIT in Skupina.OBRAZEK.viditelne(skryte))
        assertFalse(Skupina.ZVUK in viditelneSkupiny(skryte))
        assertTrue(Skupina.OBRAZEK in viditelneSkupiny(skryte))
    }

    @Test fun `otevrena skryta karta ma porad svou zalozku`() {
        val skryte = setOf(Mode.MUSIC)
        assertTrue(Skupina.ZVUK in viditelneSkupiny(skryte, otevrena = Mode.MUSIC))
        assertEquals(listOf(Mode.MUSIC), Skupina.ZVUK.viditelne(skryte, Mode.MUSIC))
    }

    @Test fun `posledni viditelnou kartu skryt nejde`() {
        val vseKromeJedne = NABIZENE_KARTY.filter { it != Mode.MODEL3D }.toSet()
        assertFalse(lzeSkryt(vseKromeJedne, Mode.MODEL3D))
        assertTrue(lzeSkryt(emptySet(), Mode.MODEL3D))
        assertEquals(Mode.MODEL3D, prvniViditelna(vseKromeJedne))
    }

    @Test fun `prvni viditelna preskoci skryte`() {
        assertEquals(NABIZENE_KARTY.first(), prvniViditelna(emptySet()))
        assertEquals(NABIZENE_KARTY[1], prvniViditelna(setOf(NABIZENE_KARTY.first())))
    }
}
