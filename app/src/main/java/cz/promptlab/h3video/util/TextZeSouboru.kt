package cz.promptlab.h3video.util

import android.content.Context
import android.net.Uri
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.util.zip.ZipInputStream

/**
 * Scénář ze souboru (5.20): .txt / .md, .docx (Word) a .pdf s textovou
 * vrstvou. Naskenované PDF text nemá — vrátí se prázdný řetězec.
 * PDF čte PdfBox pro Android (Apache 2.0); ostatní bez knihoven.
 */
object TextZeSouboru {

    /** Nejvíc, co se z jednoho souboru načte — scénář má jednotky kB. */
    private const val MAX_BAJTU = 8 * 1024 * 1024

    fun nacti(ctx: Context, uri: Uri): String? = runCatching {
        val typ = ctx.contentResolver.getType(uri).orEmpty()
        val jmeno = jmenoSouboru(ctx, uri).lowercase()
        val bajty = ctx.contentResolver.openInputStream(uri)?.use { cti(it) } ?: return null
        when {
            typ == "application/pdf" || jmeno.endsWith(".pdf") -> spojZalomene(zPdf(ctx, bajty))
            typ.contains("wordprocessingml") || jmeno.endsWith(".docx") -> zDocx(bajty)
            else -> zTextu(bajty)
        }?.let { uprav(it) }
    }.getOrNull()

    private fun cti(vstup: InputStream): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(64 * 1024)
        while (true) {
            val n = vstup.read(buf)
            if (n < 0) break
            out.write(buf, 0, n)
            require(out.size() <= MAX_BAJTU) { "soubor je moc velký" }
        }
        return out.toByteArray()
    }

    private fun jmenoSouboru(ctx: Context, uri: Uri): String = runCatching {
        ctx.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull() ?: uri.lastPathSegment.orEmpty()

    private fun zPdf(ctx: Context, bajty: ByteArray): String {
        com.tom_roush.pdfbox.android.PDFBoxResourceLoader.init(ctx.applicationContext)
        return com.tom_roush.pdfbox.pdmodel.PDDocument.load(bajty).use { dok ->
            com.tom_roush.pdfbox.text.PDFTextStripper().apply { sortByPosition = true }.getText(dok)
        }
    }

    /** Word: text odstavců z `word/document.xml`, odstavec = řádek. */
    fun zDocx(bajty: ByteArray): String? {
        ZipInputStream(bajty.inputStream()).use { zip ->
            while (true) {
                val e = zip.nextEntry ?: return null
                if (e.name == "word/document.xml") return docxXml(zip.readBytes().toString(Charsets.UTF_8))
            }
        }
    }

    fun docxXml(xml: String): String = xml
        .replace(Regex("""<w:tab/>"""), "\t")
        .replace(Regex("""<w:br[^>]*/>"""), "\n")
        .replace(Regex("""</w:p>"""), "\n")
        .replace(Regex("""<[^>]+>"""), "")
        .replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&apos;", "'")
        .replace(Regex("""&#(\d+);""")) { it.groupValues[1].toInt().toChar().toString() }
        .replace("&amp;", "&")

    /** Prostý text: UTF-8 (i s BOM), jinak Windows-1250 (starší české soubory). */
    fun zTextu(bajty: ByteArray): String {
        val bezBom = if (bajty.size >= 3 && bajty[0] == 0xEF.toByte() && bajty[1] == 0xBB.toByte() && bajty[2] == 0xBF.toByte())
            bajty.copyOfRange(3, bajty.size) else bajty
        return try {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bezBom)).toString()
        } catch (_: CharacterCodingException) {
            String(bezBom, Charset.forName("windows-1250"))
        }
    }

    /**
     * PDF zalamuje řádky podle šířky stránky, i uprostřed věty a repliky
     * („Tati,“ ⏎ „podívej.“ — emulátor 30. 9. 2026). Řádek, který nekončí
     * koncem věty ani dvojtečkou a za ním pokračuje malé písmeno, se spojí.
     */
    fun spojZalomene(t: String): String =
        uprav(t).replace(Regex("""(?<=[^\s.!?…:“”"»\]])[ \t]*\n[ \t]*(?=\p{Ll})"""), " ")

    /** Konce řádků, nezlomitelné mezery a víc prázdných řádků za sebou. */
    fun uprav(t: String): String = t.replace("\r\n", "\n").replace('\r', '\n').replace(' ', ' ')
        .replace(Regex("""[ \t]+\n"""), "\n").replace(Regex("""\n{3,}"""), "\n\n").trim()
}
