package cz.promptlab.h3video.data

import android.content.Context
import androidx.compose.runtime.Immutable
import java.io.File

/** Režim karty Upravit video. */
enum class UpravaRezim(private val titleCs: String) {
    PREMALOVAT("Přemalovat"),
    POSTAVA("Vyměnit postavu"),
    PREDLOHA("Podle předlohy"),
    ZADANI("Podle zadání");

    val title: String get() = t(titleCs)
}

/** Co se z videa bere jako předloha pro nové video (Fun ControlNet). */
enum class PredlohaDruh(private val titleCs: String) {
    OBRYSY("Obrysy"),
    POZA("Póza");

    val title: String get() = t(titleCs)
}

/**
 * Čím se vyměňuje postava ve videu. [delka] se ukazuje přímo u volby
 * (uživatel 28. 9. 2026: „ať je jasné, jak dlouhé video jaký režim zvládne“):
 * SCAIL-2 projde celé video po úsecích 81 snímků (ScailBuilder), H3 bere
 * nejvýš [UpravaScene.H3_MAX_S].
 */
enum class PostavaMotor(private val titleCs: String, private val delkaCs: String) {
    SCAIL("SCAIL-2", "celé video"),
    H3("MiniMax H3", "do 20 s");

    val title: String get() = t(titleCs)
    val delka: String get() = t(delkaCs)
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
    /** Vyměnit postavu: fotka nové postavy. */
    val postava: File? = null,
    val postavaNahled: android.graphics.Bitmap? = null,
    /** Vyměnit postavu: koho ve videu (SAM 3 podle textu, anglicky). */
    val kohoVymenit: String = "",
    /** Je video na výšku? Podle toho se volí plátno. */
    val naVysku: Boolean = true,
    /** Délka videa v sekundách (0 = neznámá). */
    val videoSekund: Float = 0f,
    val motorPostavy: PostavaMotor = PostavaMotor.SCAIL,
    /**
     * Vyměnit postavu: úsek videa od–do v sekundách (do 0 = co motor unese —
     * SCAIL do konce, H3 [H3_POSTAVA_MAX_S] od začátku úseku). Video se
     * ořízne na serveru uzlem jádra `Video Slice`, v telefonu zůstane celé.
     */
    val postavaOd: Float = 0f,
    val postavaDo: Float = 0f,
    /** Podle předlohy: co se z videa bere a jestli rychle (4 kroky). */
    val predlohaDruh: PredlohaDruh = PredlohaDruh.POZA,
    val predlohaRychle: Boolean = true,
    /** Podle předlohy: kolik sekund videa (vlastní pole, ne [sekundy] Přemalovat). */
    val predlohaSekundy: Float = PREDLOHA_MIN_S,
    /** Podle zadání (Bernini-R): turbo LoRA a 6 kroků, nebo 40 bez ní. */
    val zadaniRychle: Boolean = true,
    /** Podle zadání: nepovinná fotka jako reference (vlastní, ne fotka nové postavy). */
    val zadaniReference: File? = null,
    val zadaniReferenceNahled: android.graphics.Bitmap? = null,
    /** Rozměry (po otočení) a počet snímků videa; 0 = neznámé. */
    val videoSirka: Int = 0,
    val videoVyska: Int = 0,
    val videoSnimku: Int = 0,
) {
    val refsWithImage: List<AioSlot> get() = refs.filter { it.image != null }
    val canAddRef: Boolean get() = refs.size < AioScene.MAX_REFS

    /** Fotky, které se nahrávají — podle režimu. */
    val uploadImages: List<File>
        get() = when (rezim) {
            UpravaRezim.PREMALOVAT -> doAio().uploadImages
            UpravaRezim.POSTAVA -> listOfNotNull(postava)
            UpravaRezim.PREDLOHA -> emptyList()
            // Fotka je u zadání nepovinná reference (úloha rv2v).
            UpravaRezim.ZADANI -> listOfNotNull(zadaniReference)
        }

    companion object {
        /** Koho hledat, když uživatel nic nenapíše — výchozí hodnota předlohy. */
        const val KOHO_VYCHOZI = "human"
        /** H3 Character Swap: tvar zadání z návodu LoRA („Replace only the person…"). */
        const val KOHO_VYCHOZI_H3 = "person"
        /** H3 Ref2VA bere referenční video 2–15 s. */
        const val H3_MAX_S = 15f
        /**
         * Výměna postavy přes H3: oficiálně 15 s, uživatel ale 20 s úspěšně
         * vyzkoušel (28. 9. 2026: „H3 umí delší videa, dělal jsem 20 sekund“).
         */
        const val H3_POSTAVA_MAX_S = 20f
        const val SWAP_LORA = "h3_character_swap_pro4500_1000.safetensors"
        /** Podle předlohy: MiniMax H3 bere 5–15 s. */
        const val PREDLOHA_MIN_S = 5f
        const val PREDLOHA_MAX_S = 15f
    }

    /** Vyměnit postavu: nejdelší úsek, který motor zpracuje. */
    val postavaMax: Float
        get() = if (motorPostavy == PostavaMotor.H3) H3_POSTAVA_MAX_S else Float.MAX_VALUE

    /** Délka celého videa (neznámá = 5 s). */
    private val celkem: Float get() = if (videoSekund > 0f) videoSekund else 5f

    /** Začátek úseku. */
    val postavaZacatek: Float get() = postavaOd.coerceIn(0f, maxOf(0f, celkem - 2f))

    /** Konec úseku: zvolený, nebo co motor unese; vždy aspoň 2 s po začátku. */
    val postavaKonec: Float
        get() {
            val strop = minOf(celkem, postavaZacatek + postavaMax)
            val k = if (postavaDo <= 0f) strop else postavaDo.coerceIn(postavaZacatek, strop)
            return maxOf(k, minOf(celkem, postavaZacatek + 2f))
        }

    /** Sekundy, které se opravdu zpracují. */
    val postavaDelka: Float get() = postavaKonec - postavaZacatek

    /** Ořezává se video? Jen když je známá délka a úsek není celé video. */
    val postavaOrez: Boolean
        get() = videoSekund > 0f && (postavaZacatek > 0.05f || postavaKonec < videoSekund - 0.05f)

    /** Podle předlohy: nejdelší volitelný úsek — 15 s, nebo celé kratší video. */
    val predlohaMax: Float
        get() = if (videoSekund > 0f) videoSekund.coerceIn(PREDLOHA_MIN_S, PREDLOHA_MAX_S) else PREDLOHA_MAX_S

    /** Podle předlohy: sekundy, které se opravdu použijí. */
    val predlohaDelka: Float get() = predlohaSekundy.coerceIn(PREDLOHA_MIN_S, predlohaMax)

    /**
     * Vyměnit postavu motorem MiniMax H3: Reference se zdrojovým videem
     * `<Video 1>` a fotkou `<Picture 1>` + LoRA H3 Character Swap. Zadání má
     * tvar z návodu LoRA (akatz-ai/MiniMax-H3-Character-Swap-LoRA).
     */
    fun doSwapAio(): AioScene {
        val koho = kohoVymenit.trim().ifEmpty { KOHO_VYCHOZI_H3 }
        val zadani = buildString {
            append("Replace only the $koho in <Video 1> with the character in <Picture 1>. ")
            append("Keep the motion, pose and timing from <Video 1>. ")
            append("Preserve the source video's camera, background, lighting and other people.")
            popis.trim().takeIf { it.isNotEmpty() }?.let { append(" ").append(it) }
        }
        return AioScene(
            mode = AioMode.REFERENCE,
            prompt = zadani,
            seconds = (if (videoSekund > 0f) postavaDelka else 5f).coerceIn(2f, H3_POSTAVA_MAX_S),
            refs = listOf(AioSlot(key = 1, image = postava)),
            refVideo = video,
            // Delší video se na serveru zkrátí na zvolenou délku (dřív ho karta odmítla).
            refVideoOd = if (postavaOrez) postavaZacatek else 0f,
            refVideoSekund = if (postavaOrez) postavaDelka else 0f,
            kotva = false,
        )
    }

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
        UpravaRezim.POSTAVA -> when {
            s.postava == null -> t("Vyber fotku nové postavy.")
            else -> null
        }
        UpravaRezim.PREDLOHA -> when {
            s.videoSekund in 0.01f..(UpravaScene.PREDLOHA_MIN_S - 0.05f) -> t("Video je kratší než 5 s.")
            s.popis.isBlank() -> t("Napiš, co má ve videu být.")
            else -> null
        }
        UpravaRezim.ZADANI ->
            if (s.popis.isBlank()) t("Napiš, co se má ve videu změnit.") else null
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

    fun postavaFile(): File = File(dir(), "postava.png")

    fun zadaniReferenceFile(): File = File(dir(), "zadani_reference.png")

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
            postava = soubor(j.optString("postava")),
            kohoVymenit = j.optString("kohoVymenit"),
            naVysku = j.optBoolean("naVysku", true),
            videoSekund = j.optDouble("videoSekund", 0.0).toFloat(),
            motorPostavy = runCatching { PostavaMotor.valueOf(j.optString("motorPostavy")) }
                .getOrDefault(PostavaMotor.SCAIL),
            predlohaDruh = runCatching { PredlohaDruh.valueOf(j.optString("predlohaDruh")) }
                .getOrDefault(PredlohaDruh.POZA),
            predlohaRychle = j.optBoolean("predlohaRychle", true),
            zadaniRychle = j.optBoolean("zadaniRychle", true),
            zadaniReference = soubor(j.optString("zadaniReference")),
            predlohaSekundy = j.optDouble("predlohaSekundy", 5.0).toFloat(),
            postavaOd = j.optDouble("postavaOd", 0.0).toFloat(),
            postavaDo = j.optDouble("postavaDo", 0.0).toFloat()
                .coerceIn(UpravaScene.PREDLOHA_MIN_S, UpravaScene.PREDLOHA_MAX_S),
            videoSirka = j.optInt("videoSirka", 0),
            videoVyska = j.optInt("videoVyska", 0),
            videoSnimku = j.optInt("videoSnimku", 0),
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
                .put("postava", s.postava?.absolutePath ?: "")
                .put("kohoVymenit", s.kohoVymenit)
                .put("naVysku", s.naVysku)
                .put("videoSekund", s.videoSekund.toDouble())
                .put("motorPostavy", s.motorPostavy.name)
                .put("predlohaDruh", s.predlohaDruh.name)
                .put("predlohaRychle", s.predlohaRychle)
                .put("zadaniRychle", s.zadaniRychle)
                .put("zadaniReference", s.zadaniReference?.absolutePath ?: "")
                .put("predlohaSekundy", s.predlohaSekundy.toDouble())
                .put("postavaOd", s.postavaOd.toDouble())
                .put("postavaDo", s.postavaDo.toDouble())
                .put("videoSirka", s.videoSirka)
                .put("videoVyska", s.videoVyska)
                .put("videoSnimku", s.videoSnimku)
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
