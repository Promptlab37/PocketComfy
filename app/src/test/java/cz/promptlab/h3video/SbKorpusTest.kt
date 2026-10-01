package cz.promptlab.h3video

import cz.promptlab.h3video.comfy.ComfyClient
import cz.promptlab.h3video.comfy.H3RefWriteBuilder
import cz.promptlab.h3video.comfy.SbFilmBuilder
import cz.promptlab.h3video.comfy.nabidka
import cz.promptlab.h3video.data.SbCteniTok
import cz.promptlab.h3video.data.LongMmPomer
import cz.promptlab.h3video.data.SbFilmPrepis
import cz.promptlab.h3video.data.SbFilmScene
import cz.promptlab.h3video.data.SbPromptyTok
import cz.promptlab.h3video.data.SbZdroj
import cz.promptlab.h3video.data.SbPanelyObrazu
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.security.MessageDigest

/**
 * Regresní sada storyboardů (5.67, uživatel 1. 10. 2026: „aby appka dokázala správně přečíst
 * jakýkoliv storyboard“). Každý storyboard v [TestCesty.sbKorpus] má ruční přepis `pravda.json`;
 * test pustí přesně ten tok čtení, který jede v appce ([SbCteniTok]), a porovná repliky, mluvčí,
 * počet panelů, časy, nálady a děj.
 *
 * Odpovědi modelu se ukládají do `<storyboard>/odpovedi/` (uzel čte greedy, takže stejný obrázek
 * a otázka dají vždy stejnou odpověď) a test je pak přehrává bez serveru. Server se volá jen
 * s `SB_KORPUS_ZIVE=1` — nikdy omylem, když uživatel potřebuje grafiku.
 */
class SbKorpusTest {

    private val zive = System.getenv("SB_KORPUS_ZIVE") == "1"
    private val server = System.getenv("SB_SERVER") ?: "http://127.0.0.1:8188"

    /** Porovnání variant čtení: SB_PANEL_PX, SB_DRUHY_POMER, SB_JEDEN_POHLED=1. */
    private val nastaveni = SbCteniTok.Nastaveni().let { n ->
        n.copy(
            panelPx = System.getenv("SB_PANEL_PX")?.toFloatOrNull() ?: n.panelPx,
            druhePomer = System.getenv("SB_DRUHY_POMER")?.toFloatOrNull() ?: n.druhePomer,
            dvaPohledy = System.getenv("SB_JEDEN_POHLED") != "1",
        )
    }

    @Test fun `cteni sady storyboardu`() {
        val korpus = TestCesty.sbKorpus
        assumeTrue("sada storyboardů není na tomhle počítači", korpus != null)
        val listy = korpus!!.listFiles { f -> File(f, "list.png").isFile && File(f, "pravda.json").isFile }!!.sortedBy { it.name }
        val zprava = StringBuilder()
        var chyb = 0
        var probehlo = 0
        for (d in listy) {
            val model = ModelSCache(d)
            val v = try {
                runBlocking { SbCteniTok.precti(ObrazJvm(File(d, "list.png")), model, object : SbCteniTok.Prubeh { override fun krokNavic() = 0 }, nastaveni) }
            } catch (e: ChybiOdpoved) {
                zprava.append("== ${d.name}: přeskočeno, chybí odpověď (pusť se SB_KORPUS_ZIVE=1)\n")
                continue
            }
            probehlo++
            val h = Hodnoceni(d.name, JSONObject(File(d, "pravda.json").readText()), v)
            zprava.append(h.zprava())
            chyb += h.chyby
            // Zadání úseků pro MiniMax z přečteného plánu — stejný kód jako appka, přísná kontrola.
            if (System.getenv("SB_KORPUS_BEZ_ZADANI") != "1") try {
                val sest = SbCteniTok.sestav(v)
                val s = SbFilmScene(
                    storyboard = File(d, "list.png"), zdroj = SbZdroj.STORYBOARD, pomer = LongMmPomer.NAVYSKU,
                    panely = sest.plan.panely, hlasy = sest.plan.hlasy, vzhled = sest.plan.vzhled,
                    kontinuita = sest.cteni.kontinuita.orEmpty(), nazev = sest.cteni.nazev.orEmpty(),
                )
                val z = runBlocking { SbPromptyTok.napis(s, model.pisar(s), emptyList(), 0) }
                File(d, "zadani.txt").writeText(z.zadani.mapIndexed { k, t -> "===== úsek ${k + 1}\n$t" }.joinToString("\n"))
                zprava.append("   zadání: ${z.zadani.size} úseků, nálezů ${z.nalezy.size}\n")
                z.nalezy.forEach { zprava.append("  CHYBA zadání ${it.text}\n") }
                chyb += z.nalezy.size
            } catch (e: ChybiOdpoved) {
                zprava.append("   zadání: přeskočeno, chybí odpověď\n")
            }
        }
        val soubor = File("build/reports/sb_korpus.txt").apply { parentFile.mkdirs(); writeText(zprava.toString()) }
        println(zprava)
        println("Zpráva: ${soubor.absolutePath}")
        assumeTrue("žádný storyboard nemá uložené odpovědi", probehlo > 0)
        assertTrue("Čtení sady storyboardů má $chyb chyb — viz ${soubor.absolutePath}\n$zprava", chyb == 0)
    }

