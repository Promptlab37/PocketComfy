package cz.promptlab.h3video.data

import android.content.Context
import cz.promptlab.h3video.util.ImageUtils
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Uložení mluvící scény. Texty a volby jdou do nastavení jako JSON, obrázky
 * a namluvené repliky zůstávají jako soubory ve složce aplikace.
 *
 * Rozdělaná scéna je práce na několik minut, takže musí přežít i zabití aplikace
 * na pozadí.
 */
class TalkStore(private val ctx: Context) {

    private val sp = ctx.getSharedPreferences("h3video", Context.MODE_PRIVATE)

    fun dir(): File = File(ctx.filesDir, "talk").apply { mkdirs() }

    fun imageFile(key: Int) = File(dir(), "img_$key.jpg")
    fun audioFile(lineKey: Int) = File(dir(), "line_$lineKey.wav")

    fun save(scene: TalkScene) {
        val speakers = JSONArray()
        scene.speakers.forEach { s ->
            speakers.put(
                JSONObject()
                    .put("key", s.key)
                    .put("look", s.look)
                    .put("image", s.image?.name ?: "")
                    .put("voiceId", (s.voice as? VoiceSource.Library)?.voiceId ?: "")
                    .put("voiceName", (s.voice as? VoiceSource.Library)?.voiceName ?: "")
                    .put("sample", (s.voice as? VoiceSource.Sample)?.file?.name ?: "")
                    .put("sampleLabel", (s.voice as? VoiceSource.Sample)?.label ?: "")
            )
        }
        val lines = JSONArray()
        scene.lines.forEach { l ->
            lines.put(
                JSONObject()
                    .put("key", l.key)
                    .put("speaker", l.speakerKey)
                    .put("text", l.text)
                    .put("spoken", l.spokenText)
                    .put("spokenVoice", l.spokenVoice)
                    .put("audio", l.audio?.name ?: "")
                    .put("seconds", l.audioSeconds.toDouble())
            )
        }
        sp.edit()
            .putString(
                "talkScene",
                JSONObject()
                    .put("speakers", speakers)
                    .put("lines", lines)
                    .put("note", scene.sceneNote)
                    .put("prompt", scene.prompt)
                    .put("edited", scene.promptEdited)
                    .toString()
            )
            .apply()
    }

    /** Načte scénu; soubor, který mezitím zmizel, se tiše přeskočí. */
    fun load(): TalkScene {
        val raw = sp.getString("talkScene", "") ?: ""
        if (raw.isBlank()) return TalkScene()
        return runCatching {
            val root = JSONObject(raw)

            val sArr = root.optJSONArray("speakers") ?: JSONArray()
            val speakers = (0 until sArr.length()).map { i ->
                val o = sArr.getJSONObject(i)
                val image = o.optString("image").takeIf { it.isNotBlank() }
                    ?.let { File(dir(), it) }?.takeIf { it.exists() }
                val sample = o.optString("sample").takeIf { it.isNotBlank() }
                    ?.let { File(dir(), it) }?.takeIf { it.exists() }
                val voiceId = o.optString("voiceId")
                Speaker(
                    key = o.optInt("key", i + 1),
                    image = image,
                    thumb = image?.let { ImageUtils.loadFileThumb(it) },
                    look = o.optString("look"),
                    voice = when {
                        voiceId.isNotBlank() ->
                            VoiceSource.Library(voiceId, o.optString("voiceName", voiceId))
                        sample != null ->
                            VoiceSource.Sample(sample, o.optString("sampleLabel", t("vlastní nahrávka")))
                        else -> null
                    },
                )
            }

            val lArr = root.optJSONArray("lines") ?: JSONArray()
            val lines = (0 until lArr.length()).map { i ->
                val o = lArr.getJSONObject(i)
                val audio = o.optString("audio").takeIf { it.isNotBlank() }
                    ?.let { File(dir(), it) }?.takeIf { it.exists() }
                Line(
                    key = o.optInt("key", i + 1),
                    speakerKey = o.optInt("speaker", speakers.firstOrNull()?.key ?: 1),
                    text = o.optString("text"),
                    audio = audio,
                    status = if (audio != null) VoiceStatus.READY else VoiceStatus.NONE,
                    spokenText = o.optString("spoken"),
                    spokenVoice = o.optString("spokenVoice"),
                    audioSeconds = o.optDouble("seconds", 0.0).toFloat(),
                )
            }

            TalkScene(
                speakers = speakers.ifEmpty { listOf(Speaker(key = 1)) },
                lines = lines.ifEmpty {
                    listOf(Line(key = 1, speakerKey = speakers.firstOrNull()?.key ?: 1))
                },
                sceneNote = root.optString("note"),
                prompt = root.optString("prompt"),
                promptEdited = root.optBoolean("edited", false),
            )
        }.getOrDefault(TalkScene())
    }

    /** Smaže soubory postavy, která ze scény zmizela. */
    fun forgetSpeaker(key: Int) {
        runCatching { imageFile(key).delete() }
        smazVzorky(key)
    }

    /** Vzorky hlasu postavy (`sample_<key>.…` i `sample_<key>_<čas>.…`), kromě [krome]. */
    fun smazVzorky(key: Int, krome: File? = null) {
        dir().listFiles { f ->
            (f.name.startsWith("sample_$key.") || f.name.startsWith("sample_${key}_")) && f != krome
        }?.forEach { it.delete() }
    }

    /**
     * Zkopíruje vzorek hlasu do složky scény — jen odsud se po restartu načte.
     * Název nese čas: nahrávky replik si pamatují soubor, ze kterého vznikly,
     * takže nový vzorek nesmí přepsat starý pod stejným jménem.
     */
    fun ulozVzorek(key: Int, zdroj: File): File? = runCatching {
        val pripona = zdroj.extension.ifBlank { "wav" }
        val cil = File(dir(), "sample_${key}_${System.currentTimeMillis()}.$pripona")
        zdroj.copyTo(cil, overwrite = true)
        smazVzorky(key, krome = cil)
        cil
    }.getOrNull()

    fun forgetLine(key: Int) {
        runCatching { audioFile(key).delete() }
    }
}
