package cz.promptlab.h3video.data

import android.content.Context
import cz.promptlab.h3video.util.ImageUtils
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Uložení karty All in One – stejný vzor jako [TimelineStore] a [DirectorStore]:
 * texty a volby jako JSON v nastavení, obrázky a videa jako soubory ve složce
 * aplikace.
 *
 * Ukládá se po každé změně. Vyplněné zadání nesmí zmizet přepnutím karty,
 * otočením telefonu ani tím, že Android appku na pozadí zabije.
 */
class AioStore(private val ctx: Context) {

    private val sp = ctx.getSharedPreferences("h3video", Context.MODE_PRIVATE)

    fun dir(): File = File(ctx.filesDir, "aio").apply { mkdirs() }

    /** Obrázkové sloty. `druh` odděluje snímky videa, reference a klíčové snímky. */
    fun imageFile(druh: String, key: Int) = File(dir(), "${druh}_$key.jpg")

    fun save(s: AioScene) {
        fun slots(list: List<AioSlot>) = JSONArray().also { arr ->
            list.forEach {
                arr.put(
                    JSONObject()
                        .put("key", it.key)
                        .put("image", it.image?.name ?: "")
                        .put("position", it.position)
                )
            }
        }
        sp.edit().putString(
            "aioScene",
            JSONObject()
                .put("mode", s.mode.name)
                .put("prompt", s.prompt)
                .put("seconds", s.seconds.toDouble())
                .put("first", slots(listOf(s.first)))
                .put("last", slots(listOf(s.last)))
                .put("useLastFrame", s.useLastFrame)
                .put("refs", slots(s.refs))
                .put("keys", slots(s.keys))
                // Videa se ukládají celou cestou: importují se do složky `refs`
                // vedle referencí ostatních karet, ne do složky téhle karty.
                .put("refVideo", s.refVideo?.absolutePath ?: "")
                .put("refVideoAudio", s.refVideoAudio)
                .put("sourceVideo", s.sourceVideo?.absolutePath ?: "")
                .put("upscaler", s.upscaler.name)
                .put("upscaleResolution", s.upscaleResolution)
                .put("upscaleMultiplier", s.upscaleMultiplier)
                .put("sheetPanels", s.sheetPanels)
                .put("sheetPhotoreal", s.sheetPhotoreal)
                .toString()
        ).apply()
    }

    fun load(): AioScene {
        val raw = sp.getString("aioScene", "") ?: ""
        if (raw.isBlank()) return AioScene()
        return runCatching {
            val root = JSONObject(raw)
            fun slots(key: String, vychozi: List<AioSlot>): List<AioSlot> {
                val arr = root.optJSONArray(key) ?: return vychozi
                val out = (0 until arr.length()).map { i ->
                    val o = arr.getJSONObject(i)
                    // Soubor se mezitím mohl smazat (úklid systému, odinstalace
                    // dat) – slot pak zůstane prázdný místo odkazu do prázdna.
                    val image = o.optString("image").takeIf { it.isNotBlank() }
                        ?.let { File(dir(), it) }?.takeIf { it.exists() }
                    AioSlot(
                        key = o.optInt("key", i + 1),
                        image = image,
                        thumb = image?.let { ImageUtils.loadFileThumb(it) },
                        position = o.optInt("position", 1),
                    )
                }
                return out.ifEmpty { vychozi }
            }
            fun video(key: String): File? = root.optString(key).takeIf { it.isNotBlank() }
                ?.let { File(it) }?.takeIf { it.exists() }

            AioScene(
                mode = runCatching { AioMode.valueOf(root.optString("mode")) }
                    .getOrDefault(AioMode.TEXT),
                prompt = root.optString("prompt"),
                seconds = root.optDouble("seconds", 5.0).toFloat(),
                first = slots("first", listOf(AioSlot(key = 1))).first(),
                last = slots("last", listOf(AioSlot(key = 2))).first(),
                useLastFrame = root.optBoolean("useLastFrame", false),
                refs = slots("refs", listOf(AioSlot(key = 1))),
                keys = slots("keys", listOf(AioSlot(key = 1))),
                refVideo = video("refVideo"),
                refVideoAudio = root.optBoolean("refVideoAudio", false),
                sourceVideo = video("sourceVideo"),
                upscaler = runCatching { Upscaler.valueOf(root.optString("upscaler")) }
                    .getOrDefault(Upscaler.SEEDVR2),
                upscaleResolution = root.optInt("upscaleResolution", 1080),
                upscaleMultiplier = root.optInt("upscaleMultiplier", 2),
                sheetPanels = root.optInt("sheetPanels", 6),
                sheetPhotoreal = root.optBoolean("sheetPhotoreal", false),
            )
        }.getOrDefault(AioScene())
    }
}
