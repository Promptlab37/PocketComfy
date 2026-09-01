package cz.promptlab.h3video.util

import android.media.MediaPlayer
import java.io.File

/**
 * Přehrávač namluvených replik – ať si uživatel poslechne, co Higgs vyrobil,
 * dřív než z toho nechá generovat video.
 *
 * Drží nejvýš jedno přehrávání: spuštění další repliky tu předchozí zastaví,
 * takže si dvě postavy nikdy nemluví přes sebe.
 */
class LinePlayer {

    private var player: MediaPlayer? = null

    /** Klíč právě přehrávané repliky, nebo null když je ticho. */
    var playingKey: Int? = null
        private set

    fun play(key: Int, file: File, onFinished: () -> Unit) {
        stop()
        runCatching {
            player = MediaPlayer().apply {
                setDataSource(file.absolutePath)
                setOnCompletionListener {
                    playingKey = null
                    onFinished()
                    release()
                    player = null
                }
                setOnErrorListener { _, _, _ ->
                    playingKey = null
                    onFinished()
                    true
                }
                prepare()
                start()
            }
            playingKey = key
        }.onFailure {
            playingKey = null
            onFinished()
        }
    }

    fun stop() {
        player?.let { p ->
            runCatching { if (p.isPlaying) p.stop() }
            runCatching { p.release() }
        }
        player = null
        playingKey = null
    }
}