    /** Rozvržení z pixelů: počet panelů musí sedět bez modelu (bez serveru). */
    @Test fun `rozvrzeni sady storyboardu`() {
        val korpus = TestCesty.sbKorpus
        assumeTrue("sada storyboardů není na tomhle počítači", korpus != null)
        val out = StringBuilder()
        for (d in korpus!!.listFiles { f -> File(f, "list.png").isFile && File(f, "pravda.json").isFile }!!.sortedBy { it.name }) {
            val ocek = JSONObject(File(d, "pravda.json").readText()).getJSONArray("panely").length()
            val b = ObrazJvm(File(d, "list.png")).bunky()
            val plne = b?.count { !it.prazdna }
            val radky = SbCteniTok.podlePoctu(b, ocek)?.let { SbPanelyObrazu.radky(it).map { r -> r.size } }
            val pismo = SbCteniTok.podlePoctu(b, ocek)?.let { ObrazJvm(File(d, "list.png")).vyskaPisma(it) }
            val pasy = SbCteniTok.podlePoctu(b, ocek)?.let { ObrazJvm(File(d, "list.png")).pasyPopisku(it) }
            println("${d.name}: políček ${b?.size}, plných $plne, má být $ocek, řádky $radky, písmo $pismo px, popisky ${pasy?.count { it != null }}/${pasy?.size} ${pasy?.firstOrNull()}")
            if (radky == null) out.append("${d.name}: z obrázku ${b?.size} políček ($plne plných), má být $ocek").append('\n')
        }
        assertTrue(out.toString(), out.isEmpty())
    }

    private class ChybiOdpoved : RuntimeException()

