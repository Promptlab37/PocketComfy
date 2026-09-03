package cz.promptlab.h3video

import android.app.Application
import android.content.ContentValues
import android.os.Build
import android.provider.MediaStore
import cz.promptlab.h3video.engine.GenerationEngine
import cz.promptlab.h3video.engine.GenerationService
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class H3App : Application() {
    override fun onCreate() {
        super.onCreate()
        // Zapisovač pádů první, ať zachytí i to, co spadne hned pod ním.
        runCatching { recordCrashes() }
        // Jazyk co nejdřív — texty čte i notifikace a engine, ne jen obrazovky.
        runCatching {
            cz.promptlab.h3video.data.Jazyk.init(
                cz.promptlab.h3video.data.AppSettings(this).jazyk
            )
        }
        runCatching { GenerationService.ensureChannels(this) }
        runCatching { GenerationEngine.init(this) }
        // Fronta běhů je věc procesu, ne obrazovky — hlídač musí běžet od startu.
        runCatching { cz.promptlab.h3video.engine.RunQueue.init() }
    }

    /**
     * Zapíše pád do souboru, ať se dá přečíst v Nastavení.
     *
     * Proč to tu je: appka běží na telefonu, který není připojený kabelem, takže
     * jediná dosud dostupná informace o pádu bylo „zapnu ji a hned se vypne".
     * Bez výpisu se pak příčina jen hádá. Tohle původní chování nemění – po
     * zapsání se pád předá systému dál, takže Android reaguje jako dřív.
     */
    private fun recordCrashes() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            val text = runCatching {
                val stamp = SimpleDateFormat("d. M. yyyy HH:mm:ss", Locale("cs")).format(Date())
                val trace = StringWriter().also { error.printStackTrace(PrintWriter(it)) }
                "$stamp · verze ${BuildConfig.VERSION_NAME} · Android ${Build.VERSION.RELEASE} " +
                    "(SDK ${Build.VERSION.SDK_INT}) · ${Build.MANUFACTURER} ${Build.MODEL} · " +
                    "vlákno ${thread.name}\n\n$trace"
            }.getOrElse { error.toString() }

            runCatching { File(filesDir, CRASH_FILE).writeText(text) }
            // A ještě jednou do Stažených souborů. Když appka padá hned při startu,
            // do jejího Nastavení se nikdo nedostane a soukromá složka je bez
            // počítače nečitelná – tohle je jediná kopie, na kterou uživatel dosáhne.
            runCatching { writeToDownloads(text) }

            previous?.uncaughtException(thread, error)
        }
    }

    /**
     * Kopie výpisu do veřejné složky Stažené soubory. Přes MediaStore, takže to
     * od Androidu 10 nepotřebuje žádné oprávnění.
     */
    private fun writeToDownloads(text: String) {
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, DOWNLOAD_FILE)
            put(MediaStore.Downloads.MIME_TYPE, "text/plain")
        }
        val resolver = contentResolver
        // starou kopii smazat, ať uživatel nečte pád z minulého týdne
        runCatching {
            resolver.delete(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                "${MediaStore.Downloads.DISPLAY_NAME} = ?", arrayOf(DOWNLOAD_FILE)
            )
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return
        resolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) }
    }

    companion object {
        private const val CRASH_FILE = "posledni_pad.txt"
        const val DOWNLOAD_FILE = "H3Video_pad.txt"

        /** Výpis posledního pádu, nebo null když appka od instalace nespadla. */
        fun lastCrash(app: Application): String? =
            File(app.filesDir, CRASH_FILE).takeIf { it.exists() }?.readText()?.ifBlank { null }

        fun clearLastCrash(app: Application) {
            runCatching { File(app.filesDir, CRASH_FILE).delete() }
        }
    }
}
