package cz.promptlab.h3video.comfy

import android.content.Context
import cz.promptlab.h3video.R
import cz.promptlab.h3video.data.Aspect
import cz.promptlab.h3video.data.LoraEntry
import org.json.JSONArray
import org.json.JSONObject

/**
 * Stavitel grafu pro kartu **3 kroky** — rychlé video z textu.
 *
 * Běží na MiniMax H3 (`fl2va`) s destilační LoRA **TaoMate 3-step** a dělí
 * práci do dvou průchodů:
 *
 *  1. **tři kroky na 0,2 MPx** (608×352 u 16:9) — hrubý obraz i zvuk
 *  2. **latentní zvětšení** na 0,5 MPx učeným modelem, mezitím se latent
 *     rozdělí na obrazovou a zvukovou část a po zvětšení zase složí
 *  3. **dva kroky zjemnění** na vyšším rozlišení
 *
 * Proti běžnému běhu na dvacet kroků je to řádově rychlejší, protože drtivá
 * většina práce se odvede v malém rozlišení. Kvalita se dotahuje až v tom
 * druhém, krátkém průchodu.
 *
 * Předloha `res/raw/workflow_h3_3step.json` drží celé zapojení; dosazuje se
 * jen zadání, délka, poměr stran a seed. Čísla sigm ani posun se nemění —
 * jsou odladěné a jiné hodnoty dávají měkký, mléčný obraz.
 */
object ThreeStepBuilder {

    /** Text zadání. */
    const val N_PROMPT = "14"

    /** Délka ve vteřinách; uzel si z ní spočítá snímky na mřížku 17k+5. */
    const val N_SEKUNDY = "20"

    /** Rozlišení prvního průchodu — megapixely a poměr stran. */
    const val N_ROZLISENI = "22"

    /** Seed. */
    const val N_SEED = "37"

    /** Kam se ukládá výsledek. */
    const val N_SAVE = "4"

    /**
     * Volba attention. Tahle předloha nemá `PathchSageAttentionKJ` jako
     * ostatní video karty, ale nativní `ModelAttentionBackend` — a ten
     * **přebíjí i sage attention zapnuté na příkazové řádce serveru**
     * (`--use-sage-attention`). Proto se na něj věší vypínač z Nastavení:
     * zapnuto = kvantované Comfy Kitchen attention z předlohy, vypnuto =
     * čistá PyTorch pozornost, kterou si sage nesáhne.
     */
    const val N_ATTENTION = "40"

    /** Hodnoty uzlu `ModelAttentionBackend` — víc jich v nabídce není. */
    const val ATTENTION_RYCHLA = "comfy kitchen attention"
    const val ATTENTION_PYTORCH = "pytorch attention"

    /**
     * LoRA z předlohy (TaoMate 3step). Na ní celý tenhle postup stojí — bez ní
     * tři kroky nestačí a video je rozpité —, takže se nevyměňuje ani nevypíná.
     * Uživatelovy LoRA se řetězí **za ni**.
     */
    const val N_LORA_PREDLOHA = "41"

    /** Sigma shift; jeho vstup `model` se přepojuje na konec řetězu LoRA. */
    const val N_SHIFT = "42"

    /** Čísla, pod kterými do grafu přibývají uživatelovy LoRA. */
    const val LORA_ID_OD = 9001

    /** Megapixely prvního průchodu. Vyšší hodnota = pomalejší, ale ostřejší. */
    const val MPX_PRVNI_PRUCHOD = 0.2

    // ---- hodnoty předlohy (výchozí nastavení karty) ----
    /** Zvětšení hrany mezi průchody (uzly 302/303: `round(a*1.5811/32)*32`). */
    const val ZVETSENI = 1.5811f
    const val KROKY = 3
    const val SAMPLER = "euler"
    const val SCHEDULER = "simple"
    const val SHIFT_OBRAZ = 12f
    const val SHIFT_ZVUK = 3f

