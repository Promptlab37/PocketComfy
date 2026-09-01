package cz.promptlab.h3video.engine

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
enum class RunKind { VIDEO, EDIT, T2I, RESTORE, SWAP, UPSCALE, MUSIC }

val GenState.Running.kind: RunKind
    get() = when {
        isMusic -> RunKind.MUSIC
        isUpscale -> RunKind.UPSCALE
        isRestore -> RunKind.RESTORE
        isSwap -> RunKind.SWAP
        isT2i -> RunKind.T2I
        isImage -> RunKind.EDIT
        else -> RunKind.VIDEO
    }

/** Nadpis fáze — úplná matice, žádný druh nepropadá na texty o videu. */
fun stageText(stage: Stage, kind: RunKind): String = when (stage) {
    Stage.STARTING -> "Probouzím ComfyUI"
    Stage.UPLOADING -> when (kind) {
        RunKind.VIDEO -> "Odesílám podklady"
        RunKind.EDIT, RunKind.RESTORE, RunKind.UPSCALE -> "Odesílám fotku"
        RunKind.SWAP -> "Odesílám fotky"
        RunKind.T2I, RunKind.MUSIC -> "Připravuji zadání"
    }
    Stage.QUEUED -> "Ve frontě"
    Stage.MODELS -> when (kind) {
        RunKind.VIDEO -> "Načítám modely"
        RunKind.EDIT -> "Načítám Krea 2"
        RunKind.T2I -> "Načítám Z-Image"
        RunKind.RESTORE -> "Načítám Qwen Edit"
        RunKind.SWAP -> "Načítám Flux Fill"
        RunKind.UPSCALE -> "Načítám SeedVR2"
        RunKind.MUSIC -> "Načítám ACE-Step"
    }
    Stage.REFERENCES -> when (kind) {
        RunKind.VIDEO -> "Připravuji podklady"
        RunKind.EDIT, RunKind.RESTORE -> "Načítám fotku"
        RunKind.SWAP -> "Připravuji výřez tváře"
        RunKind.UPSCALE -> "Dělím na dlaždice"
        RunKind.T2I -> "Připravuji plátno"
        RunKind.MUSIC -> "Připravuji zadání"
    }
    Stage.ENCODING -> when (kind) {
        RunKind.VIDEO -> "Zpracovávám prompt"
        RunKind.MUSIC -> "Čtu zadání skladby"
        RunKind.SWAP -> "Připravuji vlepení"
        else -> "Čtu zadání"
    }
    Stage.SAMPLING -> when (kind) {
        RunKind.VIDEO -> "Generuji video"
        RunKind.EDIT -> "Upravuji obrázek"
        RunKind.T2I -> "Generuji obrázek"
        RunKind.RESTORE -> "Opravuji fotku"
        RunKind.SWAP -> "Měním tvář"
        RunKind.UPSCALE -> "Zvětšuji obrázek"
        RunKind.MUSIC -> "Skládám hudbu"
    }
    Stage.DECODING -> when (kind) {
        RunKind.VIDEO -> "Dekóduji obraz a zvuk"
        RunKind.MUSIC -> "Dekóduji zvuk"
        else -> "Dekóduji obraz"
    }
    Stage.MUXING -> when (kind) {
        RunKind.VIDEO -> "Skládám video"
        RunKind.MUSIC -> "Ukládám skladbu"
        RunKind.SWAP -> "Vlepuji tvář zpět"
        RunKind.UPSCALE -> "Slepuji dlaždice"
        else -> "Ukládám obrázek"
    }
    Stage.DOWNLOADING -> when (kind) {
        RunKind.VIDEO -> "Přebírám video"
        RunKind.MUSIC -> "Přebírám skladbu"
        else -> "Přebírám obrázek"
    }
    Stage.FINISHING -> "Dokončuji"
}

/** Podtitulek fáze — kde nemá druh nic zvláštního, platí obecný popis. */
fun stageDetailText(stage: Stage, kind: RunKind): String = when {
    stage == Stage.MODELS -> when (kind) {
        RunKind.VIDEO -> "MiniMax H3 + textový enkodér"
        RunKind.EDIT -> "Krea 2 + textový enkodér"
        RunKind.T2I -> "Z-Image Turbo + textový enkodér"
        RunKind.RESTORE -> "Qwen Image Edit 2511 + LoRA"
        RunKind.SWAP -> "Flux Fill + portrétní LoRA"
        RunKind.UPSCALE -> "SeedVR2 + VAE"
        RunKind.MUSIC -> "ACE-Step 1.5 Turbo"
    }
    stage == Stage.SAMPLING -> when (kind) {
        RunKind.VIDEO -> "Nejdelší část – obraz i zvuk najednou"
        RunKind.MUSIC -> "Celá skladba vzniká najednou"
        RunKind.UPSCALE -> "Dlaždice po dlaždici na 3200 px"
        else -> "Nejdelší část běhu"
    }
    stage == Stage.DOWNLOADING -> when (kind) {
        RunKind.MUSIC -> "Přenáším ji z počítače do Galerie aplikace"
        else -> "Přenáším ho z počítače do Galerie aplikace"
    }
    stage == Stage.UPLOADING && (kind == RunKind.T2I || kind == RunKind.MUSIC) ->
        "Sestavuji graf pro ComfyUI"
    stage == Stage.DECODING && kind != RunKind.VIDEO -> when (kind) {
        RunKind.MUSIC -> "Převádím latentní data na zvuk"
        else -> "Převádím latentní data na obraz"
    }
    stage == Stage.MUXING && kind != RunKind.VIDEO -> when (kind) {
        RunKind.MUSIC -> "Zapisuji MP3"
        RunKind.UPSCALE -> "Prolnutí dlaždic do jedné fotky"
        else -> "Zapisuji hotový obrázek"
    }
    else -> stage.detail
}

/** Popisek 4. fáze v pásku fází (ostatní fáze jsou pro všechny stejné). */
fun mainPhaseTitle(kind: RunKind): String = when (kind) {
    RunKind.VIDEO -> "Generování obrazu a zvuku"
    RunKind.EDIT -> "Úprava obrázku"
    RunKind.T2I -> "Nový obrázek"
    RunKind.RESTORE -> "Oprava fotky"
    RunKind.SWAP -> "Výměna tváře"
    RunKind.UPSCALE -> "Zvětšování"
    RunKind.MUSIC -> "Skládání hudby"
}

/** Popisek 1. fáze — u karet bez vstupních fotek se nic neodesílá. */
fun firstPhaseTitle(kind: RunKind): String = when (kind) {
    RunKind.VIDEO -> "Spojení a odeslání referencí"
    RunKind.T2I, RunKind.MUSIC -> "Spojení se serverem"
    else -> "Spojení a odeslání fotky"
}

/** Titulek notifikace na popředí. */
fun notificationTitle(kind: RunKind): String = stageText(Stage.SAMPLING, kind)
