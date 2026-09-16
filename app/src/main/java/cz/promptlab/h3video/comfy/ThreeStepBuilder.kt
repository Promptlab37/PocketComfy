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

    fun build(
        ctx: Context, prompt: String, sekundy: Double, aspect: Aspect, seed: Long,
        rychlaPozornost: Boolean = true,
        lory: List<LoraEntry> = emptyList(),
    ): JSONObject =
        build(template(ctx), prompt, sekundy, aspect, seed, rychlaPozornost, lory)

    /** Stejné sestavení z textu předlohy, ať jde graf ověřit testem bez Androidu. */
    fun build(
        template: String, prompt: String, sekundy: Double, aspect: Aspect, seed: Long,
        rychlaPozornost: Boolean = true,
        lory: List<LoraEntry> = emptyList(),
    ): JSONObject {
        val wf = JSONObject(template)

        wf.getJSONObject(N_PROMPT).getJSONObject("inputs")
            .put("value", prompt.trim())

        wf.getJSONObject(N_SEKUNDY).getJSONObject("inputs")
            .put("value", sekundy.coerceIn(MIN_SEKUND, MAX_SEKUND))

        wf.getJSONObject(N_ROZLISENI).getJSONObject("inputs").apply {
            put("aspect_ratio", aspectLabel(aspect))
            put("megapixels", MPX_PRVNI_PRUCHOD)
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

        return wf
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
