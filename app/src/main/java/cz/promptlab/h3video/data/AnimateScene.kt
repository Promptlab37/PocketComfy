package cz.promptlab.h3video.data

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.Immutable
import java.io.File

/**
 * Karta **Wan Animate** — Wan-Animate 2 (14B), nativně v jádře ComfyUI.
 *
 * Postava z fotky zopakuje pohyb z videa: tělo, ruce i mimiku. Řídicí video
 * jde do modelu přímo, bez vytahování kostry nebo masek. Pozadí a kameru
 * určuje zadání, ne fotka ani video.
 *
 * Délku určuje řídicí video: graf ho projde celé po úsecích 81 snímků
 * a úseky na sebe navazují přes poslední snímek předchozího.
 */
@Immutable
data class AnimateScene(
    /** Fotka postavy — z ní se bere podoba. */
    val fotka: File? = null,
    /** Náhled vybrané fotky; do uloženého zadání nepatří, jen na obrazovku. */
    val nahled: Bitmap? = null,
    /** Řídicí video. Kopie u sebe, odkaz do galerie může vypršet ve frontě. */
    val video: File? = null,
    /** Délka videa v sekundách; 0 = neznámá. */
    val videoSekund: Float = 0f,
    /** Počet snímků videa; 0 = neznámý. */
    val videoSnimku: Int = 0,
    /** Je video na výšku? Podle toho se volí plátno. */
    val naVysku: Boolean = true,
    /** Vzhled postavy. */
    val postava: String = "",
    /** Prostředí, ve kterém se postava pohybuje. */
    val prostredi: String = "",
    /** Co postava ve videu dělá. */
    val pohyb: String = "",
) {
    val sirka: Int get() = if (naVysku) KRATSI else DELSI
    val vyska: Int get() = if (naVysku) DELSI else KRATSI

    /**
     * Kolik úseků graf vyrobí. Stejný vzorec jako uzel v oficiální předloze:
     * `floor((F - 2) / (b - 1)) + 1`.
     */
    val useku: Int
        get() = if (videoSnimku < 2) 1 else (videoSnimku - 2) / (SNIMKU_NA_USEK - 1) + 1

    /**
     * Hlavní zadání ve tvaru z oficiální předlohy:
     * `Character Description: …` a `Background description: …`.
     * Co uživatel nevyplní, se vynechá.
     */
    val zadani: String
        get() = listOfNotNull(
            postava.trim().takeIf { it.isNotEmpty() }?.let { "Character Description: $it" },
            prostredi.trim().takeIf { it.isNotEmpty() }?.let { "Background description: $it" },
        ).joinToString("\n")

    val uploadImages: List<File> get() = listOfNotNull(fotka)

    companion object {
        /** Délka jednoho úseku v předloze (`fragment_length`). */
        const val SNIMKU_NA_USEK = 81
        /** Plátno 480p, strany jsou násobky 16. */
        const val KRATSI = 480
        const val DELSI = 832
    }
}

/** Co kartě chybí, než se dá spustit. */
fun animateProblem(s: AnimateScene): String? = when {
    s.fotka == null -> t("Vyber fotku postavy.")
    s.video == null -> t("Vyber video s pohybem.")
    else -> null
}

/** Uložené zadání karty — přežije zavření aplikace. */
class AnimateStore(private val ctx: Context) {

    private val sp = ctx.getSharedPreferences("h3video", Context.MODE_PRIVATE)

    fun dir(): File = File(ctx.filesDir, "animate").also { it.mkdirs() }

    fun fotkaFile(): File = File(dir(), "postava.png")

    fun load(): AnimateScene = runCatching {
        val j = org.json.JSONObject(sp.getString(KEY, "{}")!!)
        AnimateScene(
            fotka = j.optString("fotka").takeIf { it.isNotBlank() }
                ?.let { File(it) }?.takeIf { it.exists() },
            video = j.optString("video").takeIf { it.isNotBlank() }
                ?.let { File(it) }?.takeIf { it.exists() },
            videoSekund = j.optDouble("videoSekund", 0.0).toFloat(),
            videoSnimku = j.optInt("videoSnimku", 0),
            naVysku = j.optBoolean("naVysku", true),
            postava = j.optString("postava"),
            prostredi = j.optString("prostredi"),
            pohyb = j.optString("pohyb"),
        ).let { s -> if (s.video == null) s.copy(videoSekund = 0f, videoSnimku = 0) else s }
    }.getOrDefault(AnimateScene())

    fun save(s: AnimateScene) {
        sp.edit().putString(
            KEY,
            org.json.JSONObject()
                .put("fotka", s.fotka?.absolutePath ?: "")
                .put("video", s.video?.absolutePath ?: "")
                .put("videoSekund", s.videoSekund.toDouble())
                .put("videoSnimku", s.videoSnimku)
                .put("naVysku", s.naVysku)
                .put("postava", s.postava)
                .put("prostredi", s.prostredi)
                .put("pohyb", s.pohyb)
                .toString()
        ).apply()
    }

    private companion object { const val KEY = "animateScene" }
}
