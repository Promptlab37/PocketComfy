package cz.promptlab.h3video.util

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Přesná délka řeči v nahrávce repliky (5.66) — na setiny sekundy, bez ticha a šumu,
 * které Higgs nechává na začátku a na konci (0,1–0,4 s, změřeno na 16 nahrávkách).
 *
 * Postup zvukaře (ověřený proti Whisperu, chyba konce do ±0,07 s):
 *  - dvě pásma: high-pass 120 Hz (hlas bez brumu) a 3 kHz (s, š, t, k, p),
 *  - okna 10 ms (20 ms ztratí koncové „t“), práh relativně ke špičce, hystereze,
 *  - posledních 40 ms souboru se nepočítá (artefakt na konci každé nahrávky Higgse),
 *  - ořez [řeč − 30 ms ; řeč + 100 ms] s náběhem 5 ms a doběhem 15 ms (bez cvaknutí).
 * Měří se jen originál — opakované měření ořezaného souboru konec zkrátí.
 */
object ReplikaZvuk {

    data class Pcm(val sr: Int, val vzorky: FloatArray)

    /** Řeč v nahrávce: začátek a konec v sekundách od začátku souboru. */
    data class Rec(val odS: Double, val doS: Double) {
        val delkaS: Double get() = doS - odS
    }

    /** Výsledek ořezu: nový soubor, kde v něm řeč začíná a jak dlouho trvá. */
    data class Orez(val soubor: File, val recOdS: Double, val recDelkaS: Double, val delkaSouboruS: Double)

    const val OKNO_S = 0.01
    const val PRED_S = 0.03
    const val ZA_S = 0.10
    const val NABEH_S = 0.005
    const val DOBEH_S = 0.015

