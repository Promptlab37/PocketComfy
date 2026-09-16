package cz.promptlab.h3video.data

import android.content.Context
import androidx.compose.runtime.Immutable
import org.json.JSONObject

/**
 * Model, kterým se skládá hudba. Texty jsou v `*Cs` polích a překládají se až
 * při čtení — hodnoty enumu vzniknou jednou při zavedení třídy, takže by
 * přepnutí jazyka na uložený překlad nedosáhlo (viz Mode, InpaintModel).
 */
enum class MusicMotor(
    private val titleCs: String,
    private val detailCs: String,
) {
    /**
     * Uživatelovo ACE-Step 1.5 Turbo workflow. Výchozí: 8 kroků, celá píseň
     * za pár desítek sekund, a jako jediný z těch dvou umí zpívat česky.
     */
    ACE(
        titleCs = "ACE-Step 1.5 Turbo",
        detailCs = "Rychlý (8 kroků) a zpívá i česky — délku, tempo i tóninu určíš ty",
    ),

    /**
     * YuE2 3B od m-a-p, od ComfyUI 0.35 nativně. Nejdřív si napíše noty
     * (melodii, případně i akordy) a teprve podle nich zpívá — proto zní
     * muzikálněji, ale běh je řádově delší a zpěv umí jen anglicky a čínsky.
     * Délku si model řídí sám, appka zadává jen strop.
     */
    YUE2(
        titleCs = "YuE2 3B",
        detailCs = "Muzikálnější — nejdřív si napíše noty. Zpívá anglicky a čínsky, běh je delší",
    );

    val title: String get() = t(titleCs)
    val detail: String get() = t(detailCs)
}

/**
 * Co si YuE2 naplánuje, než začne zpívat. Odpovídá vstupu `mode` uzlů
 * YuE2GenerateABC / YuE2GenerateMusic: prázdné noty model přepne na „off",
 * takže „bez plánu" a volba plánu jsou ve skutečnosti jedna volba, ne dvě.
 */
enum class MusicPlan(
    /** Hodnota do `mode` obou uzlů; u [NONE] se noty vůbec nepočítají. */
    val mode: String,
    private val titleCs: String,
    private val detailCs: String,
) {
    NONE(
        mode = "full",
        titleCs = "Bez plánu",
        detailCs = "Rovnou zpívá — nejrychlejší, ale melodie bývá rozvolněná",
    ),
    MELODY(
        mode = "melody",
        titleCs = "Melodie",
        detailCs = "Nejdřív si napíše melodii, pak podle ní zpívá",
    ),
    FULL(
        mode = "full",
        titleCs = "Melodie a akordy",
        detailCs = "Napíše si melodii i doprovod — nejmuzikálnější, ale nejdelší běh",
    );

    val title: String get() = t(titleCs)
    val detail: String get() = t(detailCs)

    /** Počítají se noty zvlášť? U [NONE] se uzel z grafu vypouští. */
    val piseNoty: Boolean get() = this != NONE
}

/**
 * Karta **Hudba** — celá skladba z textu, výsledkem je MP3.
 *
 * Dva motory, každý s jinou logikou zadání:
 *  - **ACE-Step 1.5 Turbo** (uživatelovo workflow, převzaté 1:1): dosazuje se
 *    styl, text písně, PŘESNÁ délka, jazyk, BPM, tónina a seed.
 *  - **YuE2 3B** (oficiální předloha ComfyUI): dosazuje se styl, text, strop
 *    délky, plán not a seed. BPM, tóninu ani jazyk zpěvu tenhle model na
 *    vstupu nemá — určuje si je sám ze stylu a textu, takže je karta u něj
 *    ani neukazuje (nabízet knoflík, který graf zahodí, je horší než ho skrýt).
 *
 * U obou platí, že kroky, cfg, sampler i doladění zůstávají z předlohy.
 */
