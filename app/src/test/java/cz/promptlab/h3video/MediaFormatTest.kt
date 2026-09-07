package cz.promptlab.h3video

import cz.promptlab.h3video.util.mediaMimeType
import org.junit.Assert.assertEquals
import org.junit.Test

class MediaFormatTest {
    @Test fun `modely a obrazky se nesdileji jako video`() {
        assertEquals("model/gltf-binary", mediaMimeType("model.GLB"))
        assertEquals("model/gltf+json", mediaMimeType("model.gltf"))
        assertEquals("image/webp", mediaMimeType("picture.webp"))
        assertEquals("audio/flac", mediaMimeType("music.flac"))
        assertEquals("video/mp4", mediaMimeType("video.mp4"))
        assertEquals("application/octet-stream", mediaMimeType("unknown.dat"))
    }
}
