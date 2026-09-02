# Co appka potĹ™ebuje na serveru

PocketComfy je **dĂˇlkovĂ© ovlĂˇdĂˇnĂ­ ComfyUI z mobilu** â€” samo nic negeneruje,
vĹˇechno poÄŤĂ­tĂˇ tvĹŻj poÄŤĂ­taÄŤ. Tenhle soubor Ĺ™Ă­kĂˇ, co na nÄ›m musĂ­ bĂ˝t.
Seznam je vygenerovanĂ˝ pĹ™Ă­mo z workflow, kterĂˇ appka pouĹľĂ­vĂˇ (stejnĂˇ data
ÄŤte tlaÄŤĂ­tko **NastavenĂ­ â†’ Zkontrolovat server**, kterĂ© vypĂ­Ĺˇe, co konkrĂ©tnÄ›
chybĂ­ u tebe).

## ZĂˇklad

- **ComfyUI** â€” aktuĂˇlnĂ­ verze (nody MiniMax H3 jsou souÄŤĂˇstĂ­ ComfyUI,
  `comfy_extras/nodes_minimax_h3`), spuĹˇtÄ›nĂ© s `--listen 0.0.0.0`.
- Telefon na stejnĂ© sĂ­ti, nebo VPN (napĹ™. Tailscale).
- DoporuÄŤenĂ©: **ComfyUI-Manager** â€” chybÄ›jĂ­cĂ­ custom nody pĹ™es nÄ›j
  doinstalujeĹˇ na pĂˇr kliknutĂ­.

## Custom nody (podle karet)

| BalĂ­k | Poskytuje | PotĹ™ebujĂ­ karty |
|---|---|---|
| ComfyUI-ALLinONE-MinimaxH3 | Ĺˇablony workflow, endpoint `/h3one` | All in One, Dialogy |
| rgthree-comfy | Power Lora Loader, Any Switch | video karty, VĂ˝mÄ›na tvĂˇĹ™e |
| LSI-Minimax-Segment-Timeline | LSIMinimaxTimeline(+Render) | ÄŚasovĂˇ osa |
| ComfyUI-SeedVR2_VideoUpscaler | SeedVR2 nody | ZvÄ›tĹˇit |
| praveen-tools | ImageTileSplit/Merge, LoadImageWithFilename | ZvÄ›tĹˇit |
| VideoHelperSuite / KJNodes* | VHS_VideoCombine, PathchSageAttentionKJ, INTConstant, ModelPreviewOverrideKJ, ImageConcanate, ResizeMask | video karty, ĹľivĂ˝ nĂˇhled, VĂ˝mÄ›na tvĂˇĹ™e |
| nody Krea 2 Edit* | Krea2EditModelPatch, Krea2EditGroundedEncode, SpectrumApplyMiniMaxH3, H3CacheBust | Ăšprava obrĂˇzku, video karty |
| ComfyUI-Inpaint-CropAndStitch | InpaintCropImproved/Stitch | VĂ˝mÄ›na tvĂˇĹ™e |
| ComfyUI-MiniMaxH3-TeaCache | MiniMaxH3TeaCache | video karty (volitelnĂ© zrychlenĂ­) |
| Comfyui-QwenEditUtils | QwenEditConfigPreparer, TextEncodeQwenImageEditPlusCustom | Oprava fotky |
| ComfyUI_essentials | ImageResize+ | VĂ˝mÄ›na tvĂˇĹ™e |
| Impact Pack* | ImpactGaussianBlurMask | VĂ˝mÄ›na tvĂˇĹ™e |
| ComfyUI-GGUF | UnetLoaderGGUF | ObrĂˇzek â€” jen volitelnĂ˝ alternativnĂ­ model ve formĂˇtu GGUF |

Karty **ObrĂˇzek** (Z-Image) a **Hudba** (ACE-Step 1.5) jedou jen na
vestavÄ›nĂ˝ch uzlech ComfyUI â€” ĹľĂˇdnĂ˝ custom balĂ­k nepotĹ™ebujĂ­
(ComfyUI-GGUF je potĹ™eba aĹľ pro volitelnĂ˝ alternativnĂ­ model).

Pozn.: balĂ­ky LSI-Minimax-Segment-Timeline a balĂ­k s uzly Krea2Edit/H3
nejsou veĹ™ejnÄ› dostupnĂ© â€” bez nich nepojedou karty ÄŚasovĂˇ osa a Ăšprava
obrĂˇzku (a ĹľivĂ˝ nĂˇhled u videa). OstatnĂ­ karty fungujĂ­ normĂˇlnÄ›.

\* Na referenÄŤnĂ­m serveru tyhle tĹ™Ă­dy poskytuje balĂ­k `comfyui-workflow-encrypt`;
pĹ™i ÄŤistĂ© instalaci pochĂˇzejĂ­ z VideoHelperSuite, KJNodes a balĂ­ku Krea 2 Edit.
Rozhoduje tĹ™Ă­da uzlu, ne jmĂ©no balĂ­ku â€” kontrola v appce ovÄ›Ĺ™uje tĹ™Ă­dy.

## Modely (sloĹľka `models/`)

