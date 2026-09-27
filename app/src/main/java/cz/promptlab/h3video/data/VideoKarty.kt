package cz.promptlab.h3video.data

import android.content.Context
import androidx.compose.runtime.Immutable
import java.io.File

/** Režim karty Upravit video. */
enum class UpravaRezim(private val titleCs: String) {
    PREMALOVAT("Přemalovat");

    val title: String get() = t(titleCs)
}

/** Režim karty Vylepšit video. */
enum class VylepseniRezim(private val titleCs: String) {
    ZVETSIT("Zvětšit"),
    ZPLYNULIT("Zplynulit");

    val title: String get() = t(titleCs)
}

/** Režim karty Pohyb postavy. */
enum class PohybRezim(private val titleCs: String, val karta: Mode) {
    HUDBA("Podle hudby", Mode.DANCE),
    VIDEO("Podle videa", Mode.ANIMATE);

    val title: String get() = t(titleCs)
}

/**
 * Karta **Upravit video**. Zdrojové video je jedno pro všechny režimy, takže
 * přepnutí režimu ho nezahodí.
 *
 * Přemalovat jede na šabloně `mask.json` balíku All in One — úloha se pro ni
 * skládá až při spuštění ([doAio]), stav karty je vlastní a nesdílí se
 * s kartou All in One.
 */
@Immutable
data class UpravaScene(
    val video: File? = null,
    val rezim: UpravaRezim = UpravaRezim.PREMALOVAT,
    /** Co se má změnit (popis scény pro přemalovanou oblast). */
    val popis: String = "",
    /** Přemalovat: co ve videu sledovat, krátce a anglicky („head"). */
    val maskTarget: String = "",
    val maskObjects: Int = 1,
    /** Přemalovat: čím to nahradit (fotky, nepovinné). */
    val refs: List<AioSlot> = listOf(AioSlot(key = 1)),
    /** Přemalovat: kolik sekund od začátku videa zpracovat. */
    val sekundy: Float = 5f,
) {
    val refsWithImage: List<AioSlot> get() = refs.filter { it.image != null }
    val canAddRef: Boolean get() = refs.size < AioScene.MAX_REFS

    /** Úloha pro šablonu balíku All in One. */
    fun doAio(): AioScene = AioScene(
        mode = AioMode.MASK,
        prompt = popis,
        seconds = sekundy,
        refs = refs,
        sourceVideo = video,
        maskTarget = maskTarget,
        maskObjects = maskObjects,
    )
}

/**
 * Karta **Vylepšit video**. Zvětšení jede na šablonách balíku All in One
 * (SeedVR2 / RTX), zplynulení na interpolaci FILM z jádra ComfyUI.
 */
@Immutable
data class VylepseniScene(
    val video: File? = null,
    val rezim: VylepseniRezim = VylepseniRezim.ZVETSIT,
    val upscaler: Upscaler = Upscaler.SEEDVR2,
    val upscaleResolution: Int = 1080,
    val upscaleMultiplier: Int = 2,
    /** Zplynulit: kolikrát víc snímků. */
    val nasobek: Int = 2,
    /** Zplynulit: nechat fps zdroje, takže pohyb se zpomalí. */
    val zpomalit: Boolean = false,
    /** Rozměry a počet snímků vybraného videa; 0 = neznámé. */
    val sirka: Int = 0,
    val vyska: Int = 0,
    val snimku: Int = 0,
) {
    /**
     * Kolik GB operační paměti zplynulení zabere. `FrameInterpolate` drží
     * vstup i celý výsledek najednou ve float32 (3 kanály × 4 B na bod).
     */
    fun pametGb(nasobek: Int): Double {
        if (sirka <= 0 || vyska <= 0 || snimku <= 0) return 0.0
        val bod = sirka.toDouble() * vyska * 3 * 4
        val vystup = (snimku - 1).toDouble() * nasobek + 1
        return bod * (snimku + vystup) / (1L shl 30)
    }

    /** Násobky, které se vejdou do [PAMET_GB]. Když rozměry neznáme, jen 2×. */
    val nasobkyKtereSeVejdou: List<Int>
        get() = if (snimku <= 0) listOf(2) else NASOBKY.filter { pametGb(it) <= PAMET_GB }

    fun doAio(): AioScene = AioScene(
        mode = AioMode.UPSCALE,
        sourceVideo = video,
        upscaler = upscaler,
        upscaleResolution = upscaleResolution,
        upscaleMultiplier = upscaleMultiplier,
    )

    companion object {
        val NASOBKY = listOf(2, 3, 4)

        /**
         * Strop pro zplynulení. Počítač má 95 GB a MiniMax H3 si při běhu
         * bere kolem 50 GB — zbytek je rezerva, aby se nic nezaseklo.
         */
        const val PAMET_GB = 24.0
    }
}

fun upravaProblem(s: UpravaScene): String? = when {
    s.video == null -> t("Vyber video, které se má upravit.")
    else -> when (s.rezim) {
        UpravaRezim.PREMALOVAT -> aioProblem(s.doAio())
    }
}

