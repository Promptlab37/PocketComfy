package cz.promptlab.h3video.data

/** Anglické překlady textů z data/ (klíč = přesná česká věta z kódu). */
internal val SLOVNIK_C: Map<String, String> = mapOf(
    // AllInOne.kt
    "Vygeneruje se %d snímků, z toho %d navazuje na konec zdrojového videa a nových je %d (%.1f s)." to
        "%d frames will be generated: %d continue from the end of the source video and %d are new (%.1f s).",
    "Delší prodloužení než %d s uzel neumí – zbytek se ustřihne." to
        "The node can't extend by more than %d s – the rest will be cut off.",
    "Napiš, co se má ve videu sledovat — třeba „head\"." to
        "Write what should be tracked in the video — e.g. \"head\".",
    // ImageEdit.kt
    "Má páčku na věrnost podoby a je rychlejší. Druhou předlohu bere jako osobu, kterou má do scény vložit." to
        "Has a likeness slider and is faster. Treats the second reference as a person to insert into the scene.",
    "Lépe rozumí složitějšímu zadání a na obě předlohy se dá v textu odkázat („Figure 1\", „Figure 2\"). Nemá páčku na věrnost a je pomalejší." to
        "Understands complex instructions better, and you can refer to both references in the text (\"Figure 1\", \"Figure 2\"). No likeness slider and slower.",
    "Poslechnout zadání" to "Follow the prompt",
    "Když chceš převléknout, přebarvit nebo změnit scénu. Podoba se drží volněji, zato se úprava opravdu stane." to
        "For changing clothes, colors or the scene. The likeness is held more loosely, but the edit actually happens.",
    "Rozumný střed pro většinu úprav." to "A sensible middle ground for most edits.",
    "Držet podobu" to "Keep the likeness",
    "Když jde o konkrétního člověka a obličej se nesmí hnout. Pozor: silné zadání model v tomhle nastavení klidně přejde." to
        "For a specific person whose face must not change. Note: with this setting the model may ignore a strong prompt.",
    // LongVideo.kt
    "Úseků je %d — každý je vlastní vzorkování, takže běh potrvá zhruba %d× dýl než jedno video." to
        "There are %d segments — each is sampled separately, so the run will take about %d× longer than a single video.",
    "Bez referencí drží podobu jen kontext z videa. U delších řetězů se postava postupně rozjíždí — pomůže přidat její fotku." to
        "Without references, only the video context holds the likeness. In longer chains the character gradually drifts — adding their photo helps.",
    // Model3dScene.kt
    "Barvy zapečené do vrcholů sítě. Žádné rozbalování UV ani pečení map — hotové řádově dřív, ale v editoru se s tím nedá pracovat." to
        "Colors baked into the mesh vertices. No UV unwrapping or map baking — done much sooner, but not usable for editing.",
    "Plné textury" to "Full textures",
    "Rozbalí UV a upeče base color, kov, drsnost, normály i stínění. Hodnoty jsou ty, které mají uzly jako výchozí." to
        "Unwraps UVs and bakes base color, metallic, roughness, normals and occlusion. Uses the nodes' default values.",
    "Jako plné textury, ale s hodnotami z ukázkové šablony ComfyUI: hustší remesh, 700 tisíc ploch, vyhlazení a normály 2048. Navíc 20 kroků vzorkování ve všech fázích místo 12 — kroky stojí čas, ne paměť. Nejdelší běh." to
        "Like full textures, but with values from the ComfyUI example template: denser remesh, 700k faces, smoothing and 2048 normals. Plus 20 sampling steps in every stage instead of 12 — steps cost time, not memory. Longest run.",
    // Modes.kt
    "Záběry v pořadí, jejich popisy a hotové výsledky na jednom místě" to
        "Shots in order, their descriptions and finished results in one place",
    "Rychlé video z textu — tři kroky, zvětšení v latentu, dva kroky navrch" to
        "Fast video from text — three steps, latent upscale, two steps on top",
    // Params.kt
    "Kvalita" to "Quality",
    "Turbo LoRA, 8 kroků – rychlé" to "Turbo LoRA, 8 steps – fast",
    "Plný model bez LoRA, 10 kroků – lepší hlas" to "Full model without LoRA, 10 steps – better voice",
    "Nová Turbo LoRA v4, 8 kroků – rychlé" to "New Turbo LoRA v4, 8 steps – fast",
    "Nová lightx2v LoRA, 8 kroků – lepší zvuk" to "New lightx2v LoRA, 8 steps – better audio",
    "Bez LoRA, res_multistep, 10 kroků – nejvěrnější" to "No LoRA, res_multistep, 10 steps – most faithful",
    "FastH3, 4 kroky – nejrychlejší, ale bez referencí" to "FastH3, 4 steps – fastest, but no references",
    "ckpt500 (vyladěné workflow)" to "ckpt500 (tuned workflow)",
    "v4 step600 EMA (autor doporučuje)" to "v4 step600 EMA (author's pick)",
    "lightx2v 8step v1.0 (768p) — nejlepší zvuk" to "lightx2v 8step v1.0 (768p) — best audio",
    "lightx2v ref2v 4step (pro reference)" to "lightx2v ref2v 4step (for references)",
    "FastH3 4step (bez referencí)" to "FastH3 4step (no references)",
    // Timeline.kt
    "Jen z popisu" to "Text only",
    "Segment %d má %d s, model zvládne nejvýš %d s na jeden. Rozděl ho." to
        "Segment %d is %d s long; the model handles at most %d s per segment. Split it.",
    "Segment %d čeká na snímek, ze kterého má vyjít." to "Segment %d needs a frame to start from.",
    // UpscaleScene.kt
    "Mřížka %s znamená %d dlaždic — výsledek kolem %d tisíc pixelů, ale poběží to násobně déle než 2×2." to
        "A %s grid means %d tiles — a result of about %d thousand pixels, but it will take many times longer than 2×2.",
)
