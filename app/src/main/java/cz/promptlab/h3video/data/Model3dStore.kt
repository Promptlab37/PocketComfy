package cz.promptlab.h3video.data

import android.content.Context
import cz.promptlab.h3video.util.ImageUtils
import org.json.JSONObject
import java.io.File

/**
 * Uložení karty 3D model. Fotka se drží beze změny — pozadí z ní odstraňuje
 * až server, takže zmenšovat ji tady by mu jen ubralo detail.
 */
class Model3dStore(private val ctx: Context) {

    private val sp = ctx.getSharedPreferences("h3video", Context.MODE_PRIVATE)

    fun dir(): File = File(ctx.filesDir, "model3d").apply { mkdirs() }

    fun imageFile(): File = File(dir(), "predloha.jpg")

    fun save(s: Model3dScene) {
        sp.edit().putString(
            "model3dScene",
            JSONObject()
                .put("hasImage", s.source != null)
                .put("kvalita", s.kvalita.name)
                .put("detail", s.detail)
                .put("textura", s.textura)
                .toString()
        ).apply()
    }

    fun load(): Model3dScene {
        val raw = sp.getString("model3dScene", "") ?: ""
        if (raw.isBlank()) return Model3dScene()
        return runCatching {
            val root = JSONObject(raw)
            val f = imageFile().takeIf { root.optBoolean("hasImage") && it.exists() }
            Model3dScene(
                source = f,
                thumb = f?.let { ImageUtils.loadFileThumb(it) },
                kvalita = Model3dKvalita.entries
                    .firstOrNull { it.name == root.optString("kvalita") } ?: Model3dKvalita.PBR,
                detail = root.optInt("detail", 1536)
                    .takeIf { it in Model3dScene.DETAILY } ?: 1536,
                textura = root.optInt("textura", 2048)
                    .takeIf { it in Model3dScene.TEXTURY } ?: 2048,
            )
        }.getOrDefault(Model3dScene())
    }
}
