package cz.promptlab.h3video.data

/** Anglické překlady textů z MainViewModel.kt a MainActivity.kt (klíč = přesná česká věta z kódu). */
internal val SLOVNIK_A: Map<String, String> = mapOf(
    // MainActivity — lišta, proužek hotovo, mini průběh, aktualizace
    "Klepni pro zobrazení" to "Tap to view",
    "Odklidit" to "Dismiss",
    "krok %d/%d" to "step %d/%d",
    "zbývá ~%s" to "~%s left",
    "Zobrazit" to "Show",
    "Je k dispozici %s" to "%s is available",

    // MainViewModel — server, aktualizace, seznamy
    "ComfyUI vypnuto, grafika je volná" to "ComfyUI is off, the graphics card is free",
    "Nepovedlo se – běží na počítači spouštěč?" to "That didn't work – is the launcher running on the computer?",
    "Aktualizaci se nepodařilo ověřit" to "Could not check for updates",
    "Stažení se nepovedlo: %s" to "Download failed: %s",
    "Seznam LoRA se nepodařilo načíst — server neodpovídá." to
        "Could not load the LoRA list — the server does not respond.",
    "Server neodpovídá.\n\nZkontroluj, že počítač běží, ComfyUI je " +
        "spuštěné s parametrem --listen 0.0.0.0 a že jsi na stejné síti " +
        "nebo VPN (např. Tailscale). Náběh ComfyUI po zapnutí počítače " +
        "trvá i pár minut – chvíli počkej a zkus to znovu." to
        "The server does not respond.\n\nCheck that the computer is running, ComfyUI is " +
        "started with --listen 0.0.0.0 and that you are on the same network " +
        "or VPN (e.g. Tailscale). ComfyUI can take a few minutes to start " +
        "after the computer boots – wait a moment and try again.",
    "Kontrola se nedokončila — server neodpovídá. " +
        "Zkontroluj spojení tlačítkem výš a zkus to znovu." to
        "The check did not finish — the server does not respond. " +
        "Check the connection with the button above and try again.",

    // MainViewModel — co kartě chybí a upozornění
    "Na serveru chybí balík ComfyUI-ALLinONE-MinimaxH3 – nainstaluj ho " +
        "v ComfyUI a restartuj server." to
        "The ComfyUI-ALLinONE-MinimaxH3 package is missing on the server – install it " +
        "in ComfyUI and restart the server.",
    "Profil %s nejde použít s referencemi – přepni profil." to
        "The %s profile cannot be used with references – switch the profile.",
    "Sigma shift je %.2f, workflow má u tohoto profilu %.2f. Odchylka mění celý průběh vzorkování." to
        "Sigma shift is %.2f, the workflow uses %.2f for this profile. The difference changes the whole sampling.",
    "Rozlišení %s je pod plátnem modelu " +
        "(%s) – obraz bývá měkčí a tváře méně přesné." to
        "Resolution %s is below the model's canvas " +
        "(%s) – the image tends to be softer and faces less accurate.",
    "Namluvené repliky trvají %d s, ale video má %d s – " +
        "model konec ustřihne. Přidej délku, nebo repliky zkrať." to
        "The voiced lines take %d s, but the video is %d s – " +
        "the model will cut off the end. Add length or shorten the lines.",
    "Délka je nastavená na %d s podle namluvených replik (potřeba %d s)." to
        "Length is set to %d s to fit the voiced lines (%d s needed).",
    "Postava bez fotky se do videa nedostane – model nemá podle čeho ji vytvořit." to
        "A character without a photo won't appear in the video – the model has nothing to build it from.",
    "Tři repliky jsou strop jednoho videa (model bere jen tři zvukové reference)." to
        "Three lines is the limit for one video (the model takes only three audio references).",
    "Délka pod 5 s je mimo trénovaný rozsah modelu – výsledek bývá horší." to
        "Under 5 s is outside the model's trained range – results tend to be worse.",
    "Rozlišení %s je o %d %% víc bodů než " +
        "plátno modelu (%s). Generovat se to dá, jen to trvá " +
        "déle a detaily bývají měkčí – ostřejší HD spíš vyjde z nativu a zvětšení " +
        "v kartě All in One." to
        "Resolution %s has %d %% more pixels than " +
        "the model's canvas (%s). It can be generated, it just takes " +
        "longer and details tend to be softer – sharper HD usually comes from native plus upscaling " +
        "in the All in One card.",

    // MainViewModel — popisy úloh ve frontě a galerii
    "Oprava staré fotky" to "Old photo restoration",

    // MainViewModel — Higgs a namlouvání replik
    "Na počítači neodpovídá spouštěč Higgse (port 8191)." to
        "The Higgs launcher on the computer does not respond (port 8191).",
    "Higgs se nerozjel ani za tři minuty. Zkus to znovu." to
        "Higgs did not start even after three minutes. Try again.",
    "Higgs vypnutý, grafika je volná" to "Higgs is off, the graphics card is free",
    "Higgs jsem nespouštěl, není co vypínat" to "Higgs wasn't started, nothing to stop",
    "Postava nemá vybraný hlas." to "The character has no voice selected.",
    "Higgs se nepodařilo spustit." to "Higgs could not be started.",
    "Namluvení se nepovedlo." to "Voicing failed.",
    "Namlouvání trvá neúměrně dlouho, zkus kratší text." to
        "Voicing is taking far too long, try a shorter text.",

    // MainViewModel — přepisovač / překladač / vylepšení promptu
    "Chyba" to "Error",
    "Přepis se nepovedl." to "The rewrite failed.",
    "Na serveru chybí balík ComfyUI-llama-cpp_vlm — bez něj " +
        "překladač nepojede." to
        "The ComfyUI-llama-cpp_vlm package is missing on the server — " +
        "the translator needs it.",
    "Uzel nenabízí žádný model." to "The node offers no model.",
    "V models/LLM není žádný GGUF model, ze kterého by šlo překládat." to
        "There is no GGUF model in models/LLM to translate with.",
    "Server nemá uzly llama.cpp — bez nich prompt vylepšit nejde." to
        "The server has no llama.cpp nodes — the prompt can't be improved without them.",
    "V models/LLM není žádný GGUF model, ze kterého by šlo psát." to
        "There is no GGUF model in models/LLM to write with.",
    "Server neumí reference v přepisovači — aktualizuj balík " +
        "MiniMax-H3-Prompt-Rewriter-ComfyUI a restartuj ComfyUI." to
        "The server's rewriter doesn't support references — update the " +
        "MiniMax-H3-Prompt-Rewriter-ComfyUI package and restart ComfyUI.",
    "Přepisovač nemá čím fotky přečíst — chybí vidoucí GGUF s projektorem." to
        "The rewriter can't read the photos — a vision GGUF with a projector is missing.",
    "Přepisovač nemá čím psát — nahraj GGUF do models/LLM." to
        "The rewriter has nothing to write with — put a GGUF in models/LLM.",
    "Server nemá balík Prompt Rewriter — nainstaluj " +
        "MiniMax-H3-Prompt-Rewriter-ComfyUI a restartuj ComfyUI." to
        "The server doesn't have the Prompt Rewriter package — install " +
        "MiniMax-H3-Prompt-Rewriter-ComfyUI and restart ComfyUI.",
    "Přepisovač nenabízí žádný model — nahraj GGUF do models/LLM." to
        "The rewriter offers no model — put a GGUF in models/LLM.",
    "Server nemá uzly llama.cpp — bez nich návrh záběrů nejede." to
        "The server has no llama.cpp nodes — shot suggestions need them.",
    "Server nemá uzel na čtení obrázku — aktualizuj balík " +
        "MiniMax-H3-Prompt-Rewriter-ComfyUI a restartuj ComfyUI." to
        "The server has no image-reading node — update the " +
        "MiniMax-H3-Prompt-Rewriter-ComfyUI package and restart ComfyUI.",
    "Přepisovač nemá čím obrázek přečíst — chybí vidoucí GGUF s projektorem." to
        "The rewriter can't read the image — a vision GGUF with a projector is missing.",
    "Server nemá uzly llama.cpp — bez nich se zadání navázání přepsat nedá." to
        "The server has no llama.cpp nodes — the continuation prompt can't be rewritten without them.",
    "V models/LLM není žádný jazykový model, kterým by se zadání přepsalo." to
        "There is no language model in models/LLM to rewrite the prompt with.",
    "Server nemá uzel %s — potřebuje ComfyUI, " +
        "které zná LTX 2. Odvázaný přepisovač jede i bez něj." to
        "The server has no %s node — it needs a ComfyUI version " +
        "that knows LTX 2. The unfiltered rewriter works without it.",
    "Nový projekt" to "New project",
)
