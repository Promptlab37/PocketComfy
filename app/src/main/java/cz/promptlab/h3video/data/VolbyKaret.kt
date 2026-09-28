package cz.promptlab.h3video.data

import cz.promptlab.h3video.comfy.T2iModel

/**
 * Volba uvnitř karty (motor, model, režim), kterou si uživatel v Nastavení
 * → Karty v aplikaci může schovat — typicky proto, že její modely nechce
 * stahovat.
 *
 * Co volba na serveru potřebuje, se nepíše ručně: čte se ze šablon, které
 * appka pro ni opravdu posílá ([sablony], jména v `res/raw` bez přípony).
 * [soubory] jsou modely, které stavitel dosazuje až za běhu (v šabloně
 * nejsou) — ty se hledají podle složky z katalogu.
 */
class Volba(
    val karta: Mode,
    /** Jméno v rámci karty — obvykle `name` hodnoty výčtu. */
    val jmeno: String,
    private val nazev: () -> String,
    val sablony: List<String> = emptyList(),
    val soubory: List<String> = emptyList(),
) {
    val klic: String get() = VolbyKaret.klic(karta, jmeno)
    val title: String get() = nazev()
}

object VolbyKaret {

    fun klic(karta: Mode, jmeno: String): String = "${karta.name}.$jmeno"

    /** Z-Image potřebuje k vlastnímu modelu tentýž enkodér a VAE. */
    private val ZIMAGE_SPOLECNE = listOf("qwen_3_4b.safetensors", "ae.sft")

    /** Všechny volby v pořadí karet. `by lazy` — výčty karet se čtou až při prvním použití. */
    val VSECHNY: List<Volba> by lazy {
        buildList {
            MusicMotor.entries.forEach { m ->
                add(Volba(Mode.MUSIC, m.name, { m.title }, listOf(
                    when (m) {
                        MusicMotor.ACE -> "workflow_ace_music"
                        MusicMotor.YUE2 -> "workflow_yue2_music"
                        MusicMotor.MM3 -> "workflow_minimax_music3"
                    }
                )))
            }
            SwapMotor.entries.forEach { m ->
                add(Volba(Mode.FACESWAP, m.name, { m.title }, listOf(
                    if (m == SwapMotor.QWEN21) "workflow_qwen21_faceswap" else "workflow_ace_faceswap"
                )))
            }
            EditMotor.entries.forEach { m ->
                add(Volba(Mode.EDIT, m.name, { m.nazev }, listOf(
                    when (m) {
                        EditMotor.KREA2 -> "workflow_krea2_edit"
                        EditMotor.QWEN21 -> "workflow_qwen21_edit"
                        EditMotor.KLEIN -> "workflow_flux2_klein_edit"
                    }
                )))
            }
            T2iModel.NABIDKA.forEach { m ->
                val (sablony, soubory) = when (m) {
                    T2iModel.QWEN21 -> listOf("workflow_qwen21_t2i") to emptyList()
                    T2iModel.KLEIN -> listOf("workflow_flux2_klein_t2i") to emptyList()
                    T2iModel.ERNIE -> listOf("workflow_ernie_t2i") to emptyList()
                    // Rodina Z-Image sdílí šablonu, liší se jen modelem.
                    else -> emptyList<String>() to (listOf(m.soubor) + ZIMAGE_SPOLECNE)
                }
                add(Volba(Mode.IMAGE, m.name, { t(m.stitek) }, sablony, soubory))
            }
            InpaintModel.entries.forEach { m ->
                add(Volba(Mode.INPAINT, m.name, { m.title }, listOf(
                    when (m) {
                        InpaintModel.FILL -> "workflow_inpaint_fill"
                        InpaintModel.KLEIN -> "workflow_inpaint_klein"
                        InpaintModel.QWEN21 -> "workflow_inpaint_qwen21"
                    }
                )))
            }
            UpscaleMetoda.entries.forEach { m ->
                add(Volba(Mode.UPSCALE, m.name, { t(m.stitek) }, listOf(
                    when (m) {
                        UpscaleMetoda.SEEDVR2 -> "workflow_seedvr2_upscale"
                        UpscaleMetoda.DLSS -> "workflow_dlss_enhance"
                        UpscaleMetoda.CHYTRE -> "workflow_smart_upscale"
                    }
                )))
            }
            Model3dMotor.entries.forEach { m ->
                // Pixal3D dosazuje vlastní model a obrazový enkodér až stavitel.
                add(Volba(Mode.MODEL3D, m.name, { m.nazev }, listOf("workflow_trellis2"),
                    if (m == Model3dMotor.PIXAL3D) listOf(m.unet, m.clipVision).filter { it.isNotBlank() }
                    else emptyList()))
            }
            // Upravit video: Vyměnit postavu má dva motory — každý je vlastní volba.
            add(Volba(Mode.UPRAVA_VIDEA, UPRAVA_PREMALOVAT, { UpravaRezim.PREMALOVAT.title }))
            add(Volba(Mode.UPRAVA_VIDEA, UPRAVA_POSTAVA_SCAIL,
                { UpravaRezim.POSTAVA.title + " · " + PostavaMotor.SCAIL.title }, listOf("workflow_scail_postava")))
            add(Volba(Mode.UPRAVA_VIDEA, UPRAVA_POSTAVA_H3,
                { UpravaRezim.POSTAVA.title + " · " + PostavaMotor.H3.title },
                soubory = listOf(UpravaScene.SWAP_LORA)))
            add(Volba(Mode.UPRAVA_VIDEA, UpravaRezim.PREDLOHA.name, { UpravaRezim.PREDLOHA.title }, listOf("workflow_h3_controlnet")))
            add(Volba(Mode.UPRAVA_VIDEA, UpravaRezim.ZADANI.name, { UpravaRezim.ZADANI.title }, listOf("workflow_bernini_edit")))
            VylepseniRezim.entries.forEach { r ->
                add(Volba(Mode.VYLEPSENI_VIDEA, r.name, { r.title },
                    if (r == VylepseniRezim.ZPLYNULIT) listOf("workflow_interpolace") else emptyList()))
            }
            PohybRezim.entries.forEach { r ->
                add(Volba(Mode.POHYB, r.name, { r.title },
                    listOf(if (r == PohybRezim.HUDBA) "workflow_dance_wan" else "workflow_wan_animate2")))
            }
            LongMmModel.entries.forEach { m ->
                add(Volba(Mode.LONGMM, m.name, { m.title }, soubory = listOfNotNull(
                    m.unet.takeIf { it.isNotBlank() }, m.lora.takeIf { it.isNotBlank() }
                )))
            }
        }
    }

