package cz.promptlab.h3video.comfy

import android.content.Context
import cz.promptlab.h3video.R
import cz.promptlab.h3video.data.Model3dKvalita
import cz.promptlab.h3video.data.Model3dScene
import org.json.JSONArray
import org.json.JSONObject

/**
 * Stavitel grafu pro kartu **3D model** — Microsoft TRELLIS.2.
 *
 * Předloha `res/raw/workflow_trellis2.json` vznikla z oficiálního workflow
 * ComfyUI (`3d_pixal3d_trellis2_image_to_model.json`) narovnaného do API
 * podoby. Proti němu se liší třemi věcmi, a všechny mají důvod:
 *
 *  1. **Zůstala jen větev TRELLIS.2.** Předloha umí i Pixal3D a přepíná mezi
 *     nimi editorovými přepínači, které v API grafu neexistují.
 *  2. **Maska jde z odstranění pozadí.** Předloha bere alfa kanál nahraného
 *     PNG; z telefonu chodí JPEG, který alfu nemá, takže by maska byla prázdná.
 *  3. **Konec grafu je `SaveGLB`**, ne `Save3DAdvanced` — ten chce stav 3D
 *     náhledu z editoru, který appka nemá odkud vzít.
 *
 * Řetěz má čtyři průchody modelu (struktura → tvar → zjemnění → textura)
 * a teprve pak se síť remeshuje, rozbaluje do UV a pečou se mapy.
 */
object Trellis2Builder {

    const val N_IMAGE = "122"
    const val N_CROP = "312"
    const val N_BG_MODEL = "193"
    const val N_BG = "192"
    const val N_CLIP_VISION = "15"
    const val N_UNET = "40"
    const val N_VAE_SHAPE = "117"
    const val N_VAE_TEXTURE = "118"

    /** Čtyři vzorkovací průchody v pořadí, v jakém běží. */
    const val N_KS_STRUCTURE = "3"
    const val N_KS_SHAPE = "18"
    const val N_KS_UPSAMPLE = "23"
    const val N_KS_TEXTURE = "12"

    const val N_UPSAMPLE = "94"
    const val N_TEXTURE_SIZE = "288"
    const val N_DECIMATE = "186"
    const val N_REMESH = "241"

    /**
     * Úklid paměti grafiky uvnitř grafu (`VRAMCleanup`, uzel z balíku na
     * serveru — proto se vkládá, jen když ho server zná).
     *
     * Proč vůbec: síťové fáze (remesh, decimace, pečení map) si berou paměť
     * mimo správu ComfyUI, takže o ní ComfyUI neví a difuzní modely drží dál.
     * Uživateli spadl běh na „max" právě tady — v RemeshMesh bylo obsazeno
     * 13,4 GB z 16 a volných 0 bajtů. Uzel model odsune do RAM (ne z disku),
     * takže návrat na další fázi stojí vteřiny, ne minuty.
     *
     * Vkládají se dva: před síťové fáze a před pečení map. Mezi nimi totiž
     * běží texturový vzorkovač, který si modely natáhne zpátky.
     */
    const val N_UKLID_SIT = "500"
    const val N_UKLID_PECENI = "501"

    /** Třída toho uzlu. Když ji server nezná, graf se staví bez úklidu. */
    const val TRIDA_UKLIDU = "VRAMCleanup"
    const val N_MESHINFO = "202"
    const val N_DECODE_TEXTURE = "93"
    const val N_BAKE = "147"
    const val N_NORMAL = "224"

    /** Dva konce sítě: rychlé barvy ve vrcholech a plná PBR sada. */
    const val N_PAINT = "252"
    const val N_PBR = "260"
    const val N_SAVE = "400"

    /** Kroky všech čtyř průchodů dohromady — ukazatel průběhu s tím počítá. */
    const val STEPS_CELKEM = 12 + 20 + 12 + 12

    private var cached: String? = null

    private fun template(ctx: Context): String = cached ?: ctx.resources
        .openRawResource(R.raw.workflow_trellis2)
        .bufferedReader().use { it.readText() }.also { cached = it }

    fun build(
        ctx: Context, scene: Model3dScene, seed: Long, images: List<String>,
        uklidVram: Boolean = false,
    ): JSONObject = build(template(ctx), scene, seed, images, uklidVram)

