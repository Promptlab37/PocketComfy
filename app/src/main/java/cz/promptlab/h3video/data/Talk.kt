package cz.promptlab.h3video.data

import android.graphics.Bitmap
import androidx.compose.runtime.Immutable
import java.io.File

/**
 * Mluvící scéna: postavy z fotek a k nim seznam replik.
 *
 * Postava a replika jsou schválně oddělené. Dřív měla každá postava právě jednu
 * repliku, takže nešlo napsat běžný dialog „žena – muž – žena"; přidat ženu
 * podruhé jako novou postavu není řešení, model by ji bral jako jinou osobu.
 *
 * Limity jsou čtené ze schématu uzlu MiniMaxH3ReferenceToVideo: 9 obrázků, ale
 * jen 3 samostatné zvuky. Postav proto může být devět, replik nejvýš tři.
 */
const val MAX_SPEAKERS = 9
const val MAX_LINES = 3

/** Ticho mezi replikami, aby si postavy neskákaly do řeči. */
private const val PAUSE_SECONDS = 0.6f

/** Doběh na konci, ať video nekončí uprostřed posledního slova. */
private const val TAIL_SECONDS = 0.8f

/** Odkud se bere hlas postavy. */
sealed interface VoiceSource {
    /** Hotový hlas z knihovny Higgse (Ana, Eva, Marek…). */
    @Immutable
    data class Library(val voiceId: String, val voiceName: String) : VoiceSource

    /** Klon z nahrávky – vlastní vzorek řeči. */
    @Immutable
    data class Sample(val file: File, val label: String) : VoiceSource
}

/** Stav namlouvání jedné repliky. */
enum class VoiceStatus { NONE, RUNNING, READY, FAILED }

/** Postava: fotka, popis a hlas. Replik může mít víc, nebo žádnou. */
@Immutable
data class Speaker(
    val key: Int,
    val image: File? = null,
    val thumb: Bitmap? = null,
    /** Volitelný popis do promptu („muž v obleku"). */
    val look: String = "",
    val voice: VoiceSource? = null,
)

/** Replika: kdo ji říká a co. Pořadí replik je pořadí dialogu. */
@Immutable
data class Line(
    val key: Int,
    /** Klíč postavy, která repliku říká. */
    val speakerKey: Int,
    val text: String = "",
    /** Hotové WAV z Higgse – tohle jde do MiniMaxu jako `<Audio N>`. */
    val audio: File? = null,
    val status: VoiceStatus = VoiceStatus.NONE,
    val progress: Float = 0f,
    val error: String = "",
    /** Text, ze kterého vzniklo [audio] – podle něj se pozná, že je hlas neaktuální. */
    val spokenText: String = "",
    /** Skutečná délka namluvené repliky v sekundách. */
    val audioSeconds: Float = 0f,
) {
    /** Hlas sedí na aktuální text? Po přepsání repliky je starý zvuk k ničemu. */
    val voiceCurrent: Boolean
        get() = audio != null && audio.exists() && spokenText == text.trim()
}

@Immutable
data class TalkScene(
    val speakers: List<Speaker> = listOf(Speaker(key = 1)),
    val lines: List<Line> = listOf(Line(key = 1, speakerKey = 1)),
    /** Kde se to odehrává, jak se hýbe kamera – volitelné. */
    val sceneNote: String = "",
    /** Hotový prompt pro MiniMax. Skládá se sám, dokud do něj uživatel nesáhne. */
    val prompt: String = "",
    val promptEdited: Boolean = false,
) {
    val withImage: List<Speaker> get() = speakers.filter { it.image != null }

    /** Repliky s hotovým hlasem – v pořadí, v jakém dostanou `<Audio N>`. */
    val voiced: List<Line> get() = lines.filter { it.voiceCurrent }

    val written: List<Line> get() = lines.filter { it.text.isNotBlank() }

    val canAddSpeaker: Boolean get() = speakers.size < MAX_SPEAKERS
    val canAddLine: Boolean get() = lines.size < MAX_LINES

    fun speakerOf(line: Line): Speaker? = speakers.firstOrNull { it.key == line.speakerKey }

    /**
     * Jak dlouhé musí video být, aby se repliky vešly. Delší video není rezerva:
     * obraz i zvuk vznikají jedním průchodem, takže volný čas si model vyplní
     * vymyšlenou řečí.
     */
    val neededSeconds: Int?
        get() {
            val parts = voiced.map { it.audioSeconds }.filter { it > 0f }
            if (parts.isEmpty()) return null
            val total = parts.sum() + PAUSE_SECONDS * (parts.size - 1) + TAIL_SECONDS
            return kotlin.math.ceil(total).toInt().coerceIn(MIN_SECONDS, MAX_SECONDS)
        }

    fun fitsInto(seconds: Int): Boolean = (neededSeconds ?: 0) <= seconds
}

/** Čas repliky ve tvaru, jaký používá návod: `00:05.000`. */
private fun casovaZnacka(seconds: Float): String {
    val celkem = seconds.coerceAtLeast(0f)
    val minuty = (celkem / 60).toInt()
    val zbytek = celkem - minuty * 60
    return "%02d:%06.3f".format(java.util.Locale.US, minuty, zbytek)
}

