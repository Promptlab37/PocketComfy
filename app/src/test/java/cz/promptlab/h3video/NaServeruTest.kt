package cz.promptlab.h3video

import cz.promptlab.h3video.comfy.ComfyClient
import cz.promptlab.h3video.data.HistoryCodec
import cz.promptlab.h3video.data.VideoItem
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.net.ServerSocket
import kotlin.concurrent.thread

/**
 * Výsledky „Na serveru“ (5.03): velké výsledky přes mobilní data na vyžádání.
 * Stahování jde přes stejné `ComfyClient.download` jako po generování.
 */
class NaServeruTest {

    private val obsah = ByteArray(300_000) { (it % 251).toByte() }
    private lateinit var server: ServerSocket
    private var rangeHlavicky = mutableListOf<String?>()

    /** Minimální HTTP server: GET /view, umí Range (jako aiohttp FileResponse v ComfyUI). */
    @Before fun start() {
        server = ServerSocket(0)
        thread(isDaemon = true) {
            while (!server.isClosed) {
                val sock = runCatching { server.accept() }.getOrNull() ?: break
                runCatching {
                    sock.use { sc ->
                        val vstup = sc.getInputStream().bufferedReader(Charsets.ISO_8859_1)
                        var range: String? = null
                        while (true) {
                            val radek = vstup.readLine() ?: break
                            if (radek.isEmpty()) break
                            if (radek.startsWith("Range:", ignoreCase = true)) range = radek.substringAfter(':').trim()
                        }
                        synchronized(rangeHlavicky) { rangeHlavicky += range }
                        val od = range?.removePrefix("bytes=")?.substringBefore('-')?.toIntOrNull() ?: 0
                        val kus = obsah.copyOfRange(od, obsah.size)
                        val nl = "\r\n"
                        val hlava = StringBuilder()
                        if (od > 0) hlava.append("HTTP/1.1 206 Partial Content$nl")
                            .append("Content-Range: bytes $od-${obsah.size - 1}/${obsah.size}$nl")
                        else hlava.append("HTTP/1.1 200 OK$nl")
                        hlava.append("Content-Length: ${kus.size}${nl}Connection: close$nl$nl")
                        val out = sc.getOutputStream()
                        out.write(hlava.toString().toByteArray(Charsets.ISO_8859_1))
                        runCatching { out.write(kus); out.flush() }
                    }
                }
            }
        }
    }

    @After fun stop() = server.close()

    private fun url() = "http://127.0.0.1:${server.localPort}/view?filename=a.mp4"

    @Test fun `nad limitem se nic nestahuje a vrati se velikost`() {
        val cil = File.createTempFile("vysl", ".mp4").apply { delete() }
        val velikost = ComfyClient("http://127.0.0.1").download(url(), cil, maxBytes = 100_000) { _, _ -> }
        assertEquals(obsah.size.toLong(), velikost)
        assertFalse(cil.exists())
    }

    @Test fun `pod limitem se stahuje jako vzdy`() {
        val cil = File.createTempFile("vysl", ".mp4").apply { delete() }
        val r = ComfyClient("http://127.0.0.1").download(url(), cil, maxBytes = 1_000_000) { _, _ -> }
        assertEquals(-1L, r)
        assertArrayEquals(obsah, cil.readBytes())
    }

    @Test fun `prerusene stazeni se dotahne pres Range`() {
        val cil = File.createTempFile("vysl", ".mp4").apply { delete() }
        File(cil.parentFile, cil.name + ".part").writeBytes(obsah.copyOfRange(0, 120_000))
        ComfyClient("http://127.0.0.1").download(url(), cil, navazat = true) { _, _ -> }
        assertEquals("bytes=120000-", rangeHlavicky.last())
        assertArrayEquals(obsah, cil.readBytes())
        assertFalse(File(cil.parentFile, cil.name + ".part").exists())
    }

    @Test fun `bez navazani se stary part zahodi`() {
        val cil = File.createTempFile("vysl", ".mp4").apply { delete() }
        File(cil.parentFile, cil.name + ".part").writeBytes(ByteArray(50_000) { 7 })
        ComfyClient("http://127.0.0.1").download(url(), cil) { _, _ -> }
        assertEquals(null, rangeHlavicky.last())
        assertArrayEquals(obsah, cil.readBytes())
    }

    @Test fun `zaznam na serveru projde ulozenim a nactenim`() {
        val item = VideoItem(
            id = "p1", fileName = "p1.mp4", prompt = "x", createdAt = 1L, seconds = 5f,
            resolution = "480×864", seed = 1L, twoImages = false,
            serverFile = "PocketSbFilm_00005_.mp4", serverSubfolder = "sub", serverType = "output",
            serverBytes = 123_456_789L,
        )
        val zpet = HistoryCodec.decode(HistoryCodec.encode(listOf(item))).items.single()
        assertEquals(item, zpet)
        // Starý záznam bez polí serveru = stažený, jako dřív.
        val stary = HistoryCodec.decode("""[{"id":"a","file":"a.mp4"}]""").items.single()
        assertTrue(stary.serverFile.isEmpty() && stary.serverBytes == 0L)
    }
}
