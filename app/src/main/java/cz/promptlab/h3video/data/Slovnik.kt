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
        "do Úpravy obrázku nebo do Zvětšit." to "to Image edit or Upscale.",
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
        "Odsud je výsledek nejjistější." to "From here the result is the safest bet.",
        "měkčí obraz a méně přesné tváře." to "a softer picture and less accurate faces.",
        "detaily bývají měkčí. Ostřejší HD spíš vyjde z nativu " to
            "details tend to be softer. Sharper HD comes from the native size ",
        "a karty All in One → Zvětšit." to "and the All in One → Upscale card.",
        "na výsledku nepoznáš." to "you will not see it in the result.",
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

        "Jazyk" to "Language",
        "Podle telefonu" to "System",
        "Čeština" to "Czech",
        "Angličtina" to "English",
        "Jazyk rozhraní; nepřeložené části zůstanou česky." to
            "Interface language; untranslated parts stay in Czech.",
    )
}