    /** Model s uloženými odpověďmi; živě volá server. */
    private inner class ModelSCache(private val slozka: File) : SbCteniTok.Model {
        private val obrazky = HashMap<String, ByteArray>()
        private val odpovedi = File(slozka, "odpovedi").apply { mkdirs() }
        private val client by lazy { ComfyClient(server) }
        private val captioner by lazy {
            val spec = client.objectInfo(SbFilmBuilder.CTENI_CLASS) ?: error("server nemá ${SbFilmBuilder.CTENI_CLASS}")
            H3RefWriteBuilder.vyberOdblokovany(spec.getJSONObject("input").getJSONObject("required").nabidka("model"), H3RefWriteBuilder.CAPTIONER_ODVAZANY)
                ?: error("server nemá vidoucí GGUF")
        }

        override suspend fun nahraj(png: ByteArray, nazev: String): String {
            val jm = "korpus_" + slozka.name + "_" + nazev
            obrazky[jm] = png
            if (zive) nahrane[jm] = client.uploadImage(png, jm)
            return jm
        }

        override suspend fun precti(jmeno: String, otazka: String, krok: Int): String {
            val klic = sha1((otazka + "\n" + sha1(obrazky.getValue(jmeno))).toByteArray())
            val f = File(odpovedi, "$klic.txt")
            if (f.isFile) return f.readText()
            if (!zive) throw ChybiOdpoved()
            val wf = SbFilmBuilder.buildCteni(jmenoNaServeru(jmeno), captioner, 1L, otazka = otazka)
            val id = java.util.UUID.randomUUID().toString()
            client.queuePrompt(wf, java.util.UUID.randomUUID().toString(), id)
            val konec = System.currentTimeMillis() + 15 * 60_000
            while (System.currentTimeMillis() < konec) {
                Thread.sleep(1000)
                val h = client.history(id) ?: continue
                val st = h.optJSONObject("status")
                if (st?.optString("status_str") == "error") error("čtení selhalo: $st")
                val t = h.optJSONObject("outputs")?.optJSONObject(SbFilmBuilder.N_CTENI_VYSTUP)?.optJSONArray("text") ?: continue
                val text = (0 until t.length()).joinToString("") { t.getString(it) }
                f.writeText(text)
                File(odpovedi, "$klic.otazka.txt").writeText(otazka)
                return text
            }
            error("čtení nedoběhlo")
        }

        override suspend fun ocr(jmeno: String, oblasti: List<SbPanelyObrazu.Obdelnik>, krok: Int): List<String>? {
            if (System.getenv("SB_BEZ_OCR") == "1") return null
            val oblJson = SbFilmBuilder.oblastiJson(oblasti)
            val klic = sha1(("OCR\n" + oblJson + "\n" + sha1(obrazky.getValue(jmeno))).toByteArray())
            val f = File(odpovedi, "ocr_$klic.json")
            if (!f.isFile) {
                if (!zive) throw ChybiOdpoved()
                if (client.objectInfo(SbFilmBuilder.OCR_CLASS) == null) return null
                f.writeText(cekej(SbFilmBuilder.buildOcr(jmenoNaServeru(jmeno), oblJson), SbFilmBuilder.N_OCR_VYSTUP))
            }
            return SbFilmBuilder.textyOcr(f.readText())
        }

        /** Přepisovač zadání úseků jako MainViewModel.prepisSReferencemi (hlidatDialogy = false). */
        fun pisar(s: SbFilmScene) = SbPromptyTok.Pisar { sekundy, zadani, hlidka, jenTvar, _ ->
            val text = "${zadani.trimEnd().trimEnd('.')}. Do not add any on-screen text or captions unless explicitly requested."
            val obrazky = s.uploadImages.map { it.readBytes() }
            // Opakovaný pokus (oprava po kontrole) musí dostat novou odpověď — pořadí jde do klíče.
            val zaklad = sha1((text + "\n" + hlidka + "\n" + jenTvar + "\n" + sekundy + obrazky.joinToString { sha1(it) }).toByteArray())
            val pokus = (pokusy[zaklad] ?: 0) + 1
            pokusy[zaklad] = pokus
            val f = File(odpovedi, "zadani_${zaklad}_$pokus.txt")
            if (f.isFile) return@Pisar f.readText()
            if (!zive) throw ChybiOdpoved()
            val spec = client.objectInfo(H3RefWriteBuilder.NODE_CLASS)!!.getJSONObject("input").getJSONObject("required")
            val jmena = obrazky.mapIndexed { i, b -> client.uploadImage(b, "korpus_${slozka.name}_ref${i + 1}.png") }
            val wf = H3RefWriteBuilder.build(
                zadani = text, obrazky = jmena, sekundy = sekundy.coerceIn(2.0, 60.0),
                pomer = spec.nabidka("resolution").firstOrNull { it == s.pomer.kod } ?: "16:9",
                captioner = H3RefWriteBuilder.vyberOdblokovany(spec.nabidka("caption_model"), H3RefWriteBuilder.CAPTIONER_ODVAZANY)!!,
                writer = H3RefWriteBuilder.vyberOdblokovany(spec.nabidka("writer_model"), H3RefWriteBuilder.WRITER_ODVAZANY)!!,
                seed = pokus.toLong(), storyboard = s.seStoryboardem, hlidka = hlidka, jenTvar = jenTvar,
                maxTokenu = H3RefWriteBuilder.MAX_TOKENU_STORYBOARD,
            )
            val vysl = cekej(wf, H3RefWriteBuilder.N_PREVIEW)
            f.writeText(vysl)
            vysl
        }
        private val pokusy = HashMap<String, Int>()

        private fun cekej(wf: JSONObject, uzel: String): String {
            val id = java.util.UUID.randomUUID().toString()
            client.queuePrompt(wf, java.util.UUID.randomUUID().toString(), id)
            val konec = System.currentTimeMillis() + 20 * 60_000
            while (System.currentTimeMillis() < konec) {
                Thread.sleep(1000)
                val h = client.history(id) ?: continue
                val st = h.optJSONObject("status")
                if (st?.optString("status_str") == "error") error("běh selhal: $st")
                val t = h.optJSONObject("outputs")?.optJSONObject(uzel)?.optJSONArray("text") ?: continue
                return (0 until t.length()).joinToString("") { t.getString(it) }
            }
            error("běh nedoběhl")
        }

        /** uploadImage vrací jméno se složkou; LoadImage ho chce stejně. */
        private fun jmenoNaServeru(jm: String) = nahrane[jm] ?: jm
        private val nahrane = HashMap<String, String>()
    }

