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
| [ComfyUI-ALLinONE-MinimaxH3](https://github.com/LeonQ8/ComfyUI-ALLinONE-MinimaxH3) | šablony workflow, endpoint `/h3one`, H3CacheBust, H3IdentityAnchor | All in One, Dialogy, Upravit video |
| rgthree-comfy | Power Lora Loader, Any Switch | video karty, Výměna tváře, Domalovat |
| LSI-Minimax-Segment-Timeline | LSIMinimaxTimeline(+Render) | Časová osa (balík není veřejný) |
| [ComfyUI-Spectrum-MiniMax-H3](https://github.com/xmarre/ComfyUI-Spectrum-MiniMax-H3) | SpectrumApplyMiniMaxH3 | Časová osa |
| ComfyUI-SeedVR2_VideoUpscaler | SeedVR2 nody | Zvětšit fotku |
| [praveen-tools](https://github.com/Praveenhalder/praveen-tools) | ImageTileSplit/Merge, LoadImageWithFilename | Zvětšit fotku |
| [ComfyUI-Smart-Upscaler](https://github.com/HallettVisual/ComfyUI-Smart-Upscaler) | SmartUpscaledTilePlanner, SmartCachedTextGenerate, SmartTileFinalizer… | Zvětšit fotku — Chytré zvětšení |
| [ComfyUI-BingImageSelector](https://github.com/concarne000/ComfyUI-BingImageSelector) | BingImageSelector, adresy `/bing_image_selector/*` | výběr obrázku — Hledat na internetu |
| [ComfyUI-DLSS5-Enhancer](https://github.com/Blueforcer/ComfyUI-DLSS5-Enhancer) | DLSS5Settings, DLSS5EnhanceImages/VideoFile | Zvětšit fotku — metoda DLSS 5 (po instalaci ještě `install_runtime.py`) |
| [Comfyui_Minimax_h3_latent_Upscaler](https://github.com/LBH-123-AI/Comfyui_Minimax_h3_latent_Upscaler) | MinimaxH3LatentUpscaler3D | Rychlé video; Long MiniMax — volba 3 + 2 |
| [Minimax-H3-Latent-Continuation](https://github.com/SatoDive/Minimax-H3-Latent-Continuation) | MiniMaxH3Easy_SatoDive, MiniMaxH3EasyContextSegments_SatoDive, Save/Load Latent, StitchContinuation aj. | Long MiniMax, Film ze storyboardu |
| [MaskVidExperiments](https://github.com/drozbay/MaskVidExperiments) | MVEx_MaskCleanup, MVEx_SubjectCrop/Uncrop aj. | Upravit video → Přemalovat |
| [ComfyUI-LTXVideo](https://github.com/Lightricks/ComfyUI-LTXVideo) | LTXVAudioVAEEncode, LTXVLatentUpsampler, LTXVImgToVideoInplace aj. (viz níž) | LTX 2.5 |
| VideoHelperSuite / KJNodes* | VHS_VideoCombine, VHS_LoadAudioUpload, PathchSageAttentionKJ, MiniMaxH3MemoryEfficientSageAttentionPatch, INTConstant, ModelPreviewOverrideKJ, ImageConcanate, ResizeMask, ImageResizeKJv2 | video karty, živý náhled, Výměna tváře |
| [comfyui-krea2edit](https://github.com/lbouaraba/comfyui-krea2edit) | Krea2EditModelPatch, Krea2EditGroundedEncode | Úprava obrázku — Krea 2 |
| ComfyUI-Inpaint-CropAndStitch | InpaintCropImproved/Stitch | Výměna tváře, Domalovat |
| [ComfyUI-AusBoss](https://github.com/ausboss/ComfyUI-AusBoss) | AUSBOSS_NODES_LoadImagePad, AUSBOSS_NODES_StitchInpaint | Domalovat → Rozšířit obrázek |
| ComfyUI-MiniMaxH3-TeaCache | MiniMaxH3TeaCache | video karty (volitelné zrychlení) |
| ComfyUI_essentials | ImageResize+ | Výměna tváře |
| Impact Pack* | ImpactGaussianBlurMask | Výměna tváře |
| ComfyUI-GGUF | UnetLoaderGGUF | Obrázek — volby ERNIE Image Turbo a Photoreal (modely v GGUF) |
| [MiniMax-H3-Prompt-Rewriter-ComfyUI](https://github.com/pytraveler/MiniMax-H3-Prompt-Rewriter-ComfyUI) | MiniMaxH3PromptWriter8B | tlačítko **✨ Vylepšit (MiniMax)** (volitelné, viz níž) |

Karta **Long MiniMax** navazuje záběry jedné scény přes **uložený latent**.
Navazovat přes hotové video znamená dekódovat a zase zakódovat, a ten okruh
obraz pokaždé o kus ztmaví; chyba se v řetězu sčítá. Balík proto latent
hotového záběru uloží do `output/h3_latents` a další běh z něj začne.
Modely navíc karta ve výchozí volbě Turbo nepotřebuje žádné: jede na
`minimax_h3_fl2va`, obou VAE a enkodéru jako ostatní video karty, plus na LoRA
`minimax_h3_ref2v_turbo_4step_v0.1_comfyui_bf16.safetensors`.

Karty **Obrázek** (Z-Image), **Hudba**, **Úhel kamery** a **3D model** jedou
jen na vestavěných uzlech ComfyUI — žádný custom balík nepotřebují
(ComfyUI-GGUF je potřeba až pro volby ERNIE Image Turbo a Photoreal na kartě
Obrázek). Platí u nich ale minimální verze jádra: uzly **TRELLIS.2** a
**Pixal3D/MoGe** pro kartu 3D model jsou v ComfyUI **od 0.34**, uzly
`YuE2GenerateABC`, `YuE2GenerateMusic` a `EmptyYuE2LatentAudio` pro volbu
**YuE2** na kartě Hudba **až od 0.36.0**. Na starší verzi se nic neinstaluje,
ComfyUI se aktualizuje.

Qwen Image 2.1 je **jeden model na generování i úpravy**, takže ho najdeš na
víc kartách: **Obrázek** (text→obrázek), **Úprava obrázku**, **Oprava
fotky**, **Domalovat** (včetně **Rozšířit obrázek**) a **Výměna tváře**.
FLUX.2 Klein je na tom podobně (Obrázek, Úprava obrázku, Domalovat). Qwen
Image 2.1 potřebuje vestavěné uzly `TextEncodeQwenImage21` a
`QwenImage21Cache`, které přišly až v **ComfyUI 0.37.0** — na 0.36.0 a
starším chybí. Žádný custom node se neinstaluje.

Karta **Úhel kamery** jede na **Qwen Image Edit 2511**, ne na 2.1. Schopnost
změnit pohled je v LoRA `qwen-image-edit-2511-multiple-angles` a ta je
trénovaná na 2511. Modely karty jsou v seznamu níž.

Volitelně k Qwen Image 2.1 patří **✨ Vylepšit (Qwen)** — vlastní přepisovače
promptu od Qwenu (`Qwen-Image-2.1-PE-I2I` a `-PE-T2I`) na vestavěném uzlu
`TextGenerate` (taky od 0.37.0). Zadání zvládnou i česky. Bez jejich vah
tlačítko jen ohlásí, že model chybí; zbytek karty funguje dál.

Karta **LTX 2.5** (všechny tři režimy: Z textu, Z obrázku a Ze zvuku)
potřebuje balík [ComfyUI-LTXVideo](https://github.com/Lightricks/ComfyUI-LTXVideo)
(uzly `LTXVAudioVAEEncode`, `LTXVConcatAVLatent`, `LTXVSeparateAVLatent`,
`LTXVLatentUpsampler`, `LTXVImgToVideoInplace`, `LTXVDualCFGGuider`,
`LTXVPreprocess`, `LatentUpscaleModelLoader`) a režim Ze zvuku k tomu
VideoHelperSuite kvůli `VHS_LoadAudioUpload`. Verze balíku musí znát
**LTX 2.5** — starší zná jen 2.3 a část uzlů v ní chybí.

Pozn.: veřejně nedostupný je jen balík **LSI-Minimax-Segment-Timeline** —
bez něj nepojede karta **Časová osa**. Ostatní karty fungují normálně.

\* Na referenčním serveru hlásí ComfyUI u těchhle tříd balík
`comfyui-workflow-encrypt`; při čisté instalaci pocházejí z VideoHelperSuite,
KJNodes a Impact Packu. Rozhoduje třída uzlu, ne jméno balíku — kontrola
v appce ověřuje třídy.

## Modely (složka `models/`)

Skript `instalace-serveru.bat` umí stáhnout všechno, co je veřejně dostupné —
po kartách, ať nestahuješ, co nepoužiješ.

**checkpoints/**
- `ace_step_1.5_turbo_aio.safetensors` (karta Hudba — volba ACE-Step)
- `yue2_3b_int8_convrot.safetensors` (karta Hudba — volba YuE2, 3,9 GB;
  ComfyUI 0.36.0+), z [Comfy-Org/YuE2](https://huggingface.co/Comfy-Org/YuE2)
- `yue2/ar_lora_inst_v3abc_comfyui.safetensors` (karta Film ze storyboardu —
  podkresová hudba, 213 MB) — do `models/loras/yue2`, z
  [Mothersuperior/YuE2-instrumental-cot-full-loras](https://huggingface.co/Mothersuperior/YuE2-instrumental-cot-full-loras).
  Instrumentální LoRA pro YuE2 (bez ní model po chvíli začne zpívat).
  **Licence CC BY-NC 4.0 — jen nekomerční použití.** Bez ní karta volbu hudby nenabídne.
- `sam3.1_multiplex_fp16.safetensors` (Upravit video → Přemalovat a Vyměnit
  postavu, 1,7 GB), z [Comfy-Org/sam3.1](https://huggingface.co/Comfy-Org/sam3.1)

**audio_encoders/**
- `sheetsage2_bf16.safetensors` (karta Hudba — **Předělat nahrávku**, 1,3 GB),
  z [Comfy-Org/YuE2](https://huggingface.co/Comfy-Org/YuE2). Přepisuje melodii
  z nahrávky do not; bez něj jede YuE2 dál, jen bez téhle volby. Uzly
  `SheetSage2AudioToABC` a `AudioEncoderLoader` jsou v jádře ComfyUI
  **od 0.36.0**, stejně jako YuE2.

**diffusion_models/**
- `ltx-2.5-22b-distilled-transformer-comfy-int8-convrot.safetensors`
  (karta LTX 2.5, **21 GB**), z
  [Lightricks/LTX-2.5](https://huggingface.co/Lightricks/LTX-2.5)
- `minimax_h3_fl2va_pruned_int8_convrot.safetensors` (video z textu/obrázků)
- `minimax_h3_ref2va_pruned_int8_convrot.safetensors` (reference → video, Dialogy)
  — obojí z [Comfy-Org/MiniMax-H3](https://huggingface.co/Comfy-Org/MiniMax-H3),
  po 19,5 GB
- `krea2_turbo_int8_convrot.safetensors` (Úprava obrázku — Krea 2, 13,5 GB), z [Comfy-Org/Krea-2](https://huggingface.co/Comfy-Org/Krea-2)
- `z_image_turbo_bf16.safetensors` (karta Obrázek)
- `z_image_bf16.safetensors` (karta Obrázek — volba Z-Image Base, 11,5 GB),
  z [Comfy-Org/z_image](https://huggingface.co/Comfy-Org/z_image)
- `qwen_image_2.1_int8_convrot.safetensors` (Obrázek, Úprava obrázku, Oprava
  fotky, Domalovat a Výměna tváře — Qwen Image 2.1,
  7,3 GB), z [Comfy-Org/Qwen-Image-2.1](https://huggingface.co/Comfy-Org/Qwen-Image-2.1)
- `qwen_image_edit_2511_fp8_e4m3fn.safetensors` (karta Úhel kamery — Qwen
  Image Edit 2511, 20,4 GB), z
  [drbaph/Qwen-Image-Edit-2511-FP8](https://huggingface.co/drbaph/Qwen-Image-Edit-2511-FP8)
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
- `gemma4-12b-with-proj-ltx-2.5-comfy-int8-convrot.safetensors` (karta
  LTX 2.5, **15 GB**), z
  [Lightricks/LTX-2.5](https://huggingface.co/Lightricks/LTX-2.5)
- `qwen3vl_32b_minimax_h3_nvfp4_awq.safetensors` (MiniMax H3, 14,6 GB)
- `qwen3vl_32b_minimax_h3_int8_convrot.safetensors` (karta Rychlé video, 25,3 GB) —
  ta předloha má enkodér zapsaný napevno, nvfp4 se u ní nepoužije
- `qwen3vl_4b_fp8_scaled.safetensors` (Úprava obrázku — Krea 2, a Chytré
  zvětšení, 4,9 GB), z [Comfy-Org/Krea-2](https://huggingface.co/Comfy-Org/Krea-2)
- `qwen_3_4b.safetensors` (karta Obrázek — Z-Image Turbo i Base)
- `qwen3vl_8b_int8_convrot.safetensors` (Obrázek, Úprava obrázku, Oprava fotky,
  Domalovat a Výměna tváře — Qwen Image 2.1,
  9,4 GB), z [Comfy-Org/Qwen-Image-2.1](https://huggingface.co/Comfy-Org/Qwen-Image-2.1)
- `qwen_2.5_vl_7b_fp8_scaled.safetensors` (karta Úhel kamery — enkodér k Qwen
  Image Edit 2511, 9,4 GB), z
  [Comfy-Org/Qwen-Image_ComfyUI](https://huggingface.co/Comfy-Org/Qwen-Image_ComfyUI)
- `qwen3.5_9b_qwen_image_2.1_pe_i2i.int8_convrot.safetensors` (**nepovinné** —
  ✨ Vylepšit (Qwen) u úprav, 9,5 GB), z [Comfy-Org/Qwen-Image-2.1](https://huggingface.co/Comfy-Org/Qwen-Image-2.1)
- `qwen3.5_9b_qwen_image_2.1_pe_t2i.int8_convrot.safetensors` (**nepovinné** —
  ✨ Vylepšit (Qwen) na kartě Obrázek, 9,5 GB), z téhož repozitáře
- `clip_l.safetensors` + `t5xxl_fp16.safetensors` (Výměna tváře, Domalovat — Flux Fill)
- `qwen_3_8b_fp8mixed.safetensors` (karta Obrázek a Domalovat — FLUX.2 Klein), z
  [Comfy-Org/vae-text-encorder-for-flux-klein-9b](https://huggingface.co/Comfy-Org/vae-text-encorder-for-flux-klein-9b)
- `ministral-3-3b.safetensors` (karta Obrázek — ERNIE, 7,2 GB), z
  [Comfy-Org/ERNIE-Image](https://huggingface.co/Comfy-Org/ERNIE-Image)

**vae/**
- `ltx-2.5-video-vae-bf16.safetensors` a `ltx-2.5-audio-vae-bf16.safetensors`
  (karta LTX 2.5; bez toho druhého se zvuk nedostane do latentu), z
  [Lightricks/LTX-2.5](https://huggingface.co/Lightricks/LTX-2.5)
- `minimax_h3_video_vae_fp16.safetensors`
- `minimax_h3_audio_vae_fp32.safetensors`
- `qwen_image_vae.safetensors` (Úprava obrázku — Krea 2, a karta Úhel kamery), z
  [Comfy-Org/Qwen-Image_ComfyUI](https://huggingface.co/Comfy-Org/Qwen-Image_ComfyUI)
- `qwen_image_2.1_vae_bf16.safetensors` (Obrázek, Úprava obrázku, Oprava fotky,
  Domalovat a Výměna tváře — Qwen Image 2.1,
  0,7 GB; 64kanálové RGBA VAE pro průhlednost), z
  [Comfy-Org/Qwen-Image-2.1](https://huggingface.co/Comfy-Org/Qwen-Image-2.1)
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
- `ltx-2.5-latent-spatial-upscaler-x2-bf16-1.0.safetensors` (karta LTX 2.5 —
  druhý průchod, 0,95 GB), z
  [Lightricks/LTX-2.5](https://huggingface.co/Lightricks/LTX-2.5)
- `minimax_h3_latent_upscaler_3d_fp16.safetensors` (karta Rychlé video a Long
  MiniMax — volba 3 + 2), z [LBH-123-AI/Minimax_h3_latent_Upscaler](https://huggingface.co/LBH-123-AI/Minimax_h3_latent_Upscaler)

**loras/**
- `krea2_identity_edit_v1_2.safetensors` (Úprava obrázku — Krea 2; drží
  identitu při úpravě). Je to LoRA **Krea 2 Identity Edit**, ke které patří
  balík [comfyui-krea2edit](https://github.com/lbouaraba/comfyui-krea2edit)
  — odkud ji vzít, píše jeho README.
- zrychlovací (Turbo) LoRA k MiniMax H3 podle výběru v appce — nabídka se čte
  ze serveru, veřejné jsou ve složce `loras/` v
  [Comfy-Org/MiniMax-H3](https://huggingface.co/Comfy-Org/MiniMax-H3)
- `comfyui_portrait_lora64.safetensors` (ACE++), `FLUX.1-Turbo-Alpha.safetensors` (Výměna tváře)
- `qwen-image-2.1-outpaint-v2.safetensors` (Domalovat → Rozšířit obrázek,
  0,16 GB; bez ní se fotka posune nebo zůstane šedá), z
  [ausboss/Qwen-Image-2.1-Outpaint-LoRA](https://huggingface.co/ausboss/Qwen-Image-2.1-Outpaint-LoRA)
- `qwen-image-edit-2511-multiple-angles-lora.safetensors` (karta Úhel kamery,
  0,3 GB; bez ní se pohled nezmění), z
  [fal/Qwen-Image-Edit-2511-Multiple-Angles-LoRA](https://huggingface.co/fal/Qwen-Image-Edit-2511-Multiple-Angles-LoRA)
- `Qwen-Image-Edit-2511-Lightning-8steps-V1.0-fp32.safetensors` (karta Úhel
  kamery — zrychlení na 8 kroků, 1,7 GB), z
  [lightx2v/Qwen-Image-Edit-2511-Lightning](https://huggingface.co/lightx2v/Qwen-Image-Edit-2511-Lightning)
- `h3/TaoMate-H3-3step-ComfyUI.safetensors` (karta Rychlé video a Long MiniMax
  — volby FastVideo VSA a 3 + 2, 2,5 GB) — v podsložce
  `h3` a přesně pod tímhle jménem; na ní celý postup stojí, bez ní jsou tři kroky
  málo. Originál vydává [TaoLiveAIGC/TaoMate-H3](https://huggingface.co/TaoLiveAIGC/TaoMate-H3),
  podoba pro ComfyUI je např. [Robert1212star/TaoMate-H3-3Step-ComfyUI](https://huggingface.co/Robert1212star/TaoMate-H3-3Step-ComfyUI)

**vae_approx/**
- `taeh3.safetensors` (živý náhled u videa, volitelné)

### Vylepšit video → Zplynulit

- `frame_interpolation/film_net_fp16.safetensors` (0,07 GB), z
  [Comfy-Org/frame_interpolation](https://huggingface.co/Comfy-Org/frame_interpolation).
  Uzly `FrameInterpolate` a `FrameInterpolationModelLoader` jsou v jádře ComfyUI.

### Výměna tváře → Qwen Image 2.1

Modely Qwen Image 2.1 jako karta Úprava obrázku, navíc dvě LoRA (uzly jsou v jádře ComfyUI;
`ImageResizeKJv2` z balíku KJNodes, sampler `deis_2m` z balíku RES4LYF):

- `loras/bfs_head_v1.1_qwen_2.1.safetensors` (0,26 GB), z
  [Alissonerdx/BFS-Best-Face-Swap](https://huggingface.co/Alissonerdx/BFS-Best-Face-Swap)
- `loras/p_qwen_image_2.1_8step_v0.1.safetensors` (0,34 GB), z
  [PrunaAI/Pruna-Qwen-Image-2.1](https://huggingface.co/PrunaAI/Pruna-Qwen-Image-2.1)

### Upravit video → Vyměnit postavu

Uzly `WanSCAILToVideo`, `SCAIL2ColoredMask` a `SAM3_VideoTrack` jsou v jádře
ComfyUI (ověřeno na 0.37.4).

- **SCAIL-2:** `diffusion_models/wan2.1_14B_SCAIL_2_int8_convrot.safetensors`
  (16,7 GB) a `loras/wan2.1_SCAIL_2_DPO_lora_bf16.safetensors` (1,2 GB),
  z [Comfy-Org/SCAIL-2](https://huggingface.co/Comfy-Org/SCAIL-2). K tomu
  `sam3.1_multiplex_fp16.safetensors` a sdílené soubory Wan z oddílu níže
  (`umt5_xxl_fp8_e4m3fn_scaled`, `Wan2_1_VAE_bf16`, `clip_vision_h`,
  `lightx2v_I2V_14B_480p_cfg_step_distill_rank64_bf16`).
- **MiniMax H3:** `loras/h3_character_swap_pro4500_1000.safetensors` (0,16 GB),
  z [akatz-ai/MiniMax-H3-Character-Swap-LoRA](https://huggingface.co/akatz-ai/MiniMax-H3-Character-Swap-LoRA);
  jinak modely MiniMax H3 jako karta All in One.

### Upravit video → Podle předlohy

MiniMax H3 s Fun ControlNet-Union; uzly `MiniMaxH3FunControlNetApply`,
`SDPoseKeypointExtractor`, `SDPoseDrawKeypoints` a `RTDETR_detect` jsou
v jádře ComfyUI (ověřeno na 0.37.4).

- `model_patches/minimax_h3_fun_controlnet_union_pruned_int8_convrot.safetensors`
  (2,3 GB), z [Comfy-Org/MiniMax-H3](https://huggingface.co/Comfy-Org/MiniMax-H3)
- `checkpoints/sdpose_wholebody_fp16.safetensors` (1,9 GB) a
  `diffusion_models/rt_detr_v4-x-hgnet_fp16.safetensors` (0,12 GB) pro pózu,
  z [Comfy-Org/SDPose](https://huggingface.co/Comfy-Org/SDPose)
- modely MiniMax H3 (`minimax_h3_ref2va_pruned_int8_convrot`, enkodér, obě VAE)
  a pro volbu Rychle `loras/minimax_h3_ref2v_turbo_4step_v0.1_comfyui_bf16.safetensors`

### Upravit video → Podle zadání

Bernini-R na Wan 2.2; uzel `BerniniConditioning` je v jádře ComfyUI
(ověřeno na 0.37.4).

- `diffusion_models/wan2.2_bernini_r_high_noise_int8_convrot.safetensors` a
  `wan2.2_bernini_r_low_noise_int8_convrot.safetensors` (po 14,5 GB),
  z [Comfy-Org/Bernini-R](https://huggingface.co/Comfy-Org/Bernini-R)
- `loras/lightx2v_T2V_14B_cfg_step_distill_v2_lora_rank64_bf16.safetensors`
  pro volbu Rychle, z [Kijai/WanVideo_comfy](https://huggingface.co/Kijai/WanVideo_comfy)
- `umt5_xxl_fp8_e4m3fn_scaled` a `Wan2_1_VAE_bf16` z oddílu níže

### Hudba → MiniMax Music 3

Uzly `MiniMaxMusic3TextEncode` a `EmptyMiniMaxMusic3LatentAudio` jsou v jádře
ComfyUI (ověřeno na 0.37.4). Vše z
[Comfy-Org/MiniMax-Music-3](https://huggingface.co/Comfy-Org/MiniMax-Music-3):

- `diffusion_models/minimax_music3_dit_fp16.safetensors` (4,9 GB)
- `text_encoders/minimax_music3_text_encoder_pruned_int8_convrot.safetensors` (9,2 GB)
- `vae/minimax_music3_dav.safetensors` (0,22 GB)

### Pohyb postavy (Wan) — Podle hudby a Podle videa

Režim **Podle hudby** jede na Wan-Dancer, **Podle videa** na Wan-Animate 2.
Uzly `WanDancer*`, `WanAnimate2ToVideo`, `WanAnimate2Cache` a smyčka
`StartLoop`/`EndLoop` jsou v jádře ComfyUI (Wan-Animate 2 potřebuje **0.36.0+**).

- `diffusion_models/wan_animate_2_int8_convrot.safetensors` (Podle videa, 16,7 GB),
  z [Comfy-Org/Wan-Animate-2](https://huggingface.co/Comfy-Org/Wan-Animate-2)
- `text_encoders/umt5_xxl_fp8_e4m3fn_scaled.safetensors` (Podle videa, 6,7 GB),
  z [Comfy-Org/Wan-Animate-2](https://huggingface.co/Comfy-Org/Wan-Animate-2)
- `diffusion_models/wan2.2_dancer_14b_global_fp8_scaled.safetensors` a
  `wan2.2_dancer_14b_local_fp8_scaled.safetensors` (Podle hudby, po 17,1 GB),
  z [Comfy-Org/Wan-Dancer](https://huggingface.co/Comfy-Org/Wan-Dancer)
- `text_encoders/umt5_xxl_fp16.safetensors` (Podle hudby, 11,4 GB)
- `vae/Wan2_1_VAE_bf16.safetensors`, `clip_vision/clip_vision_h.safetensors`
  a `loras/lightx2v_I2V_14B_480p_cfg_step_distill_rank64_bf16.safetensors`
  (oba režimy)

### Licence Qwen Image 2.1

Qwen Image 2.1 není pod Apache 2.0. Váhy byly vydány pod
**Qwen Research License Agreement** a bez zvláštní licence jsou povolené
jen pro **nekomerční výzkum a vyhodnocování**. Pro komerční použití autoři
vyžadují samostatnou licenci přes `model-business@notice.qwencloud.com`.
Totéž platí pro oba přepisovače promptu `Qwen-Image-2.1-PE-I2I` a `-PE-T2I`.

Aplikace **váhy nešíří ani nestahuje** — uživatel je přebírá přímo z Hugging
Face a musí podmínky přijmout sám. Součástí aplikace jsou ale **systémové
prompty** obou přepisovačů (`system_prompt.txt` z repozitářů autorů), bez
kterých by modely nefungovaly. Licence jejich šíření pro nekomerční účely
dovoluje; její plné znění je v repozitáři jako
[`LICENSE-Qwen-Research.txt`](LICENSE-Qwen-Research.txt) a odkaz na originál:
[QwenLM/Qwen-Image-2.1 LICENSE](https://github.com/QwenLM/Qwen-Image-2.1/blob/main/LICENSE).

**SeedVR2** (karta Zvětšit fotku): `seedvr2_ema_7b-Q4_K_M.gguf` a
`ema_vae_fp16.safetensors` si balík **stáhne sám při prvním použití**.
Metoda **DLSS 5** na téže kartě žádný model nemá, ale po instalaci balíku
je potřeba jednou spustit jeho `install_runtime.py` — stáhne runtime třetí
strany (~470 MB) a zeptá se na licenci.

**Chytré zvětšení** (karta Zvětšit fotku) potřebuje vedle Z-Image Turbo ještě
`qwen3vl_4b_fp8_scaled.safetensors` (models/text_encoders, 4,9 GB,
[Comfy-Org/Krea-2](https://huggingface.co/Comfy-Org/Krea-2/resolve/main/text_encoders/qwen3vl_4b_fp8_scaled.safetensors))
a `Z-Image-Turbo-Fun-Controlnet-Tile-2.1-lite-2601-8steps.safetensors`
(models/model_patches, 2 GB,
[alibaba-pai](https://huggingface.co/alibaba-pai/Z-Image-Turbo-Fun-Controlnet-Union-2.1)).

## Volitelné

- **Higgs Audio Studio** (port 7860) — jen pro namlouvání replik na kartě
  Dialogy; bez něj zbytek appky funguje normálně.
- **Spouštěč na počítači** (port 8190) — umí ComfyUI na dálku zapnout
  a vypnout (uvolnit grafiku). Bez něj musí ComfyUI běžet, když appku používáš.
- **Tlačítka ✨ Vylepšit** — napíšeš pár slov, klidně česky, a jazykový
  model z nich složí plný anglický prompt. Podle karty jde o tyhle přepisovače:

  | Tlačítko | Karty | Co potřebuje |
  |---|---|---|
  | **✨ Vylepšit (MiniMax)** | All in One, Rychlé video, Long MiniMax (navázání záběru jede přes llama.cpp z řádku níž) | balík [MiniMax-H3-Prompt-Rewriter-ComfyUI](https://github.com/pytraveler/MiniMax-H3-Prompt-Rewriter-ComfyUI), model v `models/LLM`: základ + projektor + adaptér |
  | **✨ Vylepšit** a **✨ Vylepšit (odvázaně)** | Obrázek, Úprava obrázku (s Qwen Image 2.1), LTX 2.5 | balík [ComfyUI-llama-cpp_vlm](https://github.com/lihaoyun6/ComfyUI-llama-cpp_vlm), model v `models/LLM`: stačí základ |
  | **✨ Vylepšit (Qwen)** | Obrázek a Úprava obrázku s Qwen Image 2.1 | přepisovače PE-T2I / PE-I2I (viz výš), vestavěný uzel `TextGenerate` |
  | **✨ Vylepšit (LTX)** | LTX 2.5 | nic navíc — jede na enkodéru `gemma4` z téže karty |

  **Jede to jako obyčejné workflow ve tvém ComfyUI** — appka pošle malý graf
  na `/prompt` a přečte si z historie hotový text. V telefonu se tedy nic
  nestahuje a nic neodchází z domu.

  Soubory pro `models/LLM` (dohromady ~7 GB; `instalace-serveru.bat` je umí
  stáhnout za tebe):
  - **základ:** [Huihui-Qwen3-VL-8B-Instruct-abliterated Q4_K_M](https://huggingface.co/noctrex/Huihui-Qwen3-VL-8B-Instruct-abliterated-GGUF/resolve/main/Huihui-Qwen3-VL-8B-Instruct-abliterated-Q4_K_M.gguf)
    (4,7 GB) — odblokovaná verze, takže zadání neodmítá,
  - **projektor:** [mmproj-F16](https://huggingface.co/noctrex/Huihui-Qwen3-VL-8B-Instruct-abliterated-GGUF/resolve/main/mmproj-F16.gguf) (1,1 GB) ze stejného repozitáře;
    ulož ho jako `Huihui-Qwen3-VL-8B-Instruct-abliterated-mmproj-F16.gguf`,
  - **adaptér (jen pro ✨ Vylepšit (MiniMax)):**
    [MiniMax-H3-Prompt-Rewriter-LoRA-8B-F16](https://huggingface.co/pytraveler/MiniMax-H3-Prompt-Rewriter-LoRA-8B-GGUF/resolve/main/MiniMax-H3-Prompt-Rewriter-LoRA-8B-F16.gguf) (1,3 GB).

  **Film ze storyboardu – „Storyboard + scénář“** používá stejný základ přes
  [ComfyUI-llama-cpp_vlm](https://github.com/lihaoyun6/ComfyUI-llama-cpp_vlm) i jako druhé čtení scénáře
  (kontrola, že se žádná replika nevynechala). Bez něj karta funguje dál, jen bez této kontroly.

  Tentýž balík a model obsluhuje i tlačítko **🌐 Přeložit**
  u karet se zadáním — nic dalšího se pro překlad stahovat nemusí.

  Uzel přepisovače MiniMax si model umí stáhnout i sám při prvním použití
  (nabídne ho v seznamu); uzel llama.cpp to neumí — tam soubor ve složce
  `models/LLM` opravdu být musí. Appka si pak model z nabídky serveru vybere
  sama (přednost má odblokovaný) a po dopsání promptu ho uvolní z paměti
  grafiky, takže generování neomezí. Bez toho appka funguje normálně, jen
  tlačítko ohlásí, co na serveru chybí.
- **Karta Obrázek — model „Photoreal (odvázaný)"** (18+): očekává soubor
  `zimage_nsfw_photoreal_v61_Q8.gguf` v `models/diffusion_models/` (nebo
  `models/unet/`, pro ComfyUI táž složka), který si stáhneš sám z CivitAI;
  načítá ho uzel `UnetLoaderGGUF` z balíku ComfyUI-GGUF. Bez něj karta jede
  normálně na ostatních modelech.
- **Karta Obrázek — vlastní LoRA**: karta má dvě pole, **LoRA pro <model>**
  a **Druhá LoRA (nepovinná)**. Nabídka se čte ze `models/loras/` na serveru
  a řadí se podle vybraného modelu: appka pozná rodinu LoRA z metadat
  souboru, případně z názvu (`zimage`/`zit` pro Z-Image, `ernie`, `klein`
  + `9b` pro FLUX.2 Klein 9B, LoRA pro Qwen Image 2.1). Soubory bez poznatelného
  modelu nabídne pod čarou jako „neoznačený model" a před použitím se zeptá.
  **Přejmenovávat nic nemusíš.**

## Jak ověřit, že máš všechno

V appce: **Nastavení → Co serveru chybí → Zkontrolovat server.** Appka se
zeptá serveru na každou třídu uzlu ze svých workflow a u výběrových vstupů
(modely, LoRA, VAE) ověří, že server soubor opravdu nabízí. Co chybí, vypíše
jmenovitě — nody doinstaluj přes ComfyUI-Manager, modely nahraj do složek výš.

### Detailer pro Qwen Image 2.1

`elusarcas-qwen2-1-detailer-v1.safetensors` (80 MB) do `models/loras` —
z [reverentelusarca/elusarcas-qwen-2.1-detail-enhancer-lora](https://huggingface.co/reverentelusarca/elusarcas-qwen-2.1-detail-enhancer-lora).
Karta **Oprava fotky** ho používá vždy; v kartách **Úprava obrázku**
a **Obrázek** (Qwen Image 2.1) je to přepínač **Detailer**.