@Immutable
data class MusicScene(
    /** Čím se skládá — určuje, která šablona a která pole karty platí. */
    val motor: MusicMotor = MusicMotor.ACE,
    /** Styl skladby — žánr, nástroje, nálada, zpěvák… Společný pro oba motory. */
    val styl: String = "",
    /** Text písně; prázdný = instrumentálka. Společný pro oba motory. */
    val text: String = "",
    /** ACE-Step: přesná délka skladby v sekundách. */
    val seconds: Int = 120,
    /** ACE-Step: jazyk zpěvu. */
    val language: String = "cs",
    /** ACE-Step: tempo. */
    val bpm: Int = 120,
    /** ACE-Step: tónina. */
    val keyscale: String = "C major",
    /**
     * YuE2: **strop** délky (`max_duration`). Model skončí, kde má píseň
     * konec — u krátkého textu klidně dřív. Proto vlastní pole a ne [seconds]:
     * u ACE-Step je to přesná délka, tady jen mez.
     */
    val maxSeconds: Int = 240,
    /** YuE2: co si model naplánuje, než začne zpívat. */
    val plan: MusicPlan = MusicPlan.FULL,
) {
    /** Délka, kterou má smysl ukázat u karty a v historii. */
    val delka: Int get() = if (motor == MusicMotor.YUE2) maxSeconds else seconds

    companion object {
        const val MIN_SECONDS = 30
        const val MAX_SECONDS = 240

        /** YuE2 zvládne delší skladbu; strop je z předlohy (`max_duration`). */
        const val YUE2_MIN_SECONDS = 30
        const val YUE2_MAX_SECONDS = 360

        /** Jazyky zpěvu, které zná ACE-Step (výběr těch nejbližších). */
        val LANGUAGES = listOf("cs", "sk", "en", "de", "es", "fr", "it", "pl", "ru")

        /** Tóniny přesně podle nabídky uzlu TextEncodeAceStepAudio1.5. */
        val KEYSCALES = listOf(
            "C major", "C# major", "Db major", "D major", "D# major", "Eb major",
            "E major", "F major", "F# major", "Gb major", "G major", "G# major",
            "Ab major", "A major", "A# major", "Bb major", "B major",
            "C minor", "C# minor", "Db minor", "D minor", "D# minor", "Eb minor",
            "E minor", "F minor", "F# minor", "Gb minor", "G minor", "G# minor",
            "Ab minor", "A minor", "A# minor", "Bb minor", "B minor",
        )

        /** Písmena, která v textu prozradí češtinu nebo slovenštinu. */
        private const val DIAKRITIKA = "áčďéěíňóřšťúůýžäôĺľŕ"
    }

    /** Vypadá text písně česky? YuE2 takový text zazpívá zkomoleně. */
    val textVypadaCesky: Boolean
        get() = text.any { it.lowercaseChar() in DIAKRITIKA }
}

/** Co kartě chybí, než se dá spustit. */
fun musicProblem(s: MusicScene): String? =
    if (s.styl.isBlank()) t("Popiš styl skladby — žánr, nástroje, náladu.") else null

/** Upozornění, která nebrání spuštění. */
fun musicHints(s: MusicScene): List<String> {
    val out = mutableListOf<String>()
    if (s.text.isBlank()) {
        out += t("Bez textu písně vyjde instrumentálka. Text piš po slokách, ") +
            (if (s.motor == MusicMotor.YUE2) t("anglicky.") else t("klidně česky."))
    }
    if (s.motor == MusicMotor.YUE2) {
        if (s.textVypadaCesky) {
            out += t("YuE2 zpívá anglicky a čínsky — český text zazpívá zkomoleně. ") +
                t("Na češtinu přepni na ACE-Step.")
        }
        if (s.plan.piseNoty) {
            out += t("Než se rozezní první tón, model si napíše noty. ") +
                t("Chvíli se nic neděje, to je v pořádku.")
        }
        out += t("Délka je jen strop — model skončí tam, kde má píseň konec.")
    }
    return out
}

/** Uložené zadání karty Hudba — přežije zavření aplikace. */
class MusicStore(ctx: Context) {

    private val sp = ctx.getSharedPreferences("h3video", Context.MODE_PRIVATE)

    fun load(): MusicScene = runCatching {
        val j = JSONObject(sp.getString(KEY, "{}")!!)
        MusicScene(
            motor = runCatching { MusicMotor.valueOf(j.optString("motor")) }
                .getOrDefault(MusicMotor.ACE),
            styl = j.optString("styl"),
            text = j.optString("text"),
            seconds = j.optInt("seconds", 120)
                .coerceIn(MusicScene.MIN_SECONDS, MusicScene.MAX_SECONDS),
            language = j.optString("language").ifBlank { "cs" },
            bpm = j.optInt("bpm", 120).coerceIn(10, 300),
            keyscale = j.optString("keyscale").ifBlank { "C major" },
            maxSeconds = j.optInt("maxSeconds", 240)
                .coerceIn(MusicScene.YUE2_MIN_SECONDS, MusicScene.YUE2_MAX_SECONDS),
            plan = runCatching { MusicPlan.valueOf(j.optString("plan")) }
                .getOrDefault(MusicPlan.FULL),
        )
    }.getOrDefault(MusicScene())

    fun save(s: MusicScene) {
        sp.edit().putString(
            KEY,
            JSONObject()
                .put("motor", s.motor.name)
                .put("styl", s.styl)
                .put("text", s.text)
                .put("seconds", s.seconds)
                .put("language", s.language)
                .put("bpm", s.bpm)
                .put("keyscale", s.keyscale)
                .put("maxSeconds", s.maxSeconds)
                .put("plan", s.plan.name)
                .toString()
        ).apply()
    }

    private companion object { const val KEY = "musicScene" }
}
