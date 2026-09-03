package cz.promptlab.h3video.data

import android.content.Context
import cz.promptlab.h3video.util.ImageUtils
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Uložení karty Dlouhé video. Drží zadání všech úseků, protože vyplnit šest
 * popisů je práce, kterou nemá smysl ztratit zavřením appky.
 */
class LongStore(private val ctx: Context) {

    private val sp = ctx.getSharedPreferences("h3video", Context.MODE_PRIVATE)

    fun dir(): File = File(ctx.filesDir, "long").apply { mkdirs() }

    fun imageFile(key: Int): File = File(dir(), "ref_$key.jpg")

    fun videoFile(): File = File(dir(), "zdroj.mp4")

    fun save(s: LongScene) {
        val useky = JSONArray()
        s.useky.forEach {
            useky.put(
                JSONObject()
                    .put("key", it.key)
                    .put("prompt", it.prompt)
                    .put("seconds", it.seconds.toDouble())
                    .put("lora", it.lora)
                    .put("loraSila", it.loraSila.toDouble())
            )
        }
        val refs = JSONArray()
        s.refs.forEach { refs.put(JSONObject().put("key", it.key).put("has", it.image != null)) }
        sp.edit().putString(
            "longScene",
            JSONObject()
                .put("zacatek", s.zacatek.name)
                .put("hasVideo", s.sourceVideo != null)
                .put("startPrompt", s.startPrompt)
                .put("startSeconds", s.startSeconds.toDouble())
                .put("rychlyZacatek", s.rychlyZacatek)
                .put("spolecnaLora", s.spolecnaLora)
                .put("spolecnaLoraSila", s.spolecnaLoraSila.toDouble())
                .put("useky", useky)
                .put("refs", refs)
                .toString()
        ).apply()
    }

    fun load(): LongScene {
        val raw = sp.getString("longScene", "") ?: ""
        if (raw.isBlank()) return LongScene()
        return runCatching {
            val root = JSONObject(raw)
            val useky = mutableListOf<LongUsek>()
            val pole = root.optJSONArray("useky") ?: JSONArray()
            for (i in 0 until pole.length()) {
                val u = pole.getJSONObject(i)
                useky += LongUsek(
                    key = u.optInt("key", i + 1),
                    prompt = u.optString("prompt"),
                    seconds = u.optDouble("seconds", 7.0).toFloat(),
                    lora = u.optString("lora"),
                    loraSila = u.optDouble("loraSila", 1.0).toFloat(),
                )
            }
            val refs = mutableListOf<AioSlot>()
            val rp = root.optJSONArray("refs") ?: JSONArray()
            for (i in 0 until rp.length()) {
                val r = rp.getJSONObject(i)
                val key = r.optInt("key", i + 1)
                val f = imageFile(key).takeIf { r.optBoolean("has") && it.exists() }
                refs += AioSlot(key = key, image = f, thumb = f?.let { ImageUtils.loadFileThumb(it) })
            }
            val video = videoFile().takeIf { root.optBoolean("hasVideo") && it.exists() }
            LongScene(
                zacatek = LongStart.entries
                    .firstOrNull { it.name == root.optString("zacatek") } ?: LongStart.EXISTING_VIDEO,
                sourceVideo = video,
                sourceThumb = null,
                startPrompt = root.optString("startPrompt"),
                startSeconds = root.optDouble("startSeconds", 7.0).toFloat(),
                rychlyZacatek = root.optBoolean("rychlyZacatek", false),
                spolecnaLora = root.optString("spolecnaLora"),
                spolecnaLoraSila = root.optDouble("spolecnaLoraSila", 1.0).toFloat(),
                refs = refs,
                useky = useky.ifEmpty { listOf(LongUsek(key = 1)) },
            )
        }.getOrDefault(LongScene())
    }
}