    /** Kroky a vzorkování prvního průchodu; `SplitSigmas` musí sedět na kroky. */
    const val N_PLANOVAC = "8"
    const val N_ROZDELENI = "3"
    const val N_SAMPLER = "29"
    const val N_UNET = "34"
    val N_ZVETSENI = listOf("302", "303")
    val N_PRUVODCI = listOf("33", "35")
    const val N_NAHLED = "3050"
    const val TAEH3 = "taeh3.safetensors"

    /** Nejkratší a nejdelší rozumná délka. Model drží hlas do zhruba patnácti vteřin. */
    const val MIN_SEKUND = 2.0
    const val MAX_SEKUND = 15.0

    /**
     * Uzel `ResolutionSelector` čeká přesně ty popisky, které má appka
     * v [Aspect.comfyValue] — jiné zapsat nejde, uzel je má jako výčet.
     */
    private fun aspectLabel(a: Aspect): String = a.comfyValue

    fun template(ctx: Context): String =
        ctx.resources.openRawResource(R.raw.workflow_h3_3step)
            .bufferedReader().use { it.readText() }

    /** Nastavení karty; výchozí = přesně předloha. */
    data class Nastaveni(
        val mpx: Float = MPX_PRVNI_PRUCHOD.toFloat(),
        val zvetseni: Float = ZVETSENI,
        val kroky: Int = KROKY,
        val sampler: String = SAMPLER,
        val scheduler: String = SCHEDULER,
        val shiftObraz: Float = SHIFT_OBRAZ,
        val shiftZvuk: Float = SHIFT_ZVUK,
        val unet: String = "",
        val vernost: String = "max",
        val nahled: Boolean = false,
    ) {
        companion object {
            fun z(p: cz.promptlab.h3video.data.GenParams) = Nastaveni(
                p.tkMpx, p.tkZvetseni, p.tkKroky, p.tkSampler, p.tkScheduler,
                p.tkShiftObraz, p.tkShiftZvuk, p.tkUnet, p.tkVernost, p.livePreview,
            )
        }
    }

    /** Rozměry obou průchodů tak, jak je spočítají uzly v grafu. */
    fun rozmery(aspect: Aspect, n: Nastaveni): Pair<Pair<Int, Int>, Pair<Int, Int>> {
        val (w, h) = cz.promptlab.h3video.data.Resolution.calc(aspect, n.mpx)
        fun zv(a: Int) = Math.round(a * n.zvetseni / 32.0).toInt() * 32
        return (w to h) to (zv(w) to zv(h))
    }

    fun build(
        ctx: Context, prompt: String, sekundy: Double, aspect: Aspect, seed: Long,
        rychlaPozornost: Boolean = true,
        lory: List<LoraEntry> = emptyList(),
        reference: List<String> = emptyList(),
        nastaveni: Nastaveni = Nastaveni(),
    ): JSONObject =
        build(template(ctx), prompt, sekundy, aspect, seed, rychlaPozornost, lory, reference, nastaveni)

