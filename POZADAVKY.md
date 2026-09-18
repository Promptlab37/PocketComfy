***Česky** · [English](REQUIREMENTS.en.md)*

# Co appka potřebuje na serveru

PocketComfy je **dálkové ovládání ComfyUI z mobilu** — samo nic negeneruje,
všechno počítá tvůj počítač. Tenhle soubor říká, co na něm musí být.
Seznam je vygenerovaný přímo z workflow, která appka používá (stejná data
čte tlačítko **Nastavení → Zkontrolovat server**, které vypíše, co konkrétně
chybí u tebe).

## Základ

- **ComfyUI** — aktuální verze (nody MiniMax H3 jsou součástí ComfyUI,
  `comfy_extras/nodes_minimax_h3`), spuštěné s `--listen 0.0.0.0`.
- Telefon na stejné síti, nebo VPN (např. Tailscale).
- Doporučené: **ComfyUI-Manager** — chybějící custom nody přes něj
  doinstaluješ na pár kliknutí.

## Custom nody (podle karet)

| Balík | Poskytuje | Potřebují karty |
|---|---|---|
| ComfyUI-ALLinONE-MinimaxH3 | šablony workflow, endpoint `/h3one` | All in One, Dialogy |
| rgthree-comfy | Power Lora Loader, Any Switch | video karty, Výměna tváře |
| LSI-Minimax-Segment-Timeline | LSIMinimaxTimeline(+Render) | Časová osa |
| ComfyUI-SeedVR2_VideoUpscaler | SeedVR2 nody | Zvětšit |
| [praveen-tools](https://github.com/Praveenhalder/praveen-tools) | ImageTileSplit/Merge, LoadImageWithFilename | Zvětšit |
| [ComfyUI-DLSS5-Enhancer](https://github.com/Blueforcer/ComfyUI-DLSS5-Enhancer) | DLSS5Settings, DLSS5EnhanceImages/VideoFile | Zvětšit — metoda DLSS 5 (po instalaci ještě `install_runtime.py`) |
| [ComfyUI-H3-Motion-Context-MultiRef](https://github.com/seitanism/ComfyUI-H3-Motion-Context-MultiRef) | MiniMaxH3StartMaskedContext, MiniMaxH3GeneratedAVMaskedContext, MiniMaxH3CropTo32 aj. | Dlouhé video |
| [Comfyui_Minimax_h3_latent_Upscaler](https://github.com/LBH-123-AI/Comfyui_Minimax_h3_latent_Upscaler) | MinimaxH3LatentUpscaler3D | Dlouhé video — rychlý první záběr (volitelné) |
| [MaskVidExperiments](https://github.com/drozbay/MaskVidExperiments) | MVEx_MaskCleanup, MVEx_SubjectCrop/Uncrop aj. | All in One → Přemalovat ve videu |
| VideoHelperSuite / KJNodes* | VHS_VideoCombine, PathchSageAttentionKJ, INTConstant, ModelPreviewOverrideKJ, ImageConcanate, ResizeMask | video karty, živý náhled, Výměna tváře |
| nody Krea 2 Edit* | Krea2EditModelPatch, Krea2EditGroundedEncode, SpectrumApplyMiniMaxH3, H3CacheBust | Úprava obrázku, video karty |
| ComfyUI-Inpaint-CropAndStitch | InpaintCropImproved/Stitch | Výměna tváře, Domalovat |
| ComfyUI-MiniMaxH3-TeaCache | MiniMaxH3TeaCache | video karty (volitelné zrychlení) |
| Comfyui-QwenEditUtils | QwenEditConfigPreparer, TextEncodeQwenImageEditPlusCustom | Oprava fotky |
| ComfyUI_essentials | ImageResize+ | Výměna tváře |
| Impact Pack* | ImpactGaussianBlurMask | Výměna tváře |
| ComfyUI-GGUF | UnetLoaderGGUF | Obrázek — jen volitelný alternativní model ve formátu GGUF |
| [MiniMax-H3-Prompt-Rewriter-ComfyUI](https://github.com/pytraveler/MiniMax-H3-Prompt-Rewriter-ComfyUI) | MiniMaxH3PromptWriter8B | tlačítko **✨ Vylepšit prompt** (volitelné, viz níž) |

Karty **Obrázek** (Z-Image), **Hudba**, **Úhel kamery** a **3D model** jedou
jen na vestavěných uzlech ComfyUI — žádný custom balík nepotřebují
(ComfyUI-GGUF je potřeba až pro volitelný alternativní model na kartě
Obrázek). Platí u nich ale minimální verze jádra: uzly **TRELLIS.2** a
**Pixal3D/MoGe** pro kartu 3D model jsou v ComfyUI **od 0.34**, uzly
`YuE2GenerateABC`, `YuE2GenerateMusic` a `EmptyYuE2LatentAudio` pro volbu
**YuE2** na kartě Hudba **až od 0.36.0**. Na starší verzi se nic neinstaluje,
ComfyUI se aktualizuje.

Karta **Video ze zvuku** potřebuje balík
[ComfyUI-LTXVideo](https://github.com/Lightricks/ComfyUI-LTXVideo) (uzly
`LTXVAudioVAEEncode`, `LTXVConcatAVLatent`, `LTXVSeparateAVLatent`,
`LTXVLatentUpsampler`, `LTXVImgToVideoInplace`, `LTXVDualCFGGuider`,
`LTXVPreprocess`, `LatentUpscaleModelLoader`) a k tomu VideoHelperSuite kvůli
`VHS_LoadAudioUpload`. Verze balíku musí znát **LTX 2.5** — starší zná jen 2.3
a část uzlů v ní chybí.

Pozn.: balíky LSI-Minimax-Segment-Timeline a balík s uzly Krea2Edit/H3
nejsou veřejně dostupné — bez nich nepojedou karty Časová osa a Úprava
obrázku (a živý náhled u videa). Ostatní karty fungují normálně.

\* Na referenčním serveru tyhle třídy poskytuje balík `comfyui-workflow-encrypt`;
při čisté instalaci pocházejí z VideoHelperSuite, KJNodes a balíku Krea 2 Edit.
Rozhoduje třída uzlu, ne jméno balíku — kontrola v appce ověřuje třídy.

## Modely (složka `models/`)

Skript `instalace-serveru.bat` umí stáhnout všechno, co je veřejně dostupné —
po kartách, ať nestahuješ, co nepoužiješ.

**checkpoints/**
- `ace_step_1.5_turbo_aio.safetensors` (karta Hudba — volba ACE-Step)
- `yue2_3b_int8_convrot.safetensors` (karta Hudba — volba YuE2, 3,9 GB;
  ComfyUI 0.36.0+), z [Comfy-Org/YuE2](https://huggingface.co/Comfy-Org/YuE2)
- `sam3.1_multiplex_fp16.safetensors` (All in One → Přemalovat ve videu, 1,7 GB),
  z [Comfy-Org/sam3.1](https://huggingface.co/Comfy-Org/sam3.1)

**audio_encoders/**
- `sheetsage2_bf16.safetensors` (karta Hudba — **Předělat nahrávku**, 1,3 GB),
  z [Comfy-Org/YuE2](https://huggingface.co/Comfy-Org/YuE2). Přepisuje melodii
  z nahrávky do not; bez něj jede YuE2 dál, jen bez téhle volby. Uzly
  `SheetSage2AudioToABC` a `AudioEncoderLoader` jsou v jádře ComfyUI
  **od 0.36.0**, stejně jako YuE2.

**diffusion_models/**
- `ltx-2.5-22b-distilled-transformer-comfy-int8-convrot.safetensors`
  (karta Video ze zvuku, **21 GB**), z
  [Lightricks/LTX-2.5](https://huggingface.co/Lightricks/LTX-2.5)
- `minimax_h3_fl2va_pruned_int8_convrot.safetensors` (video z textu/obrázků)
- `minimax_h3_ref2va_pruned_int8_convrot.safetensors` (reference → video, Dialogy)
  — obojí z [Comfy-Org/MiniMax-H3](https://huggingface.co/Comfy-Org/MiniMax-H3),
  po 19,5 GB
- `krea2_turbo_fp8_scaled.safetensors` (Úprava obrázku)
- `z_image_turbo_bf16.safetensors` (karta Obrázek)
- `z_image_bf16.safetensors` (karta Obrázek — volba Z-Image Base, 11,5 GB),
  z [Comfy-Org/z_image](https://huggingface.co/Comfy-Org/z_image)
- `qwen_image_edit_2511_fp8_e4m3fn.safetensors` (Oprava fotky a Úhel kamery)
- `flux1-Fill-Dev_FP8.safetensors` (Výměna tváře, Domalovat — volba Flux Fill)
- `flux-2-klein-9b.safetensors` (Domalovat a karta Obrázek — volba FLUX.2 Klein;
  stáhni fp8 vydání z [black-forest-labs/FLUX.2-klein-9b-fp8](https://huggingface.co/black-forest-labs/FLUX.2-klein-9b-fp8),
  ~9,5 GB, licenci je potřeba na Hugging Face odklepnout, a ulož pod tímhle názvem)
- `trellis_2_int8_convrot.safetensors` (karta 3D model, 4,9 GB), z
  [Comfy-Org/TRELLIS.2](https://huggingface.co/Comfy-Org/TRELLIS.2)
- `pixal3d_int8_convrot.safetensors` (karta 3D model — druhý motor Pixal3D,
  5,6 GB), z [Comfy-Org/Pixal3D](https://huggingface.co/Comfy-Org/Pixal3D)

**unet/** — pozor, `models/unet` a `models/diffusion_models` jsou pro ComfyUI
**jedna a táž složka** (`unet` je jen starší název). Model v GGUF může ležet
v kterékoli z nich, stěhovat ho nemusíš.
- `ernie-image-turbo-Q8_0.gguf` (karta Obrázek — volba ERNIE Image Turbo, 8,1 GB),
  z [unsloth/ERNIE-Image-Turbo-GGUF](https://huggingface.co/unsloth/ERNIE-Image-Turbo-GGUF);
  načítá ho uzel `UnetLoaderGGUF` z balíku ComfyUI-GGUF

**text_encoders/**
- `gemma4-12b-with-proj-ltx-2.5-comfy-int8-convrot.safetensors` (karta Video
  ze zvuku, **15 GB**), z
  [Lightricks/LTX-2.5](https://huggingface.co/Lightricks/LTX-2.5)
- `qwen3vl_32b_minimax_h3_nvfp4_awq.safetensors` (MiniMax H3, 14,6 GB)
- `qwen3vl_32b_minimax_h3_int8_convrot.safetensors` (karta 3 kroky, 25,3 GB) —
  ta pred‑loha má enkodér zapsaný napevno, nvfp4 se u ní nepoužije
- `qwen3vl_4b_fp8_scaled.safetensors` (Krea 2)
- `qwen_3_4b.safetensors` (karta Obrázek — Z-Image Turbo i Base)
- `qwen_2.5_vl_7b_fp8_scaled.safetensors` (Oprava fotky, Úhel kamery)
- `clip_l.safetensors` + `t5xxl_fp16.safetensors` (Výměna tváře, Domalovat — Flux Fill)
- `qwen_3_8b_fp8mixed.safetensors` (karta Obrázek a Domalovat — FLUX.2 Klein), z
  [Comfy-Org/vae-text-encorder-for-flux-klein-9b](https://huggingface.co/Comfy-Org/vae-text-encorder-for-flux-klein-9b)
- `ministral-3-3b.safetensors` (karta Obrázek — ERNIE, 7,2 GB), z
  [Comfy-Org/ERNIE-Image](https://huggingface.co/Comfy-Org/ERNIE-Image)

**vae/**
- `ltx-2.5-video-vae-bf16.safetensors` a `ltx-2.5-audio-vae-bf16.safetensors`
  (karta Video ze zvuku; bez toho druhého se zvuk nedostane do latentu), z
  [Lightricks/LTX-2.5](https://huggingface.co/Lightricks/LTX-2.5)
- `minimax_h3_video_vae_fp16.safetensors`
- `minimax_h3_audio_vae_fp32.safetensors`
- `qwen_image_vae.safetensors` (Krea 2, Oprava fotky, Úhel kamery)
- `ae.sft` (karta Obrázek, Domalovat a Výměna tváře — FLUX/Z-Image autoenkodér)
- `flux2-vae.safetensors` (karta Obrázek a Domalovat — FLUX.2 Klein a ERNIE),
  ze stejného repozitáře jako enkodér výš
- `full_encoder_small_decoder.safetensors` (úprava obrázku přes FLUX.2 Klein —
  ta předlohu **kóduje**, takže chce plný enkodér), z
  [black-forest-labs/FLUX.2-small-decoder](https://huggingface.co/black-forest-labs/FLUX.2-small-decoder)
- `trellis_2_shape_vae_bf16.safetensors` + `trellis_2_texture_vae_bf16.safetensors`
  (karta 3D model, oba motory)

**clip_vision/**
- `dino_v3_vit_l.safetensors` (karta 3D model — TRELLIS.2)
- `dino_v3_L_naf_fp32.safetensors` (karta 3D model — Pixal3D)

**background_removal/**
- `birefnet.safetensors` (karta 3D model — odstranění pozadí z fotky), z
  [Comfy-Org/BiRefNet](https://huggingface.co/Comfy-Org/BiRefNet)

**geometry_estimation/**
- `moge_2_vitl_normal_fp16.safetensors` (karta 3D model — Pixal3D odhaduje
  z fotky úhel objektivu), z [Comfy-Org/MoGe](https://huggingface.co/Comfy-Org/MoGe)

**latent_upscale_models/**
- `ltx-2.5-latent-spatial-upscaler-x2-bf16-1.0.safetensors` (karta Video ze
  zvuku — druhý průchod, 0,95 GB), z
  [Lightricks/LTX-2.5](https://huggingface.co/Lightricks/LTX-2.5)
- `minimax_h3_latent_upscaler_3d_fp16.safetensors` (Dlouhé video — rychlý první
  záběr — a karta 3 kroky), z [LBH-123-AI/Minimax_h3_latent_Upscaler](https://huggingface.co/LBH-123-AI/Minimax_h3_latent_Upscaler)

**loras/**
- `krea2_identity_edit_v1_2.safetensors` (drží identitu při úpravě obrázku)
- zrychlovací (Turbo) LoRA k MiniMax H3 podle výběru v appce — nabídka se čte
  ze serveru, veřejné jsou ve složce `loras/` v
  [Comfy-Org/MiniMax-H3](https://huggingface.co/Comfy-Org/MiniMax-H3)
- `Qwen-Image-Edit-2511-Lightning-4steps-V1.0-fp32.safetensors`
  ([lightx2v](https://huggingface.co/lightx2v/Qwen-Image-Edit-2511-Lightning)),
  `qwen_image_edit_2511_upscale.safetensors`
  ([starsfriday](https://huggingface.co/starsfriday/Qwen-Image-Edit-2511-Upscale2K)),
  `flymy_realism.safetensors`
  ([flymy-ai](https://huggingface.co/flymy-ai/qwen-image-realism-lora)) — Oprava fotky
- `qwen-image-edit-2511-multiple-angles-lora.safetensors`
  ([fal](https://huggingface.co/fal/Qwen-Image-Edit-2511-Multiple-Angles-LoRA)) +
  `Qwen-Image-Edit-2511-Lightning-8steps-V1.0-fp32.safetensors` (Úhel kamery)
- `comfyui_portrait_lora64.safetensors` (ACE++), `FLUX.1-Turbo-Alpha.safetensors` (Výměna tváře)
- `h3/TaoMate-H3-3step-ComfyUI.safetensors` (karta 3 kroky, 2,5 GB) — v podsložce
  `h3` a přesně pod tímhle jménem; na ní celý postup stojí, bez ní jsou tři kroky
  málo. Originál vydává [TaoLiveAIGC/TaoMate-H3](https://huggingface.co/TaoLiveAIGC/TaoMate-H3),
  podoba pro ComfyUI je např. [Robert1212star/TaoMate-H3-3Step-ComfyUI](https://huggingface.co/Robert1212star/TaoMate-H3-3Step-ComfyUI)

**vae_approx/**
- `taeh3.safetensors` (živý náhled u videa, volitelné)

**SeedVR2** (karta Zvětšit): `seedvr2_ema_7b-Q4_K_M.gguf` a
`ema_vae_fp16.safetensors` si balík **stáhne sám při prvním použití**.
Metoda **DLSS 5** na téže kartě žádný model nemá, ale po instalaci balíku
je potřeba jednou spustit jeho `install_runtime.py` — stáhne runtime třetí
strany (~470 MB) a zeptá se na licenci.

## Volitelné

- **Higgs Audio Studio** (port 7860) — jen pro namlouvání replik na kartě
  Dialogy; bez něj zbytek appky funguje normálně.
- **Spouštěč na počítači** (port 8190) — umí ComfyUI na dálku zapnout
  a vypnout (uvolnit grafiku). Bez něj musí ComfyUI běžet, když appku používáš.
- **✨ Vylepšit prompt** (karty All in One a Obrázek) — napíšeš pár slov,
  klidně česky, a jazykový model z nich složí plný anglický prompt: u videa
  včetně záběrů, časování a zvuku, u obrázku podle pravidel Z-Image (souvislé
  věty, důraz na světlo, bez negativního promptu).

  **Jede to jako obyčejné workflow ve tvém ComfyUI** — appka pošle malý graf
  na `/prompt` a přečte si z historie hotový text. V telefonu se tedy nic
  nestahuje a nic neodchází z domu; potřebuješ ale na serveru balík uzlů
  a jeden model v `models/LLM`:

  | | video (All in One) | obrázek |
  |---|---|---|
  | balík | [MiniMax-H3-Prompt-Rewriter-ComfyUI](https://github.com/pytraveler/MiniMax-H3-Prompt-Rewriter-ComfyUI) | [ComfyUI-llama-cpp_vlm](https://github.com/lihaoyun6/ComfyUI-llama-cpp_vlm) |
  | model | základ + projektor + adaptér | stačí ten základ |

  Konkrétní soubory (dohromady ~7 GB; `instalace-serveru.bat` je umí stáhnout
  za tebe do `models/LLM`):
  - **základ:** [Huihui-Qwen3-VL-8B-Instruct-abliterated Q4_K_M](https://huggingface.co/noctrex/Huihui-Qwen3-VL-8B-Instruct-abliterated-GGUF/resolve/main/Huihui-Qwen3-VL-8B-Instruct-abliterated-Q4_K_M.gguf)
    (4,7 GB) — odblokovaná verze, takže zadání neodmítá,
  - **projektor:** [mmproj-F16](https://huggingface.co/noctrex/Huihui-Qwen3-VL-8B-Instruct-abliterated-GGUF/resolve/main/mmproj-F16.gguf) (1,1 GB) ze stejného repozitáře;
    ulož ho jako `Huihui-Qwen3-VL-8B-Instruct-abliterated-mmproj-F16.gguf`,
  - **adaptér (jen pro video):**
    [MiniMax-H3-Prompt-Rewriter-LoRA-8B-F16](https://huggingface.co/pytraveler/MiniMax-H3-Prompt-Rewriter-LoRA-8B-GGUF/resolve/main/MiniMax-H3-Prompt-Rewriter-LoRA-8B-F16.gguf) (1,3 GB).

  Tentýž balík a model obsluhuje i tlačítko **🌐 Přeložit do angličtiny**
  u karet s zadáním — nic dalšího se pro překlad stahovat nemusí.

  Uzel přepisovače videa si model umí stáhnout i sám při prvním použití
  (nabídne ho v seznamu); u karty Obrázek to neumí — tam soubor ve složce
  `models/LLM` opravdu být musí. Appka si pak model z nabídky serveru vybere
  sama (přednost má odblokovaný) a po dopsání promptu ho uvolní z paměti
  grafiky, takže generování neomezí. Bez toho appka funguje normálně, jen
  tlačítko ohlásí, co na serveru chybí.
- **Karta Obrázek — vlastní model**: kromě pěti připravených voleb umí karta
  jet na **jakémkoli dalším modelu z rodiny Z-Image, který máš na serveru**.
  Volba **Vlastní model** načte nabídku přímo z ComfyUI (`UNETLoader`
  i `UnetLoaderGGUF`, takže safetensors i GGUF), soubor si vybereš ze seznamu
  a k němu nastavíš kroky a cfg — z názvu souboru se nepoznají.
  Destilované finetuny Turba jedou na cfg 1 a 8–12 krocích, nedestilovaný
  základ na cfg kolem 4 a 25 krocích a víc. Soubor patří do
  `models/diffusion_models/` (nebo `models/unet/`, je to pro ComfyUI táž
  složka), model v GGUF k tomu chce balík ComfyUI-GGUF. **Přejmenovávat
  nic nemusíš** — appka nabídne, co server hlásí.
- **Karta Obrázek — přepínač „Bez cenzury" a model „Photoreal"** (18+):
  obojí vyžaduje vlastní soubory, které si stáhneš sám (CivitAI /
  Hugging Face, hledej Z-Image Turbo LoRA a finetuny). LoRA patří do
  `models/loras/` a appka nabídne **každou, která má v názvu
  `zimage`/`zit`** — pojmenuj ji tak a objeví se sama. Hotová volba
  **Photoreal** očekává soubor pod názvem
  `zimage_nsfw_photoreal_v61_Q8.gguf`; jiný finetune se dá použít bez
  přejmenování přes volbu **Vlastní model** výš.
  Bez těchto souborů karta Obrázek normálně jede na základním Z-Image Turbo.

## Jak ověřit, že máš všechno

V appce: **Nastavení → Co serveru chybí → Zkontrolovat server.** Appka se
zeptá serveru na každou třídu uzlu ze svých workflow a u výběrových vstupů
(modely, LoRA, VAE) ověří, že server soubor opravdu nabízí. Co chybí, vypíše
jmenovitě — nody doinstaluj přes ComfyUI-Manager, modely nahraj do složek výš.
