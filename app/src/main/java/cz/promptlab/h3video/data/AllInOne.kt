package cz.promptlab.h3video.data

import android.graphics.Bitmap
import androidx.compose.runtime.Immutable
import java.io.File

/**
 * Karta **All in One** – jede na hotových šablonách balíku
 * [ComfyUI-ALLinONE-MinimaxH3](https://github.com/LeonQ8/ComfyUI-ALLinONE-MinimaxH3),
 * který má uživatel nainstalovaný v ComfyUI.
 *
 * Appka si šablony **nekopíruje do sebe**, stahuje je za běhu z
 * `/h3one/workflow/{jméno}`. Po aktualizaci balíku tak generuje podle nové
 * verze a nemůže se rozejít s tím, co je na počítači – a licence balíku
 * (GPL-3.0) se tím taky neřeší přibalováním cizího kódu do APK.
 *
 * Co karta přidává proti ostatním: **klíčové snímky** (obrázek připnutý na
 * konkrétní snímek videa), **prodloužení hotového videa** a **zvětšení**
 * (SeedVR2 nebo RTX Video Super Resolution). Řetěz klipů se sem schválně
 * nedává – od toho je karta Časová osa.
 */
enum class AioMode(
    val kod: String,
    private val nazevCs: String,
    private val popisCs: String,
    val sablona: String,
) {
    TEXT("t2v", "Z textu", "Jen z popisu, nic se nepřikládá", "t2v.json"),
    IMAGE("i2v", "Z obrázku", "Rozhýbe fotku, volitelně i k poslednímu snímku", "i2v.json"),
    REFERENCE("r2v", "Reference", "Drží podobu podle fotek a videí", "r2v.json"),
    KEYFRAMES("keyframes", "Klíčové snímky", "Obrázky připnuté na konkrétní snímky", "keyframes.json"),
    EXTEND("extend", "Prodloužit", "Naváže na konec hotového videa", "video_extend.json"),
    UPSCALE("upscale", "Zvětšit", "Zvětší hotové video, negeneruje nic nového", "upscale.json"),

    /**
     * List postavy (balík 0.17): z referencí jedné postavy vznikne otočka kolem
     * ní a z jejích snímků slepený vícepohledový list. Kamera, délka (124
     * snímků) i rozlišení jsou v šabloně vyladěné na vteřinu přesně – proto se
     * do nich na rozdíl od ostatních režimů nesahá.
     */
    CHARSHEET("charsheet", "List postavy", "Z fotek složí otočný list postavy", "charsheet.json");

    /** Název a popis v jazyce rozhraní (překlad až při čtení). */
    val nazev: String get() = t(nazevCs)
    val popis: String get() = t(popisCs)

    /**
     * Potřebuje tenhle režim popis scény? Zvětšování ne – jen zvětšuje.
     * U listu postavy je popis volitelný (říká, co z referencí držet a co ne).
     */
    val needsPrompt: Boolean get() = this != UPSCALE && this != CHARSHEET

    /** Jede na referenčních (ref2va) vahách? */
    val usesRefWeights: Boolean get() = this == REFERENCE || this == CHARSHEET
}

/** Zvětšovač v režimu [AioMode.UPSCALE]. */
enum class Upscaler(
    val kod: String,
    private val nazevCs: String,
    private val popisCs: String,
    val sablona: String,
) {
    SEEDVR2("seedvr2", "SeedVR2", t("Kvalitnější, ale pomalé – dopočítává detaily"), "upscale.json"),
    RTX("rtx", "RTX Video SR", "Rychlé, jede na grafice NVIDIA", "upscale_rtx.json");

    val nazev: String get() = t(nazevCs)
    val popis: String get() = t(popisCs)
}

/** Jeden obrázkový slot karty. */
@Immutable
data class AioSlot(
    val key: Int,
    val image: File? = null,
    val thumb: Bitmap? = null,
    /** Klíčové snímky: na kterém snímku videa má obrázek být (1 = první). */
    val position: Int = 1,
)

