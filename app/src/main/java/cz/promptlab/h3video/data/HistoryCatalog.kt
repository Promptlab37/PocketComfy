package cz.promptlab.h3video.data

import org.json.JSONArray
import org.json.JSONObject
import java.text.Normalizer
import java.util.Locale

enum class MediaKind { VIDEO, IMAGE, AUDIO, MODEL3D }
enum class HistoryOrder { NEWEST, OLDEST, NAME }

val VideoItem.mediaKind: MediaKind
    get() = when {
        isImage -> MediaKind.IMAGE
        isAudio -> MediaKind.AUDIO
        isModel3d -> MediaKind.MODEL3D
        else -> MediaKind.VIDEO
    }

/** Hledání funguje i bez české diakritiky, každé slovo se hledá samostatně. */
private fun searchKey(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFD)
    .replace(Regex("\\p{M}+"), "").lowercase(Locale.ROOT)

fun filterHistory(
    items: List<VideoItem>,
    query: String = "",
    kind: MediaKind? = null,
    favoritesOnly: Boolean = false,
    order: HistoryOrder = HistoryOrder.NEWEST,
): List<VideoItem> {
    val words = searchKey(query.trim()).split(Regex("\\s+")).filter { it.isNotEmpty() }
    val filtered = items.filter { item ->
        (kind == null || item.mediaKind == kind) && (!favoritesOnly || item.favorite) &&
            (words.isEmpty() || searchKey("${item.title} ${item.prompt} ${item.fileName} ${item.seed} ${item.mode}")
                .let { text -> words.all { it in text } })
    }
    return when (order) {
        HistoryOrder.NEWEST -> filtered.sortedWith(compareByDescending<VideoItem> { it.createdAt }.thenBy { it.id })
        HistoryOrder.OLDEST -> filtered.sortedWith(compareBy<VideoItem> { it.createdAt }.thenBy { it.id })
        HistoryOrder.NAME -> filtered.sortedWith(compareBy<VideoItem> { searchKey(it.displayTitle) }.thenBy { it.id })
    }
}

/** Zpětně kompatibilní metadata; jeden vadný řádek nesmí vyřadit celou knihovnu. */
object HistoryCodec {
    data class Decoded(val items: List<VideoItem>, val damaged: Boolean)

    fun decode(raw: String): Decoded {
        val array = runCatching { JSONArray(raw) }.getOrElse { return Decoded(emptyList(), true) }
        val items = mutableListOf<VideoItem>()
        val ids = mutableSetOf<String>()
        var damaged = false
        for (index in 0 until array.length()) {
            val item = runCatching {
                val o = array.getJSONObject(index)
                val id = o.getString("id")
                val file = o.getString("file")
                require(id.isNotBlank() && file.isNotBlank() && file != "." && file != "..")
                require(file.none { it == '/' || it == '\\' || it == ':' || it.isISOControl() })
                VideoItem(
                    id = id, fileName = file, prompt = o.optString("prompt"),
                    createdAt = o.optLong("at"),
                    seconds = o.optDouble("sec", 0.0).takeIf { it.isFinite() && it >= 0 }?.toFloat() ?: 0f,
                    resolution = o.optString("res"), seed = o.optLong("seed"),
                    twoImages = o.optBoolean("two"), inGallery = o.optBoolean("gal"),
                    mode = o.optString("mode"), tookSeconds = o.optInt("took").coerceAtLeast(0),
                    title = o.optString("title").trim().take(120), favorite = o.optBoolean("favorite"),
                )
            }.getOrNull()
            if (item != null && ids.add(item.id)) items += item else damaged = true
        }
        return Decoded(items, damaged)
    }

    fun encode(items: List<VideoItem>): String = JSONArray().apply {
        items.forEach { item ->
            put(JSONObject()
                .put("id", item.id).put("file", item.fileName).put("prompt", item.prompt)
                .put("at", item.createdAt).put("sec", item.seconds.toDouble()).put("res", item.resolution)
                .put("seed", item.seed).put("two", item.twoImages).put("gal", item.inGallery)
                .put("mode", item.mode).put("took", item.tookSeconds)
                .put("title", item.title).put("favorite", item.favorite))
        }
    }.toString()
}