    /** Stejné sestavení z textu předlohy, ať jde graf ověřit testem bez Androidu. */
    fun build(
        template: String, prompt: String, sekundy: Double, aspect: Aspect, seed: Long,
        rychlaPozornost: Boolean = true,
        lory: List<LoraEntry> = emptyList(),
        reference: List<String> = emptyList(),
        nastaveni: Nastaveni = Nastaveni(),
    ): JSONObject {
        val wf = JSONObject(template)
        val n = nastaveni

        wf.getJSONObject(N_PROMPT).getJSONObject("inputs")
            .put("value", prompt.trim())

        wf.getJSONObject(N_SEKUNDY).getJSONObject("inputs")
            .put("value", sekundy.coerceIn(MIN_SEKUND, MAX_SEKUND))

        wf.getJSONObject(N_ROZLISENI).getJSONObject("inputs").apply {
            put("aspect_ratio", aspectLabel(aspect))
            put("megapixels", n.mpx.toString().toDouble())
        }
        // Zvětšení mezi průchody — výraz předlohy, jen s jiným číslem.
        N_ZVETSENI.forEach { id ->
            wf.getJSONObject(id).getJSONObject("inputs").put(
                "expression",
                "round(a*%s/32)*32".format(java.util.Locale.US, formatCislo(n.zvetseni)),
            )
        }
        // První průchod: kroky, plánovač; SplitSigmas dělí přesně za nimi,
        // takže první průchod odjede celou svou křivku jako v předloze.
        wf.getJSONObject(N_PLANOVAC).getJSONObject("inputs").apply {
            put("steps", n.kroky)
            put("scheduler", n.scheduler)
        }
        wf.getJSONObject(N_ROZDELENI).getJSONObject("inputs").put("step", n.kroky)
        wf.getJSONObject(N_SAMPLER).getJSONObject("inputs").put("sampler_name", n.sampler)
        wf.getJSONObject(N_SHIFT).getJSONObject("inputs").apply {
            put("shift_video", n.shiftObraz.toString().toDouble())
            put("shift_audio", n.shiftZvuk.toString().toDouble())
        }
        if (n.unet.isNotBlank()) {
            wf.getJSONObject(N_UNET).getJSONObject("inputs").put("unet_name", n.unet)
        }

        wf.getJSONObject(N_SEED).getJSONObject("inputs")
            .put("noise_seed", seed)

        wf.getJSONObject(N_SAVE).getJSONObject("inputs")
            .put("filename_prefix", "video/h3_3step")

        wf.getJSONObject(N_ATTENTION).getJSONObject("inputs").put(
            "attention",
            if (rychlaPozornost) ATTENTION_RYCHLA else ATTENTION_PYTORCH,
        )

        nasadLory(wf, lory)
        nasadReference(wf, reference, prompt, n.vernost)
        if (n.nahled) nasadNahled(wf)

        return wf
    }

    /** 1.5811 → „1.5811", 2.0 → „2" (výraz v předloze má čísla bez zbytečných nul). */
    private fun formatCislo(x: Float): String =
        java.math.BigDecimal(x.toDouble()).setScale(4, java.math.RoundingMode.HALF_UP)
            .stripTrailingZeros().toPlainString()

    /**
     * Živý náhled jako na ostatních kartách H3: uzel od KJ na konec řetězu
     * modelu, před oba průvodce (jinak než na ostatních kartách to nejde —
     * tahle předloha náhled nemá).
     */
    private fun nasadNahled(wf: JSONObject) {
        wf.put(
            N_NAHLED, JSONObject()
                .put("class_type", "ModelPreviewOverrideKJ")
                .put(
                    "inputs", JSONObject()
                        .put("model", JSONArray().put(N_ATTENTION).put(0))
                        .put("max_resolution", 512)
                        .put("jpeg_quality", 70)
                        .put("suppress_default_preview", true)
                        .put("preview_frames", 8)
                        .put("preview_fps", 12)
                        .put("tiny_vae", TAEH3),
                )
                .put("_meta", JSONObject().put("title", "Model Preview Override")),
        )
        N_PRUVODCI.forEach {
            wf.getJSONObject(it).getJSONObject("inputs").put("model", JSONArray().put(N_NAHLED).put(0))
        }
    }

    /** Oba průchody — nízký a vysoký. Oba dostávají stejné zadání i fotky. */
    val N_PODMINKY = listOf("16", "19")
    const val N_VIDEO_VAE = "7"
    const val N_AUDIO_VAE = "13"
    const val REF_ID_OD = 700
    const val MAX_REFERENCI = 4

