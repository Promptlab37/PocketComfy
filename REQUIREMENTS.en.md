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
| praveen-tools | ImageTileSplit/Merge, LoadImageWithFilename | Upscale |
| VideoHelperSuite / KJNodes* | VHS_VideoCombine, PathchSageAttentionKJ, INTConstant, ModelPreviewOverrideKJ, ImageConcanate, ResizeMask | video cards, live preview, Face swap |
| Krea 2 Edit nodes* | Krea2EditModelPatch, Krea2EditGroundedEncode, SpectrumApplyMiniMaxH3, H3CacheBust | Image edit, video cards |
| ComfyUI-Inpaint-CropAndStitch | InpaintCropImproved/Stitch | Face swap, Inpaint |
| ComfyUI-MiniMaxH3-TeaCache | MiniMaxH3TeaCache | video cards (optional speed-up) |
| Comfyui-QwenEditUtils | QwenEditConfigPreparer, TextEncodeQwenImageEditPlusCustom | Photo restore |
| ComfyUI_essentials | ImageResize+ | Face swap |
| Impact Pack* | ImpactGaussianBlurMask | Face swap |
| ComfyUI-GGUF | UnetLoaderGGUF | Image — only for the optional GGUF model |
| [MiniMax-H3-Prompt-Rewriter-ComfyUI](https://github.com/pytraveler/MiniMax-H3-Prompt-Rewriter-ComfyUI) | MiniMaxH3PromptWriter8B | the **AI prompt enhancer** button (optional, see below) |

The **Image** (Z-Image) and **Music** (ACE-Step 1.5) cards run on ComfyUI's
built-in nodes only — no custom pack required (ComfyUI-GGUF is needed only for
the optional alternative model).

Note: the LSI-Minimax-Segment-Timeline pack and the Krea2Edit/H3 helper nodes
are **not publicly available** — without them the Timeline and Image edit cards
will not run (nor the live preview during video). All other cards work normally.

\* On the reference server these classes come from the `comfyui-workflow-encrypt`
bundle; in a clean install they come from VideoHelperSuite, KJNodes and the
Krea 2 Edit pack. What matters is the node class, not the pack name — the in-app
check verifies classes.

## Models (the `models/` folder)

**checkpoints/**
- `ace_step_1.5_turbo_aio.safetensors` (Music)

**diffusion_models/**
- `minimax_h3_fl2va_pruned_int8_convrot.safetensors` (video from text/images)
- `minimax_h3_ref2va_pruned_int8_convrot.safetensors` (references → video, Dialogue)
- `krea2_turbo_fp8_scaled.safetensors` (Image edit)
- `z_image_turbo_bf16.safetensors` (Image)
- `qwen_image_edit_2511_fp8_e4m3fn.safetensors` (Photo restore)
- `flux1-Fill-Dev_FP8.safetensors` (Face swap, Inpaint — the Flux Fill option)
- `flux-2-klein-9b.safetensors` (Inpaint — the default FLUX.2 Klein option;
  grab the fp8 release from [black-forest-labs/FLUX.2-klein-9b-fp8](https://huggingface.co/black-forest-labs/FLUX.2-klein-9b-fp8),
  ~9.5 GB, you have to accept the licence on Hugging Face, and save it under this name)

**text_encoders/**
- `qwen3vl_32b_minimax_h3_nvfp4_awq.safetensors` (MiniMax H3)
- `qwen3vl_4b_fp8_scaled.safetensors` (Krea 2)
- `qwen_3_4b.safetensors` (Image)
- `qwen_2.5_vl_7b_fp8_scaled.safetensors` (Photo restore)
- `clip_l.safetensors` + `t5xxl_fp16.safetensors` (Face swap, Inpaint — Flux Fill)
- `qwen_3_8b_fp8mixed.safetensors` (Inpaint — FLUX.2 Klein), from
  [Comfy-Org/vae-text-encorder-for-flux-klein-9b](https://huggingface.co/Comfy-Org/vae-text-encorder-for-flux-klein-9b)

**vae/**
- `minimax_h3_video_vae_fp16.safetensors`
- `minimax_h3_audio_vae_fp32.safetensors`
- `qwen_image_vae.safetensors` (Krea 2)
- `ae.sft` (Image and Inpaint — the FLUX/Z-Image autoencoder)
- `flux2-vae.safetensors` (Inpaint — FLUX.2 Klein), from the same repository as
  the encoder above

**loras/**
- `krea2_identity_edit_v1_2.safetensors` (keeps identity during image edits)
- an acceleration (Turbo) LoRA of your choice — the app reads the list from the server
- `Qwen-Image-Edit-2511-Lightning-4steps-V1.0-fp32.safetensors`,
  `qwen_image_edit_2511_upscale.safetensors`, `flymy_realism.safetensors` (Photo restore)
- `comfyui_portrait_lora64.safetensors` (ACE++), `FLUX.1-Turbo-Alpha.safetensors` (Face swap)

**SeedVR2** (Upscale): the pack **downloads `seedvr2_ema_7b-Q4_K_M.gguf` and
`ema_vae_fp16.safetensors` by itself** on first use.

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
- **Image card — the "uncensored" toggle and the "Photoreal" model** (18+):
  both require files you download yourself (CivitAI / Hugging Face — look for
  Z-Image Turbo LoRAs and finetunes). LoRAs go into `models/loras/` and the app
  offers **every file with `zimage`/`zit` in its name** — name it that way and it
  shows up automatically. The alternative model (safetensors into
  `models/diffusion_models/`; a GGUF additionally needs the ComfyUI-GGUF pack) is
  expected under the name `zimage_nsfw_photoreal_v61_Q8.gguf`. Without these
  files the Image card simply runs on the stock Z-Image Turbo.

## How to verify you have everything

In the app: **Settings → What the server is missing → Check server.** The app
asks your server about every node class in its workflows and, for combo inputs
(models, LoRAs, VAEs), verifies the file is actually offered. Whatever is
missing is listed by name — with the pack to install, the target folder and a
download link. **Copy list** puts it on the clipboard so you can continue on
your PC.
