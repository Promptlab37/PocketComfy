package cz.promptlab.h3video.data

import android.content.Context
import androidx.compose.runtime.Immutable
import org.json.JSONObject

/**
 * Karta **Hudba** — uživatelovo ACE-Step 1.5 Turbo workflow (celá skladba
 * z textu za 8 kroků, výsledkem je MP3).
 *
 * Předloha je 1:1 kopie jeho `audio_ace_step_1_5__MUSIC_PROMPTLAB.json`
 * (jen s prázdným zadáním — texty písní jsou vždy čerstvé od uživatele).
 * Dosazuje se: styl, text písně, délka, jazyk, BPM, tónina a seed. Kroky,
 * cfg, sampler i shift zůstávají z předlohy.
 */
@Immutable
data class MusicScene(
    /** Styl skladby — žánr, nástroje, nálada, zpěvák… */
    val styl: String = "",
    /** Text písně; prázdný = instrumentálka. */
    val text: String = "",
    val seconds: Int = 120,
    val language: String = "cs",
    val bpm: Int = 120,
    val keyscale: String = "C major",
) {
    companion object {
        const val MIN_SECONDS = 30
        const val MAX_SECONDS = 240

        /** Jazyky zpěvu, které model zná (výběr těch nejbližších). */
        val LANGUAGES = listOf("cs", "sk", "en", "de", "es", "fr", "it", "pl", "ru")

        /** Tóniny přesně podle nabídky uzlu TextEncodeAceStepAudio1.5. */
        val KEYSCALES = listOf(
            "C major", "C# major", "Db major", "D major", "D# major", "Eb major",
            "E major", "F major", "F# major", "Gb major", "G major", "G# major",
            "Ab major", "A major", "A# major", "Bb major", "B major",
            "C minor", "C# minor", "Db minor", "D minor", "D# minor", "Eb minor",
            "E minor", "F minor", "F# minor", "Gb minor", "G minor", "G# minor",
            "Ab minor", "A minor", "A# minor", "Bb minor", "B minor",
        )
    }
}

/** Co kartě chybí, než se dá spustit. */
fun musicProblem(s: MusicScene): String? =
    if (s.styl.isBlank()) t("Popiš styl skladby — žánr, nástroje, náladu.") else null

/** Upozornění, která nebrání spuštění. */
fun musicHints(s: MusicScene): List<String> {
    val out = mutableListOf<String>()
    if (s.text.isBlank()) {
        out += t("Bez textu písně vyjde instrumentálka. Text piš po slokách, ") +
            t("klidně česky.")
    }
    return out
}

/** Uložené zadání karty Hudba — přežije zavření aplikace. */
class MusicStore(ctx: Context) {

    private val sp = ctx.getSharedPreferences("h3video", Context.MODE_PRIVATE)

    fun load(): MusicScene = runCatching {
        val j = JSONObject(sp.getString(KEY, "{}")!!)
        MusicScene(
            styl = j.optString("styl"),
            text = j.optString("text"),
            seconds = j.optInt("seconds", 120)
                .coerceIn(MusicScene.MIN_SECONDS, MusicScene.MAX_SECONDS),
            language = j.optString("language").ifBlank { "cs" },
            bpm = j.optInt("bpm", 120).coerceIn(10, 300),
            keyscale = j.optString("keyscale").ifBlank { "C major" },
        )
    }.getOrDefault(MusicScene())

    fun save(s: MusicScene) {
        sp.edit().putString(
            KEY,
            JSONObject()
                .put("styl", s.styl)
                .put("text", s.text)
                .put("seconds", s.seconds)
                .put("language", s.language)
                .put("bpm", s.bpm)
                .put("keyscale", s.keyscale)
                .toString()
        ).apply()
    }

    private companion object { const val KEY = "musicScene" }
}