    const val UPRAVA_PREMALOVAT = "PREMALOVAT"
    const val UPRAVA_POSTAVA_SCAIL = "POSTAVA_SCAIL"
    const val UPRAVA_POSTAVA_H3 = "POSTAVA_H3"

    fun proKartu(karta: Mode): List<Volba> = VSECHNY.filter { it.karta == karta }

    /** Karty, které jsou skryté — ručně, nebo tím, že uživatel schoval všechny jejich volby. */
    fun ucinneSkryte(skryteKarty: Set<Mode>, skryteVolby: Set<String>): Set<Mode> =
        skryteKarty + VSECHNY.groupBy { it.karta }
            .filter { (_, volby) -> volby.all { it.klic in skryteVolby } }.keys

    fun jeSkryta(karta: Mode, jmeno: String, skryteVolby: Set<String>): Boolean =
        klic(karta, jmeno) in skryteVolby

    /** Hodnoty výčtu, které karta nabídne (skryté vypadnou). */
    fun <T> viditelne(karta: Mode, vse: List<T>, skryteVolby: Set<String>, jmeno: (T) -> String): List<T> =
        vse.filter { !jeSkryta(karta, jmeno(it), skryteVolby) }

    /** Režimy Upravit video — Vyměnit postavu zůstane, dokud je vidět aspoň jeden její motor. */
    fun upravaRezimy(skryteVolby: Set<String>): List<UpravaRezim> = UpravaRezim.entries.filter { r ->
        when (r) {
            UpravaRezim.POSTAVA -> postavaMotory(skryteVolby).isNotEmpty()
            else -> !jeSkryta(Mode.UPRAVA_VIDEA, r.name, skryteVolby)
        }
    }

    fun postavaMotory(skryteVolby: Set<String>): List<PostavaMotor> = PostavaMotor.entries.filter { m ->
        !jeSkryta(Mode.UPRAVA_VIDEA, if (m == PostavaMotor.SCAIL) UPRAVA_POSTAVA_SCAIL else UPRAVA_POSTAVA_H3, skryteVolby)
    }
}
