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
        "Vybrat" to "Select",
        "Vybráno: %d" to "Selected: %d",
        "Zrušit výběr" to "Cancel selection",
        "Vybrat vše zobrazené" to "Select all shown",
        "Odznačit vše" to "Deselect all",
        "Smazat (%d)" to "Delete (%d)",
        "Odstraněno: %d" to "Deleted: %d",
        "Smazat vybrané položky?" to "Delete selected items?",
        "Počet položek: %d. Odstraní se kopie v aplikaci. Soubory uložené do telefonu zůstanou zachované." to
            "Items: %d. Copies in the app will be deleted. Files saved to your phone will be kept.",
        "Některé položky se nepodařilo odstranit." to "Some items could not be deleted.",
        "Některé položky se nepodařilo obnovit. Zkuste Vrátit znovu." to "Some items could not be restored. Try Undo again.",
        "Táhni kamerou kolem objektu — blíž je detail, dál celek" to
            "Drag the camera around the subject — closer is a close-up, further a wide shot",
        "Objekt je čelem dolů, k tobě. Pohled zprava je proto vlevo — je to jeho pravá strana, ne tvoje." to
            "The subject faces down, towards you. The right-side view therefore sits on the left — it is the subject's right, not yours.",
        "Jak vysoko" to
            "How high",
        "Pokračuj s videem" to "Continue with the video",
        "Zvětšit video" to "Upscale the video",
        // ---- karta Úhel kamery ----
        "Úhel kamery" to "Camera angle",
        "Úhel" to "Angle",
        "Tentýž objekt z jiné strany — 96 pevných póz, bez psaní" to "The same subject from another side — 96 fixed poses, no typing",
        "Vyber fotku, ze které se má udělat jiný úhel." to "Pick the photo you want seen from another angle.",
        "zepředu" to "front",
        "zprava zepředu" to "front right",
        "zprava" to "right",
        "zprava zezadu" to "back right",
        "zezadu" to "back",
        "zleva zezadu" to "back left",
        "zleva" to "left",
        "zleva zepředu" to "front left",
        "podhled" to "low angle",
        "v úrovni očí" to "eye level",
        "mírný nadhled" to "slightly above",
        "nadhled" to "high angle",
        "detail" to "close-up",
        "polocelek" to "medium shot",
        "celek" to "wide shot",
        "Postava nebo předmět, na který se chceš podívat z jiné strany" to "The person or object you want to see from another side",
        "Fotka pro nový úhel" to "Photo for the new angle",
        "Kde má být kamera" to "Where the camera should be",
        "Osm směrů dokola, čtyři výšky, tři odstupy" to "Eight directions around, four heights, three distances",
        "Směr" to "Direction",
        "Výška" to "Height",
        "Odstup" to "Distance",
        "Síla přesunu" to "Move strength",
        "Níž než 1 posune pohled míň, ale drží líp podobu předlohy." to "Below 1 the view moves less but keeps the original likeness better.",
        "Zadání pro model" to "Prompt sent to the model",
        "Otáčím pohled" to "Turning the view",
        "Qwen Image 2.1 — nový úhel kamery" to "Qwen Image 2.1 — new camera angle",
        "Nový úhel kamery" to "New camera angle",
        "Otočit pohled" to "Turn the view",
        // Qwen Image 2.1 — Úprava obrázku (3.64).
        "Qwen Image 2.1" to "Qwen Image 2.1",
        "Nový 7B model pro přesné úpravy, text a věrnost lidí i výrobků. Umí až 10 obrázků a průhledné RGBA, ale potřebuje nejnovější ComfyUI. Výzkumná licence dovoluje bez zvláštního souhlasu jen nekomerční použití." to
            "A new 7B model for precise edits, typography, and faithful people and products. It supports up to 10 images and transparent RGBA, but needs the latest ComfyUI. Its research license permits only non-commercial use without a separate agreement.",
        "Obrázek 1 je vždy ten, který se upravuje" to "Image 1 is always the image being edited",
        "Obrázek 1 — upravovaný" to "Image 1 — edit target",
        "Další předlohy" to "Additional references",
        "Až 9 referencí navíc; v zadání je označ <image2> až <image10>" to
            "Up to 9 more references; identify them as <image2> through <image10> in the prompt",
        "Obrázek %d" to "Image %d",
        "Přidat obrázek %d" to "Add image %d",
        "První obrázek určuje velikost výsledku. Ostatní mohou mít jiný poměr stran." to
            "The first image sets the output size. Other references may use a different aspect ratio.",
        "Ponech člověka z <image1> a obleč mu bundu z <image2>" to
            "Keep the person from <image1> and dress them in the jacket from <image2>",
        "Nativní 2K, více předloh a průhledné RGBA" to
            "Native 2K, multiple references, and transparent RGBA",
        "Oficiální workflow začíná na 25. Pro maximum detailu použij 40–50." to
            "The official workflow starts at 25. Use 40–50 for maximum detail.",
        "Velikost referencí" to "Reference size",
        "Původní velikost" to "Original size",
        "1024 px — doporučeno" to "1024 px — recommended",
        "2048 px — maximum" to "2048 px — maximum",
        "1024 px je oficiální výchozí hodnota. 2K zachová víc detailu, ale spotřebuje výrazně víc paměti." to
            "1024 px is the official default. 2K preserves more detail but uses substantially more memory.",
        "Průhledné pozadí (RGBA)" to "Transparent background (RGBA)",
        "Přidá do zadání výslovný pokyn k alfa kanálu. Výsledek zůstane PNG." to
            "Adds an explicit alpha-channel instruction to the prompt. The result stays a PNG.",
        "cache %s · %s" to "cache %s · %s",
        "KV cache" to "KV cache",
        "Qwen si reference spočítá jednou a používá je ve všech krocích" to
            "Qwen computes the references once and reuses them at every step",
        "Kam uložit cache" to "Where to store the cache",
        "Přesnost cache" to "Cache precision",
        "Automaticky" to "Automatic",
        "Grafická karta" to "GPU",
        "Operační paměť" to "System memory",
        "Vypnout cache" to "Disable cache",
        "Plná přesnost" to "Full precision",
        "INT8 — poloviční cache" to "INT8 — half-size cache",
        "INT4 — čtvrtinová cache" to "INT4 — quarter-size cache",
        "Automaticky je nejbezpečnější. INT8 cache zabere polovinu, INT4 čtvrtinu, ale může lehce snížit přesnost úpravy. Vypnutí cache šetří paměť za cenu pomalejšího běhu." to
            "Automatic is safest. INT8 halves the cache and INT4 quarters it, but may slightly reduce edit accuracy. Disabling the cache saves memory at the cost of a slower run.",
        "U více předloh napiš do zadání <image1>, <image2>… Model pak přesně ví, z které má co převzít." to
            "With multiple references, use <image1>, <image2>… in the prompt so the model knows exactly what to take from each.",
        "Kódování referencí ve 2K bere výrazně víc paměti. Když dojde VRAM nebo RAM, vrať 1024 px a cache dej na INT8." to
            "Encoding references at 2K uses substantially more memory. If VRAM or RAM runs out, return to 1024 px and set the cache to INT8.",
        // Knihovna obsahu a LoRA pro editační modely.
        "Knihovna obsahu" to "Content library",
        "v aplikaci" to "in the app",
        "Hledat název, zadání nebo seed…" to "Search titles, prompts or seeds…",
        "Oblíbené" to "Favorites",
        "Řazení" to "Sort order",
        "Nejnovější" to "Newest first",
        "Nejstarší" to "Oldest first",
        "Podle názvu" to "By name",
        "Zobrazit seznam" to "Show list",
        "Zobrazit mřížku" to "Show grid",
        "Prostor pro vaše nápady" to "A home for your ideas",
        "Videa, obrázky, hudba i 3D modely na jednom místě. Hotové výstupy tu zůstanou dostupné i bez serveru." to
            "Videos, images, music and 3D models in one place. Finished creations stay available even when your server is offline.",
        "Vytvořit první obsah" to "Create your first content",
        "Vyberte si to nejlepší" to "Keep your best work close",
        "Žádné odpovídající výstupy" to "No matching creations",
        "Označte výstupy hvězdičkou a budete je mít vždy po ruce." to "Star your creations to find them quickly here.",
        "Zkuste jiné hledání nebo zrušte filtry." to "Try another search or clear the filters.",
        "Zobrazit vše" to "Show all",
        "Výstup odstraněn" to "Creation removed",
        "Odstranit výstup?" to "Remove this creation?",
        "Odstraní se kopie v aplikaci. Soubory uložené do telefonu zůstanou zachované." to
            "This removes the copy in the app. Files exported to your phone will be kept.",
        "Odstranit" to "Remove",
        "Odebrat z oblíbených" to "Remove from favorites",
        "Přidat do oblíbených" to "Add to favorites",
        "Akce výstupu" to "Creation actions",
        "Přejmenovat" to "Rename",
        "Kopírovat zadání" to "Copy prompt",
        "Kopírovat seed" to "Copy seed",
        "Přehrát" to "Play",
        "Uloženo v telefonu" to "Saved to your phone",
        "Pojmenovat výstup" to "Name your creation",
        "Název výstupu" to "Creation title",
        "Prázdný název použije původní zadání." to "Leave empty to use the original prompt as the title.",
        "Zadání a parametry" to "Prompt and parameters",
        "Původní zadání" to "Original prompt",
        "Bez textového zadání" to "No text prompt",
        "Ukládám…" to "Saving…",
        "LoRA pro %s" to "LoRA for %s",
        "Volba a síla se pamatují pro každý model zvlášť" to "Selection and strength are saved separately for each model",
        "Obnovit seznam LoRA" to "Refresh LoRA list",
        "Vybrat LoRA (nepovinné)" to "Choose a LoRA (optional)",
        "Bez doplňkové LoRA" to "No additional LoRA",
        "Načítám LoRA a údaje o modelech…" to "Loading LoRAs and model details…",
        "Seznam LoRA se nepodařilo načíst. Zkontrolujte server a obnovte seznam." to
            "Could not load LoRAs. Check your server and refresh the list.",
        "Žádná LoRA s rozpoznaným základním modelem. Ve výběru lze přiřadit neoznačený soubor." to
            "No LoRAs with a recognized base model. You can assign an unclassified file in the picker.",
        "Vybraná LoRA na tomto serveru chybí. Obnovte seznam nebo vyberte jinou." to
            "The selected LoRA is missing from this server. Refresh the list or choose another one.",
        "LoRA se přidá k vybranému modelu. Síla 0 ji vypne. Nabídka zahrnuje i necenzurované LoRA pro tento model." to
            "The LoRA is added to the selected model. Strength 0 turns it off. The list also includes uncensored LoRAs for this model.",
        "Hledat LoRA…" to "Search LoRAs…",
        "Neurčené" to "Unclassified",
        "Pro model" to "For this model",
        "U těchto souborů chybí označení modelu. Vyberte jen LoRA určenou pro aktuální model." to
            "These files have no recognized base model. Only choose a LoRA trained for the current model.",
        "Výběr podle základního modelu v metadatech nebo názvu souboru." to
            "Filtered by the base model in the metadata or file name.",
        "Seznam je prázdný. LoRA musí být uložená na serveru ve složce models/loras." to
            "The list is empty. LoRAs must be stored in models/loras on your server.",
        "Obnovit" to "Refresh",
        "Přiřadit LoRA k modelu?" to "Assign this LoRA to the model?",
        "U souboru %s nelze ověřit základní model. Použijte ho jen pokud je určený pro %s." to
            "The base model of %s could not be verified. Only use it if it was trained for %s.",
        "Použít pro tento model" to "Use for this model",
        "Vyberte model podle požadované úpravy" to "Choose a model for your edit",
        "Vybraná LoRA nepatří k tomuto editačnímu modelu nebo ji již používá základní workflow." to
            "The selected LoRA is incompatible with this editing model or is already used by the base workflow.",
        "Úlohu z fronty se nepodařilo spustit: %s" to "Could not start queued task: %s",
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
        "LTX 2.5" to "LTX 2.5",
        "LTX" to "LTX",
        "Video ze zadání, z fotky nebo na hotový zvuk — s nativním zvukem" to "Video from a prompt, a photo or an existing audio file — with native sound",
        "Co se dělá" to "What to do",
        "Jeden model, tři způsoby zadání" to "One model, three ways to feed it",
        "Z textu" to "From text",
        "Jen z popisu. Zvuk k tomu model vymyslí sám" to "From the description alone. The model invents the sound too",
        "Z obrázku" to "From a photo",
        "Rozhýbe fotku a dodá k ní zvuk" to "Animates the photo and adds sound",
        "Ze zvuku" to "From audio",
        "Fotka mluví na tvůj zvuk — délka sedí přesně na něj" to "The photo speaks over your audio — the length matches it exactly",
        "Délka" to "Length",
        "Kolik sekund má video trvat" to "How many seconds the video should last",
        "%d snímků při %d fps" to "%d frames at %d fps",
        "Co je vidět A co je slyšet — anglicky" to "What is seen AND what is heard — in English",
        "A rainy night street, neon signs, distant traffic and rain on metal…" to "A rainy night street, neon signs, distant traffic and rain on metal…",
        "Napiš i to, co má být slyšet — hlas, ruch, hudbu. " to "Describe what should be heard too — voice, ambience, music. ",
        "Model skládá obraz a zvuk zároveň." to "The model builds picture and sound together.",
        "trénovaná na Turbu" to "trained on Turbo",
        "trénovaná na Base" to "trained on Base",
        "Video ze zvuku" to "Video from audio",
        "Ze zvuku" to "From audio",
        "LTX 2.5 — fotka mluví na tvůj zvuk, délka sedí přesně" to "LTX 2.5 — a photo speaks over your audio, length matches exactly",
        "Na výšku (9:16)" to "Portrait (9:16)",
        "Na šířku (16:9)" to "Landscape (16:9)",
        "Čtverec (1:1)" to "Square (1:1)",
        "Na výšku (3:4)" to "Portrait (3:4)",
        "Vyber fotku, ze které video začne." to "Pick the photo the video starts from.",
        "Vyber zvuk, na který se bude mluvit." to "Pick the audio to speak over.",
        "Popiš scénu — kdo je v záběru a co dělá." to "Describe the scene — who is on camera and what they do.",
        "Délku videa určuje zvuk — model ji nemá jak useknout." to "The audio sets the length — the model cannot cut it short.",
        "Zvuk je delší než 20 s. Běh poroste do desítek minut, " to "The audio is longer than 20 s. The run will take tens of minutes ",
        "protože se model nevejde do paměti grafiky celý." to "because the model does not fit in the GPU memory in one piece.",
        "Popis piš anglicky — model je učený na anglické popisky." to "Write the description in English — the model was trained on English captions.",
        "První snímek videa — z něj se bere podoba i prostředí." to "The first frame — it sets both the likeness and the setting.",
        "První snímek videa — z něj se bere podoba i prostředí" to "The first frame — it sets both the likeness and the setting",
        "Fotka prvního snímku" to "First frame photo",
        "Zvuk" to "Audio",
        "Řeč nebo zpěv — video se na něj napasuje" to "Speech or singing — the video is fitted to it",
        "Vybrat zvuk" to "Pick audio",
        "Vybrat jiný" to "Pick another",
        "Odebrat zvuk" to "Remove audio",
        "Délka %.1f s → %d snímků při %d fps" to "Length %.1f s → %d frames at %d fps",
        "Zvuk se nepodařilo načíst. Zkus jiný soubor." to "The audio could not be loaded. Try another file.",
        "Z toho souboru nejde přečíst délka zvuku. Zkus MP3 nebo WAV." to "The length cannot be read from that file. Try MP3 or WAV.",
        "Délku videa určuje tenhle soubor — model ji nemá jak useknout." to "This file sets the length — the model cannot cut it short.",
        "Scéna" to "Scene",
        "Co je v záběru a co se děje — anglicky" to "What is on camera and what happens — in English",
        "A news anchor in a dark blue studio speaks to the camera…" to "A news anchor in a dark blue studio speaks to the camera…",
        "Tvar obrazu" to "Frame shape",
        "Poměr stran hotového videa" to "Aspect ratio of the finished video",
        "Vygenerovat video ze zvuku" to "Generate video from audio",
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
        "ACE-Step nebo YuE2 — celá píseň z textu" to
            "ACE-Step or YuE2 — a whole song from text",
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
        "Qwen Image 2.1 — obnova fotografie" to "Qwen Image 2.1 — photo restoration",
        "Vlastní zadání nahradí obecnou obnovu, Qwen 2.1 k němu přidá ochranu detailů." to
            "Your instruction replaces general restoration; Qwen 2.1 adds detail-preservation guidance.",
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
        "Upravit (Qwen Image 2.1 — popiš změnu)" to "Edit (Krea 2 — describe the change)",
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
        "Fotka má poměr %s. Ve vybraném ji model roztáhne — klepni sem a srovnej to." to
            "The photo is %s. In the selected one the model will stretch it — tap to fix.",
        "Čím počítat" to "Engine",
        "Čím upravit" to "Edit with",
        "Projekt" to "Project",
        "Základní model neumí nahotu ani podrobnou anatomii — ty z něj byly vytrénované pryč. Rozbal „Model a doladění“ a přimíchej LoRA, jinak to zadání nesplní, ať ho napíšeš jakkoli." to
            "The base model cannot do nudity or detailed anatomy — those were trained out of it. Open \"Model and tuning\" and add a LoRA, otherwise it will not follow the instruction no matter how you word it.",
        "Záběry filmu v pořadí, na jednom místě" to "Film shots in order, in one place",
        "Založit projekt" to "Create project",
        "Název nového projektu" to "Name of the new project",
        "Přidat záběr" to "Add shot",
        "Smazat záběr" to "Delete shot",
        "Smazat projekt" to "Delete project",
        "Zpět na projekty" to "Back to projects",
        "Vyrobit" to "Make",
        "Odpojit výsledek" to "Detach result",
        "Čím vyrobit" to "Make with",
        "bez karty" to "no card yet",
        "Nahoru" to "Up",
        "Dolů" to "Down",
        "Rychlost proti kvalitě" to "Speed versus quality",
        "Obojí má oficiální předloha, liší se počtem kroků" to
            "Both come from the reference workflow; they differ in step count",
        "Vlastní nastavení páček." to "Custom slider settings.",
        "Zámek totožnosti" to "Identity lock",
        "Dva editační modely, každý jinak postavený" to
            "Two editing models, built differently",
        "rozměry podle předlohy · 4 kroky" to "size from the source · 4 steps",
        "Krea 2" to "Krea 2",
        "FLUX.2 Klein 9B" to "FLUX.2 Klein 9B",
        "Kromě otisku dostane i podrobnosti z celé plochy fotky a odhadnutý úhel záběru objektivu, takže ví, jak je předmět vůči kameře postavený, ne jen jak vypadá. Náročnější na paměť." to
            "Besides the fingerprint it also gets detail from the whole photo and an estimated lens field of view, so it knows how the object sits relative to the camera, not just what it looks like. Heavier on memory.",
        "TRELLIS.2" to "TRELLIS.2",
        "Fotku shrne do jediného otisku a z něj staví tvar. Rychlejší a osvědčené." to
            "Boils the photo down to a single fingerprint and builds the shape from it. Faster and proven.",
        "Hodnota z ukázkové šablony. Je to paměťový vrchol běhu — před ním appka kartu uklidí, ale nech si ji volnou." to
            "The value from the reference workflow. It is the run's memory peak — the app frees the card first, but keep it free.",
        "Která LoRA" to "Which LoRA",
        "Druhá LoRA (nepovinná)" to "Second LoRA (optional)",
        "Dvě LoRA na plné síle se často perou — druhou zkus níž." to
            "Two LoRAs at full strength often fight each other — try the second one lower.",
        "Cílená oprava" to "Targeted fix",
        "Nepovinné. Prázdné = obecná záchrana staré fotky jako dosud" to
            "Optional. Leave empty for the usual old-photo rescue.",
        "Co se má opravit — anglicky, model je na ni trénovaný" to
            "What to fix — in English, that is what the model was trained on",
        "Vlastní zadání nahradí opravovací zadání předlohy." to
            "Your own instruction replaces the template's restoration one.",
        "Cílená LoRA (nepovinná)" to "Targeted LoRA (optional)",
        "Řadí se za tři LoRA předlohy. Některé chtějí v zadání spouštěcí slovo." to
            "It chains after the template's three LoRAs. Some need a trigger word in the instruction.",
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
        "Pět sekund v %d × %d trvalo při měření %s. " to
            "Five seconds at %d × %d took %s when measured. ",
        "Vyšší rozlišení i delší video čas úměrně násobí." to
            "Higher resolution and longer video multiply that time.",
        "22 minut" to "22 minutes",
        "kolem hodiny" to "about an hour",
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
        "Jen zrychlovací z workflow" to "Only the speed-up LoRA from the workflow",
        "Zrychlovací z workflow + %d další" to "Workflow speed-up LoRA + %d more",
        "Rychlejší pozornost; vypnuto = čistá PyTorch pozornost" to
            "Faster attention; off = plain PyTorch attention",
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
        "✨ Vylepšit zadání" to "✨ Improve the instruction",
        "✨ Vylepšit (Qwen)" to "✨ Improve (Qwen)",
        "Qwenův přepisovač tohle zadání odmítl. " +
            "Zkus ✨ Vylepšit (odvázaně) — ten nic nepřepisuje." to
            "Qwen's rewriter refused this request. " +
            "Try ✨ Improve (unfiltered) — that one rewrites nothing.",
        "Oba se dívají na tvoje fotky. Qwen je na model vycvičený, " +
            "ale odvážnější zadání sám zjemní — odvázaný nepřepisuje nic." to
            "Both look at your photos. Qwen is trained for this model but softens " +
            "bolder requests on its own — the unfiltered one rewrites nothing.",
        "hotovo za pár sekund" to "done in seconds",
        "nic neodmítá" to "refuses nothing",
        "poslouchá líp, trvá dýl" to "follows better, takes longer",
        "drží složité zadání a text" to "holds complex prompts and text",
        "jiný rukopis" to "a different hand",
        "text v obraze a 2K" to "text in image and 2K",
        "Oficiální předloha Qwenu má 25. Víc kroků = víc detailu a času." to
            "Qwen's official template uses 25. More steps = more detail and time.",
        "Qwen 2.1 je na cfg 1 stavěný a zadání na ní drží. Zvyšovat není potřeba." to
            "Qwen 2.1 is built for cfg 1 and follows the prompt there. No need to raise it.",
        "Nad 1 se u Qwen 2.1 začne uplatňovat i negativní prompt, " +
            "ale oficiální předloha jede na 1 — vyšší hodnota může uškodit." to
            "Above 1 the negative prompt starts to apply on Qwen 2.1, " +
            "but the official template runs at 1 — a higher value can hurt.",
        "✨ Vylepšit (odvázaně)" to "✨ Improve (unfiltered)",
        "Qwen je na tenhle model vycvičený a poradí i poměr stran, " +
            "ale odvážnější zadání sám zjemní. Odvázaný nepřepisuje nic." to
            "Qwen is trained for this model and suggests an aspect ratio, " +
            "but it softens bolder requests on its own. The unfiltered one rewrites nothing.",
        "Velikost" to "Size",
        "Qwen 2.1 umí 2K nativně" to "Qwen 2.1 does 2K natively",
        "Generovat ve 2K" to "Generate at 2K",
        "Čtyřnásobek pixelů a výrazně víc paměti i času. " +
            "Vypnuto = okolo 1 megapixelu jako ostatní modely karty." to
            "Four times the pixels and far more memory and time. " +
            "Off = around 1 megapixel like the other models on this card.",
        "napsáno %d" to "written %d",
        "Přepisovač se neozval pět minut. Mrkni, jestli ComfyUI běží." to
            "The rewriter went quiet for five minutes. Check that ComfyUI is running.",
        "Server je starší než ComfyUI 0.37 — chybí uzel na přepis zadání." to
            "The server is older than ComfyUI 0.37 — the rewriting node is missing.",
        "Přepisovač nevrátil použitelný výsledek. Zkus to znovu." to
            "The rewriter returned nothing usable. Try again.",
        "Přepisuji…" to "Rewriting…",
        // karta Domalovat — režim rozšíření obrázku
        "Domalovat do masky" to "Paint into a mask",
        "Rozšířit obrázek" to "Extend the image",
        "Začmáráš místo a model ho přemaluje podle věty" to
            "You scribble over a spot and the model repaints it from your sentence",
        "Přilepí k fotce nové místo a domaluje, co tam chybí" to
            "Adds new space next to the photo and paints in what is missing",
        "Dvě úlohy nad stejnou fotkou" to "Two jobs on the same photo",
        "Fotka, která se rozšíří" to "The photo to extend",
        "Štětec tady není potřeba — nové místo si graf označí sám" to
            "No brush needed here — the graph marks the new space itself",
        "Kam a o kolik" to "Where and how much",
        "Qwen doporučuje 30–50 % plochy navíc na jeden směr" to
            "Qwen recommends 30–50% extra space per direction",
        "Dolů" to "Down",
        "Nahoru" to "Up",
        "Vlevo" to "Left",
        "Vpravo" to "Right",
        "O kolik" to "How much",
        "Počítá se z rozměru fotky a platí pro každý zvolený směr zvlášť." to
            "Measured from the photo's size, and applied to each chosen direction separately.",
        "Co má na přilepeném místě být" to "What should be in the new space",
        "Popiš, co v záběru chybí — „celá postava, nohy v džínách, chodník“" to
            "Describe what the frame is missing — “full figure, legs in jeans, pavement”",
        "celá postava, nohy v džínách a botách, dlážděný chodník" to
            "full figure, legs in jeans and boots, paved footpath",
        "Čím se rozšiřuje" to "What does the extending",
        "Qwen Image 2.1 — rozšíření obrazu má v příručce jako vlastní úlohu" to
            "Qwen Image 2.1 — its guide lists outpainting as a task of its own",
        "Původní fotka se vlepí zpátky nezměněná, model maluje jen to nové místo." to
            "The original photo is stitched back unchanged; the model paints only the new space.",
        "Původní fotka se nepřekresluje — model maluje jen to přilepené místo." to
            "The original photo is not repainted — the model paints only the added space.",
        "Nad 60 % už model nemá z čeho vycházet a scénu si vymýšlí. " to
            "Above 60% the model has little to go on and invents the scene. ",
        "Spolehlivější je rozšířit dvakrát po menším kusu." to
            "Extending twice by a smaller amount is more reliable.",
        "Čím víc směrů naráz, tím víc si model domýšlí. " to
            "The more directions at once, the more the model makes up. ",
        "Po jednom směru to bývá přesnější." to "One direction at a time is usually more accurate.",
        "Vyber fotku, kterou chceš rozšířit." to "Pick the photo you want to extend.",
        "Vyber aspoň jeden směr, kam se má fotka rozšířit." to
            "Pick at least one direction to extend the photo into.",
        "Napiš, co má na přilepeném místě být — třeba „celá postava, nohy v džínách“." to
            "Write what should be in the new space — e.g. “full figure, legs in jeans”.",
        "Rozšířit (dokreslit, co je mimo záběr)" to "Extend (paint in what is out of frame)",
        "Rozšířit" to "Extend",
        // karta Long MiniMax (navazující záběry přes latent)
        "Long MM" to "Long MM",
        "Záběr po záběru na jednu scénu, navazuje se přes latent" to
            "One shot at a time on a single scene, chained through the latent",
        "Navázat" to "Continue",
        "Založí nový řetěz a uloží jeho latent" to "Starts a new chain and saves its latent",
        "Přidá další kus k hotovému videu" to "Adds another piece to the finished video",
        "Na co se navazuje" to "What it continues from",
        "Poslední hotový celek téhle scény" to "The latest finished whole of this scene",
        "Latent předchozího záběru" to "The previous shot's latent",
        "Odsud se pokračuje bez ztráty kvality" to "This is where it picks up with no quality loss",
        "Na serveru zatím žádný latent není. Začni prvním záběrem." to
            "There is no latent on the server yet. Start with the first shot.",
        "Nejnovější je nahoře" to "Newest first",
        "Předchozí záběry téhle karty" to "Earlier shots from this card",
        "Vybrat video z telefonu" to "Pick a video from the phone",
        "✨ Vylepšit zadání" to "✨ Improve the prompt",
        "Nejdřív napiš aspoň pár slov o tom, co se má dít." to
            "Write at least a few words about what should happen first.",
        "Načíst znovu ze serveru" to "Reload from the server",
        "Zahodit poslední záběr a zkusit ho znovu" to
            "Discard the last shot and try it again",
        "Zahozeno: %d" to "Discarded: %d",
        "Ve frontě čeká: %d" to "Waiting in queue: %d",
        "Vrátit zahozené zpět" to "Restore discarded shots",
        "Zahodit tenhle záběr a zkusit ho znovu" to
            "Discard this shot and try it again",
        "Dva průchody" to "Two passes",
        "Ostrost detailů" to "Detail sharpness",
        "vyp" to "off",
        "Seznam latentů se nepodařilo načíst — server neodpovídá." to
            "The latent list could not be loaded — the server is not responding.",
        "Podoba" to "Likeness",
        "Fotky, podle kterých model drží postavy a místo (nepovinné)" to
            "Photos the model keeps the characters and the place from (optional)",
        "Plátno" to "Canvas",
        "Pro celý řetěz se volí jen teď" to "Chosen once here, for the whole chain",
        "480p" to "480p",
        "640p" to "640p",
        "720p" to "720p",
        "768p · 1 MP" to "768p · 1 MP",
        "%s má %.2f× víc bodů než 480p a úměrně tomu déle trvá." to
            "%s has %.2f× the pixels of 480p and takes proportionally longer.",
        "Na šířku" to "Landscape",
        "Na výšku" to "Portrait",
        "Čtverec" to "Square",
        "Co se má dít" to "What should happen",
        "Popis prvního záběru" to "A description of the first shot",
        "Popis toho, co se stane dál" to "A description of what happens next",
        "Muž z <Picture 1> stojí u okna a otočí se do místnosti." to
            "The man from <Picture 1> stands at the window and turns into the room.",
        "Přejde ke stolu a posadí se." to "He walks to the table and sits down.",
        "Délka záběru" to "Shot length",
        "Týká se jen tohohle kusu, ne celku" to "Applies to this piece only, not the whole",
        "Název řetězu" to "Chain name",
        "Latenty se pod ním na serveru číslují" to "Latents are numbered under it on the server",
        "Začít nový řetěz" to "Start a new chain",
        "Na kterou scénu se navazuje" to "Which scene to continue",
        "Název scény" to "Scene name",
        "Realističtější podání" to "More realistic rendering",
        "540p · 0,5 MP" to "540p · 0.5 MP",
        "Nad 0,5 MP se dva průchody nespouští — dělaly artefakty a rozsypanou barvu. Jede se jedním." to
            "Above 0.5 MP two passes are not used — they produced artefacts and scattered colour. A single pass runs instead.",
        "Zrychlovací pozornost" to "Speed-up attention",
        "Sage" to "Sage",
        "Comfy Kitchen INT8" to "Comfy Kitchen INT8",
        "Nechat na serveru" to "Leave it to the server",
        "Turbo" to "Turbo",
        "Zapojení autora balíku, beze změny" to "The pack author's wiring, unchanged",
        "FastVideo VSA" to "FastVideo VSA",
        "Čtyřkrokový model, k němu LoRA TaoMate" to "A four-step model with the TaoMate LoRA",
        "Eros Max" to "Eros Max",
        "Eros Turbo" to "Eros Turbo",
        "Singularity" to "Singularity",
        "Detailer" to "Detailer",
        "V2 Kvalita" to "V2 Quality",
        "Eros model i Eros LoRA" to "The Eros model and the Eros LoRA",
        "3 + 2" to "3 + 2",
        "Tři kroky dole, zvětšení latentu, dva nahoře" to
            "Three steps low, the latent upscaled, two steps high",
        "Kroky" to "Steps",
        "bez" to "none",
        "Síla LoRA" to "LoRA strength",
        "Autor ji má zapnutou, vypnutá jde model rovnou dál" to
            "The author keeps it on; switched off the model passes straight through",
        "Držet podobu z fotek" to "Hold the likeness from the photos",
        "Posílat je i do navazujících záběrů" to "Send them into continuing shots too",
        "LoRA h3-realism-people, navrch k rychlostní" to
            "The h3-realism-people LoRA, stacked on the speed one",
        "Podle něj se pak navazuje a číslují se latenty" to
            "Continuing goes by it, and latents are numbered under it",
        "Naváže se na její poslední hotový záběr" to "It continues from its latest finished shot",
        "Na serveru zatím žádná scéna není. Začni prvním záběrem." to
            "There is no scene on the server yet. Start with the first shot.",
        "Naváže se na video z telefonu: %s" to "It will continue from a video off the phone: %s",
        "Naváže se na záběr z %s · %s" to "It will continue from the shot of %s · %s",
        "K téhle scéně nemám v galerii žádný záběr — vyber video z telefonu." to
            "There is no shot of this scene in the gallery — pick a video off the phone.",
        "Latent: %s" to "Latent: %s",
        "Vybraný latent patří k jinému řetězu než „%s“. Vyber latent, který začíná tímhle jménem, nebo řetěz přejmenuj." to
            "The chosen latent belongs to a chain other than “%s”. Pick a latent starting with that name, or rename the chain.",
        "kuchyne" to "kitchen",
        "Uloží se jako %s_00001, %s_00002 …" to "Saved as %s_00001, %s_00002 …",
        "Napiš, co se má v záběru dít." to "Write what should happen in the shot.",
        "Pojmenuj řetěz — podle toho se latenty na serveru číslují." to
            "Name the chain — latents are numbered by it on the server.",
        "Vyber latent záběru, ze kterého se pokračuje. Nabídku plní server." to
            "Pick the shot's latent to continue from. The server fills the list.",
        "Až záběr doběhne, zůstane na serveru jeho latent. Z něj se v režimu Navázat pokračuje bez ztráty kvality." to
            "Once the shot finishes, its latent stays on the server. Continue mode picks up from it with no quality loss.",
        "Na fotky se v zadání odkazuje značkami <Picture 1>, <Picture 2>… Bez zmínky si jich model nemusí všimnout." to
            "Refer to the photos in the prompt as <Picture 1>, <Picture 2>… Unmentioned, the model may ignore them.",
        "Rozlišení i poměr musí zůstat stejné jako u prvního záběru. Latent se nedá přepočítat na jiné plátno." to
            "Resolution and aspect must stay as they were in the first shot. A latent cannot be resized.",
        "Výsledkem je celé video od začátku, ne jen přidaný kus — příště navazuj na něj." to
            "The result is the whole video from the start, not just the added piece — continue from it next time.",
        "Vygenerovat první záběr" to "Generate the first shot",
        "Navázat další záběr" to "Continue with another shot",
        // karta Dance (Wan-Dancer)
        "Dance" to "Dance",
        "Z fotky a hudby video, kde ten člověk tančí do rytmu" to
            "A photo plus music becomes a video of that person dancing to the beat",
        "Kdo bude tančit" to "Who is dancing",
        "Fotka člověka — z ní se bere podoba" to "A photo of a person — their look comes from it",
        "Fotka tanečníka" to "The dancer's photo",
        "Hudba" to "Music",
        "Na tuhle skladbu se bude tančit" to "The track to dance to",
        "Vybrat hudbu" to "Pick music",
        "Vybrat jinou" to "Pick another",
        "Odebrat hudbu" to "Remove the music",
        "Délka skladby %.1f s" to "Track length %.1f s",
        "Hudbu se nepodařilo načíst. Zkus jiný soubor." to
            "The music could not be loaded. Try another file.",
        "Rozlišení" to "Resolution",
        "Vyšší je 2,25× víc bodů a úměrně déle trvá" to
            "The higher one is 2.25× the pixels and takes proportionally longer",
        "480 × 832 — rychlejší" to "480 × 832 — faster",
        "720 × 1280 — jako v předloze" to "720 × 1280 — as in the template",
        "Druh tance" to "Kind of dance",
        "Podle toho se řídí styl pohybu" to "This drives the style of movement",
        "Latina" to "Latin",
        "Street dance" to "Street dance",
        "K-pop" to "K-pop",
        "Klasický tanec" to "Classical dance",
        "Step" to "Tap",
        "Rozsah pohybu" to "How big the moves are",
        "Jak velká gesta má dělat" to "How large the gestures should be",
        "Drobné pohyby" to "Small moves",
        "Střední" to "Medium",
        "Výrazné" to "Bold",
        "Maximální" to "Maximum",
        "Video vzniká po pětisekundových úsecích" to "The video is made in five-second segments",
        "%d úseků · %d snímků při %d fps" to "%d segments · %d frames at %d fps",
        "Doplnění zadání (nepovinné)" to "Extra detail (optional)",
        "Prostředí nebo oblečení, když na nich záleží" to
            "The setting or the clothes, when they matter",
        "na pódiu s barevnými světly" to "on a stage with coloured lights",
        "Vyber fotku člověka, který má tančit." to "Pick a photo of the person who should dance.",
        "Vyber hudbu, na kterou se bude tančit." to "Pick the music to dance to.",
        "Hudba je kratší než zvolená délka videa. Zkrať video nebo vyber delší skladbu." to
            "The music is shorter than the chosen video length. Shorten the video or pick a longer track.",
        "Pohyb si model vymyslí sám podle rytmu. Video se vzorovým tancem se sem nedává." to
            "The model invents the movement from the beat. A reference dance video is not used here.",
        "Zvolená délka je %d úseků po %d s a každý se počítá zvlášť. " to
            "The chosen length is %d segments of %d s, each computed separately. ",
        "Napoprvé zkus jeden, ať víš, jak dlouho to u tebe trvá." to
            "Try one first, to see how long it takes on your machine.",
        "Objekt je čelem dolů, k tobě. Kam táhneš kameru, odtud se bude koukat." to
            "The subject faces down, towards you. Wherever you drag the camera is where it will look from.",
        "Táhni kamerou od podhledu po nadhled" to
            "Drag the camera from below eye level to above it",
        "Domalovat přemaluje začmárané místo, rozšíří dokreslí, co je mimo záběr." to
            "Paint repaints the spot you scribble over; extend paints in what is out of frame.",
        "Co má na přilepeném místě být (nepovinné)" to
            "What should be in the new space (optional)",
        "Můžeš nechat prázdné — model scénu dotáhne podle fotky" to
            "Can stay empty — the model works the scene out from the photo",
        "Zadání můžeš nechat prázdné — model scénu dotáhne podle fotky. " to
            "You can leave the prompt empty — the model works the scene out from the photo. ",
        "Napiš ho, jen když tam má být něco konkrétního." to
            "Write one only when something specific belongs there.",
        // karta LTX 2.5 — přepisovač popisu a LoRA
        "✨ Vylepšit (LTX)" to "✨ Improve (LTX)",
        "Nejdřív napiš aspoň pár slov o tom, co má být vidět." to
            "Write a few words about what should be on screen first.",
        "Oba napíšou dlouhý popis záběru: velikost záběru, pohyb " to
            "Both write a long shot description: shot size, camera ",
        "kamery a zvuk vpletený do děje. Odvázaný nic nezjemňuje." to
            "motion and sound woven into the action. The unfiltered one softens nothing.",
        "Oba napíšou dlouhý popis záběru i s pohybem kamery. " to
            "Both write a long shot description including camera motion. ",
        "Zvuk máš svůj, takže ho nevymýšlejí — popíšou, co je z něj vidět." to
            "The soundtrack is yours, so they do not invent it — they describe what is visible in it.",
        "pro 2.5" to "for 2.5",
        "pro 2.3 — na 2.5 sedí" to "for 2.3 — fits 2.5",
        "rodina LTX" to "LTX family",
        "Server nemá uzly llama.cpp — bez nich odvázaný přepisovač nejede." to
            "The server has no llama.cpp nodes — the unfiltered rewriter needs them.",
        // karta Domalovat — třetí motor
        "Nejlíp drží podobu lidí, ale je nejpomalejší (25 kroků)" to
            "Holds people's likeness best, but is the slowest (25 steps)",
        "Tenhle model poslouchá příkazy — napiš, co se s tím místem má stát" to
            "This model follows commands — write what should happen to that spot",
        "Když se výsledek nepovede, zkus jiný model — každý kreslí jinak" to
            "If the result misses, try another model — each one paints differently",
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
        // volba modelu na kartě Hudba (3.48)
        "Čím skládat" to "What to compose with",
        "Dva různé modely — každý skládá jinak a bere jiné zadání" to
            "Two different models — each composes differently and takes different input",
        "Rychlý (8 kroků) a zpívá i česky — délku, tempo i tóninu určíš ty" to
            "Fast (8 steps) and sings Czech too — you set length, tempo and key",
        "Muzikálnější — nejdřív si napíše noty. Zpívá anglicky a čínsky, běh je delší" to
            "More musical — it writes a score first. Sings English and Chinese, and takes longer",
        "YuE2 3B — nejdřív noty, pak zpěv" to "YuE2 3B — score first, then singing",
        "Sloky a refrén anglicky; prázdné = instrumentálka" to
            "Verses and chorus in English; empty = instrumental",
        "anglicky." to "in English.",
        "Nejvýše" to "At most",
        "Strop délky. Kratší text = kratší píseň, model skončí sám." to
            "A length cap. Shorter lyrics = shorter song, the model stops on its own.",
        "Délka je jen strop — model skončí tam, kde má píseň konec." to
            "The length is only a cap — the model stops where the song ends.",
        "Než se rozezní první tón, model si napíše noty. " to
            "Before the first note sounds, the model writes the score. ",
        "Chvíli se nic neděje, to je v pořádku." to
            "Nothing happens for a while — that's normal.",
        "Plán skladby" to "Song plan",
        "Co si model napíše, než začne zpívat" to
            "What the model writes before it starts singing",
        "Noty drží melodii pohromadě — bez nich se píseň rozvolní" to
            "A score holds the melody together — without it the song drifts",
        "Bez plánu" to "No plan",
        "Rovnou zpívá — nejrychlejší, ale melodie bývá rozvolněná" to
            "Sings straight away — fastest, but the melody tends to drift",
        "Melodie" to "Melody",
        "Nejdřív si napíše melodii, pak podle ní zpívá" to
            "Writes the melody first, then sings along to it",
        "Melodie a akordy" to "Melody and chords",
        "Napíše si melodii i doprovod — nejmuzikálnější, ale nejdelší běh" to
            "Writes melody and backing — most musical, but the longest run",
        "Načítám YuE2" to "Loading YuE2",
        "Rozeznívám skladbu" to "Turning the score into sound",
        "Zpěv podle not" to "Singing from the score",
        "Píšu noty skladby" to "Writing the score",
        "Zpívá podle not, které si sám napsal" to
            "Singing from the score it wrote itself",

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

        // ------------------- Hudba: předělání nahrávky, YuE2 + SheetSage2 (3.57)
        "Odkud vzít melodii" to "Where the melody comes from",
        "Buď si ji model vymyslí, nebo ji vezme z nahrávky, kterou mu dáš" to
            "Either the model invents one, or it takes one from a recording you give it",
        "Nová skladba" to "New song",
        "Model si napíše noty i zpěv sám podle stylu a textu" to
            "The model writes both the score and the vocals itself, from style and lyrics",
        "Předělat nahrávku" to "Rework a recording",
        "Přepíše melodii z tvé nahrávky do not a zahraje ji v jiném stylu" to
            "Transcribes the melody from your recording into a score and plays it in another style",
        "Nahrávka" to "Recording",
        "Zatím žádná — vyber skladbu z telefonu" to "None yet — pick a track from your phone",
        "Vybrat nahrávku" to "Pick a recording",
        "Vybrat jinou" to "Pick a different one",
        "Odebrat nahrávku" to "Remove the recording",
        "Nahrávku se nepodařilo načíst. Zkus jinou." to
            "The recording could not be read. Try another one.",
        "Z nahrávky se vezme jen melodie. Zpěv i doprovod vzniknou znovu " to
            "Only the melody is taken from the recording. Vocals and backing are created anew ",
        "podle stylu a textu výš — z původního zvuku nezůstane nic." to
            "from the style and lyrics above — none of the original audio remains.",
        "Převzít i akordy" to "Take the chords too",
        "Drží i harmonii předlohy. Nový styl se pak prosadí míň." to
            "Keeps the source harmony as well. The new style then comes through less.",
        "Vyber nahrávku, ze které se má vzít melodie." to
            "Pick the recording the melody should come from.",
        "Z nahrávky se bere jen melodie — žádný zvuk z ní ve výsledku nezůstane." to
            "Only the melody is taken from the recording — none of its audio ends up in the result.",
        "Styl piš tak, jak má znít nová verze, ne jak zní původní nahrávka." to
            "Write the style for how the new version should sound, not how the original does.",
        "Text se lépe zpívá, když má podobný počet slabik jako původní." to
            "Lyrics sing better when the syllable count is close to the original.",
        "Odesílám nahrávku" to "Uploading the recording",
        "Načítám YuE2 a přepisovač" to "Loading YuE2 and the transcriber",
        "Přepisuji nahrávku do not" to "Transcribing the recording into a score",
        "Hraji ji v novém stylu" to "Playing it in the new style",
        "Zpívá podle melodie přepisané z nahrávky" to
            "Sings to the melody transcribed from the recording",
        "Předělání nahrávky" to "Reworking a recording",
        "Spojení a odeslání nahrávky" to "Connecting and uploading the recording",
        "Nahrávka musí být na serveru, než se z ní vezme melodie" to
            "The recording has to reach the server before its melody can be taken",

        // ------------------------ Obrázek: vlastní model ze serveru (3.56)
        "Vlastní model" to "Custom model",
        "Jiný Z-Image model ze serveru. Vybereš soubor a řekneš kroky a cfg." to
            "Another Z-Image model from your server. You pick the file and set steps and cfg.",
        "Vybrat model ze serveru" to "Pick a model from the server",
        "Soubor modelu" to "Model file",
        "Vedení promptem (cfg)" to "Prompt guidance (cfg)",
        "Víc kroků = víc detailu a času. Turbo si vystačí s 8, s vyšším cfg dej 20 a víc." to
            "More steps = more detail and more time. Turbo is fine on 8; with higher cfg use 20 or more.",
        "Na 1 si model zadání vykládá po svém. Kolem 2 začne poslouchat pózu a kompozici." to
            "At 1 the model reads the brief its own way. Around 2 it starts following pose and composition.",
        "Na cfg 1 nemá prompt žádnou váhu — destilované Turbo jede bez vedení. " to
            "At cfg 1 the prompt carries no weight — distilled Turbo runs without guidance. ",
        "Když model neposlouchá pózu, zvedni cfg na 2." to
            "When the model ignores the pose, raise cfg to 2.",
        "Vyber jiný soubor a karta přepne na „Vlastní model“." to
            "Pick a different file and the card switches to “Custom model”.",
        "Zobrazit i ostatní modely" to "Show the other models too",
        "Dokud model nevybereš, generuje se na Z-Image Turbo z předlohy." to
            "Until you pick a model, it generates on the Z-Image Turbo from the template.",
        "Turbo a jeho finetuny jedou na 8–12 krocích, nedestilovaný základ na 25 a víc." to
            "Turbo and its finetunes run on 8–12 steps, the undistilled base on 25 and up.",
        "Destilovaný model chce 1. Vyšší hodnota má smysl jen u nedestilovaného, kolem 4." to
            "A distilled model wants 1. A higher value only makes sense on an undistilled one, around 4.",
        "Model v GGUF načte uzel z balíku ComfyUI-GGUF — bez něj běh skončí chybou." to
            "A GGUF model is loaded by a node from the ComfyUI-GGUF pack — without it the run fails.",
        "Seznam modelů se nepodařilo načíst — server neodpovídá." to
            "The model list could not be loaded — the server is not responding.",

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
        "Veřejné aktualizace fungují bez tokenu." to "Public updates work without a token.",
        "Token je potřeba jen pro soukromá vydání. Zůstane v telefonu." to
            "A token is only needed for private releases. It stays on your phone.",
        "Uložit a zkontrolovat" to "Save and check",
        "Přejít na veřejná vydání" to "Switch to public releases",
        "Soukromá vydání" to "Private releases",
    )
}
