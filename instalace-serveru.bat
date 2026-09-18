@echo off
setlocal enabledelayedexpansion
REM ============================================================
REM  PocketComfy - instalace serverovych zavislosti do ComfyUI
REM  Spust tento soubor ze SLOZKY ComfyUI (tam, kde je main.py).
REM  1) naklonuje potrebne custom nody
REM  2) volitelne stahne verejne dostupne modely (po kartach)
REM  Po dokonceni RESTARTUJ ComfyUI a v aplikaci spust
REM  Nastaveni -^> Co serveru chybi -^> Zkontrolovat server.
REM
REM  Seznam odpovida appce 3.57 a souboru POZADAVKY.md.
REM  Vsechny odkazy nize byly overene 16. 9. 2026.
REM ============================================================

if not exist main.py (
  echo [CHYBA] Tento skript spust ze slozky ComfyUI - tam, kde je main.py.
  pause
  exit /b 1
)

echo.
echo === 1/2  Custom nody ===
REM --- video karty a All in One
call :klon https://github.com/LeonQ8/ComfyUI-ALLinONE-MinimaxH3
call :klon https://github.com/rgthree/rgthree-comfy
call :klon https://github.com/kijai/ComfyUI-KJNodes
call :klon https://github.com/Kosinkadink/ComfyUI-VideoHelperSuite
REM Volitelne zrychleni video karet.
call :klon https://github.com/Icyoung/ComfyUI-MiniMaxH3-TeaCache
REM Karta Dlouhe video - navaznost useku na sebe.
call :klon https://github.com/seitanism/ComfyUI-H3-Motion-Context-MultiRef
REM Volitelne: rychly prvni zaber u Dlouheho videa.
call :klon https://github.com/LBH-123-AI/Comfyui_Minimax_h3_latent_Upscaler
REM All in One, volba Premalovat ve videu.
call :klon https://github.com/drozbay/MaskVidExperiments
REM --- karta Zvetsit
call :klon https://github.com/numz/ComfyUI-SeedVR2_VideoUpscaler
call :klon https://github.com/Praveenhalder/praveen-tools
REM Volitelne: metoda DLSS 5 na karte Zvetsit. Po naklonovani je potreba
REM jeste jednou spustit install_runtime.py - viz konec skriptu.
call :klon https://github.com/Blueforcer/ComfyUI-DLSS5-Enhancer
REM --- karty s obrazky
call :klon https://github.com/lquesada/ComfyUI-Inpaint-CropAndStitch
call :klon https://github.com/lrzjason/Comfyui-QwenEditUtils
call :klon https://github.com/cubiq/ComfyUI_essentials
call :klon https://github.com/ltdrdata/ComfyUI-Impact-Pack
REM Jen pro volitelny model v GGUF na karte Obrazek.
call :klon https://github.com/city96/ComfyUI-GGUF
REM Volitelne: tlacitko "Vylepsit prompt" na karte All in One - LLM prepise
REM kratke zadani na plny H3 prompt. Model viz nabidka nize.
call :klon https://github.com/pytraveler/MiniMax-H3-Prompt-Rewriter-ComfyUI
REM Volitelne: totez pro kartu Obrazek - prompty pro Z-Image. Tenhle uzel si
REM model sam nestahne - musi lezet v models\LLM, viz nabidka nize.
call :klon https://github.com/lihaoyun6/ComfyUI-llama-cpp_vlm

echo.
echo   Pozn.: knihovny, ktere si nektere balicky zadaji v requirements.txt,
echo          doinstaluj pres ComfyUI-Manager - tlacitko Try Fix u balicku.
echo          Rucni "pip install -U" umi prepsat torch a shodit CUDA.
echo.
echo   Karty Casova osa a Uprava obrazku jedou na balicich, ktere nejsou
echo   verejne. Zbytek appky funguje i bez nich.

echo.
echo === 2/2  Modely (volitelne, velke soubory) ===
echo Muzes stahnout jen karty, ktere chces pouzivat. Co uz mas, se preskoci.
echo.