    /** PCM16 WAV → mono vzorky (−1…1). Hlavička se čte po blocích, ne natvrdo 44 B. Null = jiný formát. */
    fun nactiWav(f: File): Pcm? = runCatching {
        val b = f.readBytes()
        val bb = ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN)
        if (String(b, 0, 4) != "RIFF" || String(b, 8, 4) != "WAVE") return null
        var i = 12
        var kanaly = 0; var sr = 0; var bity = 0; var format = 0
        var data: Pair<Int, Int>? = null
        while (i + 8 <= b.size) {
            val id = String(b, i, 4)
            val delka = bb.getInt(i + 4)
            if (id == "fmt ") {
                format = bb.getShort(i + 8).toInt() and 0xffff
                kanaly = bb.getShort(i + 10).toInt()
                sr = bb.getInt(i + 12)
                bity = bb.getShort(i + 22).toInt()
            } else if (id == "data") {
                data = i + 8 to minOf(delka, b.size - i - 8)
                break
            }
            i += 8 + delka + (delka and 1)
        }
        val (od, n) = data ?: return null
        if (format != 1 || bity != 16 || kanaly < 1 || sr <= 0) return null
        val ramcu = n / (2 * kanaly)
        val out = FloatArray(ramcu)
        for (r in 0 until ramcu) {
            var s = 0f
            for (c in 0 until kanaly) s += bb.getShort(od + (r * kanaly + c) * 2) / 32768f
            out[r] = s / kanaly
        }
        Pcm(sr, out)
    }.getOrNull()

    /** RBJ high-pass (Q 0,707), direct form I. */
    private fun horniPropust(x: FloatArray, fc: Double, sr: Int): DoubleArray {
        val w0 = 2 * PI * fc / sr; val a = sin(w0) / (2 * 0.7071); val c = cos(w0)
        val a0 = 1 + a
        val b0 = (1 + c) / 2 / a0; val b1 = -(1 + c) / a0; val b2 = (1 + c) / 2 / a0
        val a1 = -2 * c / a0; val a2 = (1 - a) / a0
        val y = DoubleArray(x.size)
        var x1 = 0.0; var x2 = 0.0; var y1 = 0.0; var y2 = 0.0
        for (i in x.indices) {
            val v = x[i].toDouble()
            val o = b0 * v + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
            x2 = x1; x1 = v; y2 = y1; y1 = o; y[i] = o
        }
        return y
    }

    private fun dbOken(x: DoubleArray, w: Int): DoubleArray {
        val n = x.size / w
        return DoubleArray(n) { k ->
            var e = 0.0
            for (i in k * w until (k + 1) * w) e += x[i] * x[i]
            10 * log10(e / w + 1e-12)
        }
    }

    private fun percentil(a: DoubleArray, od: Int, doI: Int, p: Double): Double {
        val s = a.copyOfRange(od, doI).sorted()
        if (s.isEmpty()) return -120.0
        val pos = (s.size - 1) * p / 100.0
        val lo = pos.toInt(); val hi = minOf(lo + 1, s.size - 1)
        return s[lo] + (s[hi] - s[lo]) * (pos - lo)
    }

    /** Kde v nahrávce je řeč; null = nejde spolehlivě určit (pak se neořezává). */
    fun zmerRec(pcm: Pcm): Rec? {
        val w = maxOf(1, (pcm.sr * OKNO_S).roundToInt())
        val lo = dbOken(horniPropust(pcm.vzorky, 120.0, pcm.sr), w)
        val hi = dbOken(horniPropust(pcm.vzorky, 3000.0, pcm.sr), w)
        val n = lo.size - 4   // posledních 40 ms nikdy řeč
        if (n < 4) return null
        val pLo = (0 until n).maxOf { lo[it] }
        val pHi = (0 until n).maxOf { hi[it] }
        if (pLo < -50 && pHi < -50) return null   // jen ticho nebo šum
        val loOn = pLo - 24; val loOff = pLo - 30
        val hiOn = max(max(pHi - 36, -60.0), percentil(hi, 0, n, 10.0) + 12); val hiOff = hiOn - 6
        fun on(i: Int) = i in 0 until n && (lo[i] > loOn || hi[i] > hiOn)
        fun off(i: Int) = i in 0 until n && (lo[i] > loOff || hi[i] > hiOff)
        var s = (0 until n - 1).firstOrNull { on(it) && on(it + 1) } ?: return null
        var e = (n - 2 downTo 0).first { on(it) && on(it + 1) } + 2
        while (s > 0 && off(s - 1)) s--
        while (e < n && off(e)) e++
        val rec = Rec(s * OKNO_S, e * OKNO_S)
        return rec.takeIf { it.delkaS >= 0.2 }
    }

    /**
     * Ořízne nahrávku na řeč s rezervou a zapíše nový PCM16 WAV (mono, stejná frekvence).
     * Null = nejde přečíst nebo změřit — pak se použije originál beze změny.
     */
    fun orez(vstup: File, cil: File): Orez? {
        val pcm = nactiWav(vstup) ?: return null
        val rec = zmerRec(pcm) ?: return null
        val sr = pcm.sr
        val a = max(0, ((rec.odS - PRED_S) * sr).roundToInt())
        val b = minOf(pcm.vzorky.size, ((rec.doS + ZA_S) * sr).roundToInt())
        if (b - a < sr / 5) return null
        val y = pcm.vzorky.copyOfRange(a, b)
        val nab = (NABEH_S * sr).roundToInt().coerceAtMost(y.size)
        val dob = (DOBEH_S * sr).roundToInt().coerceAtMost(y.size)
        for (i in 0 until nab) y[i] *= i.toFloat() / nab
        for (i in 0 until dob) { val k = (dob - 1 - i).toFloat() / dob; y[y.size - dob + i] *= k * k }
        zapisWav(cil, sr, y)
        return Orez(cil, rec.odS - a.toDouble() / sr, rec.delkaS, y.size.toDouble() / sr)
    }

    fun zapisWav(f: File, sr: Int, y: FloatArray) {
        val data = ByteBuffer.allocate(y.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (v in y) data.putShort((v * 32767f).roundToInt().coerceIn(-32768, 32767).toShort())
        val h = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        h.put("RIFF".toByteArray()).putInt(36 + y.size * 2).put("WAVE".toByteArray())
        h.put("fmt ".toByteArray()).putInt(16).putShort(1).putShort(1).putInt(sr).putInt(sr * 2).putShort(2).putShort(16)
        h.put("data".toByteArray()).putInt(y.size * 2)
        RandomAccessFile(f, "rw").use { it.setLength(0); it.write(h.array()); it.write(data.array()) }
    }
}
