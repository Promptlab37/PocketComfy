package cz.promptlab.h3video.util

import java.io.File

/**
 * Práce s názvy souborů, které přišly odjinud – typicky ze serveru – a nedá se
 * jim věřit. ComfyUI vrací v historii `filename`, ze kterého si appka brala
 * příponu jako „vše za poslední tečkou". Podvržená odpověď
 * `x.mp4/../../shared_prefs/h3secrets.xml` by dala příponu
 * `mp4/../../shared_prefs/h3secrets.xml` a soubor by se zapsal mimo složku
 * výstupů (audit 16. 9. 2026, nález M-2).
 */
object Soubory {

    /** Přípona = 1–5 malých písmen a číslic, nic jiného. */
    private val PRIPONA = Regex("^[a-z0-9]{1,5}$")

    /**
     * Přípona z [nazev], ale jen když vypadá jako přípona. Cokoli s lomítkem,
     * tečkou navíc nebo bez přípony vrátí [vychozi].
     */
    fun bezpecnaPripona(nazev: String, vychozi: String): String {
        val p = nazev.substringAfterLast('.', "").lowercase()
        return if (PRIPONA.matches(p)) p else vychozi
    }

    /**
     * Leží [cil] uvnitř [slozka]? Porovnává kanonické cesty, takže `..`
     * v názvu nepomůže. Sama složka se za „uvnitř" nepočítá.
     */
    fun uvnitr(cil: File, slozka: File): Boolean {
        val koren = slozka.canonicalFile.path + File.separator
        return cil.canonicalFile.path.startsWith(koren)
    }
}
