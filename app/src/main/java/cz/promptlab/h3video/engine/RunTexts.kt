package cz.promptlab.h3video.engine

import cz.promptlab.h3video.data.t

import cz.promptlab.h3video.comfy.Stage

/**
 * Druh běhu — JEDINÝ zdroj pravdy pro texty průběhu a notifikací.
 *
 * Vznikl po chybě z 1. 9. 2026, kdy karta Hudba hlásila „Odesílám obrázky":
 * texty se ošetřovaly po jedné fázi na třech místech (obrazovka průběhu,
 * pásek fází, notifikace) a nové karty vždycky někde propadly na texty
 * o videu. Tady je KOMPLETNÍ matice — každý druh běhu × každá fáze — a
 * obrazovka i služba ji jen čtou.
 */
enum class RunKind { VIDEO, LONG, EDIT, T2I, RESTORE, SWAP, INPAINT, UPSCALE, DLSS, MUSIC, MODEL3D }

val GenState.Running.kind: RunKind
    get() = when {
        isMusic -> RunKind.MUSIC
        isModel3d -> RunKind.MODEL3D
        isLong -> RunKind.LONG
        // DLSS je taky karta Zvětšit, ale nedělí na dlaždice ani nenačítá
        // difuzní model — texty o SeedVR2 by u něj lhaly.
        isDlss -> RunKind.DLSS
        isUpscale -> RunKind.UPSCALE
        isRestore -> RunKind.RESTORE
        isSwap -> RunKind.SWAP
        isInpaint -> RunKind.INPAINT
        isT2i -> RunKind.T2I
        isImage -> RunKind.EDIT
        else -> RunKind.VIDEO
    }

/** Nadpis fáze — úplná matice, žádný druh nepropadá na texty o videu. */
fun stageText(stage: Stage, kind: RunKind): String = when (stage) {
    Stage.STARTING -> t("Probouzím ComfyUI")
    Stage.UPLOADING -> when (kind) {
        RunKind.VIDEO, RunKind.LONG -> t("Odesílám podklady")
        RunKind.EDIT, RunKind.RESTORE, RunKind.UPSCALE, RunKind.DLSS,
        RunKind.MODEL3D -> t("Odesílám fotku")
        RunKind.SWAP, RunKind.INPAINT -> t("Odesílám fotky")
        RunKind.T2I, RunKind.MUSIC -> t("Připravuji zadání")
    }
    Stage.QUEUED -> t("Ve frontě")
    Stage.MODELS -> when (kind) {
        RunKind.VIDEO -> t("Načítám modely")
        RunKind.LONG -> t("Načítám MiniMax H3")
        RunKind.EDIT -> t("Načítám Krea 2")
        RunKind.T2I -> t("Načítám Z-Image")
        RunKind.RESTORE -> t("Načítám Qwen Edit")
        RunKind.SWAP -> t("Načítám Flux Fill")
        RunKind.INPAINT -> t("Načítám model na domalování")
        RunKind.UPSCALE -> t("Načítám SeedVR2")
        RunKind.DLSS -> t("Spouštím DLSS 5")
        RunKind.MUSIC -> t("Načítám ACE-Step")
        RunKind.MODEL3D -> t("Načítám TRELLIS.2")
    }
    Stage.REFERENCES -> when (kind) {
        RunKind.VIDEO -> t("Připravuji podklady")
        RunKind.LONG -> t("Připravuji navázání")
        RunKind.EDIT, RunKind.RESTORE -> t("Načítám fotku")
        RunKind.SWAP -> t("Připravuji výřez tváře")
        RunKind.INPAINT -> t("Vyřezávám okolí masky")
        RunKind.UPSCALE -> t("Dělím na dlaždice")
        RunKind.DLSS -> t("Načítám fotku")
        RunKind.T2I -> t("Připravuji plátno")
        RunKind.MODEL3D -> t("Odstraňuji pozadí")
        RunKind.MUSIC -> t("Připravuji zadání")
    }
    Stage.ENCODING -> when (kind) {
        RunKind.VIDEO -> t("Zpracovávám prompt")
        RunKind.MUSIC -> t("Čtu zadání skladby")
        RunKind.SWAP -> t("Připravuji vlepení")
        else -> t("Čtu zadání")
    }
    Stage.SAMPLING -> when (kind) {
        RunKind.VIDEO -> t("Generuji video")
        RunKind.LONG -> t("Generuji úseky")
        RunKind.EDIT -> t("Upravuji obrázek")
        RunKind.T2I -> t("Generuji obrázek")
        RunKind.RESTORE -> t("Opravuji fotku")
        RunKind.SWAP -> t("Měním tvář")
        RunKind.INPAINT -> t("Domalovávám do masky")
        RunKind.UPSCALE -> t("Zvětšuji obrázek")
        RunKind.DLSS -> t("Doostřuji fotku")
        RunKind.MUSIC -> t("Skládám hudbu")
        RunKind.MODEL3D -> t("Stavím tvar modelu")
    }
    Stage.DECODING -> when (kind) {
        RunKind.VIDEO -> t("Dekóduji obraz a zvuk")
        RunKind.MUSIC -> t("Dekóduji zvuk")
        else -> t("Dekóduji obraz")
    }
    Stage.MUXING -> when (kind) {
        RunKind.VIDEO -> t("Skládám video")
        RunKind.LONG -> t("Slepuji úseky do videa")
        RunKind.MUSIC -> t("Ukládám skladbu")
        RunKind.SWAP -> t("Vlepuji tvář zpět")
        RunKind.INPAINT -> t("Vlepuji domalovaný kus zpět")
        RunKind.UPSCALE -> t("Slepuji dlaždice")
        RunKind.MODEL3D -> t("Peču textury a rozbaluji UV")
        else -> t("Ukládám obrázek")
    }
    Stage.DOWNLOADING -> when (kind) {
        RunKind.VIDEO, RunKind.LONG -> t("Přebírám video")
        RunKind.MODEL3D -> t("Přebírám model")
        RunKind.MUSIC -> t("Přebírám skladbu")
        else -> t("Přebírám obrázek")
    }
    Stage.FINISHING -> t("Dokončuji")
}