set /p ODP="Video karty - MiniMax H3, cca 59 GB. Stahnout? [a/n] "
if /i "!ODP!"=="a" (
  call :stahni "https://huggingface.co/Comfy-Org/MiniMax-H3/resolve/main/diffusion_models/minimax_h3_fl2va_pruned_int8_convrot.safetensors" "models\diffusion_models\minimax_h3_fl2va_pruned_int8_convrot.safetensors"
  call :stahni "https://huggingface.co/Comfy-Org/MiniMax-H3/resolve/main/diffusion_models/minimax_h3_ref2va_pruned_int8_convrot.safetensors" "models\diffusion_models\minimax_h3_ref2va_pruned_int8_convrot.safetensors"
  call :stahni "https://huggingface.co/Comfy-Org/MiniMax-H3/resolve/main/text_encoders/qwen3vl_32b_minimax_h3_nvfp4_awq.safetensors" "models\text_encoders\qwen3vl_32b_minimax_h3_nvfp4_awq.safetensors"
  call :stahni "https://huggingface.co/Comfy-Org/MiniMax-H3/resolve/main/vae/minimax_h3_video_vae_fp16.safetensors" "models\vae\minimax_h3_video_vae_fp16.safetensors"
  call :stahni "https://huggingface.co/Comfy-Org/MiniMax-H3/resolve/main/vae/minimax_h3_audio_vae_fp32.safetensors" "models\vae\minimax_h3_audio_vae_fp32.safetensors"
)

set /p ODP="Video karty - zrychlovaci Turbo LoRA, cca 5,5 GB. Stahnout? [a/n] "
if /i "!ODP!"=="a" (
  call :stahni "https://huggingface.co/Comfy-Org/MiniMax-H3/resolve/main/loras/minimax_h3_fl2v_turbo_4step_v1.0_768p_comfyui_bf16.safetensors" "models\loras\minimax_h3_fl2v_turbo_4step_v1.0_768p_comfyui_bf16.safetensors"
  call :stahni "https://huggingface.co/Comfy-Org/MiniMax-H3/resolve/main/loras/minimax_h3_fl2v_turbo_8step_v1.0_comfyui_bf16.safetensors" "models\loras\minimax_h3_fl2v_turbo_8step_v1.0_comfyui_bf16.safetensors"
  call :stahni "https://huggingface.co/Comfy-Org/MiniMax-H3/resolve/main/loras/minimax_h3_ref2v_turbo_4step_v0.1_comfyui_bf16.safetensors" "models\loras\minimax_h3_ref2v_turbo_4step_v0.1_comfyui_bf16.safetensors"
  echo   Pozn.: nabidku LoRA cte appka ze serveru - staci ty, ktere chces nabizet.
)

set /p ODP="All in One, Premalovat ve videu - SAM 3.1, cca 1,7 GB. Stahnout? [a/n] "
if /i "!ODP!"=="a" (
  call :stahni "https://huggingface.co/Comfy-Org/sam3.1/resolve/main/checkpoints/sam3.1_multiplex_fp16.safetensors" "models\checkpoints\sam3.1_multiplex_fp16.safetensors"
)

set /p ODP="Dlouhe video a 3 kroky - latentni zvetsovac, cca 0,7 GB. Stahnout? [a/n] "
if /i "!ODP!"=="a" (
  call :stahni "https://huggingface.co/LBH-123-AI/Minimax_h3_latent_Upscaler/resolve/main/minimax_h3_latent_upscaler_3d_fp16.safetensors" "models\latent_upscale_models\minimax_h3_latent_upscaler_3d_fp16.safetensors"
)

set /p ODP="Karta 3 kroky - textovy enkoder int8, cca 25,3 GB. Stahnout? [a/n] "
if /i "!ODP!"=="a" (
  REM Tahle predloha ma enkoder zapsany napevno - nvfp4 z video karet
  REM se pro ni nepouzije.
  call :stahni "https://huggingface.co/Comfy-Org/MiniMax-H3/resolve/main/text_encoders/qwen3vl_32b_minimax_h3_int8_convrot.safetensors" "models\text_encoders\qwen3vl_32b_minimax_h3_int8_convrot.safetensors"
  echo   [rucne] karta stoji na LoRA TaoMate 3step - bez ni jsou tri kroky malo.
  echo          Originalni adapter: https://huggingface.co/TaoLiveAIGC/TaoMate-H3
  echo          Podoba pro ComfyUI: https://huggingface.co/Robert1212star/TaoMate-H3-3Step-ComfyUI
  echo          Uloz ji jako models\loras\h3\TaoMate-H3-3step-ComfyUI.safetensors
  echo          - predloha ji hleda presne pod timhle jmenem i v podslozce h3.
)