@Immutable
data class AioScene(
    val mode: AioMode = AioMode.TEXT,
    val prompt: String = "",
    val seconds: Float = 5f,
    /** Z obrázku: první a poslední snímek. */
    val first: AioSlot = AioSlot(key = 1),
    val last: AioSlot = AioSlot(key = 2),
    val useLastFrame: Boolean = false,
    /** Reference: až [MAX_REFS] obrázků. */
    val refs: List<AioSlot> = listOf(AioSlot(key = 1)),
    /** Reference: referenční video (pohyb), volitelně i s jeho zvukem. */
    val refVideo: File? = null,
    val refVideoAudio: Boolean = false,
    /** Klíčové snímky. */
    val keys: List<AioSlot> = listOf(AioSlot(key = 1, position = 1)),
    /** Prodloužení a zvětšení: zdrojové video z telefonu. */
    val sourceVideo: File? = null,
    val upscaler: Upscaler = Upscaler.SEEDVR2,
    /** SeedVR2: kratší hrana výsledku. */
    val upscaleResolution: Int = 1080,
    /** RTX: kolikrát zvětšit. */
    val upscaleMultiplier: Int = 2,
    /** List postavy: 6 panelů (plná otočka), nebo 4 (rychlejší, méně VRAM). */
    val sheetPanels: Int = 6,
    /** List postavy: fotorealistický styl místo stylu podle první reference. */
    val sheetPhotoreal: Boolean = false,
) {
    /** Šablona, kterou je potřeba stáhnout ze serveru. */
    val sablona: String
        get() = when {
            mode == AioMode.UPSCALE -> upscaler.sablona
            mode == AioMode.CHARSHEET && sheetPanels == 4 -> "charsheet4.json"
            else -> mode.sablona
        }

    val refsWithImage: List<AioSlot> get() = refs.filter { it.image != null }
    val keysWithImage: List<AioSlot> get() = keys.filter { it.image != null }

    val canAddRef: Boolean get() = refs.size < MAX_REFS
    val canAddKey: Boolean get() = keys.size < MAX_KEYS

    /** Počet snímků po zaokrouhlení na mřížku modelu (17k+5). */
    val frames: Int get() = framesForSeconds(seconds)

    /**
     * Obrázky v pořadí, v jakém se nahrávají do ComfyUI. Stavitel grafu se pak
     * odkazuje na stejné pořadí – proto je to na jednom místě, ne dvakrát.
     */
    val uploadImages: List<File>
        get() = when (mode) {
            AioMode.TEXT, AioMode.EXTEND, AioMode.UPSCALE -> emptyList()
            AioMode.IMAGE -> listOfNotNull(first.image, last.image.takeIf { useLastFrame })
            AioMode.REFERENCE, AioMode.CHARSHEET -> refsWithImage.mapNotNull { it.image }
            AioMode.KEYFRAMES -> keysWithImage.mapNotNull { it.image }
        }

    /** Video, které se nahrává na server (reference / prodloužení / zvětšení). */
    val uploadVideo: File?
        get() = when (mode) {
            AioMode.REFERENCE -> refVideo
            AioMode.EXTEND, AioMode.UPSCALE -> sourceVideo
            else -> null
        }

    companion object {
        const val MAX_REFS = 6
        const val MAX_KEYS = 6

        /** Prodloužení: uzel zvládne cíl do 736 snímků včetně 39 snímků kontextu. */
        const val MAX_EXTEND_SECONDS = 28f
    }
}

/** Co kartě chybí, než se dá spustit. Hláška pro uživatele, nebo null. */
fun aioProblem(s: AioScene): String? {
    if (s.mode.needsPrompt && s.prompt.isBlank()) {
        return if (s.mode == AioMode.EXTEND) t("Napiš, co se má dít v prodloužení.")
        else t("Napiš, co se má ve videu dít.")
    }
    return when (s.mode) {
        AioMode.TEXT -> null
        AioMode.IMAGE -> when {
            s.first.image == null && !(s.useLastFrame && s.last.image != null) ->
                t("Vyber snímek, ze kterého se má vyjít.")
            s.useLastFrame && s.last.image == null ->
                t("Vyber poslední snímek, nebo ho vypni.")
            else -> null
        }
        AioMode.REFERENCE ->
            if (s.refsWithImage.isEmpty() && s.refVideo == null)
                t("Přidej aspoň jednu referenci – obrázek nebo video.") else null
        AioMode.KEYFRAMES -> when {
            s.keysWithImage.isEmpty() -> t("Přidej aspoň jeden klíčový snímek.")
            s.keysWithImage.any { it.position > s.frames } ->
                t("Klíčový snímek je za koncem videa – zkrať pozici, nebo prodluž video.")
            else -> null
        }
        AioMode.EXTEND ->
            if (s.sourceVideo == null) t("Vyber video, které se má prodloužit.") else null
        AioMode.UPSCALE ->
            if (s.sourceVideo == null) t("Vyber video, které se má zvětšit.") else null
        AioMode.CHARSHEET ->
            if (s.refsWithImage.isEmpty())
                t("Přidej aspoň jednu fotku postavy, ze které má list vzniknout.") else null
    }
}