fun vylepseniProblem(s: VylepseniScene): String? = when {
    s.video == null -> t("Vyber video, které se má vylepšit.")
    s.rezim == VylepseniRezim.ZPLYNULIT && s.nasobkyKtereSeVejdou.isEmpty() ->
        t("Video je na zplynulení moc dlouhé nebo velké. Zkrať ho nebo zmenši.")
    s.rezim == VylepseniRezim.ZPLYNULIT && s.nasobek !in s.nasobkyKtereSeVejdou ->
        t("Tenhle násobek se do paměti nevejde. Vyber menší.")
    else -> null
}

/**
 * Uložení karet Upravit video a Vylepšit video. Videa leží ve složce
 * `refs` pod vlastními jmény, takže se s kartou All in One nepřepisují.
 */
class VideoKartyStore(private val ctx: Context) {

    private val sp = ctx.getSharedPreferences("h3video", Context.MODE_PRIVATE)

    fun dir(): File = File(ctx.filesDir, "uprava").also { it.mkdirs() }

    fun refFile(key: Int): File = File(dir(), "ref_$key.jpg")

    private fun soubor(cesta: String): File? =
        cesta.takeIf { it.isNotBlank() }?.let { File(it) }?.takeIf { it.exists() }

    fun loadUprava(): UpravaScene = runCatching {
        val j = org.json.JSONObject(sp.getString(K_UPRAVA, "{}")!!)
        val refs = j.optJSONArray("refs")?.let { arr ->
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                AioSlot(key = o.optInt("key", i + 1), image = soubor(o.optString("image")))
            }
        }?.takeIf { it.isNotEmpty() } ?: listOf(AioSlot(key = 1))
        UpravaScene(
            video = soubor(j.optString("video")),
            rezim = runCatching { UpravaRezim.valueOf(j.optString("rezim")) }
                .getOrDefault(UpravaRezim.PREMALOVAT),
            popis = j.optString("popis"),
            maskTarget = j.optString("maskTarget"),
            maskObjects = j.optInt("maskObjects", 1).coerceIn(1, 3),
            refs = refs,
            sekundy = j.optDouble("sekundy", 5.0).toFloat().coerceIn(2f, 15f),
        )
    }.getOrDefault(UpravaScene())

    fun saveUprava(s: UpravaScene) {
        val refs = org.json.JSONArray()
        s.refs.forEach {
            refs.put(org.json.JSONObject().put("key", it.key).put("image", it.image?.absolutePath ?: ""))
        }
        sp.edit().putString(
            K_UPRAVA,
            org.json.JSONObject()
                .put("video", s.video?.absolutePath ?: "")
                .put("rezim", s.rezim.name)
                .put("popis", s.popis)
                .put("maskTarget", s.maskTarget)
                .put("maskObjects", s.maskObjects)
                .put("refs", refs)
                .put("sekundy", s.sekundy.toDouble())
                .toString()
        ).apply()
    }

    fun loadVylepseni(): VylepseniScene = runCatching {
        val j = org.json.JSONObject(sp.getString(K_VYLEPSENI, "{}")!!)
        VylepseniScene(
            video = soubor(j.optString("video")),
            rezim = runCatching { VylepseniRezim.valueOf(j.optString("rezim")) }
                .getOrDefault(VylepseniRezim.ZVETSIT),
            upscaler = runCatching { Upscaler.valueOf(j.optString("upscaler")) }
                .getOrDefault(Upscaler.SEEDVR2),
            upscaleResolution = j.optInt("upscaleResolution", 1080).coerceIn(720, 2160),
            upscaleMultiplier = j.optInt("upscaleMultiplier", 2).coerceIn(2, 4),
            nasobek = j.optInt("nasobek", 2).takeIf { it in VylepseniScene.NASOBKY } ?: 2,
            zpomalit = j.optBoolean("zpomalit", false),
            sirka = j.optInt("sirka", 0),
            vyska = j.optInt("vyska", 0),
            snimku = j.optInt("snimku", 0),
        ).let { s -> if (s.video == null) s.copy(sirka = 0, vyska = 0, snimku = 0) else s }
    }.getOrDefault(VylepseniScene())

    fun saveVylepseni(s: VylepseniScene) {
        sp.edit().putString(
            K_VYLEPSENI,
            org.json.JSONObject()
                .put("video", s.video?.absolutePath ?: "")
                .put("rezim", s.rezim.name)
                .put("upscaler", s.upscaler.name)
                .put("upscaleResolution", s.upscaleResolution)
                .put("upscaleMultiplier", s.upscaleMultiplier)
                .put("nasobek", s.nasobek)
                .put("zpomalit", s.zpomalit)
                .put("sirka", s.sirka)
                .put("vyska", s.vyska)
                .put("snimku", s.snimku)
                .toString()
        ).apply()
    }

    fun loadPohyb(): PohybRezim =
        runCatching { PohybRezim.valueOf(sp.getString(K_POHYB, "")!!) }.getOrDefault(PohybRezim.HUDBA)

    fun savePohyb(r: PohybRezim) {
        sp.edit().putString(K_POHYB, r.name).apply()
    }

    private companion object {
        const val K_UPRAVA = "upravaVideaScene"
        const val K_VYLEPSENI = "vylepseniVideaScene"
        const val K_POHYB = "pohybRezim"
    }
}
