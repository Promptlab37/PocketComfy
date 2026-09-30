package cz.promptlab.h3video

import cz.promptlab.h3video.data.SbScenar
import cz.promptlab.h3video.util.TextZeSouboru
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Scénář ze souboru (5.20): Word a prostý text; PDF ověřeno na emulátoru. */
class TextZeSouboruTest {

    private val scenar = File("src/test/resources/scenar_svatba.txt").readText()

    @Test
    fun `docx - odstavce, tabulator, entity`() {
        val odstavce = scenar.lines().joinToString("") { r ->
            "<w:p><w:r><w:t xml:space=\"preserve\">" + r.replace("&", "&amp;").replace("<", "&lt;").replace("\"", "&quot;") +
                "</w:t></w:r></w:p>"
        }
        val xml = "<?xml version=\"1.0\"?><w:document><w:body>$odstavce</w:body></w:document>"
        val zip = ByteArrayOutputStream().also { b ->
            ZipOutputStream(b).use { z ->
                z.putNextEntry(ZipEntry("[Content_Types].xml")); z.write("<x/>".toByteArray()); z.closeEntry()
                z.putNextEntry(ZipEntry("word/document.xml")); z.write(xml.toByteArray()); z.closeEntry()
            }
        }.toByteArray()
        val text = TextZeSouboru.uprav(TextZeSouboru.zDocx(zip)!!)
        assertEquals(TextZeSouboru.uprav(scenar), text)
        // Rozebere se stejně jako vložený text.
        assertEquals(4, SbScenar.rozeber(text)!!.okna.size)
        assertEquals("a\tb\nc", TextZeSouboru.docxXml("<w:p><w:r><w:t>a</w:t><w:tab/><w:t>b</w:t></w:r></w:p><w:p><w:t>c</w:t></w:p>").trim())
    }

    /** Zalomení po ~45 znacích jako z PDF (emulátor 30. 9. 2026: „Tati,“ ⏎ „podívej.“). */
    private fun zalom(t: String, sirka: Int = 45): String = t.lines().joinToString("\n") { r ->
        val out = StringBuilder()
        var radek = 0
        r.split(" ").forEach { w ->
            if (radek > 0 && radek + 1 + w.length > sirka) { out.append('\n'); radek = 0 } else if (radek > 0) { out.append(' '); radek++ }
            out.append(w); radek += w.length
        }
        out.toString()
    }

    @Test
    fun `pdf - zalomene radky i repliky`() {
        val pdf = zalom(scenar)
        assertTrue(pdf.contains("„Tati,\npodívej.“") || pdf.contains("„Tati, podívej.“"))
        for (t in listOf(pdf, TextZeSouboru.spojZalomene(pdf))) {
            val s = SbScenar.rozeber(t)!!
            assertEquals(4, s.okna.size)
            assertEquals(listOf("Tati, podívej.", "Na ten den si pamatuju."), s.okna.flatMap { o -> o.repliky.map { it.text } })
            assertEquals(listOf("Syn", "Otec"), s.okna.flatMap { o -> o.repliky.map { it.kdo } })
            assertEquals(listOf(0.0, 4.0, 7.0, 11.0), s.okna.map { it.od })
        }
    }

    @Test
    fun `txt - utf8 s BOM i windows-1250`() {
        val bom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + "Otec tiše: „Ahoj.“".toByteArray()
        assertEquals("Otec tiše: „Ahoj.“", TextZeSouboru.zTextu(bom))
        val cp1250 = "Příliš žluťoučký kůň".toByteArray(charset("windows-1250"))
        assertEquals("Příliš žluťoučký kůň", TextZeSouboru.zTextu(cp1250))
        assertTrue(TextZeSouboru.uprav("a\r\n\r\n\r\n\r\nb  \n").let { it == "a\n\nb" })
    }
}