**checkpoints/**
- `ace_step_1.5_turbo_aio.safetensors` (karta Hudba)

**diffusion_models/**
- `minimax_h3_fl2va_pruned_int8_convrot.safetensors` (video z textu/obrĂˇzkĹŻ)
- `minimax_h3_ref2va_pruned_int8_convrot.safetensors` (reference â†’ video, Dialogy)
- `krea2_turbo_fp8_scaled.safetensors` (Ăšprava obrĂˇzku)
- `z_image_turbo_bf16.safetensors` (karta ObrĂˇzek)
- `qwen_image_edit_2511_fp8_e4m3fn.safetensors` (Oprava fotky)
- `flux1-Fill-Dev_FP8.safetensors` (VĂ˝mÄ›na tvĂˇĹ™e)

**text_encoders/**
- `qwen3vl_32b_minimax_h3_nvfp4_awq.safetensors` (MiniMax H3)
- `qwen3vl_4b_fp8_scaled.safetensors` (Krea 2)
- `qwen_3_4b.safetensors` (karta ObrĂˇzek)
- `qwen_2.5_vl_7b_fp8_scaled.safetensors` (Oprava fotky)
- `clip_l.safetensors` + `t5xxl_fp16.safetensors` (VĂ˝mÄ›na tvĂˇĹ™e)

**vae/**
- `minimax_h3_video_vae_fp16.safetensors`
- `minimax_h3_audio_vae_fp32.safetensors`
- `qwen_image_vae.safetensors` (Krea 2)
- `ae.sft` (karta ObrĂˇzek â€” FLUX/Z-Image autoenkodĂ©r)

**loras/**
- `krea2_identity_edit_v1_2.safetensors` (drĹľĂ­ identitu pĹ™i ĂşpravÄ› obrĂˇzku)
- zrychlovacĂ­ (Turbo) LoRA podle vĂ˝bÄ›ru v appce â€” nabĂ­dka se ÄŤte ze serveru
- `Qwen-Image-Edit-2511-Lightning-4steps-V1.0-fp32.safetensors`,
  `qwen_image_edit_2511_upscale.safetensors`, `flymy_realism.safetensors` (Oprava fotky)
- `comfyui_portrait_lora64.safetensors` (ACE++), `FLUX.1-Turbo-Alpha.safetensors` (VĂ˝mÄ›na tvĂˇĹ™e)

**SeedVR2** (karta ZvÄ›tĹˇit): `seedvr2_ema_7b-Q4_K_M.gguf` a
`ema_vae_fp16.safetensors` si balĂ­k **stĂˇhne sĂˇm pĹ™i prvnĂ­m pouĹľitĂ­**.

## VolitelnĂ©

- **Higgs Audio Studio** (port 7860) â€” jen pro namlouvĂˇnĂ­ replik na kartÄ›
  Dialogy; bez nÄ›j zbytek appky funguje normĂˇlnÄ›.
- **SpouĹˇtÄ›ÄŤ na poÄŤĂ­taÄŤi** (port 8190) â€” umĂ­ ComfyUI na dĂˇlku zapnout
  a vypnout (uvolnit grafiku). Bez nÄ›j musĂ­ ComfyUI bÄ›Ĺľet, kdyĹľ appku pouĹľĂ­vĂˇĹˇ.
- **Karta ObrĂˇzek â€” pĹ™epĂ­naÄŤ â€žBez cenzury" a model â€žPhotoreal"** (18+):
  obojĂ­ vyĹľaduje vlastnĂ­ soubory, kterĂ© si stĂˇhneĹˇ sĂˇm (CivitAI /
  Hugging Face, hledej Z-Image Turbo LoRA a finetuny). LoRA patĹ™Ă­ do
  `models/loras/` a appka nabĂ­dne **kaĹľdou, kterĂˇ mĂˇ v nĂˇzvu
  `zimage`/`zit`** â€” pojmenuj ji tak a objevĂ­ se sama. AlternativnĂ­ model
  (safetensors do `models/diffusion_models/`, GGUF k tomu chce balĂ­k
  ComfyUI-GGUF) oÄŤekĂˇvĂˇ appka pod nĂˇzvem `zimage_nsfw_photoreal_v61_Q8.gguf`.
  Bez tÄ›chto souborĹŻ karta ObrĂˇzek normĂˇlnÄ› jede na zĂˇkladnĂ­m Z-Image Turbo.

## Jak ovÄ›Ĺ™it, Ĺľe mĂˇĹˇ vĹˇechno

V appce: **NastavenĂ­ â†’ Co serveru chybĂ­ â†’ Zkontrolovat server.** Appka se
zeptĂˇ serveru na kaĹľdou tĹ™Ă­du uzlu ze svĂ˝ch workflow a u vĂ˝bÄ›rovĂ˝ch vstupĹŻ
(modely, LoRA, VAE) ovÄ›Ĺ™Ă­, Ĺľe server soubor opravdu nabĂ­zĂ­. Co chybĂ­, vypĂ­Ĺˇe
jmenovitÄ› â€” nody doinstaluj pĹ™es ComfyUI-Manager, modely nahraj do sloĹľek vĂ˝Ĺˇ.