set /p ODP="Karta Obrazek - Z-Image Turbo, cca 19 GB. Stahnout? [a/n] "
if /i "!ODP!"=="a" (
  call :stahni "https://huggingface.co/Comfy-Org/z_image_turbo/resolve/main/split_files/diffusion_models/z_image_turbo_bf16.safetensors" "models\diffusion_models\z_image_turbo_bf16.safetensors"
  call :stahni "https://huggingface.co/Comfy-Org/z_image_turbo/resolve/main/split_files/text_encoders/qwen_3_4b.safetensors" "models\text_encoders\qwen_3_4b.safetensors"
  call :stahni "https://huggingface.co/Comfy-Org/z_image_turbo/resolve/main/split_files/vae/ae.safetensors" "models\vae\ae.sft"
)

set /p ODP="Karta Obrazek - Z-Image Base, cca 11,5 GB navic. Stahnout? [a/n] "
if /i "!ODP!"=="a" (
  call :stahni "https://huggingface.co/Comfy-Org/z_image/resolve/main/split_files/diffusion_models/z_image_bf16.safetensors" "models\diffusion_models\z_image_bf16.safetensors"
  call :stahni "https://huggingface.co/Comfy-Org/z_image_turbo/resolve/main/split_files/text_encoders/qwen_3_4b.safetensors" "models\text_encoders\qwen_3_4b.safetensors"
  call :stahni "https://huggingface.co/Comfy-Org/z_image_turbo/resolve/main/split_files/vae/ae.safetensors" "models\vae\ae.sft"
)

set /p ODP="Karta Obrazek - ERNIE Image Turbo, cca 15,5 GB. Stahnout? [a/n] "
if /i "!ODP!"=="a" (
  call :stahni "https://huggingface.co/unsloth/ERNIE-Image-Turbo-GGUF/resolve/main/ernie-image-turbo-Q8_0.gguf" "models\unet\ernie-image-turbo-Q8_0.gguf"
  call :stahni "https://huggingface.co/Comfy-Org/ERNIE-Image/resolve/main/text_encoders/ministral-3-3b.safetensors" "models\text_encoders\ministral-3-3b.safetensors"
  call :stahni "https://huggingface.co/Comfy-Org/ERNIE-Image/resolve/main/vae/flux2-vae.safetensors" "models\vae\flux2-vae.safetensors"
  echo   Pozn.: GGUF nacita uzel z balicku ComfyUI-GGUF, ten uz je naklonovany.
)

set /p ODP="Karty Obrazek a Domalovat - FLUX.2 Klein enkoder a VAE, cca 8,7 GB. Stahnout? [a/n] "
if /i "!ODP!"=="a" (
  call :stahni "https://huggingface.co/Comfy-Org/vae-text-encorder-for-flux-klein-9b/resolve/main/split_files/text_encoders/qwen_3_8b_fp8mixed.safetensors" "models\text_encoders\qwen_3_8b_fp8mixed.safetensors"
  call :stahni "https://huggingface.co/Comfy-Org/vae-text-encorder-for-flux-klein-9b/resolve/main/split_files/vae/flux2-vae.safetensors" "models\vae\flux2-vae.safetensors"
  REM Uprava fotky pres Klein predlohu enkoduje, takze chce plny enkoder.
  call :stahni "https://huggingface.co/black-forest-labs/FLUX.2-small-decoder/resolve/main/full_encoder_small_decoder.safetensors" "models\vae\full_encoder_small_decoder.safetensors"
  echo   [rucne] samotny model flux-2-klein-9b.safetensors vydava Black Forest Labs
  echo          pod licenci, kterou je potreba na Hugging Face odklepnout:
  echo          https://huggingface.co/black-forest-labs/FLUX.2-klein-9b-fp8
  echo          Soubor uloz do models\diffusion_models pod nazvem flux-2-klein-9b.safetensors.
  echo   Pozn.: druha volba karty Domalovat jede na modelech z karty Vymena tvare.
)

set /p ODP="Karta Hudba - ACE-Step 1.5, cca 9 GB. Stahnout? [a/n] "
if /i "!ODP!"=="a" (
  call :stahni "https://huggingface.co/Comfy-Org/ace_step_1.5_ComfyUI_files/resolve/main/checkpoints/ace_step_1.5_turbo_aio.safetensors" "models\checkpoints\ace_step_1.5_turbo_aio.safetensors"
)

