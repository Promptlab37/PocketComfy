package cz.promptlab.h3video

import cz.promptlab.h3video.data.SbScenar
import cz.promptlab.h3video.data.SbScenarModel
import cz.promptlab.h3video.data.SbScenarPokryti
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Kontrola pokrytí na 24 zápisech kritika a na případech z kontroly odborníků (30. 9. 2026). */
class SbScenarPokrytiVariantyTest {

    @Test
    fun `varianty - nic zbytecne nezastavi`() {
        val out = StringBuilder()
        File("src/test/resources/scenar_varianty.txt").readText().split("=====").map { it.trim() }.filter { it.isNotBlank() }.forEach { v ->
            val id = v.lineSequence().first()
            val t = v.substringAfter("\n")
            val s = SbScenar.rozeber(t) ?: return@forEach
            val n = SbScenarPokryti.nepokryte(t, s)
            if (n.isNotEmpty()) out.append("$id: $n\n")
        }
        File(File("build/sbscenar").also { it.mkdirs() }, "pokryti_varianty.txt").writeText(out.toString())
        assertEquals(out.toString(), "", out.toString())
    }

    @Test
    fun `pripady z kontroly`() {
        // Styl, tón, prázdné pole, prázdný první řádek — nezastaví.
        val t1 = "\nScénář k obrázkovému storyboardu\nStyl: realistický, teplé světlo.\nTón: dojemný.\n" +
            "[OKNO 1 | 0–3 s]\nObraz: Jana otevře dveře.\nZvuk: žádný\nDialog – žádný\n[OKNO 2 | 3–6 s]\nObraz: Tomáš se usměje.\nJana: „Ahoj.“"
        val s1 = SbScenar.rozeber(t1)!!
        assertEquals(emptyList<String>(), SbScenarPokryti.nepokryte(t1, s1))
        assertTrue(s1.kontinuita.contains("realistický"))
        // Replika s pomlčkou místo dvojtečky se nerozpozná → kontrola ji musí ukázat (jinak tichý záběr).
        val t2 = "[OKNO 1 | 0–3 s]\nObraz: Syn najde fotku.\nSyn – „Tati, podívej.“\n[OKNO 2 | 3–6 s]\nObraz: Otec se usměje."
        val s2 = SbScenar.rozeber(t2)!!
        if (s2.okna[0].repliky.isEmpty()) assertTrue(SbScenarPokryti.nepokryte(t2, s2).any { it.contains("Tati, podívej") })
        // Stejná replika ve dvou oknech: vynechaná v jednom se najde.
        val t3 = "[OKNO 1 | 0–3 s]\nObraz: Jana podá dárek.\nTomáš: „Děkuju!“\n[OKNO 2 | 3–6 s]\nObraz: Tomáš podá dárek.\nJana: „Děkuju!“"
        val s3 = SbScenar.rozeber(t3)!!
        val bez = s3.copy(okna = s3.okna.map { if (it.cislo == 2) it.copy(repliky = emptyList()) else it })
        assertTrue(SbScenarPokryti.nepokryte(t3, bez).isNotEmpty())
    }

    @Test
    fun `model - zahlavi, cislo v replice, replika bez uvozovek`() {
        val j = SbScenarModel.jednotky("ZÁBĚR 3 (6–9 s): Petra: „Mám 2 lístky!“\nZÁBĚR 4 (9–12 s): Obraz: Kino.\nTOMÁŠ (radostně): Jano! Tady jsem.")
        assertEquals(listOf("ZÁBĚR 3 (6–9 s):", "Petra: „Mám 2 lístky!“", "ZÁBĚR 4 (9–12 s):", "Obraz: Kino.", "TOMÁŠ (radostně): Jano! Tady jsem."), j.map { it.text })
    }
}
