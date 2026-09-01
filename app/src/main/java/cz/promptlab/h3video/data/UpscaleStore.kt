package cz.promptlab.h3video.data

import android.content.Context
import cz.promptlab.h3video.util.ImageUtils
import org.json.JSONObject
import java.io.File

/**
 * Uložení karty Zvětšit. Na rozdíl od ostatních karet se fotka kopíruje
 * BAJT PO BAJTU — žádné zmenšování ani překódování do JPEG. Vstup upscaleru
 * musí zůstat přesně takový, jaký je.
 */
class UpscaleStore(private val ctx: Context) {

    private val sp = ctx.getSharedPreferences("h3video", Context.MODE_PRIVATE)

    fun dir(): File = File(ctx.filesDir, "upscale").apply { mkdirs() }

    fun save(s: UpscaleScene) {
        sp.edit().putString(
            "upscaleScene",
            JSONObject()
                .put("source", s.source?.name ?: "")
                .put("grid", s.grid)
                .toString()
        ).apply()
    }

    fun load(): UpscaleScene {
        val raw = sp.getString("upscaleScene", "") ?: ""
        if (raw.isBlank()) return UpscaleScene()
        return runCatching {
            val root = JSONObject(raw)
            val src = root.optString("source").takeIf { it.isNotBlank() }
                ?.let { File(dir(), it) }?.takeIf { it.exists() }
            UpscaleScene(
                source = src,
                thumb = src?.let { ImageUtils.loadFileThumb(it) },
                grid = root.optString("grid").takeIf { it in UpscaleScene.GRIDS } ?: "2x2",
            )
        }.getOrDefault(UpscaleScene())
    }
}
