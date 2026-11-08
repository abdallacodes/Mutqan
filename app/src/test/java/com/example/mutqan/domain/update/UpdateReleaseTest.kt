package com.example.mutqan.domain.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateReleaseTest {
    private val sha = "bc34800c2a30b01e6f4dea4f6233290011138111558cd6dacde2395f9b4e66e7"

    private fun release(
        tag: String = "v6.5.3",
        draft: Boolean = false,
        pre: Boolean = false,
        name: String = "app-release.apk",
        url: String = "https://github.com/abdallacodes/Mutqan/releases/download/v6.5.3/app-release.apk",
        digest: String = "sha256:$sha"
    ) = """{"tag_name":"$tag","draft":$draft,"prerelease":$pre,"assets":[
        {"name":"notes.txt","browser_download_url":"https://github.com/x/notes.txt","size":10},
        {"name":"$name","browser_download_url":"$url","size":32403433,"digest":"$digest"}]}"""

    @Test
    fun `versions are read with or without the v`() {
        assertEquals(listOf(6, 5, 3), UpdateRelease.parseVersion("v6.5.3"))
        assertEquals(listOf(6, 5, 3), UpdateRelease.parseVersion("6.5.3"))
        assertNull(UpdateRelease.parseVersion("6.5.3-beta"))
        assertNull(UpdateRelease.parseVersion("latest"))
        assertNull(UpdateRelease.parseVersion(""))
    }

    @Test
    fun `a higher version is newer, an equal or lower one is not`() {
        assertTrue(UpdateRelease.isNewer("6.5.3", "6.5.2"))
        assertTrue(UpdateRelease.isNewer("6.10.0", "6.9.9"))
        assertTrue(UpdateRelease.isNewer("7.0.0", "6.99.99"))
        assertFalse(UpdateRelease.isNewer("6.5.2", "6.5.2"))
        assertFalse(UpdateRelease.isNewer("6.5.1", "6.5.2"))
        assertFalse(UpdateRelease.isNewer("6.5", "6.5.0"))
    }

    @Test
    fun `something that is not a version is never newer`() {
        assertFalse(UpdateRelease.isNewer("nightly", "6.5.2"))
        assertFalse(UpdateRelease.isNewer("6.5.3", "debug"))
    }

    @Test
    fun `the apk of the latest release is found`() {
        val info = UpdateRelease.parseLatest(release())!!
        assertEquals("6.5.3", info.version)
        assertEquals("https://github.com/abdallacodes/Mutqan/releases/download/v6.5.3/app-release.apk", info.apkUrl)
        assertEquals(32403433L, info.size)
        assertEquals(sha, info.sha256)
    }

    @Test
    fun `a release with no digest still counts`() {
        assertNull(UpdateRelease.parseLatest(release(digest = "")).let { it?.sha256 })
        assertEquals("6.5.3", UpdateRelease.parseLatest(release(digest = ""))?.version)
    }

    @Test
    fun `drafts, pre-releases and odd tags are ignored`() {
        assertNull(UpdateRelease.parseLatest(release(draft = true)))
        assertNull(UpdateRelease.parseLatest(release(pre = true)))
        assertNull(UpdateRelease.parseLatest(release(tag = "nightly")))
    }

    @Test
    fun `only an apk on github over https is accepted`() {
        assertNull(UpdateRelease.parseLatest(release(name = "app.zip")))
        assertNull(UpdateRelease.parseLatest(release(url = "http://github.com/a/app-release.apk")))
        assertNull(UpdateRelease.parseLatest(release(url = "https://evil.example.com/app-release.apk")))
    }

    @Test
    fun `an answer that is not json gives nothing`() {
        assertNull(UpdateRelease.parseLatest("<html>rate limited</html>"))
        assertNull(UpdateRelease.parseLatest("{}"))
    }
}
