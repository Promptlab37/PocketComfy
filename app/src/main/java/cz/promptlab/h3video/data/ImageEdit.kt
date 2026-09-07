package cz.promptlab.h3video.data

import android.graphics.Bitmap
import androidx.compose.runtime.Immutable
import java.io.File

/**
 * Karta **Úprava obrázku** — Krea 2 Turbo + LoRA Krea 2 Identity Edit.
 *
 * Vezme hotovou fotku a upraví ji podle věty v běžné řeči. Předloha jde do
 * modelu dvěma cestami zároveň: jako latent (vzhled) a do textového enkodéru
 * Qwen3-VL (aby při čtení zadání „viděl", co na obrázku je). Právě na tomhle
 * dvojím zapojení je LoRA trénovaná, takže se nedá nahradit obyčejným LoRA
 * loaderem — proto má karta vlastní workflow v `res/raw`.
 *
 * Autor LoRA přiznává dvě omezení, která appka nemá jak obejít: podoba je
 * věrná v textuře, ale geometrii obličeje táhne k běžnějším proporcím, a
 * **mazání objektů Turbo varianta nezvládá spolehlivě**.
 */
@Immutable
/**
 * Čím se fotka upraví. Obojí je editační model, každý jinak postavený.
 */
enum class EditMotor(private val nazevCs: String, private val popisCs: String) {
    KREA2(
        "Krea 2",
        "Má páčku na věrnost podoby a je rychlejší. Druhou předlohu bere " +
            "jako osobu, kterou má do scény vložit.",
    ),
    QWEN(
        "Qwen Image Edit 2511",
        "Na zadání nejposlušnější z těch tří — mění jen to, co jsi napsal, " +
            "a zbytek obrázku nechává. Druhou předlohu bere jako „obrázek 2“.",
    ),
    KLEIN(
        "FLUX.2 Klein 9B",
        "Lépe rozumí složitějšímu zadání a na obě předlohy se dá v textu " +
            "odkázat („Figure 1\", „Figure 2\"). Nemá páčku na věrnost a je pomalejší.",
    );

    val nazev: String get() = t(nazevCs)
    val popis: String get() = t(popisCs)
}

/**
 * Co má u úpravy vyhrát, když si zadání a předloha odporují.
 *
 * Krea 2 drží podobu třemi věcmi najednou: LoRA na totožnost, „ref_boost"
 * a tím, kolik pixelů předlohy vidí textový enkodér (`grounding_px`).
 * Všechny tři táhnou stejným směrem, takže když jsou nahoře, model zadání
 * **přejde** — nechá na postavě původní oblečení i scénu. Uživatel na to
 * narazil: appka měla výchozí hodnoty v tom nejvíc zamčeném rohu.
 *
 * Autor modelu to popisuje doslova: „lower = stronger edit adherence,
 * higher = stronger identity/likeness."
 */
enum class EditZamer(
    private val nazevCs: String,
    private val popisCs: String,
    val grounding: Int,
    val refBoost: Float,
    /** Síla LoRA na totožnost. Do 3.31 byla natvrdo 1,0. */
    val loraSila: Float,
) {
    ZADANI(
        "Poslechnout zadání",
        "Když chceš převléknout, přebarvit nebo změnit scénu. Podoba se drží " +
            "volněji, zato se úprava opravdu stane.",
        grounding = 512, refBoost = 1.0f, loraSila = 0.6f,
    ),
    VYVAZENE(
        "Vyvážené",
        "Rozumný střed pro většinu úprav.",
        grounding = 768, refBoost = 1.2f, loraSila = 0.85f,
    ),
    PODOBA(
        "Držet podobu",
        "Když jde o konkrétního člověka a obličej se nesmí hnout. Pozor: " +
            "silné zadání model v tomhle nastavení klidně přejde.",
        grounding = 1024, refBoost = 1.5f, loraSila = 1.0f,
    );

    val nazev: String get() = t(nazevCs)
    val popis: String get() = t(popisCs)
}

