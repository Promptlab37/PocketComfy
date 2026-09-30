package cz.promptlab.h3video.comfy

import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/**
 * Podkresová hudba k hotovému filmu ze storyboardu (5.13, přání uživatele:
 * „přes YuE2, instrumentální, podle scénáře“).
 *
 * Samostatná úloha PO filmu (kritik 29. 9. 2026): když hudba selže, hotový
 * film zůstane; H3 (~50 GB RAM) a YuE2 nejsou v paměti naráz.
 *
 * Graf:
 *  - `LoadVideo` čte hotový film rovnou z výstupů (`"jméno [output]"`),
 *    `GetVideoComponents` ho rozdělí na snímky, dialogy a fps;
 *  - YuE2 podle oficiální předlohy (`workflow_yue2_music.json`: ABC → Music →
 *    Empty latent → KSampler 32 kroků dpm_2/sgm_uniform → VAE) + **instrumentální
 *    LoRA** Mothersuperior `ar_lora_inst_v3abc_comfyui` v CLIP slotu, síla 1,0,
 *    `mode = full` (bez LoRA základní YuE2 po 20–30 s začne zpívat — diskuse
 *    u Comfy-Org/YuE2). Text písně = časované značky částí podle délky filmu
 *    (formát, na kterém je LoRA trénovaná), plánované PŘES konec filmu;
 *  - hudba se zarovná na délku filmu (`AudioMerge` s umlčenými dialogy
 *    ořízne/doplní), posledních 2 s se zeslabí po čtvrtsekundách (jádro
 *    nemá uzel na fade), ztiší se a přičte k dialogům;
 *  - `CreateVideo` + `SaveVideo` „PocketSbFilmHudba“.
 */
object SbHudbaBuilder {

    const val CKPT = "yue2_3b_int8_convrot.safetensors"
    /** ComfyUI hlásí podsložky se zpětným lomítkem. */
    const val LORA = "yue2\\ar_lora_inst_v3abc_comfyui.safetensors"
    const val PREFIX = "PocketSbFilmHudba"

    /** Hlasitost hudby pod dialogy (dB). −14 je odhad, ověřuje se během. */
    const val HLASITOST_VYCHOZI = -14
    const val HLASITOST_MIN = -24
    const val HLASITOST_MAX = -6

    /** Zeslabení na konci: 8 čtvrtsekund. */
    const val FADE_S = 2.0
    val FADE_DB = listOf(-2, -4, -7, -10, -14, -19, -26, -40)

    const val N_VIDEO = "1"
    const val N_CASTI = "2"
    const val N_CKPT = "10"
    const val N_LORA = "11"
    const val N_ABC = "24"
    const val N_MUSIC = "25"
    const val N_LATENT = "5"
    const val N_ZERO = "18"
    const val N_SAMPLER = "8"
    const val N_DECODE = "9"
    const val N_TICHO = "30"
    const val N_ZAROVNANI = "31"
    const val N_TELO = "32"
    const val N_HLASITOST = "60"
    const val N_MIX = "61"
    const val N_SLOZENI = "62"
    const val N_ULOZ = "63"
    const val STEPS = 32

    /** Výchozí styl, když čtení žádný nevrátilo. */
    const val STYL_VYCHOZI = "cinematic background score, soft piano, warm strings, gentle, 90 BPM"

    /** `m:ss` pro značky částí. */
    fun cas(s: Double): String {
        val c = Math.round(s).toInt().coerceAtLeast(0)
        return "%d:%02d".format(Locale.ROOT, c / 60, c % 60)
    }

    /**
     * Časované značky částí přes celý film + 10 s rezervy (LoRA drží spíš
     * poměry než konec; ořez a zeslabení zbytek dorovnají). Jedna značka na řádek.
     */
    fun castiSkladby(delkaS: Double): String {
        val t = delkaS + 10.0
        val hranice = listOf(0.0, 0.15, 0.55, 0.85, 1.0).map { it * t }
        val jmena = listOf("intro", "verse", "chorus", "outro")
        return jmena.indices.joinToString("\n") { i -> "[${jmena[i]} ${cas(hranice[i])}-${cas(hranice[i + 1])}]" }
    }

