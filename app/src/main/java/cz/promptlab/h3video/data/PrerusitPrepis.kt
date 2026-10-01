package cz.promptlab.h3video.data

/**
 * Smí tlačítko Zastavit běžící přepis na serveru přerušit?
 *
 * Uzly s llama-cpp **v procesu ComfyUI** (`llama_cpp_*`, `MiniMaxH3PromptWriter8B`)
 * ne: přerušení při nahrávání modelu nechá model v grafice a `/free` ho nevidí
 * (28. 9. 2026, viz paměť „prepisovac-preruseni-leak“). Ty doběhnou za desítky
 * sekund a appka na ně jen přestane čekat. Ostatní (Universal Writer a čtení
 * v samostatném procesu, uzly jádra ComfyUI) přerušení snesou — 1. 10. 2026
 * vylepšovač LTX psal hodinu a Zastavit ho dřív nezastavil.
 */
object PrerusitPrepis {
    fun bezpecne(tridy: Set<String>): Boolean =
        tridy.isNotEmpty() && tridy.none { it.contains("llama", ignoreCase = true) || it.contains("PromptWriter8B") }
}
