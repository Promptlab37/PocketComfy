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
| praveen-tools | ImageTileSplit/Merge, LoadImageWithFilename | Zvětšit |
| VideoHelperSuite / KJNodes* | VHS_VideoCombine, PathchSageAttentionKJ, INTConstant, ModelPreviewOverrideKJ, ImageConcanate, ResizeMask | video karty, živý náhled, Výměna tváře |
| nody Krea 2 Edit* | Krea2EditModelPatch, Krea2EditGroundedEncode, SpectrumApplyMiniMaxH3, H3CacheBust | Úprava obrázku, video karty |
| ComfyUI-Inpaint-CropAndStitch | InpaintCropImproved/Stitch | Výměna tváře |
| ComfyUI-MiniMaxH3-TeaCache | MiniMaxH3TeaCache | video karty (volitelné zrychlení) |
| Comfyui-QwenEditUtils | QwenEditConfigPreparer, TextEncodeQwenImageEditPlusCustom | Oprava fotky |
| ComfyUI_essentials | ImageResize+ | Výměna tváře |
| Impact Pack* | ImpactGaussianBlurMask | Výměna tváře |
| ComfyUI-GGUF | UnetLoaderGGUF | Obrázek — jen volitelný alternativní model ve formátu GGUF |
| [MiniMax-H3-Prompt-Rewriter-ComfyUI](https://github.com/pytraveler/MiniMax-H3-Prompt-Rewriter-ComfyUI) | MiniMaxH3PromptWriter8B | tlačítko **✨ Vylepšit prompt** (volitelné, viz níž) |

Karty **Obrázek** (Z-Image) a **Hudba** (ACE-Step 1.5) jedou jen na
vestavěných uzlech ComfyUI — žádný custom balík nepotřebují
(ComfyUI-GGUF je potřeba až pro volitelný alternativní model).

Pozn.: balíky LSI-Minimax-Segment-Timeline a balík s uzly Krea2Edit/H3
nejsou veřejně dostupné — bez nich nepojedou karty Časová osa a Úprava
obrázku (a živý náhled u videa). Ostatní karty fungují normálně.

\* Na referenčním serveru tyhle třídy poskytuje balík `comfyui-workflow-encrypt`;
při čisté instalaci pocházejí z VideoHelperSuite, KJNodes a balíku Krea 2 Edit.
Rozhoduje třída uzlu, ne jméno balíku — kontrola v appce ověřuje třídy.

## Modely (složka `models/`)

**checkpoints/**
- `ace_step_1.5_turbo_aio.safetensors` (karta Hudba)

**diffusion_models/**
- `minimax_h3_fl2va_pruned_int8_convrot.safetensors` (video z textu/obrázků)
- `minimax_h3_ref2va_pruned_int8_convrot.safetensors` (reference → video, Dialogy)
- `krea2_turbo_fp8_scaled.safetensors` (Úprava obrázku)
- `z_image_turbo_bf16.safetensors` (karta Obrázek)
- `qwen_image_edit_2511_fp8_e4m3fn.safetensors` (Oprava fotky)
- `flux1-Fill-Dev_FP8.safetensors` (Výměna tváře)

**text_encoders/**
- `qwen3vl_32b_minimax_h3_nvfp4_awq.safetensors` (MiniMax H3)
- `qwen3vl_4b_fp8_scaled.safetensors` (Krea 2)
- `qwen_3_4b.safetensors` (karta Obrázek)
- `qwen_2.5_vl_7b_fp8_scaled.safetensors` (Oprava fotky)
- `clip_l.safetensors` + `t5xxl_fp16.safetensors` (Výměna tváře)

**vae/**
- `minimax_h3_video_vae_fp16.safetensors`
- `minimax_h3_audio_vae_fp32.safetensors`
- `qwen_image_vae.safetensors` (Krea 2)
- `ae.sft` (karta Obrázek — FLUX/Z-Image autoenkodér)

**loras/**
- `krea2_identity_edit_v1_2.safetensors` (drží identitu při úpravě obrázku)
- zrychlovací (Turbo) LoRA podle výběru v appce — nabídka se čte ze serveru
- `Qwen-Image-Edit-2511-Lightning-4steps-V1.0-fp32.safetensors`,
  `qwen_image_edit_2511_upscale.safetensors`, `flymy_realism.safetensors` (Oprava fotky)
- `comfyui_portrait_lora64.safetensors` (ACE++), `FLUX.1-Turbo-Alpha.safetensors` (Výměna tváře)

**SeedVR2** (karta Zvětšit): `seedvr2_ema_7b-Q4_K_M.gguf` a
`ema_vae_fp16.safetensors` si balík **stáhne sám při prvním použití**.

## Volitelné

- **Higgs Audio Studio** (port 7860) — jen pro namlouvání replik na kartě
  Dialogy; bez něj zbytek appky funguje normálně.
- **Spouštěč na počítači** (port 8190) — umí ComfyUI na dálku zapnout
  a vypnout (uvolnit grafiku). Bez něj musí ComfyUI běžet, když appku používáš.
- **✨ Vylepšit prompt** (karta All in One) — napíšeš pár slov, klidně česky,
  a LLM na tvém počítači z nich složí plný anglický prompt pro H3 (záběry,
  časování, zvuk). Potřebuje balík
  [MiniMax-H3-Prompt-Rewriter-ComfyUI](https://github.com/pytraveler/MiniMax-H3-Prompt-Rewriter-ComfyUI)
  a k němu 8B model do `models/LLM` — trojici *základ + projektor + adaptér*:
  - základ: kterýkoli **Qwen3-VL-8B-Instruct** v GGUF (Q4–Q6, ~4,5–6,5 GB)
    + jeho `mmproj` soubor (~1 GB) ze stejné konverze,
  - adaptér: [MiniMax-H3-Prompt-Rewriter-LoRA-8B-GGUF](https://huggingface.co/pytraveler/MiniMax-H3-Prompt-Rewriter-LoRA-8B-GGUF) (~1,3 GB).

  Uzel si model umí i sám stáhnout při prvním použití (nabídne ho v seznamu).
  Model se po přepsání uvolní z paměti, takže generování videa neomezí.
  Bez tohohle balíku appka funguje normálně, jen tlačítko ohlásí, že chybí.
- **Karta Obrázek — přepínač „Bez cenzury" a model „Photoreal"** (18+):
  obojí vyžaduje vlastní soubory, které si stáhneš sám (CivitAI /
  Hugging Face, hledej Z-Image Turbo LoRA a finetuny). LoRA patří do
  `models/loras/` a appka nabídne **každou, která má v názvu
  `zimage`/`zit`** — pojmenuj ji tak a objeví se sama. Alternativní model
  (safetensors do `models/diffusion_models/`, GGUF k tomu chce balík
  ComfyUI-GGUF) očekává appka pod názvem `zimage_nsfw_photoreal_v61_Q8.gguf`.
  Bez těchto souborů karta Obrázek normálně jede na základním Z-Image Turbo.

## Jak ověřit, že máš všechno

V appce: **Nastavení → Co serveru chybí → Zkontrolovat server.** Appka se
zeptá serveru na každou třídu uzlu ze svých workflow a u výběrových vstupů
(modely, LoRA, VAE) ověří, že server soubor opravdu nabízí. Co chybí, vypíše
jmenovitě — nody doinstaluj přes ComfyUI-Manager, modely nahraj do složek výš.
