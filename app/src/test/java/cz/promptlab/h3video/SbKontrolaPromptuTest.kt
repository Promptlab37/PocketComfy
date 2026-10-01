package cz.promptlab.h3video

import cz.promptlab.h3video.data.SbFilmPrepis
import cz.promptlab.h3video.data.SbKontrolaPromptu
import cz.promptlab.h3video.data.SbPanel
import cz.promptlab.h3video.data.SbUsek
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** 5.67: přísná kontrola zadání úseku — na skutečných výstupech přepisovače ze serveru. */
class SbKontrolaPromptuTest {

    private val id = mapOf("Příšera" to "S1")
    private val u1 = SbUsek(listOf(
        SbPanel(1, "a", "close-up", "static", 4.0, repliky = "Příšera: „Pro dnes bylo internetu dost.“"),
        SbPanel(2, "b", "medium", "static", 2.0, repliky = "Příšera: „Běž spát.“"),
        SbPanel(3, "c", "wide", "static", 3.0),
    ))
    private val u2 = SbUsek(listOf(
        SbPanel(4, "a", "wide", "static", 4.0),
        SbPanel(5, "b", "close-up", "static", 3.0, repliky = "Příšera: „Ty tu ještě jsi?“"),
        SbPanel(6, "c", "wide", "static", 4.0, repliky = "Příšera: „Vypnout. A do postele!“"),
    ))

    /** Stejné úpravy jako v appce ([cz.promptlab.h3video.data.SbPromptyTok.dokonci] pro storyboard). */
    private fun hotovy(soubor: String, usek: SbUsek): String {
        val sp = SbFilmPrepis
        val zabalene = sp.doplnD(sp.opravZnackyAPozadi(File("src/test/resources/$soubor").readText()), usek, "Czech", id)
        val cisty = sp.opravObrazky(sp.ocistiPrepis(zabalene, usek.panely.size), 1)
        return sp.doplnIdMluvciho(sp.bezDuplicitD(sp.bezUvozovekMimoD(sp.doplnD(cisty, usek, "Czech", id))), id)
    }

    @Test fun `hotove zadani projde`() {
        assertEquals(emptyList<String>(), SbKontrolaPromptu.zkontroluj(hotovy("prisera4_usek1.txt", u1), u1))
        assertEquals(emptyList<String>(), SbKontrolaPromptu.zkontroluj(hotovy("prisera4_usek2.txt", u2), u2))
    }

    @Test fun `pozmenena replika neprojde`() {
        val z = hotovy("prisera4_usek2.txt", u2).replace("Ty tu ještě jsi?", "Ty tu ještě seš?")
        val ch = SbKontrolaPromptu.zkontroluj(z, u2)
        assertTrue(ch.toString(), ch.any { "chybí replika" in it })
        assertTrue(ch.toString(), ch.any { "replika navíc" in it })
    }

    @Test fun `syrovy vystup bez d neprojde`() {
        val syrovy = File("src/test/resources/prisera2_usek1_bez_d.txt").readText()
        assertTrue(SbKontrolaPromptu.zkontroluj(syrovy, u1).isNotEmpty())
    }

    /** 5.67: vynucení opraví pozměněnou repliku, špatný čas a chybějící záběr — jeden průchod stačí. */
    @Test fun `vynuceni opravi chyby prepisovace`() {
        val z = hotovy("prisera4_usek2.txt", u2)
            .replace("Ty tu ještě jsi?", "Ty tu ještě seš?")
            .replace(Regex("""At 00:\d{2}\.\d{3}"""), "At 00:09.999")
        assertTrue(SbKontrolaPromptu.zkontroluj(z, u2).isNotEmpty())
        val opraveny = cz.promptlab.h3video.data.SbVynuceni.vynut(z, u2, "Czech", id)
        assertEquals(emptyList<String>(), SbKontrolaPromptu.zkontroluj(opraveny, u2))
        assertTrue(opraveny.contains("At 00:04.000") && opraveny.contains("At 00:07.000"))
        // Bez záběru 3: doplní se z popisu panelu i s replikou.
        val bez3 = hotovy("prisera4_usek2.txt", u2).let { it.substring(0, it.lastIndexOf("[Shot 3]")) + it.substring(it.indexOf("overall_soundscape:")) }
        assertEquals(emptyList<String>(), SbKontrolaPromptu.zkontroluj(cz.promptlab.h3video.data.SbVynuceni.vynut(bez3, u2, "Czech", id), u2))
        // Hotové zadání zůstane beze změny v replikách.
        val dobry = hotovy("prisera4_usek1.txt", u1)
        assertEquals(emptyList<String>(), SbKontrolaPromptu.zkontroluj(cz.promptlab.h3video.data.SbVynuceni.vynut(dobry, u1, "Czech", id), u1))
    }

    @Test fun `replika v jinem zaberu neprojde`() {
        val z = hotovy("prisera4_usek1.txt", u1)
        // Prohodit záběry 1 a 2 → repliky stojí v cizím záběru.
        val prohozeny = z.replace("[Shot 1]", "[Shot X]").replace("[Shot 2]", "[Shot 1]").replace("[Shot X]", "[Shot 2]")
        val ch = SbKontrolaPromptu.zkontroluj(prohozeny, u1)
        assertTrue(ch.toString(), ch.isNotEmpty())
    }
}