set /p ODP="Karta Hudba - YuE2, cca 3,9 GB. Stahnout? [a/n] "
if /i "!ODP!"=="a" (
  call :stahni "https://huggingface.co/Comfy-Org/YuE2/resolve/main/checkpoints/yue2_3b_int8_convrot.safetensors" "models\checkpoints\yue2_3b_int8_convrot.safetensors"
  echo   Pozn.: uzly YuE2 jsou primo v jadru ComfyUI od verze 0.36.0.
  echo          Na starsi verzi se nic neinstaluje - ComfyUI se aktualizuje.
)

set /p ODP="Karta Hudba - Predelat nahravku - SheetSage2, cca 1,3 GB. Stahnout? [a/n] "
if /i "!ODP!"=="a" (
  REM Prepisovac melodie do not. Jiny model nez YuE2 a jina slozka.
  call :stahni "https://huggingface.co/Comfy-Org/YuE2/resolve/main/audio_encoders/sheetsage2_bf16.safetensors" "models\audio_encoders\sheetsage2_bf16.safetensors"
  echo   Pozn.: k tomu je potreba i samotny YuE2 z nabidky vyse.
)

set /p ODP="Karta Video ze zvuku - LTX 2.5, cca 38 GB. Stahnout? [a/n] "
if /i "!ODP!"=="a" (
  REM Fotka + zvukovy soubor -> video, ktere na ten zvuk mluvi. Pocet snimku
  REM si graf spocita z delky zvuku, takze se rec nema jak useknout.
  call :stahni "https://huggingface.co/Lightricks/LTX-2.5/resolve/main/transformers/ltx-2.5-22b-distilled-transformer-comfy-int8-convrot.safetensors" "models\diffusion_models\ltx-2.5-22b-distilled-transformer-comfy-int8-convrot.safetensors"
  call :stahni "https://huggingface.co/Lightricks/LTX-2.5/resolve/main/text_encoders/gemma4-12b-with-proj-ltx-2.5-comfy-int8-convrot.safetensors" "models\text_encoders\gemma4-12b-with-proj-ltx-2.5-comfy-int8-convrot.safetensors"
  call :stahni "https://huggingface.co/Lightricks/LTX-2.5/resolve/main/vae/ltx-2.5-video-vae-bf16.safetensors" "models\vae\ltx-2.5-video-vae-bf16.safetensors"
  call :stahni "https://huggingface.co/Lightricks/LTX-2.5/resolve/main/vae/ltx-2.5-audio-vae-bf16.safetensors" "models\vae\ltx-2.5-audio-vae-bf16.safetensors"
  call :stahni "https://huggingface.co/Lightricks/LTX-2.5/resolve/main/latent_upscale_models/ltx-2.5-latent-spatial-upscaler-x2-bf16-1.0.safetensors" "models\latent_upscale_models\ltx-2.5-latent-spatial-upscaler-x2-bf16-1.0.safetensors"
  echo   Pozn.: k tomu je potreba balik ComfyUI-LTXVideo ve verzi, ktera zna LTX 2.5.
)

set /p ODP="Karta Oprava fotky - Qwen Image Edit 2511, cca 31 GB. Stahnout? [a/n] "
if /i "!ODP!"=="a" (
  call :stahni "https://huggingface.co/drbaph/Qwen-Image-Edit-2511-FP8/resolve/main/qwen_image_edit_2511_fp8_e4m3fn.safetensors" "models\diffusion_models\qwen_image_edit_2511_fp8_e4m3fn.safetensors"
  call :stahni "https://huggingface.co/Comfy-Org/Qwen-Image_ComfyUI/resolve/main/split_files/text_encoders/qwen_2.5_vl_7b_fp8_scaled.safetensors" "models\text_encoders\qwen_2.5_vl_7b_fp8_scaled.safetensors"
  call :stahni "https://huggingface.co/Comfy-Org/Qwen-Image_ComfyUI/resolve/main/split_files/vae/qwen_image_vae.safetensors" "models\vae\qwen_image_vae.safetensors"
  call :stahni "https://huggingface.co/lightx2v/Qwen-Image-Edit-2511-Lightning/resolve/main/Qwen-Image-Edit-2511-Lightning-4steps-V1.0-fp32.safetensors" "models\loras\Qwen-Image-Edit-2511-Lightning-4steps-V1.0-fp32.safetensors"
  call :stahni "https://huggingface.co/starsfriday/Qwen-Image-Edit-2511-Upscale2K/resolve/main/qwen_image_edit_2511_upscale.safetensors" "models\loras\qwen_image_edit_2511_upscale.safetensors"
  call :stahni "https://huggingface.co/flymy-ai/qwen-image-realism-lora/resolve/main/flymy_realism.safetensors" "models\loras\flymy_realism.safetensors"
)

