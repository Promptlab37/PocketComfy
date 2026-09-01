package cz.promptlab.h3video.data

import android.content.Context
import cz.promptlab.h3video.util.ImageUtils
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Uložení časové osy – stejný vzor jako [DirectorStore] a [TalkStore]: texty
 * a volby jako JSON v nastavení, snímky segmentů jako soubory ve složce aplikace.
 *
 * Osa je práce na dlouho (dvanáct segmentů), takže nesmí zmizet, když Android
 * appku na pozadí zabije nebo když se nainstaluje nová verze.
 */
class TimelineStore(private val ctx: Context) {

    private val sp = ctx.getSharedPreferences("h3video", Context.MODE_PRIVATE)

    fun dir(): File = File(ctx.filesDir, "timeline").apply { mkdirs() }

    fun imageFile(key: Int) = File(dir(), "seg_$key.jpg")

    fun save(scene: TimelineScene) {
        val segments = JSONArray()
        scene.segments.forEach { s ->
            segments.put(
                JSONObject()
                    .put("key", s.key)
                    .put("mode", s.mode.name)
                    .put("prompt", s.prompt)
                    .put("seconds", s.seconds.toDouble())
                    .put("image", s.image?.name ?: "")
                    .put("inherit", s.inheritPrevious)
            )
        }
        sp.edit()
            .putString(
                "timelineScene",
                JSONObject()
                    .put("segments", segments)
                    .put("global", scene.globalPrompt)
                    .put("project", scene.project)
                    .put("onlySegment", scene.onlySegment)
                    .toString()
            )
            .apply()
    }

    fun load(): TimelineScene {
        val raw = sp.getString("timelineScene", "") ?: ""
        if (raw.isBlank()) return TimelineScene()
        return runCatching {
            val root = JSONObject(raw)
            val arr = root.optJSONArray("segments") ?: JSONArray()
            val segments = (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                val image = o.optString("image").takeIf { it.isNotBlank() }
                    ?.let { File(dir(), it) }?.takeIf { it.exists() }
                TimelineSegment(
                    key = o.optInt("key", i + 1),
                    mode = runCatching { SegmentMode.valueOf(o.optString("mode")) }
                        .getOrDefault(SegmentMode.TEXT),
                    prompt = o.optString("prompt"),
                    seconds = o.optDouble("seconds", 8.0).toFloat(),
                    image = image,
                    thumb = image?.let { ImageUtils.loadFileThumb(it) },
                    inheritPrevious = o.optBoolean("inherit", false),
                )
            }
            TimelineScene(
                segments = segments.ifEmpty { listOf(TimelineSegment(key = 1)) },
                globalPrompt = root.optString("global", TimelineScene.DEFAULT_GLOBAL),
                project = root.optString("project", "h3app"),
                onlySegment = root.optInt("onlySegment", 0),
            )
        }.getOrDefault(TimelineScene())
    }

    fun forgetSegment(key: Int) {
        runCatching { imageFile(key).delete() }
    }
}