data class ImageEditScene(
    /** Čím se to počítá. Výchozí zůstává Krea 2, na kterou je karta zvyklá. */
    val motor: EditMotor = EditMotor.KREA2,
    /** Upravovaná fotka. */
    val source: File? = null,
    val thumb: Bitmap? = null,
    /** Druhá předloha — osoba, kterou chce uživatel do scény vložit. */
    val person: File? = null,
    val personThumb: Bitmap? = null,
    /** Co se má změnit, běžnou řečí. */
    val prompt: String = "",
    /**
     * Věrnost předlohy. Autor tomu říká „fidelity dial": 1,0 je vypnuto,
     * vyšší hodnota přitahuje výsledek k původnímu vzhledu, nižší uvolňuje.
     * Výchozí 1,5: appka dělá skoro vždycky konkrétní lidi a s vypnutou
     * páčkou identita znatelně ujížděla.
     */
    val refBoost: Float = EditZamer.VYVAZENE.refBoost,
    /**
     * Kolik pixelů delší strany dostane textový enkodér. POZOR na směr:
     * VÍC znamená věrnější podobu, MÍŇ poslušnější úpravu — autor doslova:
     * „lower = stronger edit adherence, higher = stronger identity/likeness.
     * Try 1024 for people, 512 for stubborn scene changes."
     */
    val groundingPx: Int = EditZamer.VYVAZENE.grounding,
    /** Síla LoRA na totožnost. Nižší = ochotnější změna. */
    val loraSila: Float = EditZamer.VYVAZENE.loraSila,
    /**
     * Qwen: rychlá cesta přes Lightning LoRA (4 kroky), nebo pomalá a lepší
     * (40 kroků). Obojí má oficiální předloha, jen schované za přepínačem.
     */
    val qwenRychle: Boolean = true,
    /** Každý motor má vlastní volbu: LoRA se nepřenáší mezi nekompatibilními modely. */
    val modelLoras: Map<EditMotor, EditLora> = emptyMap(),
    /** Delší hrana výstupu; 1 MP je podle autora rozumný strop. */
    val megapixels: Float = 1f,
    val aspect: Aspect = Aspect.SQUARE_1_1,
) {
    val selectedLora: EditLora get() = modelLoras[motor] ?: EditLora()

    fun withLora(lora: EditLora): ImageEditScene = copy(modelLoras = modelLoras + (motor to lora))

    val resolution: Resolution get() = Resolution.of(aspect, megapixels)

    val hasPerson: Boolean get() = person != null

    /** Který záměr odpovídá nastaveným páčkám, nebo null u vlastního mixu. */
    val zamer: EditZamer?
        get() = EditZamer.entries.firstOrNull {
            it.grounding == groundingPx &&
                kotlin.math.abs(it.refBoost - refBoost) < 0.01f &&
                kotlin.math.abs(it.loraSila - loraSila) < 0.01f
        }

    fun sZamerem(z: EditZamer): ImageEditScene =
        copy(groundingPx = z.grounding, refBoost = z.refBoost, loraSila = z.loraSila)

    /** Obrázky v pořadí, v jakém se nahrávají do ComfyUI. */
    val uploadImages: List<File> get() = listOfNotNull(source, person)

    companion object {
        /** Nad 2 MP se podle autora obsah začíná zdvojovat. */
        const val MAX_MEGAPIXELS = 2f
        const val MIN_GROUNDING = 256
        const val MAX_GROUNDING = 1536
    }
}

/** Co kartě chybí, než se dá spustit. Hláška pro uživatele, nebo null. */
fun imageEditProblem(s: ImageEditScene): String? = when {
    s.source == null -> t("Vyber fotku, kterou chceš upravit.")
    s.prompt.isBlank() -> t("Napiš, co se má na fotce změnit.")
    else -> null
}

/** Upozornění, která nebrání spuštění. */
fun imageEditHints(s: ImageEditScene): List<String> {
    val out = mutableListOf<String>()
    val text = s.prompt.lowercase()
    if (listOf("odeber", "smaž", "smaz", "vymaž", "vymaz", "remove", "delete", "erase")
            .any { it in text }
    ) {
        out += t("Mazání věcí z obrázku tenhle model spolehlivě neumí — je to jeho ") +
            t("nejslabší úloha. Zkus místo mazání popsat, co má být na tom místě místo toho.")
    }
    if (s.hasPerson) {
        out += t("Obě předlohy jdou do modelu naráz: první je scéna, druhá vkládaná osoba. ") +
            t("U dvou lidí drž rozlišení kolem 1 MP, výš se podoba rozpadá.")
    }
    if (s.megapixels > 1.2f) {
        out += t("Nad zhruba 1 MP se u tohohle modelu začíná obsah zdvojovat. ") +
            t("Radši uprav v menším a zvětši potom v kartě All in One.")
    }
    if (s.groundingPx < 768) {
        out += t("S nízkým viděním předlohy podoba lidí ujíždí — pro věrné obličeje ") +
            t("autor doporučuje 1024. Nízké hodnoty se hodí jen na tvrdohlavé změny scény.")
    }
    return out
}