set /p ODP="Karta Uhel kamery - LoRA k Qwen Image Edit, cca 1,9 GB. Stahnout? [a/n] "
if /i "!ODP!"=="a" (
  call :stahni "https://huggingface.co/fal/Qwen-Image-Edit-2511-Multiple-Angles-LoRA/resolve/main/qwen-image-edit-2511-multiple-angles-lora.safetensors" "models\loras\qwen-image-edit-2511-multiple-angles-lora.safetensors"
  call :stahni "https://huggingface.co/lightx2v/Qwen-Image-Edit-2511-Lightning/resolve/main/Qwen-Image-Edit-2511-Lightning-8steps-V1.0-fp32.safetensors" "models\loras\Qwen-Image-Edit-2511-Lightning-8steps-V1.0-fp32.safetensors"
  echo   Pozn.: zaklad si tahle karta bere z karty Oprava fotky - stahni i ji.
)

set /p ODP="Karta Vymena tvare - Flux Fill + ACE++, cca 23 GB. Stahnout? [a/n] "
if /i "!ODP!"=="a" (
  call :stahni "https://huggingface.co/Academia-SD/flux1-Fill-Dev-FP8/resolve/main/flux1-Fill-Dev_FP8.safetensors" "models\diffusion_models\flux1-Fill-Dev_FP8.safetensors"
  call :stahni "https://huggingface.co/comfyanonymous/flux_text_encoders/resolve/main/clip_l.safetensors" "models\text_encoders\clip_l.safetensors"
  call :stahni "https://huggingface.co/comfyanonymous/flux_text_encoders/resolve/main/t5xxl_fp16.safetensors" "models\text_encoders\t5xxl_fp16.safetensors"
  call :stahni "https://huggingface.co/ali-vilab/ACE_Plus/resolve/main/portrait/comfyui_portrait_lora64.safetensors" "models\loras\comfyui_portrait_lora64.safetensors"
  call :stahni "https://huggingface.co/alimama-creative/FLUX.1-Turbo-Alpha/resolve/main/diffusion_pytorch_model.safetensors" "models\loras\FLUX.1-Turbo-Alpha.safetensors"
  call :stahni "https://huggingface.co/Comfy-Org/z_image_turbo/resolve/main/split_files/vae/ae.safetensors" "models\vae\ae.sft"
)

set /p ODP="Karta 3D model - TRELLIS.2, cca 8,3 GB. Stahnout? [a/n] "
if /i "!ODP!"=="a" (
  call :stahni "https://huggingface.co/Comfy-Org/TRELLIS.2/resolve/main/diffusion_models/trellis_2_int8_convrot.safetensors" "models\diffusion_models\trellis_2_int8_convrot.safetensors"
  call :stahni "https://huggingface.co/Comfy-Org/TRELLIS.2/resolve/main/vae/trellis_2_shape_vae_bf16.safetensors" "models\vae\trellis_2_shape_vae_bf16.safetensors"
  call :stahni "https://huggingface.co/Comfy-Org/TRELLIS.2/resolve/main/vae/trellis_2_texture_vae_bf16.safetensors" "models\vae\trellis_2_texture_vae_bf16.safetensors"
  call :stahni "https://huggingface.co/Comfy-Org/TRELLIS.2/resolve/main/clip_vision/dino_v3_vit_l.safetensors" "models\clip_vision\dino_v3_vit_l.safetensors"
  call :stahni "https://huggingface.co/Comfy-Org/BiRefNet/resolve/main/background_removal/birefnet.safetensors" "models\background_removal\birefnet.safetensors"
  echo   Pozn.: uzly TRELLIS.2 jsou primo v jadru ComfyUI od verze 0.34.
)

