package cz.promptlab.h3video

import cz.promptlab.h3video.data.LongMmPomer
import cz.promptlab.h3video.data.SbScenar
import cz.promptlab.h3video.data.SbScenarModel
import cz.promptlab.h3video.data.SbScenarPokryti
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 5.27: scénář napsaný přesně podle „Zadání pro ChatGPT“ se rozebere celý a správně. */
class SbScenarZadaniChatGptTest {

    private val cs = """
        STORYBOARD – DOPIS
        Formát: 9:16, přibližně 12 sekund.
        Postavy:
        Marie: 70 let, šedé vlasy v drdolu, modrá vesta
        Tomáš: 12 let, hnědé vlasy, červená mikina
        Rekvizity:
        Dopis: starý zažloutlý dopis v obálce
        Kontinuita: Celý film se odehrává v jedné kuchyni u okna.

        [OKNO 1 | 0–4 s] NÁLEZ
        Obraz: Tomáš najde v zásuvce starý dopis.
        Emoce: Zvědavost.
        Tomáš (překvapeně): „Babi, co je tohle?“
        Zvuk: Šustění papíru.

        [OKNO 2 | 4–8 s] VZPOMÍNKA
        Obraz: Marie si vezme dopis a usměje se.
        Marie (tiše a dojatě): „To mi psal tvůj děda.“

        [OKNO 3 | 8–12 s] ZÁVĚR
        Obraz: Oba sedí u okna a čtou dopis.
        Text na videu: „Vzpomínky, které zůstávají.“
    """.trimIndent()

    private val en = """
        STORYBOARD – THE LETTER
        Format: 16:9, about 8 seconds.
        Characters:
        Mary: 70 years, grey hair in a bun, blue vest
        Tom: 12 years, brown hair, red hoodie
        Continuity: The whole film takes place in one kitchen.

        [WINDOW 1 | 0–4 s] FOUND
        Visual: Tom finds an old letter in a drawer.
        Tom (surprised): "Grandma, what is this?"
        Sound: Rustling paper.

        [WINDOW 2 | 4–8 s] MEMORY
        Visual: Mary takes the letter and smiles.
        On-screen text: "Memories that stay."
    """.trimIndent()

    @Test
    fun `cesky podle zadani`() {
        assertTrue(SbScenarModel.ZADANI_CHATGPT_CS.contains("[OKNO 1 | 0–4 s]"))
        val s = SbScenar.rozeber(cs)!!
        assertEquals("DOPIS", s.nazev)
        assertEquals(LongMmPomer.NAVYSKU, s.pomer)
        assertEquals(listOf("Marie", "Tomáš", "Dopis"), s.postavy.keys.toList())
        assertEquals(listOf(0.0, 4.0, 8.0), s.okna.map { it.od })
        assertEquals("Tomáš" to "překvapeně", s.okna[0].repliky.single().let { it.kdo to it.podani })
        assertEquals("tiše a dojatě", s.okna[1].repliky.single().podani)
        assertEquals(listOf("Vzpomínky, které zůstávají."), s.okna[2].texty)
        assertTrue(s.kontinuita.contains("jedné kuchyni"))
        assertEquals(emptyList<String>(), SbScenarPokryti.nepokryte(cs, s))
        assertEquals("a woman in her early 70s with a soft, gentle, slightly husky voice", SbScenar.hlasy(s)["Marie"])
    }

    @Test
    fun `anglicky podle zadani`() {
        val s = SbScenar.rozeber(en)!!
        assertEquals(LongMmPomer.NASIRKU, s.pomer)
        assertEquals(listOf("Mary", "Tom"), s.postavy.keys.toList())
        assertEquals("surprised", s.okna[0].repliky.single().podani)
        assertEquals(listOf("Memories that stay."), s.okna[1].texty)
        assertEquals(emptyList<String>(), SbScenarPokryti.nepokryte(en, s))
    }
}
