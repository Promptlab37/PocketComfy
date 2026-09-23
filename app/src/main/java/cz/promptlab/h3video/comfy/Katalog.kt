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

    /**
     * Modely v GGUF. `models/unet` a `models/diffusion_models` jsou pro ComfyUI
     * **jedna a táž složka** (`folder_paths`: `diffusion_models` má obojí
     * v seznamu, `unet` je jen starší název), ale člověk, který stahuje svůj
     * první model, to neví — a když hláška řekne jen jednu z nich, začne
     * soubor, který už má, zbytečně stěhovat. Proto se řeknou obě.
     */
    private const val UNET = "models/unet nebo models/diffusion_models (ComfyUI je má jako jednu složku)"

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
        "praveen-tools", "https://github.com/Praveenhalder/praveen-tools", "Zvětšit"
    )
    private val MULTIREF = Balik(
        "ComfyUI-H3-Motion-Context-MultiRef",
        "https://github.com/seitanism/ComfyUI-H3-Motion-Context-MultiRef",
        "Dlouhé video"
    )
    private val LATENTCHAIN = Balik(
        "Minimax-H3-Latent-Continuation",
        "https://github.com/SatoDive/Minimax-H3-Latent-Continuation",
        "Long MiniMax"
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
    /**
     * YuE2 není balík z ComfyUI-Manageru — uzly jsou přímo v jádru od 0.36.0.
     * Když chybí, řešením je aktualizovat ComfyUI, ne něco doinstalovat,
     * a hláška to musí říct rovnou (jinak se hledá balík, který neexistuje).
     */
    private val COMFY035 = Balik(
        "Aktualizace ComfyUI na 0.36.0 nebo novější — YuE2 je přímo v jádru, "
            + "žádný balík se neinstaluje",
        "https://docs.comfy.org/tutorials/audio/yue2/yue2",
        "Hudba — volba YuE2"
    )
    /** Qwen Image 2.1 přibyl do jádra až 20. 9. 2026, po stabilní 0.36.0. */
    private val COMFY_QWEN21 = Balik(
        "Aktualizace ComfyUI na verzi z 20. 9. 2026 nebo novější — Qwen Image 2.1 "
            + "je přímo v jádru, žádný custom balík se neinstaluje",
        "https://github.com/Comfy-Org/ComfyUI",
        "Úprava obrázku — Qwen Image 2.1"
    )
    private val LTXV = Balik(
        "ComfyUI-LTXVideo", "https://github.com/Lightricks/ComfyUI-LTXVideo",
        "Video ze zvuku"
    )
        private val LSI = Balik(
        "LSI-Minimax-Segment-Timeline", "", "Časová osa (balík není veřejný)"
    )
    private val KREA = Balik(
        "nody Krea 2 Edit / H3", "", "Úprava obrázku a video karty (balík není veřejný)"
    )

    /** Třída uzlu → balík, který ji přináší. */
    private val UZLY: Map<String, Balik> = mapOf(
        "LTXVAudioVAEEncode" to LTXV,
        "LTXVConcatAVLatent" to LTXV,
        "LTXVSeparateAVLatent" to LTXV,
        "LTXVLatentUpsampler" to LTXV,
        "LTXVImgToVideoInplace" to LTXV,
        "LTXVDualCFGGuider" to LTXV,
        "LTXVPreprocess" to LTXV,
        "LatentUpscaleModelLoader" to LTXV,
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
        "MiniMaxH3Easy_SatoDive" to LATENTCHAIN,
        "MiniMaxH3EasyModelAdapter_SatoDive" to LATENTCHAIN,
        "MiniMaxH3EasyOutput_SatoDive" to LATENTCHAIN,
        "MiniMaxH3EasyMediaBridge_SatoDive" to LATENTCHAIN,
        "MiniMaxH3EasyContextSegments_SatoDive" to LATENTCHAIN,
        "MiniMaxH3EasySegmentRender_SatoDive" to LATENTCHAIN,
        "MiniMaxH3EasySegmentDecode_SatoDive" to LATENTCHAIN,
        "MiniMaxH3EasySaveLatent_SatoDive" to LATENTCHAIN,
        "MiniMaxH3EasyLoadLatent_SatoDive" to LATENTCHAIN,
        "MiniMaxH3EasyStitchContinuation_SatoDive" to LATENTCHAIN,
        "MiniMaxH3EasyVideoTailSlicer_SatoDive" to LATENTCHAIN,
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
        "ImageResize+" to ESSENTIALS,
        "ImpactGaussianBlurMask" to IMPACT,
        "MiniMaxH3TeaCache" to TEACACHE,
        "YuE2GenerateABC" to COMFY035,
        "TextEncodeQwenImage21" to COMFY_QWEN21,
        "QwenImage21Cache" to COMFY_QWEN21,
        // Přepisovač nahrávky do not — také přímo v jádře od 0.36.0.
        "SheetSage2AudioToABC" to COMFY035,
        "AudioEncoderLoader" to COMFY035,
        "YuE2GenerateMusic" to COMFY035,
        "EmptyYuE2LatentAudio" to COMFY035,
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

    /** Název souboru → kam patří a odkud ho vzít (odkazy ověřené 16. 9. 2026). */
    private val SOUBORY: Map<String, Soubor> = mapOf(
        "ace_step_1.5_turbo_aio.safetensors" to Soubor(
            "models/checkpoints", "Hudba",
            "$HF/Comfy-Org/ace_step_1.5_ComfyUI_files/resolve/main/checkpoints/ace_step_1.5_turbo_aio.safetensors"
        ),
        "yue2_3b_int8_convrot.safetensors" to Soubor(
            "models/checkpoints", "Hudba — volba YuE2 (3,9 GB)",
            "$HF/Comfy-Org/YuE2/resolve/main/checkpoints/yue2_3b_int8_convrot.safetensors"
        ),
        "ltx-2.5-22b-distilled-transformer-comfy-int8-convrot.safetensors" to Soubor(
            "models/diffusion_models", "Video ze zvuku (21 GB)",
            "$HF/Lightricks/LTX-2.5/resolve/main/transformers/ltx-2.5-22b-distilled-transformer-comfy-int8-convrot.safetensors"
        ),
        "gemma4-12b-with-proj-ltx-2.5-comfy-int8-convrot.safetensors" to Soubor(
            "models/text_encoders", "Video ze zvuku (15 GB)",
            "$HF/Lightricks/LTX-2.5/resolve/main/text_encoders/gemma4-12b-with-proj-ltx-2.5-comfy-int8-convrot.safetensors"
        ),
        "ltx-2.5-video-vae-bf16.safetensors" to Soubor(
            "models/vae", "Video ze zvuku",
            "$HF/Lightricks/LTX-2.5/resolve/main/vae/ltx-2.5-video-vae-bf16.safetensors"
        ),
        "ltx-2.5-audio-vae-bf16.safetensors" to Soubor(
            "models/vae", "Video ze zvuku — bez něj se zvuk nedostane do latentu",
            "$HF/Lightricks/LTX-2.5/resolve/main/vae/ltx-2.5-audio-vae-bf16.safetensors"
        ),
        "ltx-2.5-latent-spatial-upscaler-x2-bf16-1.0.safetensors" to Soubor(
            "models/latent_upscale_models", "Video ze zvuku — druhý průchod",
            "$HF/Lightricks/LTX-2.5/resolve/main/latent_upscale_models/ltx-2.5-latent-spatial-upscaler-x2-bf16-1.0.safetensors"
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
        // Karta Dance — Wan-Dancer 14B. Dvě fáze: globální model rozvrhne
        // pohyb, lokální ho dopiluje. Obojí je samostatný soubor.
        "wan2.2_dancer_14b_global_fp8_scaled.safetensors" to Soubor(
            "models/diffusion_models", "Dance — Wan-Dancer, 1. fáze (17,1 GB)",
            "$HF/Comfy-Org/Wan-Dancer/resolve/main/diffusion_models/wan2.2_dancer_14b_global_fp8_scaled.safetensors"
        ),
        "wan2.2_dancer_14b_local_fp8_scaled.safetensors" to Soubor(
            "models/diffusion_models", "Dance — Wan-Dancer, 2. fáze (17,1 GB)",
            "$HF/Comfy-Org/Wan-Dancer/resolve/main/diffusion_models/wan2.2_dancer_14b_local_fp8_scaled.safetensors"
        ),
        "umt5_xxl_fp16.safetensors" to Soubor(
            "models/text_encoders", "Dance — textový enkodér Wan (11,4 GB)",
            "$HF/Comfy-Org/Wan_2.1_ComfyUI_repackaged/resolve/main/split_files/text_encoders/umt5_xxl_fp16.safetensors"
        ),
        "Wan2_1_VAE_bf16.safetensors" to Soubor(
            "models/vae", "Dance — VAE Wan 2.1",
            "$HF/Kijai/WanVideo_comfy/resolve/main/Wan2_1_VAE_bf16.safetensors"
        ),
        "clip_vision_h.safetensors" to Soubor(
            "models/clip_vision", "Dance — obrazový enkodér (1,2 GB)",
            "$HF/Comfy-Org/Wan_2.1_ComfyUI_repackaged/resolve/main/split_files/clip_vision/clip_vision_h.safetensors"
        ),
        "lightx2v_I2V_14B_480p_cfg_step_distill_rank64_bf16.safetensors" to Soubor(
            "models/loras", "Dance — zrychlení na 6 kroků (bez ní 25 kroků a cfg 5)",
            "$HF/Kijai/WanVideo_comfy/resolve/main/Lightx2v/lightx2v_I2V_14B_480p_cfg_step_distill_rank64_bf16.safetensors"
        ),
        "qwen_image_vae.safetensors" to Soubor(
            "models/vae", "Úprava obrázku — Krea 2",
            "$HF/Comfy-Org/Qwen-Image_ComfyUI/resolve/main/split_files/vae/qwen_image_vae.safetensors"
        ),
        // Karta Úhel kamery. Schopnost změnit pohled je v LoRA
        // `multiple-angles`, ne v základním modelu — a ta je trénovaná na
        // Qwen Image Edit 2511, na Qwen Image 2.1 nesedí (jiná architektura).
        // Proto tahle jediná karta zůstává na 2511.
        "qwen_image_edit_2511_fp8_e4m3fn.safetensors" to Soubor(
            "models/diffusion_models", "Úhel kamery — Qwen Image Edit 2511 (20,4 GB)",
            "$HF/drbaph/Qwen-Image-Edit-2511-FP8/resolve/main/qwen_image_edit_2511_fp8_e4m3fn.safetensors"
        ),
        "qwen-image-edit-2511-multiple-angles-lora.safetensors" to Soubor(
            "models/loras", "Úhel kamery — 96 póz z jedné fotky (bez ní se pohled nezmění)",
            "$HF/fal/Qwen-Image-Edit-2511-Multiple-Angles-LoRA/resolve/main/qwen-image-edit-2511-multiple-angles-lora.safetensors"
        ),
        "Qwen-Image-Edit-2511-Lightning-8steps-V1.0-fp32.safetensors" to Soubor(
            "models/loras", "Úhel kamery — zrychlení na 8 kroků",
            "$HF/lightx2v/Qwen-Image-Edit-2511-Lightning/resolve/main/Qwen-Image-Edit-2511-Lightning-8steps-V1.0-fp32.safetensors"
        ),
        "qwen_2.5_vl_7b_fp8_scaled.safetensors" to Soubor(
            "models/text_encoders", "Úhel kamery — textový enkodér k 2511",
            "$HF/Comfy-Org/Qwen-Image_ComfyUI/resolve/main/split_files/text_encoders/qwen_2.5_vl_7b_fp8_scaled.safetensors"
        ),
        "qwen_image_2.1_int8_convrot.safetensors" to Soubor(
            "models/diffusion_models", "Obrázek, Úprava, Oprava fotky, Úhel kamery a Domalovat — Qwen Image 2.1 (7,3 GB)",
            "$HF/Comfy-Org/Qwen-Image-2.1/resolve/main/diffusion_models/qwen_image_2.1_int8_convrot.safetensors"
        ),
        "qwen3vl_8b_int8_convrot.safetensors" to Soubor(
            "models/text_encoders", "Obrázek, Úprava, Oprava fotky, Úhel kamery a Domalovat — Qwen Image 2.1 (9,4 GB)",
            "$HF/Comfy-Org/Qwen-Image-2.1/resolve/main/text_encoders/qwen3vl_8b_int8_convrot.safetensors"
        ),
        "qwen_image_2.1_vae_bf16.safetensors" to Soubor(
            "models/vae", "Obrázek, Úprava, Oprava fotky, Úhel kamery a Domalovat — Qwen Image 2.1 (0,7 GB, RGBA)",
            "$HF/Comfy-Org/Qwen-Image-2.1/resolve/main/vae/qwen_image_2.1_vae_bf16.safetensors"
        ),
        "qwen3.5_9b_qwen_image_2.1_pe_i2i.int8_convrot.safetensors" to Soubor(
            "models/text_encoders",
            "✨ Vylepšit zadání u úprav — Qwen 2.1 PE-I2I (9,5 GB, nepovinné)",
            "$HF/Comfy-Org/Qwen-Image-2.1/resolve/main/text_encoders/qwen3.5_9b_qwen_image_2.1_pe_i2i.int8_convrot.safetensors"
        ),
        "qwen3.5_9b_qwen_image_2.1_pe_t2i.int8_convrot.safetensors" to Soubor(
            "models/text_encoders",
            "✨ Vylepšit zadání na kartě Obrázek — Qwen 2.1 PE-T2I (9,5 GB, nepovinné)",
            "$HF/Comfy-Org/Qwen-Image-2.1/resolve/main/text_encoders/qwen3.5_9b_qwen_image_2.1_pe_t2i.int8_convrot.safetensors"
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
            "models/diffusion_models", "Domalovat a Obrázek (volba FLUX.2 Klein)",
            "$HF/black-forest-labs/FLUX.2-klein-9b-fp8"
        ),
        "qwen_3_8b_fp8mixed.safetensors" to Soubor(
            "models/text_encoders", "Domalovat a Obrázek (volba FLUX.2 Klein)",
            "$HF/Comfy-Org/vae-text-encorder-for-flux-klein-9b/resolve/main/split_files/text_encoders/qwen_3_8b_fp8mixed.safetensors"
        ),
        "flux2-vae.safetensors" to Soubor(
            "models/vae", "Domalovat a Obrázek (FLUX.2 Klein, ERNIE)",
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
        "qwen3vl_32b_minimax_h3_int8_convrot.safetensors" to Soubor(
            "models/text_encoders", "3 kroky (předloha má enkodér napevno)",
            "$HF/Comfy-Org/MiniMax-H3/resolve/main/text_encoders/qwen3vl_32b_minimax_h3_int8_convrot.safetensors"
        ),
        // Karta 3 kroky stojí na téhle LoRA; předloha ji hledá v podsložce h3.
        "TaoMate-H3-3step-ComfyUI.safetensors" to Soubor(
            "models/loras/h3", "3 kroky",
            "$HF/TaoLiveAIGC/TaoMate-H3"
        ),
        // Long MiniMax jede na referenčních vahách a na autorově Turbo LoRA;
        // základní model, enkodér i oba VAE sdílí s ostatními video kartami.
        // Volitelná: karta bez ní jede, jen bez realističtějšího podání.
        "h3-realism-people-t2v-i2v-r2v.safetensors" to Soubor(
            "models/loras", "Long MiniMax — volba Realističtější podání"
        ),
        "minimax_h3_ref2v_turbo_4step_v0.1_comfyui_bf16.safetensors" to Soubor(
            "models/loras", "Long MiniMax",
            "$HF/Comfy-Org/MiniMax-H3/resolve/main/loras/minimax_h3_ref2v_turbo_4step_v0.1_comfyui_bf16.safetensors"
        ),
        "qwen3vl_32b_minimax_h3_nvfp4_awq.safetensors" to Soubor(
            "models/text_encoders", "Long MiniMax",
            "$HF/Comfy-Org/MiniMax-H3/resolve/main/text_encoders/qwen3vl_32b_minimax_h3_nvfp4_awq.safetensors"
        ),
        "minimax_h3_video_vae_fp16.safetensors" to Soubor("models/vae", "video karty"),
        "minimax_h3_audio_vae_fp32.safetensors" to Soubor("models/vae", "video karty"),
        "qwen3vl_4b_fp8_scaled.safetensors" to Soubor("models/text_encoders", "Úprava obrázku"),
        "taeh3.safetensors" to Soubor("models/vae_approx", "živý náhled u videa"),
        // Karta Obrázek, modely přidané ve 3.02. Váhy Kleina má Black Forest
        // Labs za souhlasem s licencí, encodér a VAE přebalil Comfy-Org —
        // jejich zápisy jsou výš, u karty Domalovat. Klíč se v mapě nesmí
        // opakovat: `mapOf` nechá platit poslední zápis, takže duplicita
        // umí odkaz potichu přepsat.
        "z_image_bf16.safetensors" to Soubor(
            "models/diffusion_models", "Obrázek — Z-Image Base",
            "$HF/Comfy-Org/z_image/resolve/main/split_files/diffusion_models/z_image_bf16.safetensors"
        ),
        // Přemalování ve videu a dlouhé video (3.03).
        // Karta Hudba, YuE2 → Předělat nahrávku. Jiný model než YuE2 a jiná
        // složka — bez něj se melodie z nahrávky nemá čím přepsat.
        "sheetsage2_bf16.safetensors" to Soubor(
            "models/audio_encoders", "Hudba — Předělat nahrávku (1,3 GB)",
            "$HF/Comfy-Org/YuE2/resolve/main/audio_encoders/sheetsage2_bf16.safetensors"
        ),
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
            UNET, "Obrázek — ERNIE Image Turbo",
            "$HF/unsloth/ERNIE-Image-Turbo-GGUF/resolve/main/ernie-image-turbo-Q8_0.gguf"
        ),
        // Karta Obrázek, volba Photoreal a přepínač Bez cenzury. Soubory
        // vydává CivitAI, odkaz se u nich váže na vydaní a účet, takže jen složka
        // a karta — stahuje si je každý sám.
        "zimage_nsfw_photoreal_v61_Q8.gguf" to Soubor(
            UNET, "Obrázek — volba Photoreal (z CivitAI)"
        ),
        "zimage_nsfw_v1.safetensors" to Soubor(
            "models/loras", "Obrázek — přepínač Bez cenzury (z CivitAI)"
        ),
        "ministral-3-3b.safetensors" to Soubor(
            "models/text_encoders", "Obrázek — ERNIE Image Turbo",
            "$HF/Comfy-Org/ERNIE-Image/resolve/main/text_encoders/ministral-3-3b.safetensors"
        ),
        // Karta 3D model, druhý motor Pixal3D. Sdílí s TRELLIS.2 obě VAE.
        "pixal3d_int8_convrot.safetensors" to Soubor(
            "models/diffusion_models", "3D model — motor Pixal3D",
            "$HF/Comfy-Org/Pixal3D/resolve/main/diffusion_models/pixal3d_int8_convrot.safetensors"
        ),
        "dino_v3_L_naf_fp32.safetensors" to Soubor(
            "models/clip_vision", "3D model — motor Pixal3D",
            "$HF/Comfy-Org/Pixal3D/resolve/main/clip_vision/dino_v3_L_naf_fp32.safetensors"
        ),
        "moge_2_vitl_normal_fp16.safetensors" to Soubor(
            "models/geometry_estimation", "3D model — Pixal3D odhaduje úhel objektivu",
            "$HF/Comfy-Org/MoGe/resolve/main/geometry_estimation/moge_2_vitl_normal_fp16.safetensors"
        ),
        // Úprava obrázku přes Klein předlohu kóduje, takže chce plný enkodér.
        "full_encoder_small_decoder.safetensors" to Soubor(
            "models/vae", "Domalovat — úprava přes FLUX.2 Klein",
            "$HF/black-forest-labs/FLUX.2-small-decoder/resolve/main/full_encoder_small_decoder.safetensors"
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
        "unet_name" -> UNET
        "ckpt_name" -> "models/checkpoints"
        "clip_name", "clip_name1", "clip_name2" -> "models/text_encoders"
        "vae_name" -> "models/vae"
        "lora_name" -> "models/loras"
        "model_name" -> "models/upscale_models"
        "audio_encoder_name" -> "models/audio_encoders"
        else -> null
    }
}
