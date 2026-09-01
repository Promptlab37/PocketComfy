package cz.promptlab.h3video.data

import android.content.Context
import cz.promptlab.h3video.util.ImageUtils
import org.json.JSONObject
import java.io.File

/**
 * Uložení karty Úprava obrázku – stejný vzor jako ostatní scény: volby jako
 * JSON v nastavení, obrázky jako soubory ve složce aplikace.
 */
class ImageEditStore(private val ctx: Context) {

    private val sp = ctx.getSharedPreferences("h3video", Context.MODE_PRIVATE)

    fun dir(): File = File(ctx.filesDir, "edit").apply { mkdirs() }

    /** `druh` odděluje upravovanou fotku od vkládané osoby. */
    fun imageFile(druh: String) = File(dir(), "$druh.jpg")

    fun save(s: ImageEditScene) {
        sp.edit().putString(
            "editScene",
            JSONObject()
                .put("source", s.source?.name ?: "")
                .put("person", s.person?.name ?: "")
                .put("prompt", s.prompt)
                .put("refBoost", s.refBoost.toDouble())
                .put("groundingPx", s.groundingPx)
                .put("megapixels", s.megapixels.toDouble())
                .put("aspect", s.aspect.name)
                .toString()
        ).apply()
    }

    fun load(): ImageEditScene {
        val raw = sp.getString("editScene", "") ?: ""
        // Jednorázová oprava uložených hodnot z 2.65–2.70: popisek páčky
        // „vidění předlohy" tvrdil opak skutečnosti, takže nastavené hodnoty
        // vznikly pod špatnou informací. Jednou se přepíšou na věrnost lidí.
        if (!sp.getBoolean("editMigV2", false)) {
            sp.edit().putBoolean("editMigV2", true).apply()
            if (raw.isNotBlank()) {
                runCatching {
                    val r = org.json.JSONObject(raw)
                    r.put("groundingPx", 1024)
                    r.put("refBoost", 1.5)
                    sp.edit().putString("editScene", r.toString()).apply()
                    return load()
                }
            }
        }
        if (raw.isBlank()) return ImageEditScene()
        return runCatching {
            val root = JSONObject(raw)
            // Soubor mohl mezitím zmizet (úklid systému) – slot pak zůstane
            // prázdný místo odkazu do prázdna.
            fun obrazek(klic: String): File? = root.optString(klic).takeIf { it.isNotBlank() }
                ?.let { File(dir(), it) }?.takeIf { it.exists() }

            val src = obrazek("source")
            val osoba = obrazek("person")
            ImageEditScene(
                source = src,
                thumb = src?.let { ImageUtils.loadFileThumb(it) },
                person = osoba,
                personThumb = osoba?.let { ImageUtils.loadFileThumb(it) },
                prompt = root.optString("prompt"),
                refBoost = root.optDouble("refBoost", 1.5).toFloat(),
                groundingPx = root.optInt("groundingPx", 1024),
                megapixels = root.optDouble("megapixels", 1.0).toFloat(),
                aspect = runCatching { Aspect.valueOf(root.optString("aspect")) }
                    .getOrDefault(Aspect.SQUARE_1_1),
            )
        }.getOrDefault(ImageEditScene())
    }
}
