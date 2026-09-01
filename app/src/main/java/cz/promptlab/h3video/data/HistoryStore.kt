package cz.promptlab.h3video.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class VideoItem(
    val id: String,
    val fileName: String,
    val prompt: String,
    val createdAt: Long,
    val seconds: Float,
    val resolution: String,
    val seed: Long,
    val twoImages: Boolean,
    /** Je už kopie i v galerii telefonu (Filmy/H3 Video)? */
    val inGallery: Boolean = false,
    /** Název režimu (Mode.name), u starých záznamů prázdné. */
    val mode: String = "",
) {
    fun file(ctx: Context): File = File(videosDir(ctx), fileName)

    /**
     * Je výsledek obrázek, ne video? Karta Úprava obrázku vrací PNG, takže
     * galerie i obrazovka výsledku musí umět obojí. Pozná se to z přípony —
     * staré záznamy tím pádem zůstávají videem bez migrace.
     */
    val isImage: Boolean
        get() = fileName.substringAfterLast('.', "").lowercase() in
            listOf("png", "jpg", "jpeg", "webp")

    /** Je výsledek skladba (karta Hudba)? Poznává se stejně — z přípony. */
    val isAudio: Boolean
        get() = fileName.substringAfterLast('.', "").lowercase() in
            listOf("mp3", "flac", "wav", "opus", "ogg", "m4a")

    companion object {
        fun videosDir(ctx: Context): File =
            File(ctx.filesDir, "videos").also { it.mkdirs() }
    }
}

/** Seznam vygenerovaných videí. Soubory leží v filesDir/videos, metadata v prefs. */
class HistoryStore(private val ctx: Context) {

    private val sp = ctx.getSharedPreferences("h3history", Context.MODE_PRIVATE)

    fun all(): List<VideoItem> {
        val raw = sp.getString("items", "[]")!!
        val arr = runCatching { JSONArray(raw) }.getOrDefault(JSONArray())
        val list = mutableListOf<VideoItem>()
        var dropped = false
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val item = VideoItem(
                id = o.optString("id"),
                fileName = o.optString("file"),
                prompt = o.optString("prompt"),
                createdAt = o.optLong("at"),
                seconds = o.optDouble("sec", 0.0).toFloat(),
                resolution = o.optString("res"),
                seed = o.optLong("seed"),
                twoImages = o.optBoolean("two"),
                inGallery = o.optBoolean("gal"),
                mode = o.optString("mode"),
            )
            if (item.file(ctx).exists()) list.add(item) else dropped = true
        }
        val sorted = list.sortedByDescending { it.createdAt }
        // Záznam bez souboru se rovnou i smaže – jinak by mrtvá metadata rostla donekonečna.
        if (dropped) persist(sorted)
        return sorted
    }

    fun add(item: VideoItem) {
        val list = all().toMutableList()
        list.removeAll { it.id == item.id }
        list.add(0, item)
        persist(list)
    }

    fun markInGallery(id: String) {
        val list = all().map { if (it.id == id) it.copy(inGallery = true) else it }
        persist(list)
    }

    /** Kolik místa zabírají kopie videí uvnitř aplikace. */
    fun totalBytes(): Long = all().sumOf { runCatching { it.file(ctx).length() }.getOrDefault(0L) }

    fun remove(item: VideoItem) {
        runCatching { item.file(ctx).delete() }
        persist(all().filterNot { it.id == item.id })
    }

    private fun persist(list: List<VideoItem>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(
                JSONObject()
                    .put("id", it.id)
                    .put("file", it.fileName)
                    .put("prompt", it.prompt)
                    .put("at", it.createdAt)
                    .put("sec", it.seconds.toDouble())
                    .put("res", it.resolution)
                    .put("seed", it.seed)
                    .put("two", it.twoImages)
                    .put("gal", it.inGallery)
                    .put("mode", it.mode)
            )
        }
        sp.edit().putString("items", arr.toString()).apply()
    }
}
