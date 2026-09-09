package cz.promptlab.h3video

import cz.promptlab.h3video.update.UpdateChecker
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UpdateChannelTest {
    @Test
    fun `bez tokenu kontroluje verejne vydani a stahuje primo APK`() {
        for (token in listOf("", "  ")) {
            val request = UpdateChecker.latestRequest(token)
            assertEquals("https://api.github.com/repos/Promptlab37/PocketComfy/releases/latest", request.url.toString())
            assertNull(request.header("Authorization"))
            assertEquals("no-cache", request.header("Cache-Control"))
            assertEquals(publicAsset, UpdateChecker.assetDownloadUrl(asset, token))
            assertNull(UpdateChecker.assetDownloadRequest(publicAsset, token).header("Authorization"))
        }
    }

    @Test
    fun `rucne nastaveny token zachova soukromy kanal`() {
        val request = UpdateChecker.latestRequest("test-token")
        assertEquals("https://api.github.com/repos/Promptlab37/H3Video/releases/latest", request.url.toString())
        assertEquals("Bearer test-token", request.header("Authorization"))
        assertEquals(privateAsset, UpdateChecker.assetDownloadUrl(asset, "test-token"))
        assertEquals("Bearer test-token", UpdateChecker.assetDownloadRequest(privateAsset, "test-token").header("Authorization"))
    }

    @Test
    fun `token nikdy neposila verejnemu stazeni ani jinemu hostiteli`() {
        for (url in listOf(
            publicAsset,
            "https://release-assets.githubusercontent.com/example.apk",
            "https://example.com/repos/Promptlab37/H3Video/releases/assets/123",
            "http://api.github.com/repos/Promptlab37/H3Video/releases/assets/123",
            "https://api.github.com/repos/another/repo/releases/assets/123",
        )) assertNull(url, UpdateChecker.assetDownloadRequest(url, "test-token").header("Authorization"))
    }

    private val publicAsset = "https://github.com/Promptlab37/PocketComfy/releases/download/v147/PocketComfy_v3.35.apk"
    private val privateAsset = "https://api.github.com/repos/Promptlab37/H3Video/releases/assets/123"
    private val asset get() = JSONObject().put("url", privateAsset).put("browser_download_url", publicAsset)
}
