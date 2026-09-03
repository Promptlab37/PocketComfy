package cz.promptlab.h3video.comfy

/**
 * Překlad technických jmen na srozumitelné pokyny pro kontrolu serveru.
 *
 * Uživatel appky workflow v ComfyUI nevidí (jsou zabalená v APK), takže
 * z hlášky „UNETLoader → unet_name: flux1-Fill-Dev_FP8.safetensors" sám
 * nepozná, co má stáhnout, kam to patří ani jestli se ho to vůbec týká.
 * Tenhle katalog k tomu dodá balík, kartu, cílovou složku a odkaz.
 *
 * Je to jediný ručně udržovaný seznam v appce — co se kontroluje, plyne
 * dál z předloh. Když sem uzel nebo soubor nepatří, kontrola funguje
 * pořád, jen bez doplňujícího vysvětlení.
 */
object Katalog {

    /** Balík custom uzlů: co ho poskytuje a pro které karty. */
    data class Balik(val nazev: String, val odkaz: String, val karty: String)

    /** Soubor modelu: kam patří, pro kterou kartu a odkud ho vzít. */
    data class Soubor(val slozka: String, val karta: String, val odkaz: String? = null)

    private const val HF = "https://huggingface.co"

    private val ESSENTIALS = Balik(
        "ComfyUI_essentials", "https://github.com/cubiq/ComfyUI_essentials", "Výměna tváře"
    )
    private val KJ = Balik(
        "ComfyUI-KJNodes", "https://github.com/kijai/ComfyUI-KJNodes",
        "video karty, živý náhled, Výměna tváře"
    )
    private val VHS = Balik(
        "ComfyUI-VideoHelperSuite",
        "https://github.com/Kosinkadink/ComfyUI-VideoHelperSuite", "video karty"
    )
    private val RGTHREE = Balik(
        "rgthree-comfy", "https://github.com/rgthree/rgthree-comfy",
        "video karty, Výměna tváře"
    )
    private val SEEDVR2 = Balik(
        "ComfyUI-SeedVR2_VideoUpscaler",
        "https://github.com/numz/ComfyUI-SeedVR2_VideoUpscaler", "Zvětšit"
    )
    private val INPAINT = Balik(
        "ComfyUI-Inpaint-CropAndStitch",
        "https://github.com/lquesada/ComfyUI-Inpaint-CropAndStitch",
        "Výměna tváře a Domalovat"
    )
    private val QWENUTILS = Balik(
        "Comfyui-QwenEditUtils", "https://github.com/lrzjason/Comfyui-QwenEditUtils",
        "Oprava fotky"
    )
    private val IMPACT = Balik(
        "ComfyUI-Impact-Pack", "https://github.com/ltdrdata/ComfyUI-Impact-Pack",
        "Výměna tváře"
    )
    private val TEACACHE = Balik(
        "ComfyUI-MiniMaxH3-TeaCache",
        "https://github.com/Icyoung/ComfyUI-MiniMaxH3-TeaCache",
        "video karty (volitelné zrychlení)"
    )
    private val GGUF = Balik(
        "ComfyUI-GGUF", "https://github.com/city96/ComfyUI-GGUF",
        "Obrázek (jen volitelný model v GGUF)"
    )
    private val REWRITER = Balik(
        "MiniMax-H3-Prompt-Rewriter-ComfyUI",
        "https://github.com/pytraveler/MiniMax-H3-Prompt-Rewriter-ComfyUI",
        "tlačítko Vylepšit prompt (volitelné)"
    )
    private val LLAMACPP = Balik(
        "ComfyUI-llama-cpp_vlm", "https://github.com/lihaoyun6/ComfyUI-llama-cpp_vlm",
        "tlačítko Vylepšit prompt na kartě Obrázek (volitelné)"
    )
    private val PRAVEEN = Balik(
        "praveen-tools", "https://github.com/praveensahu/praveen-tools", "Zvětšit"
    )
    private val MULTIREF = Balik(
        "ComfyUI-H3-Motion-Context-MultiRef",
        "https://github.com/seitanism/ComfyUI-H3-Motion-Context-MultiRef",
        "Dlouhé video"
    )
    private val MASKVID = Balik(
        "MaskVidExperiments", "https://github.com/drozbay/MaskVidExperiments",
        "All in One → Přemalovat ve videu"
    )
    private val H3UPSCALER = Balik(
        "Comfyui_Minimax_h3_latent_Upscaler",
        "https://github.com/LBH-123-AI/Comfyui_Minimax_h3_latent_Upscaler",
        "Dlouhé video — rychlý první záběr (volitelné)"
    )
    private val DLSS5 = Balik(
        "ComfyUI-DLSS5-Enhancer",
        "https://github.com/Blueforcer/ComfyUI-DLSS5-Enhancer",
        "Zvětšit — metoda DLSS 5 (po instalaci ještě install_runtime.py)"
    )
    private val LSI = Balik(
        "LSI-Minimax-Segment-Timeline", "", "Časová osa (balík není veřejný)"
    )
    private val KREA = Balik(
        "nody Krea 2 Edit / H3", "", "Úprava obrázku a video karty (balík není veřejný)"
    )

