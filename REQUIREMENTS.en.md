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
| ComfyUI-Inpaint-CropAndStitch | InpaintCropImproved/Stitch | Face swap |
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
- `flux1-Fill-Dev_FP8.safetensors` (Face swap)

**text_encoders/**
- `qwen3vl_32b_minimax_h3_nvfp4_awq.safetensors` (MiniMax H3)
- `qwen3vl_4b_fp8_scaled.safetensors` (Krea 2)
- `qwen_3_4b.safetensors` (Image)
- `qwen_2.5_vl_7b_fp8_scaled.safetensors` (Photo restore)
- `clip_l.safetensors` + `t5xxl_fp16.safetensors` (Face swap)

**vae/**
- `minimax_h3_video_vae_fp16.safetensors`
- `minimax_h3_audio_vae_fp32.safetensors`
- `qwen_image_vae.safetensors` (Krea 2)
- `ae.sft` (Image — the FLUX/Z-Image autoencoder)

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
- **✨ AI prompt enhancer** (All in One card) — you type a few words and an LLM
  on your PC expands them into a full English H3 prompt (shots, timing, sound).
  Needs the
  [MiniMax-H3-Prompt-Rewriter-ComfyUI](https://github.com/pytraveler/MiniMax-H3-Prompt-Rewriter-ComfyUI)
  pack plus an 8B model in `models/LLM` — a trio of *base + projector + adapter*:
  - base: any **Qwen3-VL-8B-Instruct** in GGUF (Q4–Q6, ~4.5–6.5 GB) together
    with the `mmproj` file (~1 GB) from the same conversion,
  - adapter: [MiniMax-H3-Prompt-Rewriter-LoRA-8B-GGUF](https://huggingface.co/pytraveler/MiniMax-H3-Prompt-Rewriter-LoRA-8B-GGUF) (~1.3 GB).

  The node can also download a model itself on first use (it offers one in the
  list). The model is released from VRAM after the rewrite, so it does not get
  in the way of video generation. Without this pack the app works normally — the
  button just reports that it is missing.
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