    /**
     * S fotkami se v obou průchodech vymění `MiniMaxH3ImageToVideo` za
     * `MiniMaxH3ReferenceToVideo` z jádra ComfyUI. Má stejné výstupy
     * (positive, LATENT) i stejné zadání, šířku, výšku a délku, navíc
     * `ref_images` — zbytek receptu tedy zůstává, jak je.
     *
     * Fotky se zapisují jako `ref_images.ref_image_0` … (Autogrow s prefixem
     * `ref_image_`). Model je podle nápovědy uzlu pozná jen, když na ně
     * zadání odkazuje značkou `<Picture N>` — chybí-li, doplní se na začátek.
     */
    private fun nasadReference(
        wf: JSONObject, reference: List<String>, prompt: String, vernost: String = "max",
    ) {
        val fotky = reference.filter { it.isNotBlank() }.take(MAX_REFERENCI)
        if (fotky.isEmpty()) return
        fotky.forEachIndexed { i, jmeno ->
            wf.put(
                (REF_ID_OD + i).toString(),
                JSONObject()
                    .put("class_type", "LoadImage")
                    .put("inputs", JSONObject().put("image", jmeno)),
            )
        }
        N_PODMINKY.forEach { id ->
            val stare = wf.getJSONObject(id).getJSONObject("inputs")
            val nove = JSONObject()
                .put("clip", stare.getJSONArray("clip"))
                .put("prompt", stare.get("prompt"))
                .put("width", stare.get("width"))
                .put("height", stare.get("height"))
                .put("length", stare.get("length"))
                .put("ref_image_size", vernost)
                .put("vae", JSONArray().put(N_VIDEO_VAE).put(0))
                .put("audio_vae", JSONArray().put(N_AUDIO_VAE).put(0))
            fotky.indices.forEach { i ->
                nove.put("ref_images.ref_image_$i", JSONArray().put((REF_ID_OD + i).toString()).put(0))
            }
            wf.put(
                id,
                JSONObject()
                    .put("class_type", "MiniMaxH3ReferenceToVideo")
                    .put("inputs", nove),
            )
        }
        if (!prompt.contains("<Picture", ignoreCase = true)) {
            val znacky = fotky.indices.joinToString(", ") { "<Picture ${it + 1}>" }
            wf.getJSONObject(N_PROMPT).getJSONObject("inputs")
                .put("value", "Reference images: $znacky.\n\n" + prompt.trim())
        }
    }

    /**
     * Zapojí uživatelovy LoRA **za** LoRA z předlohy a přepojí na poslední
     * článek sigma shift. Tahle předloha nemá Power Lora Loader od rgthree
     * jako ostatní video karty, takže se řetězí obyčejné `LoraLoaderModelOnly`
     * — jeden uzel na jednu LoRA, ve stejném pořadí, v jakém je má karta.
     *
     * Vypnuté a prázdné položky se přeskakují; při žádné použitelné LoRA
     * zůstane graf přesně takový, jaký byl v předloze.
     */
    private fun nasadLory(wf: JSONObject, lory: List<LoraEntry>) {
        var predchozi = N_LORA_PREDLOHA
        lory.filter { it.enabled && it.name.isNotBlank() }
            .forEachIndexed { i, l ->
                val id = (LORA_ID_OD + i).toString()
                wf.put(
                    id, JSONObject()
                        .put("class_type", "LoraLoaderModelOnly")
                        .put(
                            "inputs", JSONObject()
                                .put("model", JSONArray().put(predchozi).put(0))
                                .put("lora_name", l.name)
                                .put("strength_model", l.strength.toDouble())
                        )
                        .put("_meta", JSONObject().put("title", "LoRA " + l.name))
                )
                predchozi = id
            }
        if (predchozi != N_LORA_PREDLOHA) {
            wf.getJSONObject(N_SHIFT).getJSONObject("inputs")
                .put("model", JSONArray().put(predchozi).put(0))
        }
    }

    /**
     * Třídy uzlů pro ukazatel průběhu. Běh má dvě fáze a uživatel jinak
     * nechápe, proč se ukazatel po prvním dojetí vrátí zpátky.
     */
    fun nodeClasses(wf: JSONObject): Map<String, String> =
        wf.keys().asSequence().associateWith { wf.getJSONObject(it).optString("class_type") }

    /** Popis fáze pro daný uzel — co se zrovna děje. */
    fun stageForClass(cls: String?): String? = when (cls) {
        "SamplerCustomAdvanced" -> "Generuji"
        "MinimaxH3LatentUpscaler3D" -> "Zvětšuji"
        "VAEDecode", "VAEDecodeAudio" -> "Skládám obraz"
        "SaveVideo" -> "Ukládám"
        else -> null
    }
}
