package cz.promptlab.h3video

import cz.promptlab.h3video.util.ReplikaZvuk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import kotlin.math.PI
import kotlin.math.sin

/** 5.66: přesná délka řeči v nahrávce repliky a ořez ticha. */
class ReplikaZvukTest {

    /** Umělá „replika“: ticho, tón 200 Hz (hlas), krátké „t“ (šum), ticho se šumem místnosti. */
    private fun umela(sr: Int = 24000): FloatArray {
        val r = java.util.Random(1)
        val x = FloatArray((sr * 2.0).toInt()) { (r.nextGaussian() * 0.0005).toFloat() }
        for (i in (0.30 * sr).toInt() until (1.40 * sr).toInt()) x[i] += (0.5 * sin(2 * PI * 200 * i / sr)).toFloat()
        for (i in (1.48 * sr).toInt() until (1.52 * sr).toInt()) x[i] += (r.nextGaussian() * 0.05).toFloat()
        return x
    }

    @Test
    fun `rec v umele nahravce na setiny`() {
        val pcm = ReplikaZvuk.Pcm(24000, umela())
        val rec = ReplikaZvuk.zmerRec(pcm)!!
        assertEquals(0.30, rec.odS, 0.02)
        // Konec až za koncovým „t“ (1,52 s), ne za samohláskou (1,40 s).
        assertEquals(1.52, rec.doS, 0.03)
    }

    @Test
    fun `orez zapise WAV s rezervou a bez cvaknuti`() {
        val d = createTempDir()
        val vstup = File(d, "in.wav"); ReplikaZvuk.zapisWav(vstup, 24000, umela())
        val o = ReplikaZvuk.orez(vstup, File(d, "out.wav"))!!
        val pcm = ReplikaZvuk.nactiWav(o.soubor)!!
        assertEquals(24000, pcm.sr)
        assertEquals(0.03, o.recOdS, 0.011)
        assertEquals(1.22, o.recDelkaS, 0.04)
        assertEquals(o.recOdS + o.recDelkaS + ReplikaZvuk.ZA_S, o.delkaSouboruS, 0.011)
        assertEquals(0f, pcm.vzorky.first(), 1e-3f)
        assertEquals(0f, pcm.vzorky.last(), 1e-3f)
        d.deleteRecursively()
    }

    @Test
    fun `ticho a jiny format se neorezava`() {
        val d = createTempDir()
        val ticho = File(d, "t.wav"); ReplikaZvuk.zapisWav(ticho, 24000, FloatArray(24000))
        assertNull(ReplikaZvuk.orez(ticho, File(d, "o.wav")))
        val mp3 = File(d, "x.mp3"); mp3.writeBytes(byteArrayOf(1, 2, 3, 4))
        assertNull(ReplikaZvuk.nactiWav(mp3))
        d.deleteRecursively()
    }

    /** Skutečné nahrávky Higgse ze serveru (jen lokálně, do repa nejdou). */
    @Test
    fun `skutecne nahravky higgse`() {
        val dir = TestCesty.comfyRoot?.let { File(it, "input") }
        assumeTrue(dir?.isDirectory == true)
        // Konce řeči podle prototypu zvukaře (Python, stejný postup) — přepis musí měřit stejně.
        val konce = mapOf("ref_868395466c87ba59.wav" to 3.24, "ref_05a19121caea9db1.wav" to 1.51, "ref_cb44e278dea06f00.wav" to 3.89,
            "ref_d0f0bf62e8d6cdd0.wav" to 2.33, "ref_70fce36c35700cc0.wav" to 1.38, "ref_b2d1228bc837838b.wav" to 2.43)
        konce.forEach { (f, konec) ->
            val soubor = File(dir!!, f)
            if (!soubor.exists()) return@forEach
            val rec = ReplikaZvuk.zmerRec(ReplikaZvuk.nactiWav(soubor)!!)
            assertNotNull(f, rec)
            assertTrue("$f: konec ${rec!!.doS} proti $konec", kotlin.math.abs(rec.doS - konec) <= 0.015)
        }
    }
}
