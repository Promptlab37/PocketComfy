*[Česky](POZADAVKY.md) · **English***

# What the app needs on the server

PocketComfy is a **remote control for ComfyUI from your phone** — it generates nothing itself,
your PC does all the work. This file lists what has to be there.
The list is generated straight from the workflows the app uses (the same data
drives the **Settings → Check server** button, which reports what exactly
is missing on your machine).

## Basics

- **ComfyUI** — a current version (the MiniMax H3 nodes ship with ComfyUI,
  `comfy_extras/nodes_minimax_h3`), started with `--listen 0.0.0.0`.
- The phone on the same network, or a VPN (e.g. Tailscale).
- Recommended: **ComfyUI-Manager** — lets you install missing custom nodes
  in a few clicks.

## Custom nodes (by card)

| Pack | Provides | Needed by cards |
|---|---|---|
| [ComfyUI-ALLinONE-MinimaxH3](https://github.com/LeonQ8/ComfyUI-ALLinONE-MinimaxH3) | workflow templates, the `/h3one` endpoint, H3CacheBust, H3IdentityAnchor | All in One, Dialogue, Edit video |
| rgthree-comfy | Power Lora Loader, Any Switch | video cards, Face swap, Inpaint |
| LSI-Minimax-Segment-Timeline | LSIMinimaxTimeline(+Render) | Timeline (the pack is not public) |
| [ComfyUI-Spectrum-MiniMax-H3](https://github.com/xmarre/ComfyUI-Spectrum-MiniMax-H3) | SpectrumApplyMiniMaxH3 | Timeline |
| ComfyUI-SeedVR2_VideoUpscaler | SeedVR2 nodes | Upscale photo |
| [praveen-tools](https://github.com/Praveenhalder/praveen-tools) | ImageTileSplit/Merge, LoadImageWithFilename | Upscale photo |
| [ComfyUI-Smart-Upscaler](https://github.com/HallettVisual/ComfyUI-Smart-Upscaler) | SmartUpscaledTilePlanner, SmartCachedTextGenerate, SmartTileFinalizer… | Upscale photo — Smart upscale |
| [ComfyUI-BingImageSelector](https://github.com/concarne000/ComfyUI-BingImageSelector) | BingImageSelector, the `/bing_image_selector/*` routes | image picker — Search the internet |
| [ComfyUI-DLSS5-Enhancer](https://github.com/Blueforcer/ComfyUI-DLSS5-Enhancer) | DLSS5Settings, DLSS5EnhanceImages/VideoFile | Upscale photo — the DLSS 5 method (after installing, also run `install_runtime.py`) |
| [Comfyui_Minimax_h3_latent_Upscaler](https://github.com/LBH-123-AI/Comfyui_Minimax_h3_latent_Upscaler) | MinimaxH3LatentUpscaler3D | Quick video; Long MiniMax — the 3 + 2 option |
| [Minimax-H3-Latent-Continuation](https://github.com/SatoDive/Minimax-H3-Latent-Continuation) | MiniMaxH3Easy_SatoDive, MiniMaxH3EasyContextSegments_SatoDive, Save/Load Latent, StitchContinuation and more | Long MiniMax, Film from storyboard |
| [MaskVidExperiments](https://github.com/drozbay/MaskVidExperiments) | MVEx_MaskCleanup, MVEx_SubjectCrop/Uncrop and more | Edit video → Repaint |
| [ComfyUI-LTXVideo](https://github.com/Lightricks/ComfyUI-LTXVideo) | LTXVAudioVAEEncode, LTXVLatentUpsampler, LTXVImgToVideoInplace and more (see below) | LTX 2.5 |
| VideoHelperSuite / KJNodes* | VHS_VideoCombine, VHS_LoadAudioUpload, PathchSageAttentionKJ, MiniMaxH3MemoryEfficientSageAttentionPatch, INTConstant, ModelPreviewOverrideKJ, ImageConcanate, ResizeMask, ImageResizeKJv2 | video cards, live preview, Face swap |
| [comfyui-krea2edit](https://github.com/lbouaraba/comfyui-krea2edit) | Krea2EditModelPatch, Krea2EditGroundedEncode | Image edit — Krea 2 |
| ComfyUI-Inpaint-CropAndStitch | InpaintCropImproved/Stitch | Face swap, Inpaint |
| [ComfyUI-AusBoss](https://github.com/ausboss/ComfyUI-AusBoss) | AUSBOSS_NODES_LoadImagePad, AUSBOSS_NODES_StitchInpaint | Inpaint → Extend the image |
| ComfyUI-MiniMaxH3-TeaCache | MiniMaxH3TeaCache | video cards (optional speed-up) |
| ComfyUI_essentials | ImageResize+ | Face swap |
| Impact Pack* | ImpactGaussianBlurMask | Face swap |
| ComfyUI-GGUF | UnetLoaderGGUF | Image — the ERNIE Image Turbo and Photoreal options (GGUF models) |
| [MiniMax-H3-Prompt-Rewriter-ComfyUI](https://github.com/pytraveler/MiniMax-H3-Prompt-Rewriter-ComfyUI) | MiniMaxH3PromptWriter8B | the **✨ Improve (MiniMax)** button (optional, see below) |

The **Long MiniMax** card continues the shots of one scene through a **saved latent**.
Continuing from a finished video means decoding and encoding again, and that round trip
darkens the image a little every time; the error adds up along the chain. So the pack
saves the latent of the finished shot to `output/h3_latents` and the next run starts from it.
With its default Turbo option the card needs no extra models: it runs on `minimax_h3_fl2va`,
both VAEs and the encoder like the other video cards, plus the LoRA
`minimax_h3_ref2v_turbo_4step_v0.1_comfyui_bf16.safetensors`.

The **Image** (Z-Image), **Music**, **Camera angle** and **3D model** cards run
on ComfyUI's built-in nodes only — they need no custom pack
(ComfyUI-GGUF is needed only for the ERNIE Image Turbo and Photoreal options on the
Image card). They do have a minimum core version, though: the **TRELLIS.2** and
**Pixal3D/MoGe** nodes for the 3D model card are in ComfyUI **from 0.34 on**, the nodes
`YuE2GenerateABC`, `YuE2GenerateMusic` and `EmptyYuE2LatentAudio` for the
**YuE2** option on the Music card **only from 0.36.0 on**. On an older version there is nothing to install,
you update ComfyUI.

Qwen Image 2.1 is **one model for both generating and editing**, so you will find it on
several cards: **Image** (text→image), **Image edit**, **Photo restore**,
**Inpaint** (including **Extend the image**) and **Face swap**.
FLUX.2 Klein is similar (Image, Image edit, Inpaint). Qwen Image 2.1 needs the
built-in nodes `TextEncodeQwenImage21` and `QwenImage21Cache`, which only arrived
in **ComfyUI 0.37.0** — they are missing on 0.36.0 and older. No custom node
needs to be installed.

The **Camera angle** card runs on **Qwen Image Edit 2511**, not on 2.1. The ability
to change the viewpoint lives in the `qwen-image-edit-2511-multiple-angles` LoRA,
which is trained on 2511. The card's models are in the list below.

Optionally Qwen Image 2.1 comes with **✨ Improve (Qwen)** — Qwen's own prompt rewriters
(`Qwen-Image-2.1-PE-I2I` and `-PE-T2I`) on the built-in node
`TextGenerate` (also from 0.37.0). They handle prompts in Czech too. Without their weights
the button only reports that the model is missing; the rest of the card keeps working.

The **LTX 2.5** card (all three modes: From text, From a photo and From audio) needs the
[ComfyUI-LTXVideo](https://github.com/Lightricks/ComfyUI-LTXVideo) pack (nodes
`LTXVAudioVAEEncode`, `LTXVConcatAVLatent`, `LTXVSeparateAVLatent`,
`LTXVLatentUpsampler`, `LTXVImgToVideoInplace`, `LTXVDualCFGGuider`,
`LTXVPreprocess`, `LatentUpscaleModelLoader`), and the From audio mode also needs
VideoHelperSuite for `VHS_LoadAudioUpload`. The pack must be a version that knows
**LTX 2.5** — older ones only know 2.3 and some of the nodes are missing in them.

Note: the only pack that is not publicly available is **LSI-Minimax-Segment-Timeline** —
without it the **Timeline** card will not run. All other cards work normally.

\* On the reference server ComfyUI reports the `comfyui-workflow-encrypt` pack for these
classes; in a clean install they come from VideoHelperSuite, KJNodes and Impact Pack.
What matters is the node class, not the pack name — the in-app check verifies classes.

## Models (the `models/` folder)

The `instalace-serveru.bat` script can download everything that is publicly available —
card by card, so you do not download what you will not use.

**checkpoints/**
- `ace_step_1.5_turbo_aio.safetensors` (Music card — ACE-Step option)
- `yue2_3b_int8_convrot.safetensors` (Music card — YuE2 option, 3.9 GB;
  ComfyUI 0.36.0+), from [Comfy-Org/YuE2](https://huggingface.co/Comfy-Org/YuE2)
- `yue2/ar_lora_inst_v3abc_comfyui.safetensors` in `models/loras/yue2` (Film from storyboard —
  background music, 213 MB), from [Mothersuperior/YuE2-instrumental-cot-full-loras](https://huggingface.co/Mothersuperior/YuE2-instrumental-cot-full-loras).
  Instrumental LoRA for YuE2 (without it the model starts singing after a while).
  **Licence CC BY-NC 4.0 — non-commercial use only.** Without it the card doesn't offer music.
- `sam3.1_multiplex_fp16.safetensors` (Edit video → Repaint and Replace
  character, 1.7 GB), from [Comfy-Org/sam3.1](https://huggingface.co/Comfy-Org/sam3.1)

**audio_encoders/**
- `sheetsage2_bf16.safetensors` (Music card — **Rework a recording**, 1.3 GB),
  from [Comfy-Org/YuE2](https://huggingface.co/Comfy-Org/YuE2). It transcribes the melody
  of a recording into notes; without it YuE2 still runs, just without that option. The
  `SheetSage2AudioToABC` and `AudioEncoderLoader` nodes are in ComfyUI core
  **from 0.36.0 on**, same as YuE2.

**diffusion_models/**
- `ltx-2.5-22b-distilled-transformer-comfy-int8-convrot.safetensors`
  (LTX 2.5 card, **21 GB**), from
  [Lightricks/LTX-2.5](https://huggingface.co/Lightricks/LTX-2.5)
- `minimax_h3_fl2va_pruned_int8_convrot.safetensors` (video from text/images)
- `minimax_h3_ref2va_pruned_int8_convrot.safetensors` (references → video, Dialogue)
  — both from [Comfy-Org/MiniMax-H3](https://huggingface.co/Comfy-Org/MiniMax-H3),
  19.5 GB each
- `krea2_turbo_int8_convrot.safetensors` (Image edit — Krea 2, 13.5 GB), from [Comfy-Org/Krea-2](https://huggingface.co/Comfy-Org/Krea-2)
- `z_image_turbo_bf16.safetensors` (Image card)
- `z_image_bf16.safetensors` (Image card — the Z-Image Base option, 11.5 GB),
  from [Comfy-Org/z_image](https://huggingface.co/Comfy-Org/z_image)
- `qwen_image_2.1_int8_convrot.safetensors` (Image, Image edit, Photo
  restore, Inpaint and Face swap — Qwen Image 2.1,
  7.3 GB), from [Comfy-Org/Qwen-Image-2.1](https://huggingface.co/Comfy-Org/Qwen-Image-2.1)
- `qwen_image_edit_2511_fp8_e4m3fn.safetensors` (Camera angle card — Qwen
  Image Edit 2511, 20.4 GB), from
  [drbaph/Qwen-Image-Edit-2511-FP8](https://huggingface.co/drbaph/Qwen-Image-Edit-2511-FP8)
- `flux1-Fill-Dev_FP8.safetensors` (Face swap, Inpaint — the Flux Fill option)
- `flux-2-klein-9b.safetensors` (Inpaint and the Image card — the FLUX.2 Klein option;
  download the fp8 release from [black-forest-labs/FLUX.2-klein-9b-fp8](https://huggingface.co/black-forest-labs/FLUX.2-klein-9b-fp8),
  ~9.5 GB, you have to accept the licence on Hugging Face, and save it under this name)
- `trellis_2_int8_convrot.safetensors` (3D model card, 4.9 GB), from
  [Comfy-Org/TRELLIS.2](https://huggingface.co/Comfy-Org/TRELLIS.2)
- `pixal3d_int8_convrot.safetensors` (3D model card — the second engine, Pixal3D,
  5.6 GB), from [Comfy-Org/Pixal3D](https://huggingface.co/Comfy-Org/Pixal3D)

**unet/** — note that `models/unet` and `models/diffusion_models` are, to ComfyUI,
**one and the same folder** (`unet` is just the older name). A GGUF model can sit
in either of them; there is no need to move it.
- `ernie-image-turbo-Q8_0.gguf` (Image card — the ERNIE Image Turbo option, 8.1 GB),
  from [unsloth/ERNIE-Image-Turbo-GGUF](https://huggingface.co/unsloth/ERNIE-Image-Turbo-GGUF);
  loaded by the `UnetLoaderGGUF` node from the ComfyUI-GGUF pack

**text_encoders/**
- `gemma4-12b-with-proj-ltx-2.5-comfy-int8-convrot.safetensors` (LTX 2.5
  card, **15 GB**), from
  [Lightricks/LTX-2.5](https://huggingface.co/Lightricks/LTX-2.5)
- `qwen3vl_32b_minimax_h3_nvfp4_awq.safetensors` (MiniMax H3, 14.6 GB)
- `qwen3vl_32b_minimax_h3_int8_convrot.safetensors` (Quick video card, 25.3 GB) —
  that template has its encoder hard-coded, so nvfp4 is not used there
- `qwen3vl_4b_fp8_scaled.safetensors` (Image edit — Krea 2, and Smart
  upscale, 4.9 GB), from [Comfy-Org/Krea-2](https://huggingface.co/Comfy-Org/Krea-2)
- `qwen_3_4b.safetensors` (Image card — both Z-Image Turbo and Base)
- `qwen3vl_8b_int8_convrot.safetensors` (Image, Image edit, Photo restore,
  Inpaint and Face swap — Qwen Image 2.1,
  9.4 GB), from [Comfy-Org/Qwen-Image-2.1](https://huggingface.co/Comfy-Org/Qwen-Image-2.1)
- `qwen_2.5_vl_7b_fp8_scaled.safetensors` (Camera angle card — the encoder for Qwen
  Image Edit 2511, 9.4 GB), from
  [Comfy-Org/Qwen-Image_ComfyUI](https://huggingface.co/Comfy-Org/Qwen-Image_ComfyUI)
- `qwen3.5_9b_qwen_image_2.1_pe_i2i.int8_convrot.safetensors` (**optional** —
  ✨ Improve (Qwen) for edits, 9.5 GB), from [Comfy-Org/Qwen-Image-2.1](https://huggingface.co/Comfy-Org/Qwen-Image-2.1)
- `qwen3.5_9b_qwen_image_2.1_pe_t2i.int8_convrot.safetensors` (**optional** —
  ✨ Improve (Qwen) on the Image card, 9.5 GB), from the same repository
- `clip_l.safetensors` + `t5xxl_fp16.safetensors` (Face swap, Inpaint — Flux Fill)
- `qwen_3_8b_fp8mixed.safetensors` (Image card and Inpaint — FLUX.2 Klein), from
  [Comfy-Org/vae-text-encorder-for-flux-klein-9b](https://huggingface.co/Comfy-Org/vae-text-encorder-for-flux-klein-9b)
- `ministral-3-3b.safetensors` (Image card — ERNIE, 7.2 GB), from
  [Comfy-Org/ERNIE-Image](https://huggingface.co/Comfy-Org/ERNIE-Image)

**vae/**
- `ltx-2.5-video-vae-bf16.safetensors` and `ltx-2.5-audio-vae-bf16.safetensors`
  (LTX 2.5 card; without the second one the audio never reaches the latent), from
  [Lightricks/LTX-2.5](https://huggingface.co/Lightricks/LTX-2.5)
- `minimax_h3_video_vae_fp16.safetensors`
- `minimax_h3_audio_vae_fp32.safetensors`
- `qwen_image_vae.safetensors` (Image edit — Krea 2, and the Camera angle card), from
  [Comfy-Org/Qwen-Image_ComfyUI](https://huggingface.co/Comfy-Org/Qwen-Image_ComfyUI)
- `qwen_image_2.1_vae_bf16.safetensors` (Image, Image edit, Photo restore,
  Inpaint and Face swap — Qwen Image 2.1,
  0.7 GB; a 64-channel RGBA VAE for transparency), from
  [Comfy-Org/Qwen-Image-2.1](https://huggingface.co/Comfy-Org/Qwen-Image-2.1)
- `ae.sft` (Image card, Inpaint and Face swap — the FLUX/Z-Image autoencoder)
- `flux2-vae.safetensors` (Image card and Inpaint — FLUX.2 Klein and ERNIE),
  from the same repository as the encoder above
- `full_encoder_small_decoder.safetensors` (image editing through FLUX.2 Klein —
  it **encodes** the source image, so it needs the full encoder), from
  [black-forest-labs/FLUX.2-small-decoder](https://huggingface.co/black-forest-labs/FLUX.2-small-decoder)
- `trellis_2_shape_vae_bf16.safetensors` + `trellis_2_texture_vae_bf16.safetensors`
  (3D model card, both engines)

**clip_vision/**
- `dino_v3_vit_l.safetensors` (3D model card — TRELLIS.2)
- `dino_v3_L_naf_fp32.safetensors` (3D model card — Pixal3D)

**background_removal/**
- `birefnet.safetensors` (3D model card — removes the background from the photo), from
  [Comfy-Org/BiRefNet](https://huggingface.co/Comfy-Org/BiRefNet)

**geometry_estimation/**
- `moge_2_vitl_normal_fp16.safetensors` (3D model card — Pixal3D estimates the lens angle
  from the photo), from [Comfy-Org/MoGe](https://huggingface.co/Comfy-Org/MoGe)

**latent_upscale_models/**
- `ltx-2.5-latent-spatial-upscaler-x2-bf16-1.0.safetensors` (LTX 2.5 card —
  second pass, 0.95 GB), from
  [Lightricks/LTX-2.5](https://huggingface.co/Lightricks/LTX-2.5)
- `minimax_h3_latent_upscaler_3d_fp16.safetensors` (Quick video card and Long
  MiniMax — the 3 + 2 option), from [LBH-123-AI/Minimax_h3_latent_Upscaler](https://huggingface.co/LBH-123-AI/Minimax_h3_latent_Upscaler)

**loras/**
- `krea2_identity_edit_v1_2.safetensors` (Image edit — Krea 2; keeps identity
  during edits). This is the **Krea 2 Identity Edit** LoRA that goes with the
  [comfyui-krea2edit](https://github.com/lbouaraba/comfyui-krea2edit) pack —
  its README says where to get it.
- an acceleration (Turbo) LoRA for MiniMax H3 as chosen in the app — the list is read
  from the server; the public ones are in the `loras/` folder of
  [Comfy-Org/MiniMax-H3](https://huggingface.co/Comfy-Org/MiniMax-H3)
- `comfyui_portrait_lora64.safetensors` (ACE++), `FLUX.1-Turbo-Alpha.safetensors` (Face swap)
- `qwen-image-2.1-outpaint-v2.safetensors` (Inpaint → Extend the image,
  0.16 GB; without it the photo shifts or the new area stays grey), from
  [ausboss/Qwen-Image-2.1-Outpaint-LoRA](https://huggingface.co/ausboss/Qwen-Image-2.1-Outpaint-LoRA)
- `qwen-image-edit-2511-multiple-angles-lora.safetensors` (Camera angle card,
  0.3 GB; without it the viewpoint does not change), from
  [fal/Qwen-Image-Edit-2511-Multiple-Angles-LoRA](https://huggingface.co/fal/Qwen-Image-Edit-2511-Multiple-Angles-LoRA)
- `Qwen-Image-Edit-2511-Lightning-8steps-V1.0-fp32.safetensors` (Camera angle
  card — 8-step speed-up, 1.7 GB), from
  [lightx2v/Qwen-Image-Edit-2511-Lightning](https://huggingface.co/lightx2v/Qwen-Image-Edit-2511-Lightning)
- `h3/TaoMate-H3-3step-ComfyUI.safetensors` (Quick video card and Long MiniMax —
  the FastVideo VSA and 3 + 2 options, 2.5 GB) — in the subfolder
  `h3` and under exactly this name; the whole procedure stands on it, without it three steps
  are too few. The original is published by [TaoLiveAIGC/TaoMate-H3](https://huggingface.co/TaoLiveAIGC/TaoMate-H3),
  a ComfyUI-ready form is e.g. [Robert1212star/TaoMate-H3-3Step-ComfyUI](https://huggingface.co/Robert1212star/TaoMate-H3-3Step-ComfyUI)

**vae_approx/**
- `taeh3.safetensors` (live preview during video, optional)

### Enhance video → Smooth

- `frame_interpolation/film_net_fp16.safetensors` (0.07 GB), from
  [Comfy-Org/frame_interpolation](https://huggingface.co/Comfy-Org/frame_interpolation).
  The `FrameInterpolate` and `FrameInterpolationModelLoader` nodes are in the ComfyUI core.

### Face swap → Qwen Image 2.1

The Qwen Image 2.1 models as for the Image edit card, plus two LoRAs (the nodes are in the ComfyUI
core; `ImageResizeKJv2` comes from KJNodes, the `deis_2m` sampler from RES4LYF):

- `loras/bfs_head_v1.1_qwen_2.1.safetensors` (0.26 GB), from
  [Alissonerdx/BFS-Best-Face-Swap](https://huggingface.co/Alissonerdx/BFS-Best-Face-Swap)
- `loras/p_qwen_image_2.1_8step_v0.1.safetensors` (0.34 GB), from
  [PrunaAI/Pruna-Qwen-Image-2.1](https://huggingface.co/PrunaAI/Pruna-Qwen-Image-2.1)

### Edit video → Replace character

The `WanSCAILToVideo`, `SCAIL2ColoredMask` and `SAM3_VideoTrack` nodes are in
the ComfyUI core (verified on 0.37.4).

- **SCAIL-2:** `diffusion_models/wan2.1_14B_SCAIL_2_int8_convrot.safetensors`
  (16.7 GB) and `loras/wan2.1_SCAIL_2_DPO_lora_bf16.safetensors` (1.2 GB),
  from [Comfy-Org/SCAIL-2](https://huggingface.co/Comfy-Org/SCAIL-2). Plus
  `sam3.1_multiplex_fp16.safetensors` and the shared Wan files from the section
  below (`umt5_xxl_fp8_e4m3fn_scaled`, `Wan2_1_VAE_bf16`, `clip_vision_h`,
  `lightx2v_I2V_14B_480p_cfg_step_distill_rank64_bf16`).
- **MiniMax H3:** `loras/h3_character_swap_pro4500_1000.safetensors` (0.16 GB),
  from [akatz-ai/MiniMax-H3-Character-Swap-LoRA](https://huggingface.co/akatz-ai/MiniMax-H3-Character-Swap-LoRA);
  otherwise the MiniMax H3 models, as for the All in One card.

### Edit video → Follow a video

MiniMax H3 with Fun ControlNet-Union; the `MiniMaxH3FunControlNetApply`,
`SDPoseKeypointExtractor`, `SDPoseDrawKeypoints` and `RTDETR_detect` nodes are in
the ComfyUI core (verified on 0.37.4).

- `model_patches/minimax_h3_fun_controlnet_union_pruned_int8_convrot.safetensors`
  (2.3 GB), from [Comfy-Org/MiniMax-H3](https://huggingface.co/Comfy-Org/MiniMax-H3)
- `checkpoints/sdpose_wholebody_fp16.safetensors` (1.9 GB) and
  `diffusion_models/rt_detr_v4-x-hgnet_fp16.safetensors` (0.12 GB) for pose,
  from [Comfy-Org/SDPose](https://huggingface.co/Comfy-Org/SDPose)
- the MiniMax H3 models (`minimax_h3_ref2va_pruned_int8_convrot`, encoder, both VAEs)
  and, for Fast, `loras/minimax_h3_ref2v_turbo_4step_v0.1_comfyui_bf16.safetensors`

### Edit video → By instruction

Bernini-R on Wan 2.2; the `BerniniConditioning` node is in the ComfyUI core
(verified on 0.37.4).

- `diffusion_models/wan2.2_bernini_r_high_noise_int8_convrot.safetensors` and
  `wan2.2_bernini_r_low_noise_int8_convrot.safetensors` (14.5 GB each),
  from [Comfy-Org/Bernini-R](https://huggingface.co/Comfy-Org/Bernini-R)
- `loras/lightx2v_T2V_14B_cfg_step_distill_v2_lora_rank64_bf16.safetensors`
  for Fast, from [Kijai/WanVideo_comfy](https://huggingface.co/Kijai/WanVideo_comfy)
- `umt5_xxl_fp8_e4m3fn_scaled` and `Wan2_1_VAE_bf16` from the section below

### Music → MiniMax Music 3

The `MiniMaxMusic3TextEncode` and `EmptyMiniMaxMusic3LatentAudio` nodes are in
the ComfyUI core (verified on 0.37.4). All from
[Comfy-Org/MiniMax-Music-3](https://huggingface.co/Comfy-Org/MiniMax-Music-3):

- `diffusion_models/minimax_music3_dit_fp16.safetensors` (4.9 GB)
- `text_encoders/minimax_music3_text_encoder_pruned_int8_convrot.safetensors` (9.2 GB)
- `vae/minimax_music3_dav.safetensors` (0.22 GB)

### Character motion (Wan) — To music and From video

The **To music** mode runs on Wan-Dancer, **From video** on Wan-Animate 2.
The `WanDancer*`, `WanAnimate2ToVideo` and `WanAnimate2Cache` nodes and the
`StartLoop`/`EndLoop` loop are in the ComfyUI core (Wan-Animate 2 needs **0.36.0+**).

- `diffusion_models/wan_animate_2_int8_convrot.safetensors` (From video, 16.7 GB),
  from [Comfy-Org/Wan-Animate-2](https://huggingface.co/Comfy-Org/Wan-Animate-2)
- `text_encoders/umt5_xxl_fp8_e4m3fn_scaled.safetensors` (From video, 6.7 GB),
  from [Comfy-Org/Wan-Animate-2](https://huggingface.co/Comfy-Org/Wan-Animate-2)
- `diffusion_models/wan2.2_dancer_14b_global_fp8_scaled.safetensors` and
  `wan2.2_dancer_14b_local_fp8_scaled.safetensors` (To music, 17.1 GB each),
  from [Comfy-Org/Wan-Dancer](https://huggingface.co/Comfy-Org/Wan-Dancer)
- `text_encoders/umt5_xxl_fp16.safetensors` (To music, 11.4 GB)
- `vae/Wan2_1_VAE_bf16.safetensors`, `clip_vision/clip_vision_h.safetensors`
  and `loras/lightx2v_I2V_14B_480p_cfg_step_distill_rank64_bf16.safetensors`
  (both modes)

### Qwen Image 2.1 licence

Qwen Image 2.1 is not under Apache 2.0. The weights were released under the
**Qwen Research License Agreement** and, without a separate licence, are permitted
only for **non-commercial research and evaluation**. For commercial use the authors
require a separate licence via `model-business@notice.qwencloud.com`.
The same applies to both prompt rewriters, `Qwen-Image-2.1-PE-I2I` and `-PE-T2I`.

The app **neither distributes nor downloads the weights** — users take them directly from Hugging
Face and must accept the terms themselves. The app does, however, include the **system
prompts** of both rewriters (`system_prompt.txt` from the authors' repositories), without
which the models would not work. The licence permits distributing them for non-commercial purposes;
its full text is in the repository as
[`LICENSE-Qwen-Research.txt`](LICENSE-Qwen-Research.txt), and a link to the original:
[QwenLM/Qwen-Image-2.1 LICENSE](https://github.com/QwenLM/Qwen-Image-2.1/blob/main/LICENSE).

**SeedVR2** (Upscale photo card): the pack **downloads** `seedvr2_ema_7b-Q4_K_M.gguf` and
`ema_vae_fp16.safetensors` **by itself on first use**.
The **DLSS 5** method on the same card has no model, but after installing the pack
you have to run its `install_runtime.py` once — it downloads a third-party
runtime (~470 MB) and asks about the licence.

**Smart upscale** (Upscale photo card) needs, besides Z-Image Turbo, also
`qwen3vl_4b_fp8_scaled.safetensors` (models/text_encoders, 4.9 GB,
[Comfy-Org/Krea-2](https://huggingface.co/Comfy-Org/Krea-2/resolve/main/text_encoders/qwen3vl_4b_fp8_scaled.safetensors))
and `Z-Image-Turbo-Fun-Controlnet-Tile-2.1-lite-2601-8steps.safetensors`
(models/model_patches, 2 GB,
[alibaba-pai](https://huggingface.co/alibaba-pai/Z-Image-Turbo-Fun-Controlnet-Union-2.1)).

## Optional

- **Higgs Audio Studio** (port 7860) — only for voicing lines on the
  Dialogue card; without it the rest of the app works normally.
- **A launcher on the PC** (port 8190) — can start and stop ComfyUI remotely
  (to free the GPU). Without it ComfyUI has to be running while you use the app.
- **The ✨ Improve buttons** — you type a few words, in Czech if you like, and a
  language model turns them into a full English prompt. Depending on the card
  these rewriters are used:

  | Button | Cards | What it needs |
  |---|---|---|
  | **✨ Improve (MiniMax)** | All in One, Quick video, Long MiniMax (continuing a shot uses llama.cpp from the row below) | the [MiniMax-H3-Prompt-Rewriter-ComfyUI](https://github.com/pytraveler/MiniMax-H3-Prompt-Rewriter-ComfyUI) pack, a model in `models/LLM`: base + projector + adapter |
  | **✨ Improve** and **✨ Improve (unfiltered)** | Image, Image edit (with Qwen Image 2.1), LTX 2.5 | the [ComfyUI-llama-cpp_vlm](https://github.com/lihaoyun6/ComfyUI-llama-cpp_vlm) pack, a model in `models/LLM`: the base alone is enough |
  | **✨ Improve (Qwen)** | Image and Image edit with Qwen Image 2.1 | the PE-T2I / PE-I2I rewriters (see above), the built-in `TextGenerate` node |
  | **✨ Improve (LTX)** | LTX 2.5 | nothing extra — it runs on the card's own `gemma4` encoder |

  **It runs as an ordinary workflow in your ComfyUI** — the app sends a small graph
  to `/prompt` and reads the finished text from the history. So nothing is downloaded
  onto the phone and nothing leaves the house.

  Files for `models/LLM` (~7 GB together; `instalace-serveru.bat` can download them
  for you):
  - **base:** [Huihui-Qwen3-VL-8B-Instruct-abliterated Q4_K_M](https://huggingface.co/noctrex/Huihui-Qwen3-VL-8B-Instruct-abliterated-GGUF/resolve/main/Huihui-Qwen3-VL-8B-Instruct-abliterated-Q4_K_M.gguf)
    (4.7 GB) — an uncensored build, so it does not refuse prompts,
  - **projector:** [mmproj-F16](https://huggingface.co/noctrex/Huihui-Qwen3-VL-8B-Instruct-abliterated-GGUF/resolve/main/mmproj-F16.gguf) (1.1 GB) from the same repository;
    save it as `Huihui-Qwen3-VL-8B-Instruct-abliterated-mmproj-F16.gguf`,
  - **adapter (only for ✨ Improve (MiniMax)):**
    [MiniMax-H3-Prompt-Rewriter-LoRA-8B-F16](https://huggingface.co/pytraveler/MiniMax-H3-Prompt-Rewriter-LoRA-8B-GGUF/resolve/main/MiniMax-H3-Prompt-Rewriter-LoRA-8B-F16.gguf) (1.3 GB).

  The same pack and model also power the **🌐 Translate** button
  on the cards with a prompt — nothing else needs to be downloaded for translation.

  The MiniMax rewriter node can also download the model by itself on first use
  (it offers it in the list); the llama.cpp node cannot — there the file really has to be in the
  `models/LLM` folder. The app then picks the model from the server's list
  by itself (preferring the uncensored one) and, once the prompt is written, releases it from GPU
  memory, so it does not hold back generation. Without all this the app works normally, only
  the button reports what is missing on the server.
- **Image card — the "Photoreal (uncensored)" model** (18+): expects the file
  `zimage_nsfw_photoreal_v61_Q8.gguf` in `models/diffusion_models/` (or
  `models/unet/`, the same folder to ComfyUI), which you download yourself from CivitAI;
  it is loaded by the `UnetLoaderGGUF` node from the ComfyUI-GGUF pack. Without it the card
  runs normally on the other models.
- **Image card — your own LoRAs**: the card has two fields, **LoRA for <model>**
  and **Second LoRA (optional)**. The list is read from `models/loras/` on the server
  and sorted by the selected model: the app recognises the LoRA family from the file's
  metadata, or from its name (`zimage`/`zit` for Z-Image, `ernie`, `klein`
  + `9b` for FLUX.2 Klein 9B, LoRAs for Qwen Image 2.1). Files with no recognisable
  model are offered below a divider as "model not labelled" and the app asks before
  using them. **You do not need to rename anything.**

## How to verify you have everything

In the app: **Settings → What the server is missing → Check server.** The app
asks the server about every node class from its workflows and, for combo inputs
(models, LoRAs, VAEs), verifies that the server really offers the file. Whatever is missing is listed
by name — install nodes through ComfyUI-Manager, upload models into the folders above.

### Detailer for Qwen Image 2.1

`elusarcas-qwen2-1-detailer-v1.safetensors` (80 MB) into `models/loras` —
from [reverentelusarca/elusarcas-qwen-2.1-detail-enhancer-lora](https://huggingface.co/reverentelusarca/elusarcas-qwen-2.1-detail-enhancer-lora).
The **Photo restore** card always uses it; on the **Image edit**
and **Image** (Qwen Image 2.1) cards it is the **Detailer** toggle.
