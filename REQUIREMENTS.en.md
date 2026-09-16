*[Česky](POZADAVKY.md) · **English***

# What the app needs on the server

PocketComfy is a **remote control for ComfyUI** — it generates nothing itself,
your PC does all the work. This file lists what has to be there. The list is
derived from the very workflows the app ships with (the same data drives the
**Settings → Check server** button, which reports what is missing on *your*
machine, including target folders and download links).

## Basics

- **ComfyUI** — a current version (the MiniMax H3 nodes ship with ComfyUI in
  `comfy_extras/nodes_minimax_h3`), started with `--listen 0.0.0.0`.
- The phone on the same network, or a VPN (e.g. Tailscale).
- Recommended: **ComfyUI-Manager** — installs missing custom nodes in a few clicks.

## Custom nodes (by card)

| Pack | Provides | Needed by |
|---|---|---|
| ComfyUI-ALLinONE-MinimaxH3 | workflow templates, the `/h3one` endpoint | All in One, Dialogue |
| rgthree-comfy | Power Lora Loader, Any Switch | video cards, Face swap |
| LSI-Minimax-Segment-Timeline | LSIMinimaxTimeline(+Render) | Timeline |
| ComfyUI-SeedVR2_VideoUpscaler | SeedVR2 nodes | Upscale |
| [praveen-tools](https://github.com/Praveenhalder/praveen-tools) | ImageTileSplit/Merge, LoadImageWithFilename | Upscale |
| [ComfyUI-DLSS5-Enhancer](https://github.com/Blueforcer/ComfyUI-DLSS5-Enhancer) | DLSS5Settings, DLSS5EnhanceImages/VideoFile | Upscale - the DLSS 5 method (run `install_runtime.py` once after installing) |
| [ComfyUI-H3-Motion-Context-MultiRef](https://github.com/seitanism/ComfyUI-H3-Motion-Context-MultiRef) | MiniMaxH3StartMaskedContext, MiniMaxH3GeneratedAVMaskedContext, MiniMaxH3CropTo32 and more | Long video |
| [Comfyui_Minimax_h3_latent_Upscaler](https://github.com/LBH-123-AI/Comfyui_Minimax_h3_latent_Upscaler) | MinimaxH3LatentUpscaler3D | Long video - fast first shot (optional) |
| [MaskVidExperiments](https://github.com/drozbay/MaskVidExperiments) | MVEx_MaskCleanup, MVEx_SubjectCrop/Uncrop and more | All in One > Repaint in video |
| VideoHelperSuite / KJNodes* | VHS_VideoCombine, PathchSageAttentionKJ, INTConstant, ModelPreviewOverrideKJ, ImageConcanate, ResizeMask | video cards, live preview, Face swap |
| Krea 2 Edit nodes* | Krea2EditModelPatch, Krea2EditGroundedEncode, SpectrumApplyMiniMaxH3, H3CacheBust | Image edit, video cards |
| ComfyUI-Inpaint-CropAndStitch | InpaintCropImproved/Stitch | Face swap, Inpaint |
| ComfyUI-MiniMaxH3-TeaCache | MiniMaxH3TeaCache | video cards (optional speed-up) |
| Comfyui-QwenEditUtils | QwenEditConfigPreparer, TextEncodeQwenImageEditPlusCustom | Photo restore |
| ComfyUI_essentials | ImageResize+ | Face swap |
| Impact Pack* | ImpactGaussianBlurMask | Face swap |
| ComfyUI-GGUF | UnetLoaderGGUF | Image — only for the optional GGUF model |
| [MiniMax-H3-Prompt-Rewriter-ComfyUI](https://github.com/pytraveler/MiniMax-H3-Prompt-Rewriter-ComfyUI) | MiniMaxH3PromptWriter8B | the **AI prompt enhancer** button (optional, see below) |

The **Image** (Z-Image), **Music**, **Camera angle** and **3D model** cards run
on ComfyUI's built-in nodes only — no custom pack required (ComfyUI-GGUF is
needed only for the optional alternative model on the Image card). They do have
a minimum core version, though: the **TRELLIS.2** and **Pixal3D/MoGe** nodes
behind the 3D model card ship in ComfyUI **from 0.34 on**, and
`YuE2GenerateABC`, `YuE2GenerateMusic` and `EmptyYuE2LatentAudio` behind the
**YuE2** option on the Music card **from 0.36.0 on**. On an older build there is
nothing to install, you update ComfyUI.

Note: the LSI-Minimax-Segment-Timeline pack and the Krea2Edit/H3 helper nodes
are **not publicly available** — without them the Timeline and Image edit cards
will not run (nor the live preview during video). All other cards work normally.

\* On the reference server these classes come from the `comfyui-workflow-encrypt`
bundle; in a clean install they come from VideoHelperSuite, KJNodes and the
Krea 2 Edit pack. What matters is the node class, not the pack name — the in-app
check verifies classes.

## Models (the `models/` folder)

`instalace-serveru.bat` can fetch everything that is publicly available — card
by card, so you only download what you actually use.

**checkpoints/**
- `ace_step_1.5_turbo_aio.safetensors` (Music — ACE-Step option)
- `yue2_3b_int8_convrot.safetensors` (Music — YuE2 option, 3.9 GB;
  ComfyUI 0.36.0+), from [Comfy-Org/YuE2](https://huggingface.co/Comfy-Org/YuE2)
- `sam3.1_multiplex_fp16.safetensors` (All in One → Repaint in video, 1.7 GB),
  from [Comfy-Org/sam3.1](https://huggingface.co/Comfy-Org/sam3.1)

**diffusion_models/**
- `minimax_h3_fl2va_pruned_int8_convrot.safetensors` (video from text/images)
- `minimax_h3_ref2va_pruned_int8_convrot.safetensors` (references → video, Dialogue)
  — both from [Comfy-Org/MiniMax-H3](https://huggingface.co/Comfy-Org/MiniMax-H3),
  19.5 GB each
- `krea2_turbo_fp8_scaled.safetensors` (Image edit)
- `z_image_turbo_bf16.safetensors` (Image)
- `z_image_bf16.safetensors` (Image — the Z-Image Base option, 11.5 GB), from
  [Comfy-Org/z_image](https://huggingface.co/Comfy-Org/z_image)
- `qwen_image_edit_2511_fp8_e4m3fn.safetensors` (Photo restore and Camera angle)
- `flux1-Fill-Dev_FP8.safetensors` (Face swap, Inpaint — the Flux Fill option)
- `flux-2-klein-9b.safetensors` (Inpaint and Image — the FLUX.2 Klein option;
  grab the fp8 release from [black-forest-labs/FLUX.2-klein-9b-fp8](https://huggingface.co/black-forest-labs/FLUX.2-klein-9b-fp8),
  ~9.5 GB, you have to accept the licence on Hugging Face, and save it under this name)
- `trellis_2_int8_convrot.safetensors` (3D model, 4.9 GB), from
  [Comfy-Org/TRELLIS.2](https://huggingface.co/Comfy-Org/TRELLIS.2)
- `pixal3d_int8_convrot.safetensors` (3D model — the Pixal3D engine, 5.6 GB),
  from [Comfy-Org/Pixal3D](https://huggingface.co/Comfy-Org/Pixal3D)

**unet/** — note that `models/unet` and `models/diffusion_models` are **one and
the same folder** to ComfyUI (`unet` is just the older name). A GGUF model can
sit in either; there is no need to move it.
- `ernie-image-turbo-Q8_0.gguf` (Image — the ERNIE Image Turbo option, 8.1 GB),
  from [unsloth/ERNIE-Image-Turbo-GGUF](https://huggingface.co/unsloth/ERNIE-Image-Turbo-GGUF);
  loaded by `UnetLoaderGGUF` from the ComfyUI-GGUF pack

**text_encoders/**
- `qwen3vl_32b_minimax_h3_nvfp4_awq.safetensors` (MiniMax H3, 14.6 GB)
- `qwen3vl_32b_minimax_h3_int8_convrot.safetensors` (3 steps card, 25.3 GB) —
  that template pins its encoder, so nvfp4 is not used there
- `qwen3vl_4b_fp8_scaled.safetensors` (Krea 2)
- `qwen_3_4b.safetensors` (Image — both Z-Image Turbo and Base)
- `qwen_2.5_vl_7b_fp8_scaled.safetensors` (Photo restore, Camera angle)
- `clip_l.safetensors` + `t5xxl_fp16.safetensors` (Face swap, Inpaint — Flux Fill)
- `qwen_3_8b_fp8mixed.safetensors` (Image and Inpaint — FLUX.2 Klein), from
  [Comfy-Org/vae-text-encorder-for-flux-klein-9b](https://huggingface.co/Comfy-Org/vae-text-encorder-for-flux-klein-9b)
- `ministral-3-3b.safetensors` (Image — ERNIE, 7.2 GB), from
  [Comfy-Org/ERNIE-Image](https://huggingface.co/Comfy-Org/ERNIE-Image)

**vae/**
- `minimax_h3_video_vae_fp16.safetensors`
- `minimax_h3_audio_vae_fp32.safetensors`
- `qwen_image_vae.safetensors` (Krea 2, Photo restore, Camera angle)
- `ae.sft` (Image, Inpaint and Face swap — the FLUX/Z-Image autoencoder)
- `flux2-vae.safetensors` (Image and Inpaint — FLUX.2 Klein and ERNIE), from the
  same repository as the encoder above
- `full_encoder_small_decoder.safetensors` (editing an image through FLUX.2
  Klein **encodes** the source, so it needs the full encoder), from
  [black-forest-labs/FLUX.2-small-decoder](https://huggingface.co/black-forest-labs/FLUX.2-small-decoder)
- `trellis_2_shape_vae_bf16.safetensors` + `trellis_2_texture_vae_bf16.safetensors`
  (3D model, both engines)

**clip_vision/**
- `dino_v3_vit_l.safetensors` (3D model — TRELLIS.2)
- `dino_v3_L_naf_fp32.safetensors` (3D model — Pixal3D)

**background_removal/**
- `birefnet.safetensors` (3D model — cuts the background out of the photo), from
  [Comfy-Org/BiRefNet](https://huggingface.co/Comfy-Org/BiRefNet)

**geometry_estimation/**
- `moge_2_vitl_normal_fp16.safetensors` (3D model — Pixal3D estimates the lens
  angle from the photo), from [Comfy-Org/MoGe](https://huggingface.co/Comfy-Org/MoGe)

**latent_upscale_models/**
- `minimax_h3_latent_upscaler_3d_fp16.safetensors` (Long video — fast first
  shot — and the 3 steps card), from [LBH-123-AI/Minimax_h3_latent_Upscaler](https://huggingface.co/LBH-123-AI/Minimax_h3_latent_Upscaler)

**loras/**
- `krea2_identity_edit_v1_2.safetensors` (keeps identity during image edits)
- an acceleration (Turbo) LoRA for MiniMax H3 of your choice — the app reads the
  list from the server; the public ones live in the `loras/` folder of
  [Comfy-Org/MiniMax-H3](https://huggingface.co/Comfy-Org/MiniMax-H3)
- `Qwen-Image-Edit-2511-Lightning-4steps-V1.0-fp32.safetensors`
  ([lightx2v](https://huggingface.co/lightx2v/Qwen-Image-Edit-2511-Lightning)),
  `qwen_image_edit_2511_upscale.safetensors`
  ([starsfriday](https://huggingface.co/starsfriday/Qwen-Image-Edit-2511-Upscale2K)),
  `flymy_realism.safetensors`
  ([flymy-ai](https://huggingface.co/flymy-ai/qwen-image-realism-lora)) — Photo restore
- `qwen-image-edit-2511-multiple-angles-lora.safetensors`
  ([fal](https://huggingface.co/fal/Qwen-Image-Edit-2511-Multiple-Angles-LoRA)) +
  `Qwen-Image-Edit-2511-Lightning-8steps-V1.0-fp32.safetensors` (Camera angle)
- `comfyui_portrait_lora64.safetensors` (ACE++), `FLUX.1-Turbo-Alpha.safetensors` (Face swap)
- `h3/TaoMate-H3-3step-ComfyUI.safetensors` (3 steps card, 2.5 GB) — in the `h3`
  subfolder and under exactly this name; the whole three-step route stands on it.
  The original adapter is [TaoLiveAIGC/TaoMate-H3](https://huggingface.co/TaoLiveAIGC/TaoMate-H3),
  a ComfyUI-ready build is e.g. [Robert1212star/TaoMate-H3-3Step-ComfyUI](https://huggingface.co/Robert1212star/TaoMate-H3-3Step-ComfyUI)

**vae_approx/**
- `taeh3.safetensors` (live preview during video, optional)

**SeedVR2** (Upscale): the pack **downloads `seedvr2_ema_7b-Q4_K_M.gguf` and
`ema_vae_fp16.safetensors` by itself** on first use. The **DLSS 5** method on the
same card has no model of its own, but after installing the pack you have to run
its `install_runtime.py` once — it downloads a third-party runtime (~470 MB) and
asks you to accept its licence.

## Optional

- **Higgs Audio Studio** (port 7860) — only for voicing lines on the Dialogue
  card; everything else works without it.
- **A launcher on the PC** (port 8190) — lets the app start and stop ComfyUI
  remotely (to free the GPU). Without it ComfyUI has to be running while you
  use the app.
- **✨ AI prompt enhancer** (All in One and Image cards) — you type a few words,
  in any language, and a language model expands them into a full English
  prompt: for video with shots, timing and sound, for stills written to the
  Z-Image rules (flowing sentences, lighting first, no negative prompt).

  **It runs as an ordinary workflow inside your own ComfyUI** — the app posts a
  small graph to `/prompt` and reads the finished text back from the history.
  Nothing is downloaded onto the phone and nothing leaves the house; you do
  need a node pack and one model in `models/LLM` on the server:

  | | video (All in One) | image |
  |---|---|---|
  | pack | [MiniMax-H3-Prompt-Rewriter-ComfyUI](https://github.com/pytraveler/MiniMax-H3-Prompt-Rewriter-ComfyUI) | [ComfyUI-llama-cpp_vlm](https://github.com/lihaoyun6/ComfyUI-llama-cpp_vlm) |
  | model | base + projector + adapter | the base alone is enough |

  The exact files (~7 GB together; `instalace-serveru.bat` can fetch them for
  you into `models/LLM`):
  - **base:** [Huihui-Qwen3-VL-8B-Instruct-abliterated Q4_K_M](https://huggingface.co/noctrex/Huihui-Qwen3-VL-8B-Instruct-abliterated-GGUF/resolve/main/Huihui-Qwen3-VL-8B-Instruct-abliterated-Q4_K_M.gguf)
    (4.7 GB) — the uncensored build, so it does not refuse your idea,
  - **projector:** [mmproj-F16](https://huggingface.co/noctrex/Huihui-Qwen3-VL-8B-Instruct-abliterated-GGUF/resolve/main/mmproj-F16.gguf) (1.1 GB) from the same repository;
    save it as `Huihui-Qwen3-VL-8B-Instruct-abliterated-mmproj-F16.gguf`,
  - **adapter (video only):**
    [MiniMax-H3-Prompt-Rewriter-LoRA-8B-F16](https://huggingface.co/pytraveler/MiniMax-H3-Prompt-Rewriter-LoRA-8B-GGUF/resolve/main/MiniMax-H3-Prompt-Rewriter-LoRA-8B-F16.gguf) (1.3 GB).

  The same pack and model also power the **🌐 Translate to English** button
  next to the prompt fields — nothing extra to download for translation.

  The video rewriter node can also download a model itself on first use (it
  offers one in the list); the Image card cannot — there the file really has to
  sit in `models/LLM`. The app then picks a model from the server's list on its
  own (preferring an uncensored build) and releases it from VRAM once the
  prompt is written, so it never gets in the way of generating. Without all
  this the app works normally — the button just reports what is missing.
- **Image card — a custom model**: besides the five ready-made options, the card
  can run on **any other Z-Image-family model you have on the server**. The
  **Custom model** option reads the list straight from ComfyUI (`UNETLoader` and
  `UnetLoaderGGUF`, so safetensors and GGUF alike), you pick the file from that
  list and set steps and cfg for it — those cannot be guessed from a file name.
  Distilled Turbo finetunes run on cfg 1 and 8–12 steps; the undistilled base
  wants cfg around 4 and 25 steps or more. The file goes into
  `models/diffusion_models/` (or `models/unet/`, one and the same folder to
  ComfyUI), and a GGUF model additionally needs the ComfyUI-GGUF pack.
  **Nothing needs renaming** — the app offers whatever the server reports.
- **Image card — the "uncensored" toggle and the "Photoreal" model** (18+):
  both require files you download yourself (CivitAI / Hugging Face — look for
  Z-Image Turbo LoRAs and finetunes). LoRAs go into `models/loras/` and the app
  offers **every file with `zimage`/`zit` in its name** — name it that way and it
  shows up automatically. The ready-made **Photoreal** option expects the file
  under the name `zimage_nsfw_photoreal_v61_Q8.gguf`; any other finetune can be
  used without renaming through the **Custom model** option above. Without these
  files the Image card simply runs on the stock Z-Image Turbo.

## How to verify you have everything

In the app: **Settings → What the server is missing → Check server.** The app
asks your server about every node class in its workflows and, for combo inputs
(models, LoRAs, VAEs), verifies the file is actually offered. Whatever is
missing is listed by name — with the pack to install, the target folder and a
download link. **Copy list** puts it on the clipboard so you can continue on
your PC.
