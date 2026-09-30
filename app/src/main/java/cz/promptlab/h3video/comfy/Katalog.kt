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
    private val AUSBOSS = Balik(
        "ComfyUI-AusBoss", "https://github.com/ausboss/ComfyUI-AusBoss",
        "Domalovat — rozšíření obrázku"
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
        "Upravit video → Přemalovat"
    )
    private val H3UPSCALER = Balik(
        "Comfyui_Minimax_h3_latent_Upscaler",
        "https://github.com/LBH-123-AI/Comfyui_Minimax_h3_latent_Upscaler",
        "Dlouhé video — rychlý první záběr (volitelné)"
    )
    private val SMART_UPSCALER = Balik(
        "ComfyUI-Smart-Upscaler",
        "https://github.com/HallettVisual/ComfyUI-Smart-Upscaler",
        "Zvětšit — chytré zvětšení",
    )
    private val BING = Balik(
        "ComfyUI-BingImageSelector",
        "https://github.com/concarne000/ComfyUI-BingImageSelector",
        "Hledat obrázky na internetu",
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
        "LTX 2.5"
    )
    private val LSI = Balik(
        "LSI-Minimax-Segment-Timeline", "", "Časová osa (balík není veřejný)"
    )
    /** Krea 2 Identity Edit — veřejný balík (pyproject.toml na serveru, 29. 9. 2026). */
    private val KREA = Balik(
        "comfyui-krea2edit (Krea 2 Identity Edit)",
        "https://github.com/lbouaraba/comfyui-krea2edit",
        "Úprava obrázku — Krea 2"
    )
    /** Pomocné uzly balíku All in One (H3CacheBust, H3IdentityAnchor). */
    private val ALLINONE = Balik(
        "ComfyUI-ALLinONE-MinimaxH3",
        "https://github.com/LeonQ8/ComfyUI-ALLinONE-MinimaxH3",
        "All in One a Časová osa"
    )
    private val SPECTRUM = Balik(
        "ComfyUI-Spectrum-MiniMax-H3",
        "https://github.com/xmarre/ComfyUI-Spectrum-MiniMax-H3",
        "Dlouhé video"
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
        // Film ze storyboardu: úseky po jednom s vlastním zadáním.
        "MiniMaxH3EasySegmentSampleSetup_SatoDive" to LATENTCHAIN,
        "MiniMaxH3EasySegmentStep_SatoDive" to LATENTCHAIN,
        "MiniMaxH3EasySegmentCollect_SatoDive" to LATENTCHAIN,
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
        "SmartUpscaledTilePlanner" to SMART_UPSCALER,
        "SmartTileJobDirector" to SMART_UPSCALER,
        "SmartCachedTextGenerate" to SMART_UPSCALER,
        "SmartCachedTilePromptGenerator" to SMART_UPSCALER,
        "SmartUnifiedPromptGuidance" to SMART_UPSCALER,
        "SmartSamplerTileSelector" to SMART_UPSCALER,
        "SmartTileColorMatch" to SMART_UPSCALER,
        "SmartTileFinalizer" to SMART_UPSCALER,
        BingHledani.UZEL to BING,
        "DLSS5Settings" to DLSS5,
        "DLSS5EnhanceImages" to DLSS5,
        "DLSS5EnhanceVideoFile" to DLSS5,
        "ImageTileSplit" to PRAVEEN,
        "ImageTileMerge" to PRAVEEN,
        "LoadImageWithFilename" to PRAVEEN,
        "InpaintCropImproved" to INPAINT,
        "InpaintStitchImproved" to INPAINT,
        "AUSBOSS_NODES_LoadImagePad" to AUSBOSS,
        "AUSBOSS_NODES_StitchInpaint" to AUSBOSS,
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
        // Vylepšení s referencemi a čtení storyboardu.
        "MiniMaxH3UniversalWriter" to REWRITER,
        "MiniMaxH3ReferenceCaption" to REWRITER,
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
        // Třídy ověřené proti NODE_CLASS_MAPPINGS na serveru (29. 9. 2026).
        "SpectrumApplyMiniMaxH3" to SPECTRUM,
        "H3CacheBust" to ALLINONE,
        "H3IdentityAnchor" to ALLINONE,
        "MiniMaxH3MemoryEfficientSageAttentionPatch" to KJ,
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
        // Podkresová hudba k filmu ze storyboardu (5.13). Bez ní YuE2 zpívá.
        // Licence CC BY-NC 4.0 (jen nekomerčně).
        "ar_lora_inst_v3abc_comfyui.safetensors" to Soubor(
            "models/loras/yue2", "Film ze storyboardu — podkresová hudba (213 MB, CC BY-NC)",
            "$HF/Mothersuperior/YuE2-instrumental-cot-full-loras/resolve/main/ar_lora_inst_v3abc_comfyui.safetensors"
        ),
        "ltx-2.5-22b-distilled-transformer-comfy-int8-convrot.safetensors" to Soubor(
            "models/diffusion_models", "LTX 2.5 (21 GB)",
            "$HF/Lightricks/LTX-2.5/resolve/main/diffusion_models/ltx-2.5-22b-distilled-transformer-comfy-int8-convrot.safetensors"
        ),
        "gemma4-12b-with-proj-ltx-2.5-comfy-int8-convrot.safetensors" to Soubor(
            "models/text_encoders", "LTX 2.5 (15 GB)",
            "$HF/Lightricks/LTX-2.5/resolve/main/text_encoders/gemma4-12b-with-proj-ltx-2.5-comfy-int8-convrot.safetensors"
        ),
        "ltx-2.5-video-vae-bf16.safetensors" to Soubor(
            "models/vae", "Video ze zvuku",
            "$HF/Lightricks/LTX-2.5/resolve/main/vae/ltx-2.5-video-vae-bf16.safetensors"
        ),
        "ltx-2.5-audio-vae-bf16.safetensors" to Soubor(
            "models/vae", "LTX 2.5 — bez něj se zvuk nedostane do latentu",
            "$HF/Lightricks/LTX-2.5/resolve/main/vae/ltx-2.5-audio-vae-bf16.safetensors"
        ),
        "ltx-2.5-latent-spatial-upscaler-x2-bf16-1.0.safetensors" to Soubor(
            "models/latent_upscale_models", "LTX 2.5 — druhý průchod",
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
        // Chytré zvětšení (Smart Upscaler): vidoucí model pro prompty dlaždic
        // a ControlNet Tile, který drží tvar dlaždice.
        "qwen3vl_4b_fp8_scaled.safetensors" to Soubor(
            "models/text_encoders", "Zvětšit — chytré zvětšení a Úprava obrázku (4,9 GB)",
            "$HF/Comfy-Org/Krea-2/resolve/main/text_encoders/qwen3vl_4b_fp8_scaled.safetensors"
        ),
        "Z-Image-Turbo-Fun-Controlnet-Tile-2.1-lite-2601-8steps.safetensors" to Soubor(
            "models/model_patches", "Zvětšit — chytré zvětšení (2 GB)",
            "$HF/alibaba-pai/Z-Image-Turbo-Fun-Controlnet-Union-2.1/resolve/main/Z-Image-Turbo-Fun-Controlnet-Tile-2.1-lite-2601-8steps.safetensors"
        ),
        // Karta Dance — Wan-Dancer 14B. Dvě fáze: globální model rozvrhne
        // pohyb, lokální ho dopiluje. Obojí je samostatný soubor.
        "wan2.2_dancer_14b_global_fp8_scaled.safetensors" to Soubor(
            "models/diffusion_models", "Pohyb postavy — Wan-Dancer, 1. fáze (17,1 GB)",
            "$HF/Comfy-Org/Wan-Dancer/resolve/main/diffusion_models/wan2.2_dancer_14b_global_fp8_scaled.safetensors"
        ),
        "wan2.2_dancer_14b_local_fp8_scaled.safetensors" to Soubor(
            "models/diffusion_models", "Pohyb postavy — Wan-Dancer, 2. fáze (17,1 GB)",
            "$HF/Comfy-Org/Wan-Dancer/resolve/main/diffusion_models/wan2.2_dancer_14b_local_fp8_scaled.safetensors"
        ),
        "umt5_xxl_fp16.safetensors" to Soubor(
            "models/text_encoders", "Pohyb postavy — textový enkodér Wan (11,4 GB)",
            "$HF/Comfy-Org/Wan_2.1_ComfyUI_repackaged/resolve/main/split_files/text_encoders/umt5_xxl_fp16.safetensors"
        ),
        "Wan2_1_VAE_bf16.safetensors" to Soubor(
            "models/vae", "Pohyb postavy — VAE Wan 2.1",
            "$HF/Kijai/WanVideo_comfy/resolve/main/Wan2_1_VAE_bf16.safetensors"
        ),
        "clip_vision_h.safetensors" to Soubor(
            "models/clip_vision", "Pohyb postavy — obrazový enkodér (1,2 GB)",
            "$HF/Comfy-Org/Wan_2.1_ComfyUI_repackaged/resolve/main/split_files/clip_vision/clip_vision_h.safetensors"
        ),
        "lightx2v_I2V_14B_480p_cfg_step_distill_rank64_bf16.safetensors" to Soubor(
            "models/loras", "Pohyb postavy — zrychlení na 6 kroků",
            "$HF/Kijai/WanVideo_comfy/resolve/main/Lightx2v/lightx2v_I2V_14B_480p_cfg_step_distill_rank64_bf16.safetensors"
        ),
        // Upravit video → Vyměnit postavu: SCAIL-2 (Comfy-Org/SCAIL-2).
        "wan2.1_14B_SCAIL_2_int8_convrot.safetensors" to Soubor(
            "models/diffusion_models", "Upravit video — vyměnit postavu (16,7 GB)",
            "$HF/Comfy-Org/SCAIL-2/resolve/main/diffusion_models/wan2.1_14B_SCAIL_2_int8_convrot.safetensors"
        ),
        "wan2.1_SCAIL_2_DPO_lora_bf16.safetensors" to Soubor(
            "models/loras", "Upravit video — vyměnit postavu, LoRA (1,2 GB)",
            "$HF/Comfy-Org/SCAIL-2/resolve/main/loras/wan2.1_SCAIL_2_DPO_lora_bf16.safetensors"
        ),
        "h3_character_swap_pro4500_1000.safetensors" to Soubor(
            "models/loras", "Upravit video — vyměnit postavu, motor MiniMax H3 (0,16 GB)",
            "$HF/akatz-ai/MiniMax-H3-Character-Swap-LoRA/resolve/main/h3_character_swap_pro4500_1000.safetensors"
        ),
        // Upravit video → Podle předlohy: Fun ControlNet-Union + SDPose (Comfy-Org).
        "minimax_h3_fun_controlnet_union_pruned_int8_convrot.safetensors" to Soubor(
            "models/model_patches", "Upravit video — podle předlohy (2,3 GB)",
            "$HF/Comfy-Org/MiniMax-H3/resolve/main/model_patches/minimax_h3_fun_controlnet_union_pruned_int8_convrot.safetensors"
        ),
        "sdpose_wholebody_fp16.safetensors" to Soubor(
            "models/checkpoints", "Upravit video — podle předlohy, póza (1,9 GB)",
            "$HF/Comfy-Org/SDPose/resolve/main/checkpoints/sdpose_wholebody_fp16.safetensors"
        ),
        "rt_detr_v4-x-hgnet_fp16.safetensors" to Soubor(
            "models/diffusion_models", "Upravit video — podle předlohy, hledání lidí (0,12 GB)",
            "$HF/Comfy-Org/SDPose/resolve/main/diffusion_models/rt_detr_v4-x-hgnet_fp16.safetensors"
        ),
        // Upravit video → Podle zadání: Bernini-R na Wan 2.2 (Comfy-Org/Bernini-R).
        "wan2.2_bernini_r_high_noise_int8_convrot.safetensors" to Soubor(
            "models/diffusion_models", "Upravit video — podle zadání, první půlka (14,5 GB)",
            "$HF/Comfy-Org/Bernini-R/resolve/main/diffusion_models/wan2.2_bernini_r_high_noise_int8_convrot.safetensors"
        ),
        "wan2.2_bernini_r_low_noise_int8_convrot.safetensors" to Soubor(
            "models/diffusion_models", "Upravit video — podle zadání, druhá půlka (14,5 GB)",
            "$HF/Comfy-Org/Bernini-R/resolve/main/diffusion_models/wan2.2_bernini_r_low_noise_int8_convrot.safetensors"
        ),
        "lightx2v_T2V_14B_cfg_step_distill_v2_lora_rank64_bf16.safetensors" to Soubor(
            "models/loras", "Upravit video — podle zadání, zrychlení na 6 kroků",
            "$HF/Kijai/WanVideo_comfy/resolve/main/Lightx2v/lightx2v_T2V_14B_cfg_step_distill_v2_lora_rank64_bf16.safetensors"
        ),
        // Výměna tváře → Qwen Image 2.1: BFS Head v1.1 a zrychlení Pruna na 8 kroků.
        "bfs_head_v1.1_qwen_2.1.safetensors" to Soubor(
            "models/loras", "Výměna tváře — Qwen 2.1, BFS Head (0,26 GB)",
            "$HF/Alissonerdx/BFS-Best-Face-Swap/resolve/main/bfs_head_v1.1_qwen_2.1.safetensors"
        ),
        "p_qwen_image_2.1_8step_v0.1.safetensors" to Soubor(
            "models/loras", "Výměna tváře — Qwen 2.1, zrychlení na 8 kroků (0,34 GB)",
            "$HF/PrunaAI/Pruna-Qwen-Image-2.1/resolve/main/p_qwen_image_2.1_8step_v0.1.safetensors"
        ),
        // Hudba → MiniMax Music 3 (Comfy-Org/MiniMax-Music-3).
        "minimax_music3_dit_fp16.safetensors" to Soubor(
            "models/diffusion_models", "Hudba — MiniMax Music 3 (4,9 GB)",
            "$HF/Comfy-Org/MiniMax-Music-3/resolve/main/diffusion_models/minimax_music3_dit_fp16.safetensors"
        ),
        "minimax_music3_text_encoder_pruned_int8_convrot.safetensors" to Soubor(
            "models/text_encoders", "Hudba — MiniMax Music 3, skladatel (9,2 GB)",
            "$HF/Comfy-Org/MiniMax-Music-3/resolve/main/text_encoders/minimax_music3_text_encoder_pruned_int8_convrot.safetensors"
        ),
        "minimax_music3_dav.safetensors" to Soubor(
            "models/vae", "Hudba — MiniMax Music 3, zvuk (0,22 GB)",
            "$HF/Comfy-Org/MiniMax-Music-3/resolve/main/vae/minimax_music3_dav.safetensors"
        ),
        // Vylepšit video → Zplynulit: interpolace FILM z jádra ComfyUI.
        "film_net_fp16.safetensors" to Soubor(
            "models/frame_interpolation", "Vylepšit video — zplynulení (0,07 GB)",
            "$HF/Comfy-Org/frame_interpolation/resolve/main/frame_interpolation/film_net_fp16.safetensors"
        ),
        // Karta Wan Animate — Wan-Animate 2, nativně v jádře ComfyUI.
        "wan_animate_2_int8_convrot.safetensors" to Soubor(
            "models/diffusion_models", "Pohyb postavy (podle videa) — Wan-Animate 2 (16,7 GB)",
            "$HF/Comfy-Org/Wan-Animate-2/resolve/main/diffusion_models/wan_animate_2_int8_convrot.safetensors"
        ),
        "umt5_xxl_fp8_e4m3fn_scaled.safetensors" to Soubor(
            "models/text_encoders", "Pohyb postavy (podle videa) — textový enkodér Wan (6,7 GB)",
            "$HF/Comfy-Org/Wan-Animate-2/resolve/main/text_encoders/umt5_xxl_fp8_e4m3fn_scaled.safetensors"
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
            "models/diffusion_models", "Obrázek, Úprava, Oprava fotky, Domalovat a Výměna tváře — Qwen Image 2.1 (7,3 GB)",
            "$HF/Comfy-Org/Qwen-Image-2.1/resolve/main/diffusion_models/qwen_image_2.1_int8_convrot.safetensors"
        ),
        "qwen3vl_8b_int8_convrot.safetensors" to Soubor(
            "models/text_encoders", "Obrázek, Úprava, Oprava fotky, Domalovat a Výměna tváře — Qwen Image 2.1 (9,4 GB)",
            "$HF/Comfy-Org/Qwen-Image-2.1/resolve/main/text_encoders/qwen3vl_8b_int8_convrot.safetensors"
        ),
        "qwen_image_2.1_vae_bf16.safetensors" to Soubor(
            "models/vae", "Obrázek, Úprava, Oprava fotky, Domalovat a Výměna tváře — Qwen Image 2.1 (0,7 GB, RGBA)",
            "$HF/Comfy-Org/Qwen-Image-2.1/resolve/main/vae/qwen_image_2.1_vae_bf16.safetensors"
        ),
        "qwen-image-2.1-outpaint-v2.safetensors" to Soubor(
            "models/loras", "Domalovat — rozšíření obrázku, Qwen 2.1 (0,16 GB, bez ní se fotka posune nebo zůstane šedá)",
            "$HF/ausboss/Qwen-Image-2.1-Outpaint-LoRA/resolve/main/qwen-image-2.1-outpaint-v2.safetensors"
        ),
        "elusarcas-qwen2-1-detailer-v1.safetensors" to Soubor(
            "models/loras", "Oprava fotky (vždy), Detailer v Úpravě a Obrázku — Qwen 2.1 (80 MB)",
            "$HF/reverentelusarca/elusarcas-qwen-2.1-detail-enhancer-lora/resolve/main/elusarcas-qwen2-1-detailer-v1.safetensors"
        ),
        "10Eros_Max_h3_TURBO-hybrid_beta5_int8.safetensors" to Soubor(
            "models/diffusion_models", "Long MiniMax — volba Eros Turbo (21 GB, nepovinné)",
            "$HF/TenStrip/10Eros-Max/resolve/main/10Eros_Max_h3_TURBO-hybrid_beta5_int8.safetensors"
        ),
        "Minimax-h3_Singularity_ref2va_Pruned_v1.3_int8.safetensors" to Soubor(
            "models/diffusion_models", "Long MiniMax — volba Singularity (21 GB, nepovinné)",
            "$HF/WarmBloodAban/Minimax-h3_Singularity/resolve/main/Minimax-h3_Singularity_ref2va_Pruned_v1.3_int8.safetensors"
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
        "krea2_turbo_int8_convrot.safetensors" to Soubor(
            "models/diffusion_models", "Úprava obrázku — Krea 2 (13,5 GB)",
            "$HF/Comfy-Org/Krea-2/resolve/main/diffusion_models/krea2_turbo_int8_convrot.safetensors"
        ),
        "krea2_identity_edit_v1_2.safetensors" to Soubor("models/loras", "Úprava obrázku"),
        "qwen3vl_32b_minimax_h3_int8_convrot.safetensors" to Soubor(
            "models/text_encoders", "Rychlé video (předloha má enkodér napevno)",
            "$HF/Comfy-Org/MiniMax-H3/resolve/main/text_encoders/qwen3vl_32b_minimax_h3_int8_convrot.safetensors"
        ),
        // Karta 3 kroky stojí na téhle LoRA; předloha ji hledá v podsložce h3.
        // Long MiniMax jede na referenčních vahách a na autorově Turbo LoRA;
        // základní model, enkodér i oba VAE sdílí s ostatními video kartami.
        // Volby modelu na kartě Long MiniMax. Turbo je výchozí, zbylé dvě
        // jsou navíc — bez nich karta jede dál, jen tu volbu nenabídne.
        "minimax_h3_fastvideo_vsa_datafree_1300step_4step_int8_convrot.safetensors" to Soubor(
            "models/diffusion_models", "Long MiniMax — volba FastVideo VSA",
            "$HF/Jackxuanxuan/MiniMax-H3-experimental/resolve/main/minimax_h3_fastvideo_vsa_datafree_1300step_4step_int8_convrot.safetensors"
        ),
        "TaoMate-H3-3step-ComfyUI.safetensors" to Soubor(
            "models/loras/h3", "Long MiniMax, Film ze storyboardu — volba 3 + 2 a karta Rychlé video",
            "$HF/TaoLiveAIGC/TaoMate-H3"
        ),
        "10Eros_Max_h3_fl2va_pruned_int8_convrot.safetensors" to Soubor(
            "models/diffusion_models", "Long MiniMax — volba Eros Max"
        ),
        "10Eros_Max_H3_test2_pruned_r128.safetensors" to Soubor(
            "models/loras", "Long MiniMax — volba Eros Max"
        ),
        // Volitelná: karta bez ní jede, jen bez realističtějšího podání.
        "h3-realism-people-t2v-i2v-r2v.safetensors" to Soubor(
            "models/loras", "Long MiniMax — volba Realističtější podání"
        ),
        // 5.39: novější Turbo LoRA od lightx2v (volitelné).
        "minimax_h3_fl2v_turbo_4step_v1.2_768p_comfyui_bf16.safetensors" to Soubor(
            "models/loras", "Turbo LoRA 4step v1.2",
            "$HF/lightx2v/Minimax-h3-Turbo/resolve/main/minimax_h3_fl2v_turbo_4step_v1.2_768p_comfyui_bf16.safetensors"
        ),
        "minimax_h3_ref2v_turbo_8step_v1.0_768p_comfyui_bf16.safetensors" to Soubor(
            "models/loras", "Turbo LoRA ref2v 8step v1.0",
            "$HF/lightx2v/Minimax-h3-Turbo/resolve/main/minimax_h3_ref2v_turbo_8step_v1.0_768p_comfyui_bf16.safetensors"
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
        "taeh3.safetensors" to Soubor("models/vae_approx", "živý náhled u videa"),
        // Audit 30. 9. 2026: grafy je používají, v katalogu chyběly.
        "minimax_h3_video_vae_int8_convrot.safetensors" to Soubor(
            "models/vae", "Upravit video — Podle předlohy",
            "$HF/Comfy-Org/MiniMax-H3/resolve/main/vae/minimax_h3_video_vae_int8_convrot.safetensors"
        ),
        "ema_vae_fp16.safetensors" to Soubor("models/SEEDVR2", "Zvětšit video (SeedVR2)"),
        "seedvr2_ema_7b-Q4_K_M.gguf" to Soubor("models/SEEDVR2", "Zvětšit video (SeedVR2)"),
        // Ostrý náhled Qwen 2.1 (5.34), volitelný — appka ho zapojí, jen když na serveru je.
        "TAEQwenImage21_AcademiaSD.safetensors" to Soubor(
            "models/vae_approx", "živý náhled Qwen Image 2.1",
            "$HF/AcademiaSD/TAE-Qwen-Image-2.1/resolve/main/TAEQwenImage21_AcademiaSD.safetensors"
        ),
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
            "$HF/LBH-123-AI/Minimax_h3_latent_Upscaler/resolve/main/minimax_h3_latent_upscaler_3d_conv_v1/minimax_h3_latent_upscaler_3d_conv_v1_fp16.safetensors"
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
