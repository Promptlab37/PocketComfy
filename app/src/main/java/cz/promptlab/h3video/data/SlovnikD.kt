package cz.promptlab.h3video.data

/** Anglické překlady textů z ui/ (klíč = přesná česká věta z kódu). */
internal val SLOVNIK_D: Map<String, String> = mapOf(
    "Na serveru · %.0f MB" to "On the server · %.0f MB",
    "Na serveru" to "On the server",
    "Stáhnout do telefonu" to "Download to phone",
    "Velké výsledky přes mobilní data" to "Large results over mobile data",
    "Odebrat fotku" to "Remove photo",
    "Odebrat hlas" to "Remove voice",
    // ---- společné prvky (Components, GenerateScreen, přehrávač)
    "Rozbalit" to "Expand",
    "Právě běží" to "Running now",
    "Ve frontě čeká %d" to "Waiting in queue: %d",
    "Zobrazit frontu" to "Show queue",
    "Běží: %s" to "Running: %s",
    "Pauza" to "Pause",
    "Vymazat text" to "Clear text",
    "Další karty vlevo" to "More cards on the left",
    "Další karty vpravo" to "More cards on the right",
    "Zapnuto" to "On",
    "Vypnuto" to "Off",
    "Vypnutá" to "Off",
    "vlastní" to "custom",

    // ---- karta Video a nastavení generování
    "Nejde s referencemi" to "Not available with references",
    "%d snímků · 24 fps" to "%d frames · 24 fps",
    "%s • nativní" to "%s • native",
    "Plátno modelu %s" to "Model canvas %s",
    "Nastaveno podle workflow" to "Set as in the workflow",
    "Hodnota z workflow je 3." to "The workflow value is 3.",
    "Postavit 3D model" to "Build the 3D model",
    "Vygenerovat dlouhé video" to "Generate a long video",
    "Tři kroky na malém rozlišení, zvětšení v latentu, dva kroky navrch" to
        "Three steps at low resolution, a latent upscale, two more steps on top",
    "Popiš, co se má ve videu dít — jednoduše a bez záporů" to
        "Describe what should happen in the video — simply, without negatives",
    "Pozornost" to "Attention",
    "%d bez Turba" to "%d without Turbo",
    "Turbo + %d další" to "Turbo + %d more",
    "Síla %.2f" to "Strength %.2f",
    "%d kroků · shift %.2f" to "%d steps · shift %.2f",
    "Zobrazit i ostatní modely (%d)" to "Show other models too (%d)",

    // ---- 3 kroky (TriKrokyNastaveni)
    "kroky" to "steps",
    "První průchod → výsledek po zvětšení" to "First pass → result after upscaling",
    "První průchod" to "First pass",
    "Předloha má 0,2. Víc = ostřejší a pomalejší." to "The template uses 0.2. More = sharper and slower.",
    "Zvětšení v latentu" to "Latent upscale",
    "Předloha má ×1,58." to "The template uses ×1.58.",
    "Vrátit ×1,58 z předlohy" to "Restore ×1.58 from the template",
    "Vzorkování" to "Sampling",
    "První průchod; zjemnění má 2 kroky napevno" to "First pass; refinement has a fixed 2 steps",
    "Zrychlovací LoRA je trénovaná na 3." to "The speed-up LoRA is trained for 3.",
    "Předloha má 12." to "The template uses 12.",
    "Předloha má 3." to "The template uses 3.",
    "Váhy" to "Weights",
    "Z předlohy (fl2va)" to "From the template (fl2va)",
    "Vrátit hodnoty z předlohy" to "Restore template values",

    // ---- All in One
    "%.1f s navíc" to "%.1f s extra",
    "%.1f s (%d snímků)" to "%.1f s (%d frames)",
    "Reference %d" to "Reference %d",
    "Model dostane zvukovou stopu videa jako referenci" to "The model gets the video's audio track as a reference",
    "Snímek %d" to "Frame %d",
    "Kde ve videu" to "Where in the video",
    "snímek %d · %.1f s" to "frame %d · %.1f s",
    "Krátce a anglicky — „head\", „the red car\". SAM 3 to najde na každém snímku" to
        "Short and in English — “head”, “the red car”. SAM 3 finds it in every frame",
    "Odebrat video" to "Remove video",

    // ---- Úprava obrázku, Domalovat, Zvětšit, 3D model
    "rozměry podle předlohy" to "size from the source image",
    " · vidí %d px · věrnost %.2f" to " · sees %d px · fidelity %.2f",
    "Síla LoRA, která drží obličej. Na plné síle model přejde i jasné zadání." to
        "Strength of the LoRA that keeps the face. At full strength the model ignores even a clear prompt.",
    " · síla %.2f" to " · strength %.2f",
    "%s · ~%d tis. px" to "%s · ~%dk px",
    "%d • náročné" to "%d • demanding",
    "%d×%d • náročné" to "%d×%d • demanding",
    "Model se nepodařilo načíst: %s" to "The model could not be loaded: %s",

    // ---- Dlouhé video, Časová osa
    "Spočítá se na pětině plochy a latent se neuronově zvětší. Znatelně rychlejší, o kus měkčí." to
        "Computed at a fifth of the area, then the latent is upscaled by a neural net. Noticeably faster, a bit softer.",
    "Potřebuje balík Comfyui_Minimax_h3_latent_Upscaler a jeho model ve složce models/latent_upscale_models." to
        "Needs the Comfyui_Minimax_h3_latent_Upscaler package and its model in models/latent_upscale_models.",
    "Segment %d" to "Segment %d",
    "Posunout doleva" to "Move left",
    "Posunout doprava" to "Move right",
    "Duplikovat" to "Duplicate",
    "Generovat přepočítá jen segment %d, ostatní se vezmou " +
        "z mezipaměti. Po dokončení se volba sama vypne." to
        "Generate recomputes only segment %d, the rest comes from the cache. " +
        "The option switches itself off when done.",

    // ---- Hudba
    "Skladba" to "Song",

    // ---- Projekt
    "Zatím tu nic není." to "Nothing here yet.",
    "%d záběrů · %d hotových" to "%d shots · %d done",
    "Další hotový výsledek se připojí k záběru „%s“." to "The next finished result will be attached to the shot “%s”.",
    "bez názvu" to "untitled",
    "Záběr %d" to "Shot %d",
    "hotovo · %s" to "done · %s",
    "čeká na výrobu · %s" to "waiting to be made · %s",
    "Označení, třeba „1A — Anna vejde do haly“" to "Label, e.g. “1A — Anna walks into the hall”",
    "Co se v záběru děje — odsud se předvyplní zadání" to "What happens in the shot — the prompt is pre-filled from this",

    // ---- průběh generování
    "GENERUJI" to "GENERATING",
    "Před tebou je %d úloha ve frontě" to "Jobs ahead of you in the queue: %d",
    "Uplynulo" to "Elapsed",
    "Krok" to "Step",
    "Fáze %d z %d" to "Phase %d of %d",

    // ---- výsledek a rady k chybám
    "Sdílet 3D model" to "Share the 3D model",
    "Uloženo do Stažených" to "Saved to Downloads",
    "Uložit .%s do Stažených" to "Save .%s to Downloads",
    "Grafické kartě došla paměť." to "The graphics card ran out of memory.",
    "Spadlo to při dodělávání sítě — hustý remesh je druhý paměťový vrchol běhu." to
        "It failed while finishing the mesh — the dense remesh is the second memory peak of the run.",
    "Přepni Kvalitu z Maximální na Plné textury; remesh klesne ze 768 na 512." to
        "Switch Quality from Maximum to Full textures; the remesh drops from 768 to 512.",
    "U 3D modelu spadl převod tvaru na síť — to je paměťový vrchol celého běhu." to
        "The 3D model failed while converting the shape to a mesh — that is the memory peak of the whole run.",
    "Sniž na kartě Jemnost tvaru na 1024." to "Lower Shape detail on the card to 1024.",
    "Zavři ostatní aplikace, které používají grafiku (hry, prohlížeč s videem) a zkus to znovu." to
        "Close other apps that use the graphics card (games, a browser playing video) and try again.",

    // ---- galerie
    "3D modely" to "3D models",

    // ---- uvítání
    "V Nastavení pak najdeš tlačítko „Zkontrolovat server\" — vypíše, " to
        "In Settings you'll find the “Check server” button — it lists ",

    // ---- Nastavení
    "Seznam se načítá ze serveru… když se neobjeví, " to "The list is loading from the server… if it doesn't appear, ",
    "ComfyUI se zapíná samo při generování" to "ComfyUI starts by itself when you generate",
    "Vypnout ComfyUI a uvolnit grafiku" to "Stop ComfyUI and free the graphics card",
    "Vypnout Higgs Audio" to "Stop Higgs Audio",
    "Aby to fungovalo z mobilu" to "To make it work from your phone",
    "Krátký seznam, když se appka nemůže spojit" to "A short checklist when the app cannot connect",
    "Počítač musí být zapnutý a přihlášený. ComfyUI se pak spouští samo " +
        "(úloha „H3 ComfyUI autostart\") – náběh po zapnutí trvá asi 3 minuty." to
        "The PC has to be on and signed in. ComfyUI then starts by itself " +
        "(the “H3 ComfyUI autostart” task) – it takes about 3 minutes after power-on.",
    "Telefon musí mít zapnutý Tailscale na stejném účtu, nebo být " +
        "ve stejné Wi-Fi jako počítač." to
        "The phone needs Tailscale on with the same account, or has to be on the same Wi-Fi as the PC.",
    "Přes Tailscale se používá port 8189, po domácí síti 8188 – " +
        "rychlá volba níž nastaví obojí správně." to
        "Over Tailscale the port is 8189, on the home network 8188 – the quick choice below sets both correctly.",
    "Modely MiniMax H3 (ref2va, qwen3vl enkodér a oba VAE) musí být " +
        "v ComfyUI stažené – appka je nedoinstaluje." to
        "The MiniMax H3 models (ref2va, the qwen3vl encoder and both VAEs) must be " +
        "downloaded in ComfyUI – the app will not install them.",
    "Stahuji… %d %%" to "Downloading… %d %%",
    "Nainstalovat %s" to "Install %s",
    "Zkusit znovu" to "Try again",
    "Zkontrolovat znovu" to "Check again",

    "%d kroky" to "%d steps",
    "Nastavení modelu" to "Model settings",
    "Krok %d z %d" to "Step %d of %d",
    "Napsat scénář" to "Write script",
    "Píšu scénář…" to "Writing script…",
    "Napsat znovu" to "Write again",
    "Scénář" to "Script",

    // ---- aktualizace
    "Je tu %s" to "%s is here",
    "Stahuji %s" to "Downloading %s",
)