/**
 * Poskládá prompt do struktury, kterou MiniMax H3 čeká.
 *
 * Značky sedí na pořadí referencí, ne na pořadí postav: `<Picture N>` počítá
 * postavy s obrázkem, `<Audio N>` repliky s hotovým hlasem. Mluvčí má stabilní
 * ID `(S1)`, `(S2)` podle postavy — takže když jedna postava mluví dvakrát, má
 * obě repliky pod stejným ID a model ví, že je to pořád ona.
 *
 * Zápis dialogu `<d>[Czech] …</d>` a značkování je převzaté z návodu k ULTRA V2.
 */
fun composePrompt(scene: TalkScene): String {
    val pictures = scene.withImage
    val voiced = scene.voiced
    if (pictures.isEmpty()) return ""

    val subjectOf = pictures.withIndex().associate { (i, s) -> s.key to i + 1 }
    val audioOf = voiced.withIndex().associate { (i, l) -> l.key to i + 1 }

    // ---- subject_definitions: každá reference dostane úkol
    val definitions = buildString {
        pictures.forEach { s ->
            val n = subjectOf.getValue(s.key)
            val look = s.look.trim().ifBlank { "the person" }
            appendLine(
                "<Subject $n> (S$n) is $look from <Picture $n>, preserving face, " +
                    "hairstyle, clothing and jewelry."
            )
        }
        voiced.forEach { line ->
            val a = audioOf.getValue(line.key)
            val n = subjectOf[line.speakerKey] ?: return@forEach
            appendLine("<Audio $a> is the voice-timbre reference for <Subject $n> (S$n).")
        }
    }.trimEnd()

    // ---- summary: k čemu ty reference jsou
    val kdo = pictures.joinToString(" and ") { "<Subject ${subjectOf.getValue(it.key)}>" }
    val summary = buildString {
        append("[reference generation")
        if (voiced.isNotEmpty()) append(" + audio reference")
        append("] The target video shows $kdo in one continuous live-action scene. ")
        append("Identity comes from the reference pictures")
        if (voiced.isNotEmpty()) {
            append(" and the voice timbre of each spoken line from its audio reference")
        }
        append("; the characters speak exactly the lines written below.")
    }

    // ---- retention_analysis: co se má zachovat a co jen vést
    val retention = buildString {
        pictures.forEach { s ->
            val n = subjectOf.getValue(s.key)
            appendLine(
                "<Subject $n>: fully_preserved - identity, face, hairstyle and clothing " +
                    "stay consistent for the whole video."
            )
        }
        voiced.forEach { line ->
            val a = audioOf.getValue(line.key)
            val n = subjectOf[line.speakerKey] ?: return@forEach
            appendLine(
                "<Audio $a>: reference - guides the voice timbre and delivery of " +
                    "<Subject $n> (S$n) without copying the original recording."
            )
        }
    }.trimEnd()

    // ---- detailed_description: děj chronologicky, včetně dialogu
    //
    // Repliky dostávají ČASOVOU ZNAČKU. Bez ní model dostal jen „Then …" a řadu
    // replik odbyl – druhá nebo třetí prostě nezazněla. Návod k V2 popisuje děj
    // chronologicky a čas u repliky je jediné, čím se dá říct „tahle padne až
    // tady". Čas se počítá z naměřené délky namluvených replik plus pauza mezi
    // nimi, tedy přesně tak, jak se počítá i potřebná délka videa.
    val action = StringBuilder()
    var first = true
    var at = 0f
    scene.written.forEach { line ->
        val n = subjectOf[line.speakerKey] ?: return@forEach
        val audioNumber = audioOf[line.key]
        val cas = if (line.audioSeconds > 0f || at > 0f) "At ${casovaZnacka(at)}, " else ""
        val lead = if (first) "[Shot 1] Live-action, cinematic. $cas<Subject $n> (S$n)"
        else "$cas<Subject $n> (S$n)"
        val verb = if (first) "looks into the camera and says" else "answers"
        if (audioNumber != null) {
            action.append("$lead $verb, heard in <Audio $audioNumber>: <d>[Czech] ${line.text.trim()}</d>\n")
        } else {
            action.append("$lead $verb: <d>[Czech] ${line.text.trim()}</d>\n")
        }
        if (line.audioSeconds > 0f) at += line.audioSeconds + PAUSE_SECONDS
        first = false
    }
    // Postavy, které nemluví, ať v záběru přesto jsou.
    pictures.filter { s -> scene.written.none { it.speakerKey == s.key } }.forEach { s ->
        action.append("<Subject ${subjectOf.getValue(s.key)}> is present in the shot.\n")
    }
    scene.sceneNote.trim().takeIf { it.isNotEmpty() }?.let { action.append(it).append("\n") }
    if (voiced.isNotEmpty()) {
        action.append(
            "After the last spoken line the characters remain silent and simply " +
                "hold the scene until the end of the video.\n"
        )
    }

    val soundscape = if (voiced.isEmpty())
        "Natural room tone."
    else
        "Clear spoken dialogue, natural room tone, no background music. " +
            "Nobody speaks except the lines written above: no additional, improvised " +
            "or background dialogue. After the last line the characters stop talking " +
            "and only quiet room tone remains until the end."

    return buildString {
        append("subject_definitions:\n").append(definitions).append("\n\n")
        append("summary:\n").append(summary).append("\n\n")
        append("retention_analysis:\n").append(retention).append("\n\n")
        append("detailed_description:\n").append(action.toString().trimEnd()).append("\n\n")
        append("overall_soundscape:\n").append(soundscape).append("\n\n")
        // Návod chce sekci vyplněnou i tehdy, když hudba být nemá – proto N/A,
        // ne prázdno. Prázdné povinné pole si model vyloží po svém.
        append("non_diegetic_music:\nN/A")
    }
}