    /** Třída uzlu → balík, který ji přináší. */
    private val UZLY: Map<String, Balik> = mapOf(
        "Power Lora Loader (rgthree)" to RGTHREE,
        "Any Switch (rgthree)" to RGTHREE,
        "SeedVR2VideoUpscaler" to SEEDVR2,
        "SeedVR2LoadDiTModel" to SEEDVR2,
        "SeedVR2LoadVAEModel" to SEEDVR2,
        "MiniMaxH3StreamLiveExtensionAVToVHS" to MULTIREF,
        "MiniMaxH3StartMaskedContext" to MULTIREF,
        "MiniMaxH3GeneratedAVMaskedContext" to MULTIREF,
        "MiniMaxH3StartCanvasSelector" to MULTIREF,
        "MiniMaxH3CropTo32" to MULTIREF,
        "MVEx_MaskCleanup" to MASKVID,
        "MVEx_SubjectCrop" to MASKVID,
        "MVEx_SubjectUncrop" to MASKVID,
        "MVEx_MaskToLatentSpace" to MASKVID,
        "MVEx_LatentMaskToMask" to MASKVID,
        "MinimaxH3LatentUpscaler3D" to H3UPSCALER,
        "DLSS5Settings" to DLSS5,
        "DLSS5EnhanceImages" to DLSS5,
        "DLSS5EnhanceVideoFile" to DLSS5,
        "ImageTileSplit" to PRAVEEN,
        "ImageTileMerge" to PRAVEEN,
        "LoadImageWithFilename" to PRAVEEN,
        "InpaintCropImproved" to INPAINT,
        "InpaintStitchImproved" to INPAINT,
        "QwenEditConfigPreparer" to QWENUTILS,
        "TextEncodeQwenImageEditPlusCustom" to QWENUTILS,
        "ImageResize+" to ESSENTIALS,
        "ImpactGaussianBlurMask" to IMPACT,
        "MiniMaxH3TeaCache" to TEACACHE,
        "UnetLoaderGGUF" to GGUF,
        "MiniMaxH3PromptWriter8B" to REWRITER,
        "llama_cpp_model_loader" to LLAMACPP,
        "llama_cpp_instruct_adv" to LLAMACPP,
        "llama_cpp_parameters" to LLAMACPP,
        "ModelPreviewOverrideKJ" to KJ,
        "ImageConcanate" to KJ,
        "ResizeMask" to KJ,
        "INTConstant" to KJ,
        "PathchSageAttentionKJ" to KJ,
        "VHS_VideoCombine" to VHS,
        "LSIMinimaxTimeline" to LSI,
        "LSIMinimaxTimelineRender" to LSI,
        "Krea2EditModelPatch" to KREA,
        "Krea2EditGroundedEncode" to KREA,
        "SpectrumApplyMiniMaxH3" to KREA,
        "H3CacheBust" to KREA,
        "H3IdentityAnchor" to KREA,
        "MiniMaxH3MemoryEfficientSageAttentionPatch" to KREA,
    )