set /p ODP="Karta 3D model - druhy motor Pixal3D, cca 7,5 GB navic. Stahnout? [a/n] "
if /i "!ODP!"=="a" (
  call :stahni "https://huggingface.co/Comfy-Org/Pixal3D/resolve/main/diffusion_models/pixal3d_int8_convrot.safetensors" "models\diffusion_models\pixal3d_int8_convrot.safetensors"
  call :stahni "https://huggingface.co/Comfy-Org/Pixal3D/resolve/main/clip_vision/dino_v3_L_naf_fp32.safetensors" "models\clip_vision\dino_v3_L_naf_fp32.safetensors"
  call :stahni "https://huggingface.co/Comfy-Org/MoGe/resolve/main/geometry_estimation/moge_2_vitl_normal_fp16.safetensors" "models\geometry_estimation\moge_2_vitl_normal_fp16.safetensors"
  echo   Pozn.: Pixal3D jede na stejnych VAE jako TRELLIS.2 - stahni i je.
)

echo.
echo Tlacitko "Vylepsit prompt" - jazykovy model do models\LLM, cca 7 GB.
echo   Bez nej karty jedou normalne, jen tlacitko ohlasi chybejici model.
set /p ODP="Stahnout? [a/n] "
if /i "!ODP!"=="a" (
  call :stahni "https://huggingface.co/noctrex/Huihui-Qwen3-VL-8B-Instruct-abliterated-GGUF/resolve/main/Huihui-Qwen3-VL-8B-Instruct-abliterated-Q4_K_M.gguf" "models\LLM\Huihui-Qwen3-VL-8B-Instruct-abliterated-Q4_K_M.gguf"
  call :stahni "https://huggingface.co/noctrex/Huihui-Qwen3-VL-8B-Instruct-abliterated-GGUF/resolve/main/mmproj-F16.gguf" "models\LLM\Huihui-Qwen3-VL-8B-Instruct-abliterated-mmproj-F16.gguf"
  call :stahni "https://huggingface.co/pytraveler/MiniMax-H3-Prompt-Rewriter-LoRA-8B-GGUF/resolve/main/MiniMax-H3-Prompt-Rewriter-LoRA-8B-F16.gguf" "models\LLM\MiniMax-H3-Prompt-Rewriter-LoRA-8B-F16.gguf"
)

echo.
echo Karta Zvetsit: SeedVR2 si sve modely stahne SAM pri prvnim pouziti.
echo Metoda DLSS 5 chce jeste jeden krok - spust jednou tenhle prikaz,
echo kde PYTHON je python tveho ComfyUI:
echo   PYTHON custom_nodes\ComfyUI-DLSS5-Enhancer\install_runtime.py
echo   Stahne si runtime treti strany, cca 470 MB, a zepta se na licenci.
echo.
echo Karta Uprava obrazku - Krea 2: uzly ani modely nejsou verejne, viz POZADAVKY.md.
echo Karta Obrazek - prepinac "Bez cenzury" a model "Photoreal": vlastni soubory
echo   z CivitAI, viz POZADAVKY.md.
echo.
echo HOTOVO. Ted RESTARTUJ ComfyUI a v aplikaci spust:
echo   Nastaveni ^-^> Co serveru chybi ^-^> Zkontrolovat server
echo.
pause
exit /b 0

:klon
set URL=%~1
for %%A in (%URL%) do set NAZEV=%%~nxA
if exist "custom_nodes\!NAZEV!" (
  echo   [ok] !NAZEV! uz je nainstalovany
) else (
  echo   Klonuji !NAZEV! ...
  git clone --depth 1 %URL% "custom_nodes\!NAZEV!"
)
exit /b 0

:stahni
set CIL=%~2
if exist "!CIL!" (
  echo   [ok] uz existuje: !CIL!
  exit /b 0
)
for %%D in ("!CIL!") do if not exist "%%~dpD" mkdir "%%~dpD"
echo   Stahuji !CIL! ...
curl.exe -L --retry 5 --retry-delay 10 -o "!CIL!.part" %1
if errorlevel 1 (
  echo   [CHYBA] stazeni selhalo: !CIL!
  del "!CIL!.part" 2>nul
  exit /b 1
)
move /y "!CIL!.part" "!CIL!" >nul
exit /b 0
