package cz.promptlab.h3video.util

import java.util.Locale

fun mediaMimeType(fileName: String): String = when (fileName.substringAfterLast('.', "").lowercase(Locale.ROOT)) {
    "png" -> "image/png"
    "jpg", "jpeg" -> "image/jpeg"
    "webp" -> "image/webp"
    "mp3" -> "audio/mpeg"
    "flac" -> "audio/flac"
    "wav" -> "audio/wav"
    "ogg", "opus" -> "audio/ogg"
    "m4a" -> "audio/mp4"
    "glb" -> "model/gltf-binary"
    "gltf" -> "model/gltf+json"
    "mp4" -> "video/mp4"
    "webm" -> "video/webm"
    "mov" -> "video/quicktime"
    "mkv" -> "video/x-matroska"
    else -> "application/octet-stream"
}
