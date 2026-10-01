package cz.promptlab.h3video

import cz.promptlab.h3video.data.SbOcrShoda
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 5.67: OCR rozhoduje o tvaru slov repliky, vidoucí model o struktuře (Pepa, sada storyboardů 1. 10. 2026). */
class SbOcrShodaTest {

    private val ocr12 = "12. Finální pointa: oba sedí potmě na gauči, všechna chytrá zařízení jsou vypnutá. " +
        "Pepa: „Hlavně že mi ta chytrá domácnost šetří čas.“ Kamarád: „Jo. Už jsme ušetřili celej večer.“"

    @Test fun `spisovne opravene slovo vrati OCR`() {
        val (t, op) = SbOcrShoda.oprav("Jo. Už jsme ušetřili celý večer.", ocr12)
        assertEquals("Jo. Už jsme ušetřili celej večer.", t)
        assertEquals(listOf(SbOcrShoda.Oprava("celý", "celej")), op)
    }

    @Test fun `preklep a vynechane pismeno podle OCR`() {
        assertEquals("A nabiječku ovládá taky AI?",
            SbOcrShoda.oprav("A nabíječku ovládá taky AI?", "11. Telefon zobrazí vybitou baterii. Kamarád: „A nabiječku ovládá taky Al?“").first)
        assertEquals("Tak teď je to fakt chytrý. Už to neposluchá vůbec nikoho.",
            SbOcrShoda.oprav("Tak teď je to fakt chytrý. Už to neposlouchá vůbec nikoho.",
                "Kamarád: „Tak teď je to fakt chytrý. Už to neposluchá vůbec nikoho.“").first)
    }

    @Test fun `AI a Al se nemeni, spravne cteni zustane`() {
        val (t, op) = SbOcrShoda.oprav("Objednávám obývací stůl.", "Al: „Objednávám obývací stůl.“")
        assertEquals("Objednávám obývací stůl.", t)
        assertTrue(op.isEmpty())
        assertTrue(SbOcrShoda.oprav("Spouštím AI úklid.", "Spouštím Al úklid.").second.isEmpty())
    }

    @Test fun `cizi text OCR nic nemeni`() {
        val (t, op) = SbOcrShoda.oprav("Přestaň scrollovat!", "Obraz: Pavel sedí na jediné židli. Emoce: Ledový klid.")
        assertEquals("Přestaň scrollovat!", t)
        assertTrue(op.isEmpty())
    }

    @Test fun `repliky panelu, jmena mluvcich se nemeni`() {
        val (r, op) = SbOcrShoda.opravRepliky("Pepa: \"Hlavně že mi ta chytrá domácnost šetří čas.\"; Kamarád: \"Jo. Už jsme ušetřili celý večer.\"", ocr12)
        assertTrue(r, r.contains("Kamarád: \"Jo. Už jsme ušetřili celej večer.\""))
        assertTrue(r, r.startsWith("Pepa: \"Hlavně"))
        assertEquals(1, op.size)
    }
}