    /**
     * Storyboard z disku — stejné výřezy jako [cz.promptlab.h3video.util.ObrazStoryboardu].
     * javax.imageio není v android.jar, proti kterému se testy překládají: čte se přes reflexi,
     * výřez, zvětšení (bilineárně jako Bitmap.createScaledBitmap) a PNG jsou v čistém Kotlinu.
     */
    private class ObrazJvm(private val soubor: File) : SbCteniTok.Obraz {
        override val sirka: Int
        override val vyska: Int
        private val px: IntArray

        init {
            val im = Class.forName("javax.imageio.ImageIO").getMethod("read", File::class.java).invoke(null, soubor)
            sirka = im.javaClass.getMethod("getWidth").invoke(im) as Int
            vyska = im.javaClass.getMethod("getHeight").invoke(im) as Int
            px = im.javaClass.getMethod(
                "getRGB", Int::class.java, Int::class.java, Int::class.java, Int::class.java,
                IntArray::class.java, Int::class.java, Int::class.java,
            ).invoke(im, 0, 0, sirka, vyska, null, 0, sirka) as IntArray
        }

        override fun original() = soubor.readBytes()

        override fun vyrezPng(x0: Int, y0: Int, x1: Int, y1: Int, meritko: Float): ByteArray {
            val sw = x1 - x0; val sh = y1 - y0
            val w = if (meritko > 1f) (sw * meritko).toInt() else sw
            val h = if (meritko > 1f) (sh * meritko).toInt() else sh
            val out = IntArray(w * h)
            for (y in 0 until h) {
                val fy = ((y + 0.5) * sh / h - 0.5).coerceIn(0.0, sh - 1.0)
                val iy = fy.toInt(); val dy = fy - iy; val iy2 = minOf(iy + 1, sh - 1)
                for (x in 0 until w) {
                    val fx = ((x + 0.5) * sw / w - 0.5).coerceIn(0.0, sw - 1.0)
                    val ix = fx.toInt(); val dx = fx - ix; val ix2 = minOf(ix + 1, sw - 1)
                    fun p(xx: Int, yy: Int) = px[(y0 + yy) * sirka + x0 + xx]
                    var c = 0
                    for (sh8 in intArrayOf(16, 8, 0)) {
                        fun k(v: Int) = (v shr sh8) and 0xFF
                        val v = k(p(ix, iy)) * (1 - dx) * (1 - dy) + k(p(ix2, iy)) * dx * (1 - dy) +
                            k(p(ix, iy2)) * (1 - dx) * dy + k(p(ix2, iy2)) * dx * dy
                        c = c or ((v + 0.5).toInt().coerceIn(0, 255) shl sh8)
                    }
                    out[y * w + x] = c
                }
            }
            return png(out, w, h)
        }

