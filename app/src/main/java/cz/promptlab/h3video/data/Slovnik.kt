package cz.promptlab.h3video.data

/**
 * Česko-anglický slovník rozhraní. Klíč je přesná česká věta z kódu.
 *
 * Co tu není, zůstane česky — přidávat se dá po vrstvách, aniž by se cokoli
 * rozbilo. Pokrytí zatím: karty, průběh generování, uvítání, obrazovka
 * výsledku, galerie a hlášky „co kartě chybí".
 */
object Slovnik {

    val EN: Map<String, String> = mapOf(
        // ---------------------------------------------------------- karty
        "Dialogy" to "Dialogue",
        "Časová osa" to "Timeline",
        "Osa" to "Timeline",
        "Obrázek" to "Image",
        "Úprava obrázku" to "Image edit",
        "Úprava" to "Edit",
        "Oprava fotky" to "Photo restore",
        "Oprava" to "Restore",
        "Výměna tváře" to "Face swap",
        "Tvář" to "Face",
        "Zvětšit" to "Upscale",
        "Hudba" to "Music",
        "Z textu, obrázků i referencí, klíčové snímky, prodloužení a zvětšení" to
            "From text, images or references; keyframes, extension and upscale",
        "Postavy z fotek řeknou, co napíšeš" to "Characters from photos say what you type",
        "Delší video složené ze segmentů" to "A longer video assembled from segments",
        "Z-Image Turbo — nová fotka z textu za pár sekund" to
            "Z-Image Turbo — a new picture from text in seconds",
        "Změní hotovou fotku podle popisu, tvář zůstane" to
            "Changes a finished photo from your description, the face stays",
        "Stará či poškozená fotka jako nová, i barevně" to
            "An old or damaged photo as new, colourised too",
        "Začmáráš obličej, vybereš novou tvář, hotovo" to
            "Scribble over the face, pick a new one, done",
        "SeedVR2 gigapixel — fotka ve velkém rozlišení" to
            "SeedVR2 gigapixel — a picture at huge resolution",
        "ACE-Step 1.5 — celá píseň z textu, i česky" to
            "ACE-Step 1.5 — a whole song from text, vocals included",

        // ------------------------------------------------ průběh generování
        "Probouzím ComfyUI" to "Waking up ComfyUI",
        "Odesílám podklady" to "Uploading inputs",
        "Odesílám fotku" to "Uploading the photo",
        "Odesílám fotky" to "Uploading the photos",
        "Připravuji zadání" to "Preparing the job",
        "Ve frontě" to "In the queue",
        "Načítám modely" to "Loading models",
        "Načítám Krea 2" to "Loading Krea 2",
        "Načítám Z-Image" to "Loading Z-Image",
        "Načítám Qwen Edit" to "Loading Qwen Edit",
        "Načítám Flux Fill" to "Loading Flux Fill",
        "Načítám SeedVR2" to "Loading SeedVR2",
        "Načítám ACE-Step" to "Loading ACE-Step",
        "Připravuji podklady" to "Preparing the inputs",
        "Načítám fotku" to "Loading the photo",
        "Připravuji výřez tváře" to "Preparing the face crop",
        "Dělím na dlaždice" to "Splitting into tiles",
        "Spouštím DLSS 5" to "Starting DLSS 5",
        "Připravuji plátno" to "Preparing the canvas",
        "Zpracovávám prompt" to "Processing the prompt",
        "Čtu zadání skladby" to "Reading the song brief",
        "Připravuji vlepení" to "Preparing the stitch",
        "Čtu zadání" to "Reading the brief",
        "Generuji video" to "Generating video",
        "Upravuji obrázek" to "Editing the image",
        "Generuji obrázek" to "Generating the image",
        "Opravuji fotku" to "Restoring the photo",
        "Měním tvář" to "Swapping the face",
        "Zvětšuji obrázek" to "Upscaling the image",
        "Doostřuji fotku" to "Sharpening the photo",
        "Skládám hudbu" to "Composing the music",
        "Dekóduji obraz a zvuk" to "Decoding video and audio",
        "Dekóduji zvuk" to "Decoding audio",
        "Dekóduji obraz" to "Decoding video",
        "Skládám video" to "Assembling the video",
        "Ukládám skladbu" to "Saving the song",
        "Vlepuji tvář zpět" to "Stitching the face back",
        "Slepuji dlaždice" to "Merging the tiles",
        "Ukládám obrázek" to "Saving the image",
        "Přebírám video" to "Fetching the video",
        "Přebírám skladbu" to "Fetching the song",
        "Přebírám obrázek" to "Fetching the image",
        "Dokončuji" to "Finishing",
        "Zvětšování" to "Upscaling",
        "MiniMax H3 + textový enkodér" to "MiniMax H3 + text encoder",
        "Krea 2 + textový enkodér" to "Krea 2 + text encoder",
        "Z-Image Turbo + textový enkodér" to "Z-Image Turbo + text encoder",
        "Qwen Image Edit 2511 + LoRA" to "Qwen Image Edit 2511 + LoRA",
        "Flux Fill + portrétní LoRA" to "Flux Fill + portrait LoRA",
        "Nejdelší část – obraz i zvuk najednou" to "The long part — video and audio at once",
        "Celá skladba vzniká najednou" to "The whole song is generated in one pass",
        "Dlaždice po dlaždici na 3200 px" to "Tile by tile, up to 3200 px each",
        "Nejdelší část běhu" to "The longest part of the run",
        "Přenáším ji z počítače do Galerie aplikace" to
            "Transferring it from the PC to the app gallery",
        "Přenáším ho z počítače do Galerie aplikace" to
            "Transferring it from the PC to the app gallery",
        "Sestavuji graf pro ComfyUI" to "Building the ComfyUI graph",
        "Převádím latentní data na zvuk" to "Turning latents into audio",
        "Převádím latentní data na obraz" to "Turning latents into pixels",
        "Zapisuji MP3" to "Writing the MP3",
        "Prolnutí dlaždic do jedné fotky" to "Blending the tiles into one picture",
        "Zapisuji hotový obrázek" to "Writing the finished image",
        "Generování obrazu a zvuku" to "Generating video and audio",
        "Nový obrázek" to "New image",
        "Skládání hudby" to "Composing music",
        "Spojení a odeslání referencí" to "Connecting and uploading references",
        "Spojení se serverem" to "Connecting to the server",
        "Spojení a odeslání fotky" to "Connecting and uploading the photo",

        // ------------------------------------------------------- uvítání
        "Vítej v PocketComfy" to "Welcome to PocketComfy",
        "Appka je klient pro tvůj vlastní ComfyUI server — všechno se " to
            "This app is a client for your own ComfyUI server — everything is ",
        "Zadej adresu počítače, na kterém ComfyUI běží. Musí být " to
            "Enter the address of the PC running ComfyUI. It has to be ",
        "spuštěné s parametrem --listen 0.0.0.0 a telefon musí být " to
            "started with --listen 0.0.0.0 and the phone has to be on ",
        "na stejné síti nebo VPN (např. Tailscale)." to
            "the same network or VPN (e.g. Tailscale).",
        "Zkouším spojení…" to "Testing the connection…",
        "Otestovat spojení" to "Test connection",
        "  Připojuji se…" to "  Connecting…",
        "Vstoupit do appky" to "Enter the app",
        "Pokračovat bez testu" to "Continue without testing",
        " — vypíše, " to " — it lists ",
        "jestli na serveru nechybí custom nody nebo modely, které " to
            "any custom nodes or models the app's cards need but ",
        "karty appky potřebují." to "your server does not have.",

        // ------------------------------------------------------- výsledek
        "Obrázek je hotový" to "The image is ready",
        "Skladba je hotová" to "The song is ready",
        "Video je hotové" to "The video is ready",
        " · hotovo za " to " · done in ",
        "2 reference · " to "2 references · ",
        "Upravený obrázek – klepnutím zvětšíš" to "Edited image — tap to enlarge",
        "Klepni pro zvětšení" to "Tap to enlarge",
        "V galerii telefonu" to "In the phone gallery",
        "Uložit do galerie" to "Save to gallery",
        "Uložení se nepovedlo" to "Saving failed",
        "Uloženo do Hudba/H3 Video" to "Saved to Music/H3 Video",
        "Uloženo do Obrázky/H3 Video" to "Saved to Pictures/H3 Video",
        "Uloženo do Filmy/H3 Video" to "Saved to Movies/H3 Video",
        "Sdílet" to "Share",
        "Sdílet skladbu" to "Share the song",
        "Sdílet obrázek" to "Share the image",
        "Sdílet video" to "Share the video",
        "Pokračuj s obrázkem" to "Continue with this image",
        "Rozhýbat — video z obrázku" to "Animate — video from the image",
        "Upravit (Krea 2 — popiš změnu)" to "Edit (Krea 2 — describe the change)",
        "Zvětšit (SeedVR2 gigapixel)" to "Upscale (SeedVR2 gigapixel)",
        "Generovat další" to "Generate another",
        "Nepovedlo se" to "It did not work",
        "Zkusit přenos znovu" to "Try the transfer again",
        "Zpět na zadání" to "Back to the form",
        "Co k tomu řekly uzly" to "What the nodes reported",
        "Obrázek na celou obrazovku" to "Full-screen image",
        "Zavřít" to "Close",
        "Zkopírováno" to "Copied",

        // -------------------------------------------------------- galerie
        "Zatím tu nic není" to "Nothing here yet",
        "Vygenerovaná videa se ukládají sem a zůstanou tu,\ni když je počítač vypnutý." to
            "Generated videos are stored here and stay\neven when the PC is switched off.",
        "Hledat v popisech…" to "Search in prompts…",
        "Vše" to "All",
        "Videa" to "Videos",
        "Obrázky" to "Images",
        "Tomuhle filtru nic neodpovídá." to "Nothing matches this filter.",
        "(bez popisu)" to "(no prompt)",
        " · za " to " · in ",
        "Smazáno" to "Deleted",
        "Vrátit" to "Undo",
        "d. M. HH:mm" to "MMM d, HH:mm",

        // ------------------------------------------ co kartě ještě chybí
        "Vyber fotku, ve které se má vyměnit tvář." to
            "Pick the photo whose face should be swapped.",
        "Začmárej prstem obličej, který se má vyměnit." to
            "Scribble over the face that should be swapped.",
        "Vyber fotku s novou tváří." to "Pick the photo with the new face.",
        "Nejlíp funguje ostrá tvář zepředu, bez brýlí a bez stínů." to
            "A sharp front-facing face without glasses or shadows works best.",
        "Vyber fotku, kterou chceš opravit." to "Pick the photo you want restored.",
        "Vyber fotku, kterou chceš zvětšit." to "Pick the photo you want upscaled.",

        // -------------------------------------------------- hlavní obrazovka
        "Tvořit" to "Create",
        "Galerie" to "Gallery",
        "Nastavení" to "Settings",
        "nastavení" to "settings",
        "galerie" to "gallery",
        "Klepni pro stažení a instalaci" to "Tap to download and install",
        "Počítač je připravený" to "The PC is ready",
        "ComfyUI je vypnuté" to "ComfyUI is switched off",
        "Zjišťuji stav počítače…" to "Checking the PC…",
        "Telefon je bez spojení s počítačem" to "No connection to the PC",
        "Grafika je volná. Až dáš Generovat, appka ho zapne sama – " to
            "The GPU is free. When you hit Generate the app starts it — ",
        "náběh trvá asi tři minuty." to "startup takes about three minutes.",
        "Generování na počítači běží dál – appka se připojí sama, " to
            "Generation keeps running on the PC — the app reconnects ",
        "jakmile bude spojení zpátky." to "as soon as the connection is back.",

        // ------------------------------------------------- karta a zadání
        "Nový obrázek" to "New image",
        "Z-Image Turbo — hotovo za pár sekund" to "Z-Image Turbo — done in seconds",
        "Popiš, co má na obrázku být — jednoduše a bez záporů" to
            "Describe what should be in the picture — simply, no negatives",
        "Poměr stran" to "Aspect ratio",
        "Vygenerovat obrázek" to "Generate image",
        "Upravit obrázek" to "Edit image",
        "Zvětšit obrázek" to "Upscale image",
        "Vyměnit tvář" to "Swap the face",
        "Přidat do fronty" to "Add to queue",
        "Generování běží. Klidně uprav zadání a přidej další běh do fronty." to
            "A run is in progress. Feel free to adjust the brief and queue another.",
        "Model" to "Model",
        "Turbo (základ)" to "Turbo (stock)",
        "Photoreal (odvázaný)" to "Photoreal (uncensored)",
        "NSFW Photorealistic v6.1 — nic neodmítá, jede na 12 kroků. " to
            "NSFW Photorealistic v6.1 — refuses nothing, runs 12 steps. ",
        "LoRA níž s ním není potřeba." to "The LoRA below is not needed with it.",
        "Bez cenzury" to "Uncensored",
        "Přimíchá odvázanou LoRA — model pak nic neodmítá" to
            "Mixes in an uncensored LoRA — the model then refuses nothing",
        "Která LoRA" to "Which LoRA",
        "Síla" to "Strength",
        "1.00 = jak byla trénovaná; kolem 0.75 jemnější výsledky." to
            "1.00 = as trained; around 0.75 gives subtler results.",
        "Přidej do promptu spouštěcí slovo: l3n0v0" to
            "Add the trigger word to the prompt: l3n0v0",
        "Přidej do promptu: photorealistic, detailed skin, fine texture" to
            "Add to the prompt: photorealistic, detailed skin, fine texture",
        "Chce v promptu polohu/akt (missionary, cowgirl…) — je na ně trénovaná." to
            "Wants a pose/act in the prompt (missionary, cowgirl…) — that is what it was trained on.",
        "Ve frontě" to "In the queue",
        "Odebrat z fronty" to "Remove from the queue",

        // ------------------------------------------------------- pokročilé
        "Pokročilé" to "Advanced",
        "Zobrazit pokročilé volby" to "Show advanced options",
        "Změněno oproti workflow" to "Changed from the workflow",
        "Vrátit hodnoty z workflow" to "Restore the workflow values",
        "Rozlišení" to "Resolution",
        "Megapixely × poměr stran, zaokrouhleno na násobek 32" to
            "Megapixels × aspect ratio, rounded to a multiple of 32",
        "Maximální detail" to "Maximum detail",
        "Vyvážené" to "Balanced",
        "Délka" to "Length",
        "Model počítá po blocích 17 snímků, proto se délka zaokrouhlí" to
            "The model works in blocks of 17 frames, so the length is rounded",
        "trénováno na 5–15 s" to "trained on 5–15 s",
        "Počet kroků" to "Steps",
        "Workflow používá 8 s Turbo LoRA." to "The workflow uses 8 with the Turbo LoRA.",
        "Plánovač (scheduler)" to "Scheduler",
        "Sigma shift – obraz" to "Sigma shift — video",
        "Sigma shift – zvuk" to "Sigma shift — audio",
        "Hodnota z workflow je 12,19." to "The workflow value is 12.19.",
        "Komprese videa (CRF)" to "Video compression (CRF)",
        "Nižší číslo = lepší obraz a větší soubor. Workflow má 19." to
            "Lower number = better picture and a bigger file. The workflow uses 19.",
        "Náhodný seed" to "Random seed",
        "Vypni, když chceš stejné zadání zopakovat beze změny" to
            "Turn off to repeat the same brief unchanged",
        "Živý náhled" to "Live preview",
        "Rozpracované snímky během generování; vypnutí šetří grafiku" to
            "Frames in progress while generating; turning it off saves GPU",
        "Rychlejší pozornost, ve workflow zapnutá" to
            "Faster attention, enabled in the workflow",
        "Přeskočí podobné kroky — až 3× rychlejší, drobně méně věrné" to
            "Skips similar steps — up to 3× faster, slightly less faithful",
        "Totéž co vypínač nahoře – přibližné zrychlení" to
            "Same as the switch above — approximate speed-up",
        "Zrychluje generování, ale zvuk je jen přibližný" to
            "Speeds generation up, but the audio is only approximate",
        "Vypnuté – věrnější zvuk, o něco pomalejší" to
            "Off — more faithful audio, a bit slower",
        "Věrnost referencí" to "Reference fidelity",
        "Žádná – model jede na plno" to "None — the model runs at full strength",
        "Vypnutá – plný model, lepší hlas, ale pomalejší" to
            "Off — full model, better voice, but slower",
        "Přidat LoRA" to "Add a LoRA",
        "Vyměnit Turbo LoRA" to "Swap the Turbo LoRA",
        "Zobrazit všechny LoRA" to "Show all LoRAs",
        "Zrušit výměnu" to "Cancel the swap",
        "Zrušit" to "Cancel",
        "Opravdu zrušit?" to "Really cancel?",
        "Skrýt" to "Hide",
        "Na serveru není – nejdřív ji stáhni do models/loras" to
            "Not on the server — download it into models/loras first",
        "Načítám seznam ze serveru…" to "Loading the list from the server…",
        "Seznam se načítá ze serveru…" to "Loading the list from the server…",
        "Nic dalšího pro H3 na serveru není." to "The server has nothing else for H3.",
        "Z workflow (výchozí)" to "From the workflow (default)",
        "Tahle karta jede na referenčním modelu z workflow" to
            "This card runs the reference model from the workflow",
        "Na téhle kartě se generuje referenčními vahami z workflow. Výběr " to
            "This card generates with the reference weights from the workflow. The choice ",
        "výš se projeví na kartách Text → video, Obrázek → video a v All in One." to
            "above applies to text → video, image → video and All in One.",
        "Vlastní model se týká textu, snímků a All in One. Reference, Mluvící " to
            "A custom model applies to text, frames and All in One. References, Dialogue ",
        "scéna a Režisér s referencemi jedou dál na modelu z workflow." to
            "and reference-driven runs keep the workflow model.",
        "Nepodařilo se připravit soubor pro fotku" to "Could not prepare a file for the photo",
        "V telefonu není žádná aplikace fotoaparátu" to "There is no camera app on this phone",

        // -------------------------------------------------- scéna All in One
        "Co se má udělat" to "What to do",
        "Šablonu si appka stáhne z ComfyUI, z balíku All in One" to
            "The app downloads the template from ComfyUI, from the All in One pack",
        "Popis scény" to "Scene description",
        "Anglicky to model chápe nejlíp, ale rozumí i česky" to
            "The model understands English best, but other languages work too",
        "Co se má dít dál" to "What happens next",
        "Popiš, co se má stát po konci původního videa" to
            "Describe what should happen after the original video ends",
        "Popis postavy (nepovinné)" to "Character description (optional)",
        "Co z fotek držet (obličej, účes, oblečení) a co vynechat" to
            "What to keep from the photos (face, hair, clothes) and what to skip",
        "✨ Vylepšit prompt" to "✨ Improve the prompt",
        "Přepisuji…" to "Rewriting…",
        "Podkresová hudba" to "Background music",
        "AI k videu vymyslí i soundtrack" to "The AI also invents a soundtrack",
        "Jen zvuky scény — bez vymyšlené hudby" to "Scene sounds only — no invented music",
        "Vrátit původní" to "Restore the original",
        "Napiš klidně česky pár slov — AI na tvém počítači z nich " to
            "Write a few words in any language — an AI on your PC turns them ",
        "složí plný anglický prompt (záběry, časování, zvuk)." to
            "into a full English prompt (shots, timing, sound).",
        "Značka říká modelu, kterou fotku myslíš — klidně si ji " to
            "The tag tells the model which photo you mean — feel free to ",
        "přesuň doprostřed věty („The woman from <Picture 1>…“)." to
            "move it into the sentence („The woman from <Picture 1>…“).",
        "Délka videa" to "Video length",
        "O kolik prodloužit" to "How much to extend",
        "Model počítá po blocích 17 snímků, délka se proto zaokrouhlí" to
            "The model works in blocks of 17 frames, so the length is rounded",
        "Sekundy" to "Seconds",
        "Snímky videa" to "Video frames",
        "První snímek určuje, čím video začne; poslední, kam dojede" to
            "The first frame sets where the video starts; the last, where it ends",
        "První snímek" to "First frame",
        "Poslední snímek" to "Last frame",
        "Zadat i poslední snímek" to "Set the last frame too",
        "Video půjde od prvního snímku k poslednímu" to
            "The video will travel from the first frame to the last",
        "Video začne prvním snímkem a dál se rozvine samo" to
            "The video starts with the first frame and develops on its own",
        "Reference" to "References",
        "Podle nich model drží podobu postav, věcí i stylu" to
            "They keep the look of characters, objects and style",
        "Přidat referenci" to "Add a reference",
        "Odebrat obrázek" to "Remove the image",
        "Referenční video" to "Reference video",
        "Nepovinné – z videa se bere pohyb, podobu drží fotky" to
            "Optional — motion comes from the video, the look from the photos",
        "Použít i zvuk z videa" to "Use the audio from the video too",
        "Zvuk z videa se zahodí, model si vytvoří vlastní" to
            "The video's audio is dropped, the model makes its own",
        "Klíčové snímky" to "Keyframes",
        "Přidat klíčový snímek" to "Add a keyframe",
        "Obrázek se připne na konkrétní snímek a video jimi projde po řadě" to
            "Each image is pinned to a frame and the video passes through them in order",
        "Video, které se má prodloužit" to "The video to extend",
        "Naváže se na jeho konec – stejné rámování, žádný střih" to
            "It continues from its end — same framing, no cut",
        "Video, které se má zvětšit" to "The video to upscale",
        "Nic se negeneruje znovu, jen se dopočítají detaily" to
            "Nothing is generated again, only details are filled in",
        "Zvětšovač" to "Upscaler",
        "SeedVR2 dopočítává detaily, RTX jen rychle zvětší" to
            "SeedVR2 reconstructs detail, RTX just upscales fast",
        "Kratší hrana výsledku" to "Shorter edge of the result",
        "Kolikrát zvětšit" to "Upscale factor",
        "Jede na grafice NVIDIA přes ovladač, model se nespouští." to
            "Runs on the NVIDIA GPU through the driver, no model is loaded.",
        "Čím víc, tím déle to trvá a tím víc paměti to sní." to
            "The more, the longer it takes and the more memory it eats.",
        "Fotky postavy" to "Character photos",
        "První fotka určuje styl, další doplňují podobu" to
            "The first photo sets the style, the others fill in the look",
        "Podoba listu" to "Sheet layout",
        "Vzorkování a kameru řídí šablona balíku" to
            "Sampling and camera are driven by the pack's template",
        "Počet pohledů" to "Number of views",
        "6 – plná otočka" to "6 — full turnaround",
        "4 – rychlejší" to "4 — faster",
        "Fotorealistický styl" to "Photorealistic style",
        "Neretušovaná studiová fotografie, bez make-upu" to
            "Unretouched studio photography, no make-up",
        "Styl se převezme z první fotky" to "The style is taken from the first photo",

        // ------------------------------------- režimy a hlášky karty All in One
        "Z textu" to "From text",
        "Jen z popisu, nic se nepřikládá" to "From a description alone, nothing attached",
        "Z obrázku" to "From an image",
        "Rozhýbe fotku, volitelně i k poslednímu snímku" to
            "Animates a photo, optionally towards a last frame",
        "Drží podobu podle fotek a videí" to "Keeps the look from photos and videos",
        "Obrázky připnuté na konkrétní snímky" to "Images pinned to specific frames",
        "Prodloužit" to "Extend",
        "Naváže na konec hotového videa" to "Continues from the end of a finished video",
        "Zvětší hotové video, negeneruje nic nového" to
            "Upscales a finished video, generates nothing new",
        "List postavy" to "Character sheet",
        "Z fotek složí otočný list postavy" to
            "Builds a turnaround character sheet from photos",
        "Kvalitnější, ale pomalé – dopočítává detaily" to
            "Better quality but slow — it reconstructs detail",
        "Rychlé, jede na grafice NVIDIA" to "Fast, runs on the NVIDIA GPU",
        "Napiš, co se má ve videu dít." to "Write what should happen in the video.",
        "Napiš, co se má dít v prodloužení." to "Write what should happen in the extension.",
        "Vyber snímek, ze kterého se má vyjít." to "Pick the frame to start from.",
        "Vyber poslední snímek, nebo ho vypni." to "Pick the last frame, or switch it off.",
        "Vyber video, které se má prodloužit." to "Pick the video to extend.",
        "Vyber video, které se má zvětšit." to "Pick the video to upscale.",
        "Přidej aspoň jednu referenci – obrázek nebo video." to
            "Add at least one reference — an image or a video.",
        "Přidej aspoň jeden klíčový snímek." to "Add at least one keyframe.",
        "Přidej aspoň jednu fotku postavy, ze které má list vzniknout." to
            "Add at least one photo of the character for the sheet.",
        "Klíčový snímek je za koncem videa – zkrať pozici, nebo prodluž video." to
            "A keyframe sits past the end of the video — move it or make the video longer.",

        // ------------------------------------------------------ nastavení
        "Server ComfyUI" to "ComfyUI server",
        "Adresa počítače, na kterém běží generování" to
            "Address of the PC that does the generating",
        "Adresa se ukládá sama při psaní – tlačítko níž ji jen otestuje." to
            "The address saves itself as you type — the button below only tests it.",
        "Rychlá volba" to "Quick pick",
        "Uložit a otestovat" to "Save and test",
        "Uložit" to "Save",
        "ComfyUI neodpovídá." to "ComfyUI is not responding.",
        "Grafická karta" to "Graphics card",
        "Co serveru chybí" to "What the server is missing",
        "Nody a modely, které karty appky potřebují" to
            "Nodes and models the app's cards need",
        "Porovná workflow appky s tím, co tvůj ComfyUI opravdu nabízí — " to
            "Compares the app's workflows with what your ComfyUI really offers — ",
        "vypíše chybějící custom nody a modely. Higgs Audio je " to
            "it lists missing custom nodes and models. Higgs Audio is ",
        "volitelný, bez něj nefunguje jen namlouvání replik." to
            "optional; without it only voicing lines stops working.",
        "Zkontrolovat server" to "Check server",
        "Porovnávám…" to "Comparing…",
        "  Čtu definice uzlů ze serveru…" to "  Reading node definitions from the server…",
        "Zkopírovat seznam" to "Copy list",
        "Pošli si seznam do počítače (e-mailem, chatem) a stahuj " to
            "Send the list to your PC (e-mail, chat) and download ",
        "podle odkazů — nemusíš nic přepisovat." to "by the links — nothing to retype.",
        "Namlouvání replik pro kartu Mluvící scéna" to
            "Voicing lines for the Dialogue card",
        "Prázdné pole = stejný počítač jako ComfyUI, port 7860. " to
            "Empty field = the same PC as ComfyUI, port 7860. ",
        "Higgs se zapíná sám, když necháš namluvit repliku, a před " to
            "Higgs starts itself when you voice a line and shuts down before ",
        "generováním videa se zase vypne – na grafiku se oba modely nevejdou." to
            "video generation — both models do not fit on the GPU.",
        "Přístupový kód (jen když si ho Higgs vyžádá)" to
            "Access code (only if Higgs asks for one)",
        "Ukládat vše do telefonu" to "Save everything to the phone",
        "Normálně vypnuté – stahuješ si jen to, co chceš" to
            "Off by default — you download only what you want",
        "Zapnuto: každé hotové video se rovnou uloží do Filmy/H3 Video. " to
            "On: every finished video goes straight to Movies/H3 Video. ",
        "Hodí se, když chceš mít úplně všechno v telefonu." to
            "Handy when you want absolutely everything on the phone.",
        "Videa zůstanou v Galerii aplikace a do telefonu se uloží až tehdy, " to
            "Videos stay in the app gallery and reach the phone only when ",
        "Jen pozor, že odinstalace aplikace neuložená videa smaže." to
            "Note that uninstalling the app deletes videos you never saved.",
        "Doplnit chybějící videa do galerie telefonu" to
            "Copy missing videos into the phone gallery",
        "Nic nechybělo – všechna videa už v telefonu jsou." to
            "Nothing was missing — every video is already on the phone.",
        "Aktualizace" to "Updates",
        "Zkontrolovat aktualizace" to "Check for updates",
        "Máš nejnovější verzi." to "You are on the latest version.",
        "  Hledám novou verzi…" to "  Looking for a new version…",
        "Stáhnout a nainstalovat" to "Download and install",
        "Staženo. Android se teď zeptá na potvrzení instalace." to
            "Downloaded. Android will now ask you to confirm the installation.",
        "Který MiniMax H3 se použije pro text a snímky" to
            "Which MiniMax H3 is used for text and frames",
        "O aplikaci" to "About",
        "Appka naposledy spadla" to "The app crashed last time",
        "Tohle pošli vývojáři, je v tom příčina" to
            "Send this to the developer — it contains the cause",
        "Zahodit výpis" to "Discard the report",
        // --------------------------------------------------- časová osa
        "ČASOVÁ OSA" to "TIMELINE",
        "Styl celého filmu" to "Style of the whole film",
        "Přidá se ke každému segmentu – drží jednotný vzhled, světlo a barvy." to
            "Added to every segment — it keeps the look, light and colours consistent.",
        "Ťukni na klip a uprav ho dole. Délku táhni za okraj klipu." to
            "Tap a clip and edit it below. Drag the clip edge to change its length.",
        "Přidat segment" to "Add a segment",
        "Začít snímkem" to "Start from a frame",
        "Vyměnit snímek" to "Replace the frame",
        "Navázat na předchozí" to "Continue from the previous one",
        "Nebo táhni za pravý okraj klipu v ose." to
            "Or drag the right edge of the clip in the timeline.",
        "Přegenerovat jen tento segment" to "Regenerate this segment only",
        "Segment čeká na snímek, ze kterého má vyjít." to
            "The segment is waiting for a frame to start from.",
        "Muž jde po molu, kamera ho sleduje zezadu…" to
            "A man walks along a pier, the camera follows him from behind…",
        "Pokračuje dál, kamera se stáčí k moři…" to
            "He keeps walking, the camera turns towards the sea…",
        "Generování už běží" to "A run is already in progress",
        "Napiš, co se má v prvním segmentu dít." to
            "Write what should happen in the first segment.",
        "První segment nemá na co navázat – vyber mu snímek." to
            "The first segment has nothing to continue from — pick a frame for it.",
        "Segment bez popisu model neumí natočit – doplň ho, nebo ho odeber." to
            "The model cannot shoot a segment without a description — add one or remove it.",
        "Z prvního snímku" to "From the first frame",

        // -------------------------------------------------- mluvící scéna
        "Scéna" to "Scene",
        "Postavy" to "Characters",
        "Dialog" to "Dialogue",
        "Fotka" to "Photo",
        "Vyfotit" to "Take a photo",
        "Vybrat hlas" to "Pick a voice",
        "Namluvit" to "Voice it",
        "Namluvit repliku" to "Voice this line",
        "Namluvit znovu" to "Voice it again",
        "Zastavit" to "Stop",
        "Odebrat postavu" to "Remove the character",
        "Odebrat repliku" to "Remove the line",
        "Prompt pro model" to "Prompt for the model",
        "Postava %d" to "Character %d",
        "%d. replika" to "Line %d",
        "Namluvit všechny (%d)" to "Voice all (%d)",
        "Dialog trvá %s s – délka videa se podle něj nastavila sama." to
            "The dialogue runs %s s — the video length was set from it automatically.",
        "Přidej první postavě fotku." to "Add a photo to the first character.",
        "Napiš aspoň jednu repliku." to "Write at least one line.",
        "Repliku říká postava bez fotky – doplň jí fotku." to
            "A line is spoken by a character with no photo — add one.",
        "Postava, která mluví, potřebuje vybraný hlas." to
            "A speaking character needs a voice selected.",
        "Nech repliky namluvit." to "Have the lines voiced.",
        "Replika se změnila – nech ji namluvit znovu." to
            "The line changed — have it voiced again.",
        "Prompt je prázdný." to "The prompt is empty.",
        "Napiš, co má na obrázku být." to "Write what should be in the picture.",
        "Kde se to odehrává a jak se chová kamera – nepovinné" to
            "Where it takes place and how the camera behaves — optional",
        "Kavárna, měkké odpolední světlo, kamera pomalu najíždí…" to
            "A café, soft afternoon light, the camera slowly pushes in…",
        "Fotka drží podobu, hlas namluví repliky. Každá postava může mluvit vícekrát." to
            "The photo keeps the look, the voice reads the lines. Each character can speak several times.",
        "Přidat postavu" to "Add a character",
        "muž v obleku (nepovinné)" to "a man in a suit (optional)",
        "Repliky jdou po sobě v tomto pořadí. U každé vyber, kdo ji říká." to
            "Lines play in this order. For each one pick who says it.",
        "Přidat repliku" to "Add a line",
        "Co má říct…" to "What they should say…",
        "Ťukni pro namluvení" to "Tap to voice it",
        "Přehrát repliku" to "Play the line",
        "Text se změnil – namluv znovu" to "The text changed — voice it again",
        "Hlas je hotový (%.1f s)" to "The voice is ready (%.1f s)",
        "Postava nemá vybraný hlas" to "This character has no voice selected",
        "Zapínám Higgs Audio na počítači…" to "Starting Higgs Audio on the PC…",
        "Načítám hlasy z počítače…" to "Loading voices from the PC…",
        "Nahrát vlastní hlas mikrofonem" to "Record your own voice with the microphone",
        "Zastavit nahrávání" to "Stop recording",
        "Mluv souvisle, ideálně 5–30 sekund." to "Speak continuously, ideally 5–30 seconds.",
        "Naklonovat ze zvukového souboru" to "Clone from an audio file",
        "5–30 s čisté řeči; podle ní Higgs hlas naklonuje." to
            "5–30 s of clean speech; Higgs clones the voice from it.",
        "vlastní nahrávka" to "own recording",
        "Vložit vlastní zvuk" to "Use your own audio",
        "Vyměnit za vlastní zvuk" to "Replace with your own audio",
        "Složit prompt znovu podle dialogu" to "Rebuild the prompt from the dialogue",
        "Skládá se sám z postav a replik. Můžeš do něj sáhnout." to
            "It is built from the characters and lines. You can edit it.",
        "Doplní se, jakmile přidáš fotku a repliku" to
            "It fills in once you add a photo and a line",

        // ------------------------------------------------------- průběh
        "Čtení zadání" to "Reading the brief",
        "Fronta a modely" to "Queue and models",
        "%s celkem · %s" to "%s total · %s",
        "%.0f s / krok" to "%.0f s / step",
        "z %.1f MB" to "of %.1f MB",
        "Hotovo" to "Done",
        "Víc než %d repliky se do jednoho videa nevejdou – model bere jen tři zvukové reference." to
            "More than %d lines will not fit into one video — the model takes only three audio references.",
        "Načítám model" to "Loading the model",
        "Model se nahrává do grafické karty, chvíli to trvá." to
            "The model is loading into the GPU, this takes a moment.",
        "Dokončení a přenos do aplikace" to "Finishing and transfer to the app",
        "Náhled se objeví, až model začne kreslit" to
            "The preview appears once the model starts drawing",
        "Náhled se objeví, jakmile model vykreslí první snímek" to
            "The preview appears as soon as the model renders the first frame",
        "Zbývá" to "Remaining",
        "Přeneseno" to "Transferred",
        "počítám" to "estimating",
        "Telefon můžeš zamknout, generování běží na počítači dál." to
            "You can lock the phone — generation keeps running on the PC.",

        // -------------------------------------------------------- hudba
        "ACE-Step 1.5 — celá píseň za pár desítek sekund" to
            "ACE-Step 1.5 — a whole song in a few dozen seconds",
        "Popiš styl skladby — žánr, nástroje, náladu." to
            "Describe the style — genre, instruments, mood.",
        "Žánr, nástroje, nálada, hlas zpěváka…" to
            "Genre, instruments, mood, the singer's voice…",
        "Text písně (nepovinný)" to "Lyrics (optional)",
        "Sloky a refrén; prázdné = instrumentálka" to
            "Verses and chorus; empty = instrumental",
        "Bez textu písně vyjde instrumentálka. Text piš po slokách, " to
            "With no lyrics you get an instrumental. Write the lyrics verse by verse, ",
        "klidně česky." to "in any language.",
        "Délka a jazyk" to "Length and language",
        "Jazyk zpěvu" to "Singing language",
        "Hudební detaily" to "Musical details",
        "Tónina" to "Key",

        // ------------------------------------------------ úprava obrázku
        "Fotka k úpravě" to "Photo to edit",
        "Upravovaná fotka" to "The photo being edited",
        "Z ní se bere podoba i scéna" to "The look and the scene come from it",
        "Osoba navíc (nepovinné)" to "Extra person (optional)",
        "Co se má změnit" to "What should change",
        "Napiš to jednoduše, běžnou větou" to "Write it simply, in a plain sentence",
        "Dej jí červenou bundu a přesaď je na zasněženou horskou cestu" to
            "Give her a red jacket and move them to a snowy mountain road",
        "Nastavení úpravy" to "Edit settings",
        "Kolem 1 MP je u tohohle modelu nejjistější" to
            "Around 1 MP is the safest for this model",
        "Nad 1 MP se u dvou lidí začíná rozpadat podoba." to
            "Above 1 MP the likeness starts to fall apart with two people.",
        "Vidění předlohy" to "Reference vision",
        "Víc = věrnější podoba, míň = poslušnější úprava. Na lidi dej 1024." to
            "More = closer likeness, less = more obedient edit. Use 1024 for people.",
        "Věrnost předloze" to "Fidelity to the reference",
        "Síla úpravy" to "Edit strength",
        "Kompromis mezi poslušností zadání a věrností obličeje" to
            "A trade-off between following the brief and keeping the face",
        "1,00 je vypnuto. Na věrné obličeje zkus 1,5–2." to
            "1.00 is off. For faithful faces try 1.5–2.",
        "Vyber fotku, kterou chceš upravit." to "Pick the photo you want to edit.",
        "Napiš, co se má na fotce změnit." to "Write what should change in the photo.",
        "Nad zhruba 1 MP se u tohohle modelu začíná obsah zdvojovat. " to
            "Above roughly 1 MP this model starts duplicating content. ",
        "Radši uprav v menším a zvětši potom v kartě All in One." to
            "Better to edit smaller and upscale afterwards on the All in One card.",
        "U dvou lidí drž rozlišení kolem 1 MP, výš se podoba rozpadá." to
            "With two people keep the resolution around 1 MP; higher and the likeness breaks.",
        "S nízkým viděním předlohy podoba lidí ujíždí — pro věrné obličeje " to
            "With low reference vision the likeness drifts — for faithful faces ",
        "autor doporučuje 1024. Nízké hodnoty se hodí jen na tvrdohlavé změny scény." to
            "the author recommends 1024. Low values only help with stubborn scene changes.",
        "Mazání věcí z obrázku tenhle model spolehlivě neumí — je to jeho " to
            "This model cannot reliably delete things from a picture — that is its ",
        "nejslabší úloha. Zkus místo mazání popsat, co má být na tom místě místo toho." to
            "weakest task. Instead of deleting, describe what should be there instead.",
        "Obě předlohy jdou do modelu naráz: první je scéna, druhá vkládaná osoba. " to
            "Both references go into the model at once: the first is the scene, the second the person being inserted. ",

        // ---------------------------------------------- tvář, oprava, zvětšit
        "Fotka, kde se mění tvář" to "The photo whose face changes",
        "Vyber fotku a pak prstem začmárej obličej, který se má vyměnit" to
            "Pick a photo, then scribble over the face to be swapped",
        "Cílová fotka" to "Target photo",
        "Maska je namalovaná — klepnutím na štětec ji předěláš" to
            "The mask is painted — tap the brush to redo it",
        "Nová tvář" to "New face",
        "Nejlíp ostrá fotka zepředu" to "A sharp front-facing photo works best",
        "Vybrat tvář" to "Pick a face",
        "Začmárej obličej, který se vymění" to "Scribble over the face to be swapped",
        "Klidně s přesahem přes okraje tváře — přechod se změkčí sám. " to
            "Feel free to overlap the edges of the face — the transition softens itself. ",
        "Dvěma prsty přiblížíš na detaily." to "Pinch with two fingers to zoom in on details.",
        "Štětec" to "Brush",
        "Krok zpět" to "Undo",
        "Smazat vše" to "Clear all",
        "Nejdřív začmárej obličej" to "Scribble over the face first",
        "Hotovo — použít masku" to "Done — use this mask",
        "Zavřít bez uložení" to "Close without saving",
        "Fotka k opravě" to "Photo to restore",
        "Stará nebo poškozená fotka" to "An old or damaged photo",
        "Škrábance, prach, vybledlé barvy i trhliny — appka opraví vše naráz" to
            "Scratches, dust, faded colours and tears — the app fixes it all at once",
        "Fotka ke zvětšení" to "Photo to upscale",
        "Vezme se v plném rozlišení, bez překódování" to
            "Taken at full resolution, without re-encoding",
        "Velikost zvětšení" to "Upscale size",
        "Fotka se rozdělí na dlaždice, každá se zvětší na 3200 px a slepí se" to
            "The photo is split into tiles, each upscaled to 3200 px and merged back",
        "Víc dlaždic = větší výsledek, ale úměrně delší běh. 2×2 je vyladěné výchozí." to
            "More tiles = a bigger result but a proportionally longer run. 2×2 is the tuned default.",

        // -------------------------------------------- aktualizace a notifikace
        "Později" to "Later",
        "Staženo" to "Downloaded",
        "Android se teď zeptá na potvrzení instalace." to
            "Android will now ask you to confirm the installation.",
        "Android potřebuje povolit instalaci z této aplikace." to
            "Android needs permission to install from this app.",
        "Průběh generování" to "Generation progress",
        "Živý průběh běžícího generování" to "Live progress of the running job",
        "Hotová videa" to "Finished videos",
        "Upozornění, když je video hotové" to "A notification when the video is done",
        "Připravuji…" to "Preparing…",
        "Generování se nepovedlo" to "Generation failed",

        // Věty s hodnotou — překládá se celá věta i s %s/%d, aby nevznikla
        // půl česká, půl anglická.
        "Přesně plátno, na kterém model vznikl (%s). Odsud je výsledek nejjistější." to
            "Exactly the canvas the model was trained on (%s). Results are the safest bet here.",
        "O %d %% víc bodů než plátno modelu (%s). Jde to, ale bude to déle trvat a detaily bývají měkčí. Ostřejší HD spíš vyjde z nativu a karty All in One → Zvětšit." to
            "%d %% more pixels than the model's canvas (%s). It works, but it takes longer and details tend to be softer. Sharper HD usually comes from the native size plus All in One → Upscale.",
        "Pod plátnem modelu (%s) – rychlejší, ale měkčí obraz a méně přesné tváře." to
            "Below the model's canvas (%s) — faster, but a softer picture and less accurate faces.",
        "Prakticky plátno modelu (%s) – tenhle rozdíl na výsledku nepoznáš." to
            "Practically the model's canvas (%s) — you will not see this difference.",
        " (čeká %d)" to " (%d waiting)",
        "Vyjde %d×%d px. Z výsledku se dá rovnou pokračovat do Úpravy obrázku nebo do Zvětšit." to
            "Comes out at %d×%d px. From the result you can go straight to Image edit or Upscale.",
        "Uloženo %d videí do Filmy/H3 Video." to "Saved %d videos to Movies/H3 Video.",
        "Verze %s (sestavení %d)" to "Version %s (build %d)",
        "Vygenerovat video" to "Generate video",
        "Vygenerovat skladbu" to "Generate a song",
        "Opravit fotku" to "Restore the photo",

        // ------------------------------------------------ domalovat
        "Domalovat" to "Inpaint",
        "Začmáráš místo, napíšeš co tam má být, přepíše se jen ono" to
            "Scribble over a spot, type what belongs there, only that gets repainted",
        "Fotka, do které se maluje" to "The photo you paint into",
        "Fotka k domalování" to "Photo to inpaint",
        "Vyber fotku a pak prstem začmárej místo, které se má přemalovat" to
            "Pick a photo, then scribble over the spot to repaint",
        "Co má na tom místě být" to "What belongs in that spot",
        "Popiš to jako výsledný obraz, ne jako příkaz" to
            "Describe the finished picture, not a command",
        "dřevěná lavička pod stromem, dopolední světlo" to
            "a wooden bench under a tree, late morning light",
        "Čím domalovat" to "What to inpaint with",
        "Když se výsledek nepovede, zkus druhý model — každý kreslí jinak" to
            "If a result disappoints, try the other model — they paint differently",
        "Začmárej místo, které se přemaluje" to "Scribble over the spot to repaint",
        "Maluj s malým přesahem — okraje se prolnou samy. " to
            "Paint slightly past the edges — the transition blends itself. ",
        "Nejdřív začmárej místo" to "Scribble over a spot first",
        "Domalovat do masky" to "Inpaint the mask",
        "Vyber fotku, do které se má domalovávat." to "Pick a photo to inpaint into.",
        "Začmárej prstem místo, které se má přemalovat." to
            "Scribble over the spot that should be repainted.",
        "Napiš, co má na zamaskovaném místě být." to "Type what should be in the masked spot.",
        "Popiš celé místo i s okolím („muž v černé bundě na lavičce“), ne jen samotnou věc — model píše obraz, ne příkaz." to
            "Describe the whole spot with its surroundings (\"a man in a black jacket on a bench\"), not just the object — the model paints a picture, it does not follow orders.",
        "Načítám model na domalování" to "Loading the inpainting model",
        "Model na domalování + textový enkodér" to "Inpainting model + text encoder",
        "Vyřezávám okolí masky" to "Cropping around the mask",
        "Domalovávám do masky" to "Inpainting the mask",
        "Vlepuji domalovaný kus zpět" to "Stitching the repainted piece back",
        "Domalování do masky" to "Inpainting",
        "Odebrat" to "Remove",
        "Vybrat fotku" to "Pick a photo",
        "Malovat masku" to "Paint the mask",
        "Nejdřív napiš aspoň pár slov o tom, co chceš." to
            "Type at least a few words about what you want first.",
        "🌐 Přeložit do angličtiny" to "🌐 Translate to English",
        "🌐 Přeložit" to "🌐 Translate",
        "Překládám…" to "Translating…",
        "Překlad se nepovedl." to "The translation failed.",
        "Nejdřív něco napiš, ať je co překládat." to
            "Type something first, so there is text to translate.",
        "Napiš zadání česky a nech ho přeložit — obsah zůstane, jen bude anglicky." to
            "Write the prompt in your own language and have it translated — same content, just in English.",
        "Domaluje do masky to, co popíšeš — na tohle je trénovaný" to
            "Paints into the mask whatever you describe — that is what it was trained for",
        "Rychlejší (4 kroky), ale poslouchá příkazy — „dej mu plnovous“" to
            "Faster (4 steps) but takes orders — \"give him a full beard\"",
        "Klein poslouchá příkazy — napiš, co se s tím místem má stát" to
            "Klein takes orders — write what should happen to that spot",
        "posaď ho na dřevěnou lavičku pod stromem" to "sit him on a wooden bench under a tree",
        "Schovat navigační tlačítka" to "Hide the navigation buttons",
        "Víc místa na obrazovce; vytáhneš je přejetím zespodu" to
            "More room on screen; swipe up from the bottom to get them back",
        "Lišta s tlačítky je pryč, dokud ji nepotřebuješ — přejeď prstem od spodního okraje a na chvíli se ukáže." to
            "The button bar stays hidden until you need it — swipe up from the bottom edge and it appears for a moment.",
        "Lišta s tlačítky zůstává vidět pořád." to "The button bar stays visible all the time.",
        "Model a doladění" to "Model and tuning",
        "Doplňková LoRA" to "Extra LoRA",
        "Pomůže tam, kde model sám tápe — třeba na anatomii" to
            "Helps where the model is vague on its own — anatomy, for instance",
        "Na serveru není žádná LoRA pro tenhle model" to "The server has no LoRA for this model",
        "Žádná" to "None",
        "Síla LoRA" to "LoRA strength",
        "Kolem 0,8–1,0 bývá nejjistější; víc už deformuje okolí." to
            "Around 0.8–1.0 is the safe range; more starts deforming the surroundings.",
        "Síla přemalování" to "Repaint strength",
        "Kolik z původního místa se smí zahodit" to
            "How much of the original spot may be discarded",
        "1,00 = pod maskou vzniká všechno znovu. Na dokreslení detailu (ne výměnu obsahu) zkus 0,50–0,70 — tvar a póza zůstanou." to
            "1.00 = everything under the mask is generated from scratch. To add detail rather than replace content, try 0.50–0.70 — shape and pose stay.",
        "Paměť grafiky" to "GPU memory",
        "Když je plná, generování se táhne" to "When it is full, generating drags on",
        "Zjisti, kolik je na grafice volno, a případně uvolni, co si drží ComfyUI." to
            "Check how much room the GPU has and, if needed, release what ComfyUI is holding.",
        "Zjistit stav" to "Check",
        "Zjišťuji…" to "Checking…",
        "Uvolnit paměť" to "Free memory",
        "Uvolní se jen modely, které drží ComfyUI. Cizí programy appka nevypíná." to
            "Only models held by ComfyUI are released. The app never shuts down other programs.",
        "Volných %.1f z %.1f GB." to "%.1f of %.1f GB free.",
        "Grafika je volná, generování poběží naplno." to
            "The GPU is free, generating will run at full speed.",
        "Na obrázky to stačí; u videa se může model dohrávat z RAM." to
            "Enough for images; for video the model may have to stream from RAM.",
        "Málo místa — něco jiného na počítači grafiku drží. Zavři hru nebo prohlížeč a zkus uvolnit znovu." to
            "Not much room — something else on the PC is holding the GPU. Close a game or browser and try releasing again.",
        "Server o paměti grafiky nic neřekl." to "The server reported nothing about GPU memory.",
        "Nepodařilo se zeptat serveru na paměť grafiky." to
            "Could not ask the server about GPU memory.",
        "Jazyk" to "Language",
        "Podle telefonu" to "System",
        "Čeština" to "Czech",
        "Angličtina" to "English",
        "Jazyk rozhraní; nepřeložené části zůstanou česky." to
            "Interface language; untranslated parts stay in Czech.",

        // ------------------------------------ Zvětšit: metoda DLSS 5 (3.02)
        "Čím zvětšit" to "How to upscale",
        "Dvě různé cesty — jedna dokresluje, druhá rekonstruuje" to
            "Two different routes — one invents detail, the other reconstructs it",
        "SeedVR2 (gigapixel)" to "SeedVR2 (gigapixel)",
        "DLSS 5 (rychlé)" to "DLSS 5 (fast)",
        "Difuzní model dokreslí detaily, které v předloze nejsou. Minuty až desítky minut." to
            "A diffusion model invents detail the source never had. Minutes to tens of minutes.",
        "NVIDIA Neural Rendering rekonstruuje, co ve fotce je. Sekundy, ale nic si nevymýšlí." to
            "NVIDIA Neural Rendering reconstructs what the photo holds. Seconds, but it invents nothing.",
        "Nastavení DLSS 5" to "DLSS 5 settings",
        "Neural Rendering na grafické kartě, výsledek za pár sekund" to
            "Neural Rendering on the graphics card, done in seconds",
        "Zvětšení" to "Upscaling",
        "1× jen doostřit" to "1× sharpen only",
        "Styl" to "Style",
        "Výchozí" to "Default",
        "Přirozený" to "Natural",
        "Filmový" to "Cinematic",
        "Nad 1.00 už runtime nic nepřidá; níž se výsledek přimíchává zpátky k předloze." to
            "Above 1.00 the runtime adds nothing more; below it the result blends back to the source.",
        "Rekonstruovat pleť" to "Reconstruct skin",
        "Model si sám najde kůži a dopočítá póry. Na fotky bez lidí to vypni." to
            "The model finds skin on its own and rebuilds pores. Turn it off for photos without people.",
        "Zvětšení 3× je „Ultra Performance\" — DLSS má na rekonstrukci nejmíň podkladu a výsledek bývá měkčí než při 2×." to
            "3× is \"Ultra Performance\" — DLSS has the least to reconstruct from and the result tends to be softer than 2×.",
        "Doostření DLSS 5" to "DLSS 5 sharpening",
        "NVIDIA Neural Rendering, žádný difuzní model" to
            "NVIDIA Neural Rendering, no diffusion model",
        "Rekonstrukce na grafické kartě, jde to rychle" to
            "Reconstruction on the graphics card, it goes fast",

        // ------------------------------------ Obrázek: výběr modelu (3.02)
        "Z-Image Turbo" to "Z-Image Turbo",
        "Z-Image Base" to "Z-Image Base",
        "FLUX.2 Klein 9B" to "FLUX.2 Klein 9B",
        "ERNIE Image Turbo" to "ERNIE Image Turbo",
        "Nejrychlejší. Fotorealismus za pár sekund, na text v obraze slabší." to
            "The fastest. Photorealism in seconds, weaker at text inside the image.",
        "NSFW Photorealistic v6.1 — nic neodmítá. LoRA s ním není potřeba." to
            "NSFW Photorealistic v6.1 — refuses nothing. No LoRA needed with it.",
        "Nedestilovaný základ. Poslouchá zadání líp než Turbo, ale trvá to násobně dýl." to
            "The undistilled base. Follows the brief better than Turbo, but takes several times longer.",
        "Nejlíp drží složité zadání a text v obraze. Velký model, načítá se dýl." to
            "Best at complex briefs and text inside the image. A big model, slower to load.",
        "Baidu ERNIE na architektuře FLUX.2. Jiný rukopis než Z-Image." to
            "Baidu ERNIE on the FLUX.2 architecture. A different handwriting than Z-Image.",
        "%d kroků" to "%d steps",

        // ------------------------------- Dlouhé video a přemalování (3.03)
        "Dlouhé video" to "Long video",
        "Dlouhé" to "Long",
        "Až šest navazujících úseků najednou, každý s vlastním zadáním" to
            "Up to six chained sections in one run, each with its own brief",
        "Odkud začít" to "Where to start",
        "Buď se naváže na hotové video, nebo se první záběr vyrobí" to
            "Either continue a finished video, or generate the first shot",
        "Navázat na video" to "Continue a video",
        "Začít od nuly" to "Start from scratch",
        "Vezme hotové video a plynule na jeho konec naváže další úseky" to
            "Takes a finished video and seamlessly chains more sections onto its end",
        "První záběr vznikne z popisu a další úseky na něj navážou" to
            "The first shot comes from your brief and the rest chain onto it",
        "Video, na které se navazuje" to "Video being continued",
        "Jeho konec se použije jako kontext prvního úseku" to
            "Its ending becomes the context for section one",
        "První záběr" to "First shot",
        "Vznikne z popisu a všechny úseky pak navazují na něj" to
            "Generated from the brief; every section chains onto it",
        "Rychlý první záběr" to "Fast first shot",
        "Úseky" to "Sections",
        "Každý je vlastní záběr — navazují na sebe v tomhle pořadí" to
            "Each is its own shot — they chain in this order",
        "Úsek %d" to "Section %d",
        "Odebrat úsek" to "Remove section",
        "Přidat úsek (max %d)" to "Add a section (max %d)",
        "Nepovinné — drží podobu postav a věcí ve všech úsecích" to
            "Optional — keeps people and things looking the same across sections",
        "Vybrat video z galerie" to "Pick a video from the gallery",
        "Vyber video, na které se má navázat." to "Pick the video to continue.",
        "Napiš, co má být v prvním záběru." to "Describe the first shot.",
        "Vyplň zadání aspoň u jednoho úseku." to "Fill in at least one section.",
        "Úseky bez zadání se přeskočí — do videa se nedostanou." to
            "Sections with no brief are skipped — they never reach the video.",
        "Generuji úseky" to "Generating sections",
        "Slepuji úseky do videa" to "Joining the sections into one video",
        "Generování úseků" to "Generating sections",
        "Načítám MiniMax H3" to "Loading MiniMax H3",
        "Připravuji navázání" to "Preparing the continuation",
        "MiniMax H3 (referenční váhy) + enkodér" to "MiniMax H3 (reference weights) + encoder",
        "Každý úsek je vlastní vzorkování, jede se popořadě" to
            "Each section is its own sampling pass, run in order",

        // ------------------------------- All in One → Přemalovat ve videu
        "Přemalovat ve videu" to "Repaint in video",
        "Vymění sledovaný kus záběru, zbytek nechá" to
            "Replaces the tracked part of the shot and leaves the rest",
        "Video, ve kterém se má přemalovávat" to "Video to repaint in",
        "Zpracuje se úsek od začátku, zbytek záběru i zvuk zůstanou" to
            "A stretch from the start is processed; the rest of the shot and the audio stay",
        "Co ve videu sledovat" to "What to track in the video",
        "Kolik objektů" to "How many objects",
        "Čím to nahradit" to "What to replace it with",
        "Fotky toho, co se má na sledovaném místě objevit" to
            "Photos of what should appear in the tracked spot",
        "Vyber video, ve kterém se má přemalovávat." to "Pick the video to repaint in.",
        "Přidej aspoň jednu fotku toho, čím se to má nahradit." to
            "Add at least one photo of the replacement.",

        // ------------------------------------------ 3D model, TRELLIS.2 (3.04)
        "3D model" to "3D model",
        "3D" to "3D",
        "TRELLIS.2 — z fotky p\u0159edm\u011btu model se s\u00edt\u00ed a texturami" to
            "TRELLIS.2 — a textured mesh from a photo of an object",
        "Fotka p\u0159edm\u011btu" to "Photo of the object",
        "Pozad\u00ed odstran\u00ed server s\u00e1m — sta\u010d\u00ed b\u011b\u017en\u00e1 fotka z telefonu" to
            "The server removes the background — an ordinary phone photo is enough",
        "Co m\u00e1 z modelu vyl\u00e9zt" to "What you get out",
        "Rozd\u00edl nen\u00ed v tvaru, ale v tom, co se s modelem d\u00e1 d\u011blat d\u00e1l" to
            "The difference is not the shape, but what you can do with it afterwards",
        "Rychl\u00e1" to "Fast",
        "Pln\u00e9 textury (PBR)" to "Full textures (PBR)",
        "Detail" to "Detail",
        "Jemnost s\u00edt\u011b a velikost textury" to "Mesh detail and texture size",
        "Jemnost tvaru" to "Shape detail",
        "Textura" to "Texture",
        "3D model je hotov\u00fd" to "The 3D model is ready",
        "Na\u010d\u00edt\u00e1m TRELLIS.2" to "Loading TRELLIS.2",
        "Odstra\u0148uji pozad\u00ed" to "Removing the background",
        "Stav\u00edm tvar modelu" to "Building the shape",
        "Pe\u010du textury a rozbaluji UV" to "Baking textures and unwrapping UVs",
        "P\u0159eb\u00edr\u00e1m model" to "Fetching the model",
        "Stavba 3D modelu" to "Building the 3D model",
        "\u010cty\u0159i pr\u016fchody: struktura, tvar, zjemn\u011bn\u00ed, textura" to
            "Four passes: structure, shape, refinement, texture",
        "Vyber fotku p\u0159edm\u011btu, ze kter\u00e9 se m\u00e1 model ud\u011blat." to
            "Pick a photo of the object to build the model from.",

        // ---------------------------- opravy nelogi\u010dnost\u00ed na kart\u00e1ch (3.04)
        "Kolik videa zpracovat" to "How much of the video to process",
        "Bere se \u00fasek od za\u010d\u00e1tku. Del\u0161\u00ed \u00fasek = v\u00edc sn\u00edmk\u016f k p\u0159egenerov\u00e1n\u00ed." to
            "A stretch from the start is taken. Longer stretch = more frames to regenerate.",
        "\u010c\u00edm to nahradit (nepovinn\u00e9)" to "What to replace it with (optional)",
        "Bez fotek se p\u0159emaluje jen podle popisu; s fotkou dr\u017e\u00ed podobu" to
            "Without photos it repaints from the description alone; a photo keeps the likeness",
    )
}
