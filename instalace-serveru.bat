@echo off
setlocal enabledelayedexpansion
REM ============================================================
REM  PocketComfy - instalace serverovych zavislosti do ComfyUI
REM  Spust tento soubor ze SLOZKY ComfyUI (tam, kde je main.py).
REM  1) naklonuje potrebne custom nody
REM  2) volitelne stahne verejne dostupne modely (po kartach)
REM  Po dokonceni RESTARTUJ ComfyUI a v aplikaci spust
REM  Nastaveni -> Zkontrolovat server.
REM ============================================================

if not exist main.py (
  echo [CHYBA] Tento skript spust ze slozky ComfyUI - tam, kde je main.py.
  pause
  exit /b 1
)

echo.
echo === 1/2  Custom nody ===
call :klon https://github.com/LeonQ8/ComfyUI-ALLinONE-MinimaxH3
call :klon https://github.com/rgthree/rgthree-comfy
call :klon https://github.com/numz/ComfyUI-SeedVR2_VideoUpscaler
call :klon https://github.com/lquesada/ComfyUI-Inpaint-CropAndStitch
call :klon https://github.com/lrzjason/Comfyui-QwenEditUtils
call :klon https://github.com/cubiq/ComfyUI_essentials
call :klon https://github.com/kijai/ComfyUI-KJNodes
call :klon https://github.com/Kosinkadink/ComfyUI-VideoHelperSuite
call :klon https://github.com/ltdrdata/ComfyUI-Impact-Pack
call :klon https://github.com/Icyoung/ComfyUI-MiniMaxH3-TeaCache
call :klon https://github.com/city96/ComfyUI-GGUF
REM Volitelne: tlacitko "Vylepsit prompt" na karte All in One - LLM prepise
REM kratke zadani na plny H3 prompt. Model viz nabidka nize.
call :klon https://github.com/pytraveler/MiniMax-H3-Prompt-Rewriter-ComfyUI
REM Volitelne: totez pro kartu Obrazek (prompty pro Z-Image). Tenhle uzel si
REM model sam nestahne - musi lezet v models\LLM, viz nabidka nize.
call :klon https://github.com/lihaoyun6/ComfyUI-llama-cpp_vlm

echo.
echo === 2/2  Modely (volitelne, velke soubory) ===
echo Muzes stahnout jen karty, ktere chces pouzivat.
echo.

set /p ODP="Karta Obrazek - Z-Image Turbo, cca 19 GB. Stahnout? [a/n] "
if /i "!ODP!"=="a" (
  call :stahni "https://huggingface.co/Comfy-Org/z_image_turbo/resolve/main/split_files/diffusion_models/z_image_turbo_bf16.safetensors" "models\diffusion_models\z_image_turbo_bf16.safetensors"
  call :stahni "https://huggingface.co/Comfy-Org/z_image_turbo/resolve/main/split_files/text_encoders/qwen_3_4b.safetensors" "models\text_encoders\qwen_3_4b.safetensors"
  call :stahni "https://huggingface.co/Comfy-Org/z_image_turbo/resolve/main/split_files/vae/ae.safetensors" "models\vae\ae.sft"
)

set /p ODP="Karta Hudba - ACE-Step 1.5, cca 9 GB. Stahnout? [a/n] "
if /i "!ODP!"=="a" (
  call :stahni "https://huggingface.co/Comfy-Org/ace_step_1.5_ComfyUI_files/resolve/main/checkpoints/ace_step_1.5_turbo_aio.safetensors" "models\checkpoints\ace_step_1.5_turbo_aio.safetensors"
)

set /p ODP="Karta Oprava fotky - Qwen Image Edit 2511, cca 31 GB. Stahnout? [a/n] "
if /i "!ODP!"=="a" (
  call :stahni "https://huggingface.co/drbaph/Qwen-Image-Edit-2511-FP8/resolve/main/qwen_image_edit_2511_fp8_e4m3fn.safetensors" "models\diffusion_models\qwen_image_edit_2511_fp8_e4m3fn.safetensors"
  call :stahni "https://huggingface.co/Comfy-Org/Qwen-Image_ComfyUI/resolve/main/split_files/text_encoders/qwen_2.5_vl_7b_fp8_scaled.safetensors" "models\text_encoders\qwen_2.5_vl_7b_fp8_scaled.safetensors"
  call :stahni "https://huggingface.co/Comfy-Org/Qwen-Image_ComfyUI/resolve/main/split_files/vae/qwen_image_vae.safetensors" "models\vae\qwen_image_vae.safetensors"
  call :stahni "https://huggingface.co/lightx2v/Qwen-Image-Edit-2511-Lightning/resolve/main/Qwen-Image-Edit-2511-Lightning-4steps-V1.0-fp32.safetensors" "models\loras\Qwen-Image-Edit-2511-Lightning-4steps-V1.0-fp32.safetensors"
  echo   [rucne] models\loras\qwen_image_edit_2511_upscale.safetensors a flymy_realism.safetensors - viz POZADAVKY.md
)

set /p ODP="Karta Vymena tvare - Flux Fill + ACE++, cca 23 GB. Stahnout? [a/n] "
if /i "!ODP!"=="a" (
  call :stahni "https://huggingface.co/Academia-SD/flux1-Fill-Dev-FP8/resolve/main/flux1-Fill-Dev_FP8.safetensors" "models\diffusion_models\flux1-Fill-Dev_FP8.safetensors"
  call :stahni "https://huggingface.co/comfyanonymous/flux_text_encoders/resolve/main/clip_l.safetensors" "models\text_encoders\clip_l.safetensors"
  call :stahni "https://huggingface.co/comfyanonymous/flux_text_encoders/resolve/main/t5xxl_fp16.safetensors" "models\text_encoders\t5xxl_fp16.safetensors"
  call :stahni "https://huggingface.co/ali-vilab/ACE_Plus/resolve/main/portrait/comfyui_portrait_lora64.safetensors" "models\loras\comfyui_portrait_lora64.safetensors"
  call :stahni "https://huggingface.co/alimama-creative/FLUX.1-Turbo-Alpha/resolve/main/diffusion_pytorch_model.safetensors" "models\loras\FLUX.1-Turbo-Alpha.safetensors"
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
echo Video karty (MiniMax H3) a Uprava obrazku (Krea 2): modely viz POZADAVKY.md.
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
