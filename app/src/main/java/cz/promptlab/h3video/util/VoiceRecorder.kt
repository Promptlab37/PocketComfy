package cz.promptlab.h3video.util

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import java.io.File

/**
 * Nahrání vzorku hlasu ke klonování.
 *
 * Nahrává se do M4A (AAC 44,1 kHz mono, 128 kb/s) – Higgs si vstup stejně
 * převádí přes ffmpeg, takže na formátu nezáleží, ale AAC je jediný, který
 * Android umí do souboru sám a bez knihovny navíc. Mono schválně: klonování
 * chce jeden hlas, ne prostor.
 */
class VoiceRecorder(private val ctx: Context) {

    private var recorder: MediaRecorder? = null
    private var target: File? = null

    fun start(): Boolean = runCatching {
        stopQuietly()
        val dir = File(ctx.cacheDir, "hlas").apply { mkdirs() }
        val file = File(dir, "vzorek_${System.currentTimeMillis()}.m4a")
        val r = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(ctx)
        else @Suppress("DEPRECATION") MediaRecorder()
        r.setAudioSource(MediaRecorder.AudioSource.MIC)
        r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        r.setAudioChannels(1)
        r.setAudioSamplingRate(44_100)
        r.setAudioEncodingBitRate(128_000)
        r.setOutputFile(file.absolutePath)
        r.prepare()
        r.start()
        recorder = r
        target = file
        true
    }.getOrElse {
        stopQuietly()
        false
    }

    /**
     * Ukončí nahrávání a vrátí soubor, nebo null když se nic použitelného
     * nenahrálo. Příliš krátká nahrávka (pod ~1 s) bývá jen ťuknutí do tlačítka
     * a MediaRecorder z ní udělá vadný soubor, proto se zahazuje.
     */
    fun stop(): File? {
        val r = recorder ?: return null
        val file = target
        recorder = null
        target = null
        runCatching { r.stop() }.onFailure {
            runCatching { r.release() }
            file?.delete()
            return null
        }
        runCatching { r.release() }
        return file?.takeIf { it.exists() && it.length() > 8_000 }
    }

    private fun stopQuietly() {
        recorder?.let { r ->
            runCatching { r.stop() }
            runCatching { r.release() }
        }
        recorder = null
    }
}