/** Podtitulek fáze — kde nemá druh nic zvláštního, platí obecný popis. */
fun stageDetailText(stage: Stage, kind: RunKind): String = when {
    stage == Stage.MODELS -> when (kind) {
        RunKind.VIDEO -> t("MiniMax H3 + textový enkodér")
        RunKind.LONG -> t("MiniMax H3 (referenční váhy) + enkodér")
        RunKind.EDIT -> t("Krea 2 + textový enkodér")
        RunKind.T2I -> t("Z-Image Turbo + textový enkodér")
        RunKind.RESTORE -> t("Qwen Image Edit 2511 + LoRA")
        RunKind.SWAP -> t("Flux Fill + portrétní LoRA")
        RunKind.INPAINT -> t("Model na domalování + textový enkodér")
        RunKind.UPSCALE -> "SeedVR2 + VAE"
        RunKind.DLSS -> t("NVIDIA Neural Rendering, žádný difuzní model")
        RunKind.MUSIC -> "ACE-Step 1.5 Turbo"
        RunKind.MODEL3D -> t("TRELLIS.2 + DINOv3")
    }
    stage == Stage.SAMPLING -> when (kind) {
        RunKind.VIDEO -> t("Nejdelší část – obraz i zvuk najednou")
        RunKind.LONG -> t("Každý úsek je vlastní vzorkování, jede se popořadě")
        RunKind.MUSIC -> t("Celá skladba vzniká najednou")
        RunKind.UPSCALE -> t("Dlaždice po dlaždici na 3200 px")
        RunKind.DLSS -> t("Rekonstrukce na grafické kartě, jde to rychle")
        RunKind.MODEL3D -> t("Čtyři průchody: struktura, tvar, zjemnění, textura")
        else -> t("Nejdelší část běhu")
    }
    stage == Stage.DOWNLOADING -> when (kind) {
        RunKind.MUSIC -> t("Přenáším ji z počítače do Galerie aplikace")
        else -> t("Přenáším ho z počítače do Galerie aplikace")
    }
    stage == Stage.UPLOADING && (kind == RunKind.T2I || kind == RunKind.MUSIC) ->
        t("Sestavuji graf pro ComfyUI")
    stage == Stage.DECODING && kind != RunKind.VIDEO -> when (kind) {
        RunKind.MUSIC -> t("Převádím latentní data na zvuk")
        else -> t("Převádím latentní data na obraz")
    }
    stage == Stage.MUXING && kind != RunKind.VIDEO -> when (kind) {
        RunKind.MUSIC -> t("Zapisuji MP3")
        RunKind.UPSCALE -> t("Prolnutí dlaždic do jedné fotky")
        else -> t("Zapisuji hotový obrázek")
    }
    else -> stage.detail
}

/** Popisek 4. fáze v pásku fází (ostatní fáze jsou pro všechny stejné). */
fun mainPhaseTitle(kind: RunKind): String = when (kind) {
    RunKind.VIDEO -> t("Generování obrazu a zvuku")
    RunKind.LONG -> t("Generování úseků")
    RunKind.EDIT -> t("Úprava obrázku")
    RunKind.T2I -> t("Nový obrázek")
    RunKind.RESTORE -> t("Oprava fotky")
    RunKind.SWAP -> t("Výměna tváře")
    RunKind.INPAINT -> t("Domalování do masky")
    RunKind.UPSCALE -> t("Zvětšování")
    RunKind.DLSS -> t("Doostření DLSS 5")
    RunKind.MUSIC -> t("Skládání hudby")
    RunKind.MODEL3D -> t("Stavba 3D modelu")
}

/** Popisek 1. fáze — u karet bez vstupních fotek se nic neodesílá. */
fun firstPhaseTitle(kind: RunKind): String = when (kind) {
    RunKind.VIDEO, RunKind.LONG -> t("Spojení a odeslání referencí")
    RunKind.T2I, RunKind.MUSIC -> t("Spojení se serverem")
    else -> t("Spojení a odeslání fotky")
}

/** Titulek notifikace na popředí. */
fun notificationTitle(kind: RunKind): String = stageText(Stage.SAMPLING, kind)