    /** Název souboru → kam patří a odkud ho vzít (odkazy ověřené 1. 9. 2026). */
    private val SOUBORY: Map<String, Soubor> = mapOf(
        "ace_step_1.5_turbo_aio.safetensors" to Soubor(
            "models/checkpoints", "Hudba",
            "$HF/Comfy-Org/ace_step_1.5_ComfyUI_files/resolve/main/checkpoints/ace_step_1.5_turbo_aio.safetensors"
        ),
        "z_image_turbo_bf16.safetensors" to Soubor(
            "models/diffusion_models", "Obrázek",
            "$HF/Comfy-Org/z_image_turbo/resolve/main/split_files/diffusion_models/z_image_turbo_bf16.safetensors"
        ),
        "qwen_3_4b.safetensors" to Soubor(
            "models/text_encoders", "Obrázek",
            "$HF/Comfy-Org/z_image_turbo/resolve/main/split_files/text_encoders/qwen_3_4b.safetensors"
        ),
        "ae.sft" to Soubor(
            "models/vae", "Obrázek",
            "$HF/Comfy-Org/z_image_turbo/resolve/main/split_files/vae/ae.safetensors"
        ),
        "qwen_image_edit_2511_fp8_e4m3fn.safetensors" to Soubor(
            "models/diffusion_models", "Oprava fotky",
            "$HF/drbaph/Qwen-Image-Edit-2511-FP8/resolve/main/qwen_image_edit_2511_fp8_e4m3fn.safetensors"
        ),
        "qwen_2.5_vl_7b_fp8_scaled.safetensors" to Soubor(
            "models/text_encoders", "Oprava fotky",
            "$HF/Comfy-Org/Qwen-Image_ComfyUI/resolve/main/split_files/text_encoders/qwen_2.5_vl_7b_fp8_scaled.safetensors"
        ),
        "qwen_image_vae.safetensors" to Soubor(
            "models/vae", "Oprava fotky a Úprava obrázku",
            "$HF/Comfy-Org/Qwen-Image_ComfyUI/resolve/main/split_files/vae/qwen_image_vae.safetensors"
        ),
        "Qwen-Image-Edit-2511-Lightning-4steps-V1.0-fp32.safetensors" to Soubor(
            "models/loras", "Oprava fotky",
            "$HF/lightx2v/Qwen-Image-Edit-2511-Lightning/resolve/main/Qwen-Image-Edit-2511-Lightning-4steps-V1.0-fp32.safetensors"
        ),
        "flux1-Fill-Dev_FP8.safetensors" to Soubor(
            "models/diffusion_models", "Výměna tváře a Domalovat (volba Flux Fill)",
            "$HF/Academia-SD/flux1-Fill-Dev-FP8/resolve/main/flux1-Fill-Dev_FP8.safetensors"
        ),
        // Domalovat, výchozí volba. Odkaz vede na stránku modelu, ne rovnou na
        // soubor: Black Forest Labs ho vydává pod licencí, kterou je potřeba na
        // Hugging Face nejdřív odklepnout (jinak stahování vrací 401). Appka
        // čeká soubor pod tímhle jménem, tak ho tak ulož.
        "flux-2-klein-9b.safetensors" to Soubor(
            "models/diffusion_models", "Domalovat (volba FLUX.2 Klein)",
            "$HF/black-forest-labs/FLUX.2-klein-9b-fp8"
        ),
        "qwen_3_8b_fp8mixed.safetensors" to Soubor(
            "models/text_encoders", "Domalovat (volba FLUX.2 Klein)",
            "$HF/Comfy-Org/vae-text-encorder-for-flux-klein-9b/resolve/main/split_files/text_encoders/qwen_3_8b_fp8mixed.safetensors"
        ),
        "flux2-vae.safetensors" to Soubor(
            "models/vae", "Domalovat (volba FLUX.2 Klein)",
            "$HF/Comfy-Org/vae-text-encorder-for-flux-klein-9b/resolve/main/split_files/vae/flux2-vae.safetensors"
        ),
        "clip_l.safetensors" to Soubor(
            "models/text_encoders", "Výměna tváře",
            "$HF/comfyanonymous/flux_text_encoders/resolve/main/clip_l.safetensors"
        ),
        "t5xxl_fp16.safetensors" to Soubor(
            "models/text_encoders", "Výměna tváře",
            "$HF/comfyanonymous/flux_text_encoders/resolve/main/t5xxl_fp16.safetensors"
        ),
        "comfyui_portrait_lora64.safetensors" to Soubor(
            "models/loras", "Výměna tváře",
            "$HF/ali-vilab/ACE_Plus/resolve/main/portrait/comfyui_portrait_lora64.safetensors"
        ),
        "FLUX.1-Turbo-Alpha.safetensors" to Soubor(
            "models/loras", "Výměna tváře",
            "$HF/alimama-creative/FLUX.1-Turbo-Alpha/resolve/main/diffusion_pytorch_model.safetensors"
        ),
        // MiniMax H3 a Krea 2: veřejný odkaz se u těchhle vah liší podle
        // vydání, proto jen složka a karta — přesné jméno hlásí kontrola.
        "minimax_h3_fl2va_pruned_int8_convrot.safetensors" to Soubor(
            "models/diffusion_models", "video karty"
        ),
        "minimax_h3_ref2va_pruned_int8_convrot.safetensors" to Soubor(
            "models/diffusion_models", "video karty s referencemi a Dialogy"
        ),
        "krea2_turbo_fp8_scaled.safetensors" to Soubor(
            "models/diffusion_models", "Úprava obrázku"
        ),
        "krea2_identity_edit_v1_2.safetensors" to Soubor("models/loras", "Úprava obrázku"),
        "minimax_h3_video_vae_fp16.safetensors" to Soubor("models/vae", "video karty"),
        "minimax_h3_audio_vae_fp32.safetensors" to Soubor("models/vae", "video karty"),
        "qwen3vl_4b_fp8_scaled.safetensors" to Soubor("models/text_encoders", "Úprava obrázku"),
        "taeh3.safetensors" to Soubor("models/vae_approx", "živý náhled u videa"),
        // Karta Obrázek, modely přidané ve 3.02. Váhy Kleina má Black Forest
        // Labs za souhlasem s licencí, encodér a VAE přebalil Comfy-Org.
        "z_image_bf16.safetensors" to Soubor(
            "models/diffusion_models", "Obrázek — Z-Image Base"
        ),
        "flux-2-klein-9b.safetensors" to Soubor(
            "models/diffusion_models", "Obrázek — FLUX.2 Klein a Domalovat",
            "$HF/black-forest-labs/FLUX.2-klein-9B"
        ),
        "qwen_3_8b_fp8mixed.safetensors" to Soubor(
            "models/text_encoders", "Obrázek — FLUX.2 Klein a Domalovat",
            "$HF/Comfy-Org/flux2-klein-9b/resolve/main/split_files/text_encoders/qwen_3_8b_fp8mixed.safetensors"
        ),
        "flux2-vae.safetensors" to Soubor(
            "models/vae", "Obrázek — FLUX.2 Klein a ERNIE, Domalovat",
            "$HF/Comfy-Org/flux2-klein-9b/resolve/main/split_files/vae/flux2-vae.safetensors"
        ),
        // Přemalování ve videu a dlouhé video (3.03).
        "sam3.1_multiplex_fp16.safetensors" to Soubor(
            "models/checkpoints", "All in One \u2192 P\u0159emalovat ve videu",
            "$HF/Comfy-Org/sam3.1/resolve/main/checkpoints/sam3.1_multiplex_fp16.safetensors"
        ),
        "minimax_h3_latent_upscaler_3d_fp16.safetensors" to Soubor(
            "models/latent_upscale_models", "Dlouh\u00e9 video \u2014 rychl\u00fd prvn\u00ed z\u00e1b\u011br",
            "$HF/LBH-123-AI/Minimax_h3_latent_Upscaler/resolve/main/minimax_h3_latent_upscaler_3d_fp16.safetensors"
        ),
        // Karta 3D model (3.04). Uzly jsou v jádru ComfyUI od 0.34, chybět
        // můžou jen váhy.
        "trellis_2_int8_convrot.safetensors" to Soubor(
            "models/diffusion_models", "3D model",
            "$HF/Comfy-Org/TRELLIS.2/resolve/main/diffusion_models/trellis_2_int8_convrot.safetensors"
        ),
        "trellis_2_shape_vae_bf16.safetensors" to Soubor(
            "models/vae", "3D model",
            "$HF/Comfy-Org/TRELLIS.2/resolve/main/vae/trellis_2_shape_vae_bf16.safetensors"
        ),
        "trellis_2_texture_vae_bf16.safetensors" to Soubor(
            "models/vae", "3D model",
            "$HF/Comfy-Org/TRELLIS.2/resolve/main/vae/trellis_2_texture_vae_bf16.safetensors"
        ),
        "dino_v3_vit_l.safetensors" to Soubor(
            "models/clip_vision", "3D model",
            "$HF/Comfy-Org/TRELLIS.2/resolve/main/clip_vision/dino_v3_vit_l.safetensors"
        ),
        "birefnet.safetensors" to Soubor(
            "models/background_removal", "3D model (odstran\u011bn\u00ed pozad\u00ed)",
            "$HF/Comfy-Org/BiRefNet/resolve/main/background_removal/birefnet.safetensors"
        ),
        "ernie-image-turbo-Q8_0.gguf" to Soubor(
            "models/unet", "Obrázek — ERNIE Image Turbo"
        ),
        "ministral-3-3b.safetensors" to Soubor(
            "models/text_encoders", "Obrázek — ERNIE Image Turbo"
        ),
    )

    fun balikProUzel(trida: String): Balik? = UZLY[trida]

    fun soubor(jmeno: String): Soubor? = SOUBORY[jmeno]
        ?: SOUBORY[jmeno.substringAfterLast('/').substringAfterLast('\\')]

    /**
     * Do jaké složky soubor patří, i když ho katalog nezná — pozná se to
     * ze vstupu uzlu, který ho žádá (`unet_name` → diffusion_models…).
     */
    fun slozkaPodleVstupu(vstup: String): String? = when (vstup) {
        "unet_name" -> "models/diffusion_models"
        "ckpt_name" -> "models/checkpoints"
        "clip_name", "clip_name1", "clip_name2" -> "models/text_encoders"
        "vae_name" -> "models/vae"
        "lora_name" -> "models/loras"
        "model_name" -> "models/upscale_models"
        else -> null
    }
}