/**
 * Upozornění ke kartě – nebrání spuštění, jen se hodí vědět.
 */
fun aioHints(s: AioScene, p: GenParams): List<String> {
    val out = mutableListOf<String>()
    if (s.mode == AioMode.UPSCALE) {
        out += "Zvětšování nic negeneruje – model MiniMax se vůbec nespustí, " +
            "takže na profilu, krocích ani rozlišení tady nezáleží."
        if (s.upscaler == Upscaler.SEEDVR2) {
            out += "SeedVR2 potřebuje své modely ve složce models/SEEDVR2. Když tam nejsou, " +
                "ComfyUI si je při prvním použití stáhne a chvíli to trvá."
        }
    }
    if (s.mode == AioMode.EXTEND) {
        val (_, cil, nove) = planExtend(s.seconds)
        out += "Vygeneruje se $cil snímků, z toho ${cil - nove} navazuje na konec zdrojového " +
            "videa a nových je $nove (%.1f s).".format(nove / 24f)
        if (s.seconds > AioScene.MAX_EXTEND_SECONDS) {
            out += "Delší prodloužení než ${AioScene.MAX_EXTEND_SECONDS.toInt()} s uzel neumí – " +
                "zbytek se ustřihne."
        }
    }
    // Jen režim Reference: list postavy má vzorkování i velikost referencí
    // pevně v šabloně, takže se ho tahle upozornění netýkají.
    if (s.mode == AioMode.REFERENCE && p.turboLoraOn) {
        out += "Turbo LoRA je trénovaná na text a snímky, ne na reference. Tady se vyplatí " +
            "profil Kvalita – podoba postav bývá věrnější."
    }
    if (!s.mode.usesRefWeights && p.profile.bezReferenci) {
        out += "Profil Fast je čtyřkroková destilace FastH3. Je nejrychlejší, ale podle " +
            "autorů může u složitého pohybu, jemných detailů a části zvuku zůstat pod " +
            "základním modelem."
    }
    if (s.mode == AioMode.REFERENCE && p.refImageSize != "max") {
        out += "Reference se posílají zmenšené na velikost výstupu („Vyvážené\"). " +
            "Pro věrnou podobu lidí přepni v pokročilém nastavení na „Maximální detail\"."
    }
    if (s.mode == AioMode.REFERENCE && s.refsWithImage.isNotEmpty() && s.refVideo != null) {
        out += "První referenční obrázek se připne jako snímek 0, aby obličej z videa " +
            "nepřebil fotku."
    }
    if (s.mode == AioMode.KEYFRAMES) {
        val mimo = s.keysWithImage.filter { it.position !in 1..s.frames }
        if (mimo.isEmpty() && s.keysWithImage.size == 1) {
            out += "S jediným klíčovým snímkem se karta chová jako „Z obrázku“ – " +
                "smysl to dává od dvou nahoru."
        }
    }
    if (s.mode == AioMode.CHARSHEET) {
        out += "První fotka určuje styl, další doplňují podobu (obličej, vlasy, oblečení). " +
            "Délka, kamera i vzorkování jsou dané šablonou – profil ani pokročilé volby " +
            "sem nesahají. Hotový list se uloží do Obrázky/H3 Video, otočka do galerie appky."
    }
    return out
}

/**
 * Prodloužení videa: kolik snímků kontextu, jaký je cíl a kolik je nového.
 *
 * Výstup je [zdrojové video] + [nový obsah], takže cíl musí být kontext + to,
 * co se má přidat. Obojí leží na mřížce 17k+5 a kontext navíc na hranici
 * sdílené 24 fps obrazem a 40 Hz zvukem (39/90/141/…), jinak si ho uzel sám
 * zmenší a prodloužení potichu naroste. Stejný výpočet má balík v `planExtend`.
 */
fun planExtend(seconds: Number, fps: Int = 24): Triple<Int, Int, Int> {
    val wantNew = maxOf(1, Math.round(seconds.toDouble() * fps).toInt())
    val maxTarget = 736
    val maxBlocks = maxOf(1, (maxTarget - 39) / 17)
    val blocks = maxOf(1, minOf(Math.round(wantNew / 17.0).toInt(), maxBlocks))
    val newFrames = blocks * 17
    return Triple(39, 39 + newFrames, newFrames)
}
