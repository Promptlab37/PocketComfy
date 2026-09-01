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
data class ImageEditScene(
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
    val refBoost: Float = 1.5f,
    /**
     * Kolik pixelů delší strany dostane textový enkodér. POZOR na směr:
     * VÍC znamená věrnější podobu, MÍŇ poslušnější úpravu — autor doslova:
     * „lower = stronger edit adherence, higher = stronger identity/likeness.
     * Try 1024 for people, 512 for stubborn scene changes."
     */
    val groundingPx: Int = 1024,
    /** Delší hrana výstupu; 1 MP je podle autora rozumný strop. */
    val megapixels: Float = 1f,
    val aspect: Aspect = Aspect.SQUARE_1_1,
) {
    val resolution: Resolution get() = Resolution.of(aspect, megapixels)

    val hasPerson: Boolean get() = person != null

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
    s.source == null -> "Vyber fotku, kterou chceš upravit."
    s.prompt.isBlank() -> "Napiš, co se má na fotce změnit."
    else -> null
}

/** Upozornění, která nebrání spuštění. */
fun imageEditHints(s: ImageEditScene): List<String> {
    val out = mutableListOf<String>()
    val text = s.prompt.lowercase()
    if (listOf("odeber", "smaž", "smaz", "vymaž", "vymaz", "remove", "delete", "erase")
            .any { it in text }
    ) {
        out += "Mazání věcí z obrázku tenhle model spolehlivě neumí — je to jeho " +
            "nejslabší úloha. Zkus místo mazání popsat, co má být na tom místě místo toho."
    }
    if (s.hasPerson) {
        out += "Obě předlohy jdou do modelu naráz: první je scéna, druhá vkládaná osoba. " +
            "U dvou lidí drž rozlišení kolem 1 MP, výš se podoba rozpadá."
    }
    if (s.megapixels > 1.2f) {
        out += "Nad zhruba 1 MP se u tohohle modelu začíná obsah zdvojovat. " +
            "Radši uprav v menším a zvětši potom v kartě All in One."
    }
    if (s.groundingPx < 768) {
        out += "S nízkým viděním předlohy podoba lidí ujíždí — pro věrné obličeje " +
            "autor doporučuje 1024. Nízké hodnoty se hodí jen na tvrdohlavé změny scény."
    }
    return out
}
