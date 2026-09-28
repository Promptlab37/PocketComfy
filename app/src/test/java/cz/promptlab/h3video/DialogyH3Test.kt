package cz.promptlab.h3video

import cz.promptlab.h3video.data.DialogyH3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Repliky pro vylepšovač MiniMax: dialog ve tvaru scénáře 28. 9. 2026
 * přepisovač celý zahodil a H3 pak mluvil anglicky.
 */
class DialogyH3Test {

    private val scenar = """
        Scéna: Noční ulice v dešti, neonová světla. Detektiv Novák stojí pod lampou.

        DIALOG:
        Žena: Pane Nováku, konečně vás nacházím.
        Novák: Kdo vás poslal?
        Žena (šeptem): „Nikdo. Přišla jsem sama.“

        Kamera: pomalý nájezd na tvář detektiva, noir styl.
    """.trimIndent()

    @Test
    fun `scenar - postavy ano, popisky scenare ne`() {
        val r = DialogyH3.najdi(scenar)
        assertEquals(3, r.size)
        assertEquals(listOf("Žena", "Novák", "Žena"), r.map { it.mluvci })
        assertEquals("Pane Nováku, konečně vás nacházím.", r[0].text)
        // Uvozovky kolem repliky se nepřenášejí.
        assertEquals("Nikdo. Přišla jsem sama.", r[2].text)
        assertFalse(r.any { it.text.contains("nájezd") || it.text.contains("Noční") })
    }

    @Test
    fun `hlidka ma format H3 - Czech, stala ID a vsechny repliky`() {
        val h = DialogyH3.hlidka(scenar)
        assertTrue(h.contains("<d>[Czech] …</d>"))
        assertTrue(h.contains("Žena = (S1), Novák = (S2)"))
        assertTrue(h.contains("1. Žena (S1) says: <d>[Czech] Pane Nováku, konečně vás nacházím.</d>"))
        assertTrue(h.contains("2. Novák (S2) says: <d>[Czech] Kdo vás poslal?</d>"))
        // Závorka pokynu na vlastním řádku — jinak ji model opsal do repliky.
        assertTrue(h.contains("3. Žena (S1) says: <d>[Czech] Nikdo. Přišla jsem sama.</d>\nEnd of dialogue.\n]"))
        assertTrue(h.contains("Never translate"))
    }

    @Test
    fun `repliky v uvozovkach uvnitr vety`() {
        val t = "Servírka se usměje a řekne: „Dobré ráno, dáte si kafe?“ Muž odpoví: „Dneska ne.“"
        val r = DialogyH3.najdi(t)
        assertEquals(listOf("Dobré ráno, dáte si kafe?", "Dneska ne."), r.map { it.text })
        assertTrue(r.all { it.mluvci == null })
        // Uvozovky ve větách přepisovač zachová sám; pokyn je tam naopak
        // vynechal (živý běh 28. 9. 2026) — proto se nepřidává.
        assertEquals("", DialogyH3.hlidka(t))
    }

    @Test
    fun `bez dialogu se zadani nemeni`() {
        val t = "Kočka skáče přes plot, zlaté světlo, pomalý nájezd kamery."
        assertEquals("", DialogyH3.hlidka(t))
        assertEquals(
            "Kočka skáče přes plot, zlaté světlo, pomalý nájezd kamery. Do not add any on-screen text or captions unless explicitly requested.",
            DialogyH3.proPrepisovac(t),
        )
        // Jedno slovo v uvozovkách je název, ne replika.
        assertTrue(DialogyH3.najdi("Na ceduli je nápis „Pekárna“.").isEmpty())
    }

    @Test
    fun `jazyk - cestina ano, slovenstina a anglictina ne`() {
        assertEquals("Czech", DialogyH3.jazyk(DialogyH3.najdi("A: Řekni mi, kde je.")))
        assertNull(DialogyH3.jazyk(DialogyH3.najdi("A: Povedz mi, kde bola tá ľalia.")))
        assertNull(DialogyH3.jazyk(DialogyH3.najdi("Anna: Where have you been?")))
        assertTrue(DialogyH3.hlidka("Anna: Where have you been?").contains("original language"))
    }

    @Test
    fun `vypis pro zivy test`() {
        java.io.File(System.getProperty("java.io.tmpdir"), "dialog_scenar.txt")
            .writeText(DialogyH3.proPrepisovac(scenar))
    }
}