        override fun bunky(): List<SbPanelyObrazu.Bunka>? {
            var vzorek = 1
            while (maxOf(sirka, vyska) / (vzorek * 2) >= 1000) vzorek *= 2
            val w = sirka / vzorek; val h = vyska / vzorek
            val maly = IntArray(w * h) { i -> px[((i / w) * vzorek) * sirka + (i % w) * vzorek] }
            val sx = sirka.toDouble() / w; val sy = vyska.toDouble() / h
            return SbPanelyObrazu.najdiBunky(maly, w, h)?.map { b ->
                val o = b.obdelnik
                SbPanelyObrazu.Bunka(SbPanelyObrazu.Obdelnik((o.x0 * sx).toInt(), (o.y0 * sy).toInt(),
                    minOf(sirka, (o.x1 * sx).toInt()), minOf(vyska, (o.y1 * sy).toInt())), b.prazdna)
            }
        }

        override fun vyskaPisma(panely: List<SbPanelyObrazu.Obdelnik>): Float? = SbPanelyObrazu.vyskaPisma(px, sirka, vyska, panely)
        override fun pasyPopisku(panely: List<SbPanelyObrazu.Obdelnik>) = panely.map { SbPanelyObrazu.pasPopisku(px, sirka, vyska, it) }

        /** Nejjednodušší PNG: RGB 8 bitů, filtr 0, jeden IDAT. */
        private fun png(argb: IntArray, w: Int, h: Int): ByteArray {
            val raw = java.io.ByteArrayOutputStream()
            for (y in 0 until h) {
                raw.write(0)
                for (x in 0 until w) { val c = argb[y * w + x]; raw.write(c shr 16 and 0xFF); raw.write(c shr 8 and 0xFF); raw.write(c and 0xFF) }
            }
            val out = java.io.ByteArrayOutputStream()
            out.write(byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 13, 10, 26, 10))
            fun blok(typ: String, data: ByteArray) {
                val d = java.io.DataOutputStream(out)
                d.writeInt(data.size)
                val crc = java.util.zip.CRC32()
                val t = typ.toByteArray(Charsets.US_ASCII)
                d.write(t); d.write(data); crc.update(t); crc.update(data)
                d.writeInt(crc.value.toInt())
            }
            val ihdr = java.nio.ByteBuffer.allocate(13).putInt(w).putInt(h).put(8).put(2).put(0).put(0).put(0).array()
            blok("IHDR", ihdr)
            val z = java.io.ByteArrayOutputStream()
            java.util.zip.DeflaterOutputStream(z, java.util.zip.Deflater(6)).use { it.write(raw.toByteArray()) }
            blok("IDAT", z.toByteArray())
            blok("IEND", ByteArray(0))
            return out.toByteArray()
        }
    }

    /** Porovnání čtení s ručním přepisem. */
    private class Hodnoceni(val jmeno: String, pravda: JSONObject, v: SbCteniTok.Vysledek) {
        val radky = mutableListOf<String>()
        var chyby = 0
        private var replikOk = 0
        private var replik = 0
        private var zachyceno = 0
        private val sporne = v.sporne
        private var naladOk = 0
        private var nalad = 0
        private var dejOk = 0
        private var deju = 0

        init {
            val s = SbCteniTok.sestav(v)
            val pp = pravda.getJSONArray("panely")
            if (s.plan.panely.size != pp.length()) chyba("počet panelů ${s.plan.panely.size}, má být ${pp.length()}")
            for (i in 0 until pp.length()) {
                val p = pp.getJSONObject(i)
                val n = p.getInt("cislo")
                val panel = s.plan.panely.firstOrNull { it.cislo == n }
                if (panel == null) { chyba("panel $n chybí"); continue }
                val ocek = (0 until p.getJSONArray("repliky").length()).map { k ->
                    val r = p.getJSONArray("repliky").getJSONArray(k); r.getString(0) to r.getString(1)
                }
                val prectene = SbFilmPrepis.repliky(panel.repliky)
                replik += ocek.size
                ocek.forEachIndexed { k, (kdo, co) ->
                    val pr = prectene.getOrNull(k)
                    when {
                        pr == null -> chyba("panel $n: chybí replika $kdo: „$co“")
                        !pr.first.equals(kdo, ignoreCase = true) -> chyba("panel $n: mluvčí „${pr.first}“, má být „$kdo“")
                        norm(pr.second) != norm(co) && v.sporne.containsKey(n) -> {
                            zachyceno++
                            radky += "  zachyceno panel $n: „${pr.second}“ (má být „$co“) — appka ukáže: ${v.sporne[n]}"
                        }
                        norm(pr.second) != norm(co) -> chyba("panel $n: „${pr.second}“\n      má být „$co“")
                        else -> replikOk++
                    }
                }
                if (prectene.size > ocek.size) prectene.drop(ocek.size).forEach { chyba("panel $n: replika navíc ${it.first}: „${it.second}“") }
                if (p.has("od") && s.plan.zeStoryboardu) {
                    val delka = p.getDouble("do") - p.getDouble("od")
                    if (kotlin.math.abs(panel.sekundy - delka) > 0.01) chyba("panel $n: délka ${panel.sekundy}, má být $delka")
                }
                p.optString("nalada").takeIf { it.isNotBlank() && it != "null" }?.let { o ->
                    nalad++
                    val pr = v.naladyOpis[n]
                    if (pr != null && normVolne(pr) == normVolne(o)) naladOk++ else poznamka("panel $n: nálada „$pr“, má být „$o“")
                }
                p.optString("dej").takeIf { it.isNotBlank() && it != "null" }?.let { o ->
                    deju++
                    val pr = v.dejeOpis[n]
                    if (pr != null && normVolne(pr) == normVolne(o)) dejOk++ else poznamka("panel $n: děj „$pr“, má být „$o“")
                }
            }
            pravda.optString("kontinuita").takeIf { it.isNotBlank() && it != "null" }?.let {
                if (s.cteni.kontinuita.isNullOrBlank()) chyba("kontinuita se nepřečetla")
            }
        }

        private fun chyba(t: String) { chyby++; radky += "  CHYBA $t" }
        private fun poznamka(t: String) { radky += "  pozn. $t" }

        fun zprava() = "== $jmeno: repliky $replikOk/$replik (ke kontrole zachyceno $zachyceno, poplachů navíc ${sporne.size - zachyceno}), nálady $naladOk/$nalad, děj $dejOk/$deju, chyb $chyby\n" +
            radky.joinToString("") { "$it\n" }

        companion object {
            /** Uvozovky, mezery a trojtečka sjednocené; jinak znak po znaku. */
            fun norm(t: String) = t.replace("…", "...").replace(Regex("""[„“”"«»]"""), "")
                .replace("‘", "").replace("’", "").replace(Regex("""\s+"""), " ").trim()
            /** Nálada a děj: navíc bez koncové tečky, závorek a velikosti písmen. */
            fun normVolne(t: String) = norm(t).trim('(', ')', ' ').trimEnd('.').lowercase()
        }
    }

    companion object {
        fun sha1(b: ByteArray): String = MessageDigest.getInstance("SHA-1").digest(b).joinToString("") { "%02x".format(it) }
    }
}
