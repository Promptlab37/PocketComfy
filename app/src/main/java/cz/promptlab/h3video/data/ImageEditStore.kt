package cz.promptlab.h3video.data

import android.content.Context
import cz.promptlab.h3video.util.ImageUtils
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * Uložení karty Úprava obrázku – stejný vzor jako ostatní scény: volby jako
 * JSON v nastavení, obrázky jako soubory ve složce aplikace.
 */
class ImageEditStore(private val ctx: Context) {

    private val sp = ctx.getSharedPreferences("h3video", Context.MODE_PRIVATE)

    fun dir(): File = File(ctx.filesDir, "edit").apply { mkdirs() }

    /** `druh` odděluje upravovanou fotku od vkládané osoby. */
    /** PNG zachová alfa kanál, který Qwen Image 2.1 umí číst i vyrábět. */
    fun imageFile(druh: String) = File(dir(), "$druh.png")

    /** Reference jsou unikátní, aby po odebrání a posunu slotů nedošlo k přepsání jiné. */
    fun newReferenceFile() = File(dir(), "reference_${UUID.randomUUID()}.png")

    fun save(s: ImageEditScene) {
        sp.edit().putString(
            "editScene",
            JSONObject()
                .put("source", s.source?.name ?: "")
                .put("person", s.person?.name ?: "")
                .put("moreReferences", JSONArray().apply {
                    s.moreReferences.forEach { put(it.file.name) }
                })
                .put("prompt", s.prompt)
                .put("refBoost", s.refBoost.toDouble())
                .put("loraSila", s.loraSila.toDouble())
                .put("qwen21Steps", s.qwen21Steps)
                .put("qwen21Resolution", s.qwen21Resolution.name)
                .put("qwen21CacheDevice", s.qwen21CacheDevice.name)
                .put("qwen21CachePrecision", s.qwen21CachePrecision.name)
                .put("qwen21Transparent", s.qwen21Transparent)
                .put("qwen21Detailer", s.qwen21Detailer)
                .put("modelLoras", EditLoras.encode(s.modelLoras))
                .put("groundingPx", s.groundingPx)
                .put("megapixels", s.megapixels.toDouble())
                .put("aspect", s.aspect.name)
                .put("motor", s.motor.name)
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
            val dalsi = root.optJSONArray("moreReferences")?.let { arr ->
                (0 until arr.length()).mapNotNull { index ->
                    arr.optString(index).takeIf { it.isNotBlank() }
                        ?.let { File(dir(), it) }?.takeIf { it.exists() }
                        ?.let { EditReference(it, ImageUtils.loadFileThumb(it)) }
                }
            }.orEmpty().take(ImageEditScene.MAX_QWEN21_REFERENCES - if (osoba != null) 1 else 0)
            ImageEditScene(
                source = src,
                thumb = src?.let { ImageUtils.loadFileThumb(it) },
                person = osoba,
                personThumb = osoba?.let { ImageUtils.loadFileThumb(it) },
                moreReferences = dalsi,
                prompt = root.optString("prompt"),
                qwen21Steps = root.optInt("qwen21Steps", 25).coerceIn(10, 50),
                qwen21Resolution = enumOrDefault(
                    root.optString("qwen21Resolution"), Qwen21Resolution.STANDARD,
                ),
                qwen21CacheDevice = enumOrDefault(
                    root.optString("qwen21CacheDevice"), Qwen21CacheDevice.AUTO,
                ),
                qwen21CachePrecision = enumOrDefault(
                    root.optString("qwen21CachePrecision"), Qwen21CachePrecision.DEFAULT,
                ),
                qwen21Transparent = root.optBoolean("qwen21Transparent", false),
                qwen21Detailer = root.optBoolean("qwen21Detailer", false),
                modelLoras = EditLoras.decode(root.optJSONObject("modelLoras")),
                // Uložená volba odstraněného starého Qwenu se automaticky převede
                // na jediný podporovaný Qwen Image 2.1.
                motor = if (root.optString("motor") == "QWEN") EditMotor.QWEN21 else
                    EditMotor.entries.firstOrNull { it.name == root.optString("motor") }
                        ?: EditMotor.KREA2,
                // Výchozí je nově vyvážené nastavení, ne to nejvíc zamčené —
                // v něm model zadání přecházel.
                refBoost = root.optDouble("refBoost", EditZamer.VYVAZENE.refBoost.toDouble())
                    .toFloat(),
                loraSila = root.optDouble("loraSila", EditZamer.VYVAZENE.loraSila.toDouble())
                    .toFloat(),
                groundingPx = root.optInt("groundingPx", EditZamer.VYVAZENE.grounding),
                megapixels = root.optDouble("megapixels", 1.0).toFloat(),
                aspect = runCatching { Aspect.valueOf(root.optString("aspect")) }
                    .getOrDefault(Aspect.SQUARE_1_1),
            )
        }.getOrDefault(ImageEditScene())
    }

    private inline fun <reified T : Enum<T>> enumOrDefault(raw: String, fallback: T): T =
        enumValues<T>().firstOrNull { it.name == raw } ?: fallback
}