    /** Stejné sestavení z textu předlohy, ať jde graf ověřit testem bez Androidu. */
    fun build(
        template: String,
        scene: Model3dScene,
        seed: Long,
        images: List<String>,
        /** Vložit do grafu úklid paměti grafiky. Viz [N_UKLID_SIT]. */
        uklidVram: Boolean = false,
    ): JSONObject {
        val wf = JSONObject(template)
        wf.inputs(N_IMAGE).put("image", images.getOrElse(0) { "" })

        // Každý průchod má vlastní seed. V předloze jsou různé schválně —
        // se stejným seedem si průchody „lezou do zelí" a výsledek bývá plochý.
        val zaklad = seed and 0xFFFF_FFFFL
        listOf(N_KS_STRUCTURE, N_KS_SHAPE, N_KS_UPSAMPLE, N_KS_TEXTURE)
            .forEachIndexed { i, id -> wf.inputs(id).put("seed", (zaklad + i) and 0xFFFF_FFFFL) }

        wf.inputs(N_UPSAMPLE).put("target_resolution", scene.detail)
        wf.inputs(N_TEXTURE_SIZE).put("value", scene.textura)

        // Dodělání sítě (remesh → decimace → vyhlazení → normály) jede z velké
        // části na procesoru, takže je to ta část, kterou rychlejší grafika
        // nezkrátí. Předloha ComfyUI má všude hodnoty NAD výchozími hodnotami
        // uzlů — proto si úroveň volí uživatel a nevnucuje se mu ukázková.
        if (uklidVram) vlozUklid(wf)
        wf.inputs(N_REMESH).put("resolution", scene.kvalita.remesh)
        wf.inputs(N_REMESH).put("smooth_iters", scene.kvalita.vyhlazeni)
        wf.inputs(N_DECIMATE).put("target_face_count", scene.kvalita.plochy)
        wf.inputs(N_NORMAL).put("resolution", scene.kvalita.normaly)

        // Rychlá varianta končí obarvenou sítí; ostatní uzly PBR větve zůstávají
        // v grafu, ale ComfyUI je nespustí — neveden do výstupního uzlu.
        val konec = if (scene.kvalita.jePbr) N_PBR else N_PAINT
        wf.inputs(N_SAVE).put("mesh", JSONArray().put(konec).put(0))
        return wf
    }

    private fun JSONObject.inputs(node: String): JSONObject =
        getJSONObject(node).getJSONObject("inputs")

    /**
     * Fáze podle třídy uzlu. Pečení map a rozbalování UV je u téhle karty
     * samostatná, dlouhá část — proto nespadá pod „ukládám", ale pod skládání.
     */
    /**
     * Vloží dva úklidové uzly do cesty grafu. Nejsou to slepá ramena —
     * data jimi protékají, takže ComfyUI musí úklid provést přesně mezi
     * fázemi, ne kdykoli se mu zachce.
     */
    private fun vlozUklid(wf: JSONObject) {
        fun uklid(id: String, zdroj: String) {
            wf.put(
                id,
                JSONObject()
                    .put("class_type", TRIDA_UKLIDU)
                    .put(
                        "inputs",
                        JSONObject()
                            .put("anything", org.json.JSONArray().put(zdroj).put(0))
                            .put("offload_model", true)
                            .put("offload_cache", true),
                    )
                    .put("_meta", JSONObject().put("title", "Uvolnit paměť grafiky")),
            )
        }
        // Před síťové fáze: tady běh padal.
        uklid(N_UKLID_SIT, N_MESHINFO)
        wf.inputs(N_REMESH).put("mesh", org.json.JSONArray().put(N_UKLID_SIT).put(0))

        // Před pečení map: mezitím doběhl texturový vzorkovač, který si
        // modely natáhl zpátky, a pečení jde na 2048.
        uklid(N_UKLID_PECENI, N_DECODE_TEXTURE)
        wf.inputs(N_BAKE).put("voxel_colors", org.json.JSONArray().put(N_UKLID_PECENI).put(0))
    }

    fun stageForClass(cls: String?): Stage = when (cls) {
        "UNETLoader", "VAELoader", "CLIPVisionLoader", "LoadBackgroundRemovalModel",
        "ModelSamplingSD3", "RescaleCFG", "CFGOverride" -> Stage.MODELS
        "LoadImage", "RemoveBackground", "ImageCropToMask" -> Stage.REFERENCES
        "Trellis2Conditioning", "EmptyTrellis2LatentStructure", "Trellis2ShapeStage",
        "Trellis2UpsampleStage", "Trellis2TextureStage", "PrimitiveInt" -> Stage.ENCODING
        "KSampler" -> Stage.SAMPLING
        "VaeDecodeStructureTrellis2", "VaeDecodeShapeTrellis",
        "VaeDecodeTextureTrellis" -> Stage.DECODING
        "GetMeshInfo", "RemeshMesh", "DecimateMesh", "MeshSmoothNormals", "UnwrapMesh",
        "BakeTextureFromVoxel", "BakeNormalMapFromMesh", "BakeAmbientOcclusion",
        "ApplyTextureToMesh", "PaintMesh", "VoxelToMesh",
        // Úklid stojí mezi fázemi sítě — ať průběh neskáče zpátky.
        TRIDA_UKLIDU -> Stage.MUXING
        "SaveGLB" -> Stage.FINISHING
        else -> Stage.SAMPLING
    }

    fun rangeForClass(cls: String?): Pair<Float, Float> = when (stageForClass(cls)) {
        Stage.MODELS -> 0.00f to 0.05f
        Stage.REFERENCES -> 0.05f to 0.08f
        Stage.ENCODING -> 0.08f to 0.12f
        Stage.SAMPLING -> 0.12f to 0.70f
        Stage.DECODING -> 0.70f to 0.76f
        Stage.MUXING -> 0.76f to 0.88f
        else -> 0.88f to 0.90f
    }

    fun reportsSteps(cls: String?): Boolean = cls == "KSampler"

    fun nodeClasses(wf: JSONObject): Map<String, String> =
        wf.keys().asSequence().mapNotNull { id ->
            wf.optJSONObject(id)?.optString("class_type")?.takeIf { it.isNotEmpty() }?.let { id to it }
        }.toMap()
}