    fun build(
        /** Hotový film ve výstupech ComfyUI, např. „PocketSbFilm_00013_.mp4 [output]“. */
        video: String,
        styl: String,
        delkaS: Double,
        hlasitostDb: Int,
        seed: Long,
    ): JSONObject {
        val wf = JSONObject()
        val s = styl.trim().ifBlank { STYL_VYCHOZI }
        val l = delkaS.coerceAtLeast(FADE_S + 1.0)
        wf.put(N_VIDEO, uzel("LoadVideo", "Film", JSONObject().put("file", video)))
        wf.put(N_CASTI, uzel("GetVideoComponents", "Obraz a dialogy", JSONObject().put("video", odkaz(N_VIDEO))))

        wf.put(N_CKPT, uzel("CheckpointLoaderSimple", "YuE2", JSONObject().put("ckpt_name", CKPT)))
        wf.put(N_LORA, uzel("LoraLoader", "Instrumentální LoRA", JSONObject()
            .put("model", odkaz(N_CKPT, 0)).put("clip", odkaz(N_CKPT, 1))
            .put("lora_name", LORA).put("strength_model", 1.0).put("strength_clip", 1.0)))
        val casti = castiSkladby(l)
        wf.put(N_ABC, uzel("YuE2GenerateABC", "Noty", JSONObject()
            .put("clip", odkaz(N_LORA, 1)).put("style", s).put("lyrics", casti)
            .put("seed", seed and 0xFFFFFFFFL).put("mode", "full").put("max_abc_tokens", 8192)
            .put("temperature", 0.7).put("top_p", 0.9).put("top_k", 30)
            .put("repetition_penalty", 1.005).put("penalty_window", 100)))
        wf.put(N_MUSIC, uzel("YuE2GenerateMusic", "Hudba", JSONObject()
            .put("clip", odkaz(N_LORA, 1)).put("style", s).put("lyrics", casti).put("abc", odkaz(N_ABC, 0))
            .put("seed", seed and 0xFFFFFFFFL).put("mode", "full").put("max_duration", l + 20.0)
            .put("temperature", 1.0).put("top_p", 0.95).put("top_k", 100).put("repetition_penalty", 1.2)))
        wf.put(N_LATENT, uzel("EmptyYuE2LatentAudio", "Délka", JSONObject()
            .put("seconds", odkaz(N_MUSIC, 1)).put("batch_size", 1)))
        wf.put(N_ZERO, uzel("ConditioningZeroOut", "Negativ", JSONObject().put("conditioning", odkaz(N_MUSIC, 0))))
        wf.put(N_SAMPLER, uzel("KSampler", "Vzorkování", JSONObject()
            .put("model", odkaz(N_CKPT, 0)).put("positive", odkaz(N_MUSIC, 0)).put("negative", odkaz(N_ZERO, 0))
            .put("latent_image", odkaz(N_LATENT, 0)).put("seed", seed and 0xFFFFFFFFL).put("steps", STEPS)
            .put("cfg", 1.0).put("sampler_name", "dpm_2").put("scheduler", "sgm_uniform").put("denoise", 1.0)))
        wf.put(N_DECODE, uzel("VAEDecodeAudio", "Zvuk", JSONObject()
            .put("samples", odkaz(N_SAMPLER, 0)).put("vae", odkaz(N_CKPT, 2))))

        // Hudba přesně na délku filmu: umlčené dialogy jako šablona délky.
        wf.put(N_TICHO, uzel("AudioAdjustVolume", "Ticho délky filmu", JSONObject()
            .put("audio", odkaz(N_CASTI, 1)).put("volume", -100)))
        wf.put(N_ZAROVNANI, uzel("AudioMerge", "Na délku filmu", JSONObject()
            .put("audio1", odkaz(N_TICHO, 0)).put("audio2", odkaz(N_DECODE, 0)).put("merge_method", "add")))

        // Zeslabení posledních 2 s po čtvrtsekundách (počítáno od konce).
        wf.put(N_TELO, uzel("TrimAudioDuration", "Hudba bez konce", JSONObject()
            .put("audio", odkaz(N_ZAROVNANI, 0)).put("start_index", 0.0).put("duration", l - FADE_S)))
        var spojeno = N_TELO
        FADE_DB.forEachIndexed { i, db ->
            val kus = (40 + i).toString()
            val tisi = (50 + i).toString()
            val spoj = (70 + i).toString()
            wf.put(kus, uzel("TrimAudioDuration", "Konec ${i + 1}", JSONObject()
                .put("audio", odkaz(N_ZAROVNANI, 0)).put("start_index", -FADE_S + 0.25 * i).put("duration", 0.25)))
            wf.put(tisi, uzel("AudioAdjustVolume", "Zeslabení ${i + 1}", JSONObject()
                .put("audio", odkaz(kus, 0)).put("volume", db)))
            wf.put(spoj, uzel("AudioConcat", "Spojení ${i + 1}", JSONObject()
                .put("audio1", odkaz(spojeno, 0)).put("audio2", odkaz(tisi, 0)).put("direction", "after")))
            spojeno = spoj
        }

        wf.put(N_HLASITOST, uzel("AudioAdjustVolume", "Hudba pod dialogy", JSONObject()
            .put("audio", odkaz(spojeno, 0)).put("volume", hlasitostDb.coerceIn(HLASITOST_MIN, HLASITOST_MAX))))
        wf.put(N_MIX, uzel("AudioMerge", "Dialogy + hudba", JSONObject()
            .put("audio1", odkaz(N_CASTI, 1)).put("audio2", odkaz(N_HLASITOST, 0)).put("merge_method", "add")))
        wf.put(N_SLOZENI, uzel("CreateVideo", "Video s hudbou", JSONObject()
            .put("images", odkaz(N_CASTI, 0)).put("fps", odkaz(N_CASTI, 2)).put("audio", odkaz(N_MIX, 0))))
        wf.put(N_ULOZ, uzel("SaveVideo", "Uložit", JSONObject()
            .put("video", odkaz(N_SLOZENI, 0)).put("filename_prefix", PREFIX)
            .put("format", "auto").put("codec", "auto")))
        return wf
    }

    /** Hotový soubor z výstupů → hodnota pro `LoadVideo`. */
    fun zVystupu(filename: String, subfolder: String): String =
        (if (subfolder.isBlank()) filename else "$subfolder/$filename") + " [output]"

    private fun uzel(cls: String, titulek: String, vstupy: JSONObject) = JSONObject()
        .put("class_type", cls).put("inputs", vstupy).put("_meta", JSONObject().put("title", titulek))

    private fun odkaz(uzel: String, slot: Int = 0) = JSONArray().put(uzel).put(slot)
}
