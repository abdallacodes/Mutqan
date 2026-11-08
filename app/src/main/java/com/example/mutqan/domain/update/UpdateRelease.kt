package com.example.mutqan.domain.update

import org.json.JSONObject

/** A release of the app on GitHub that has an APK: its version, where the APK is, how big it is and its SHA-256 (if GitHub gave one). */
data class UpdateInfo(val version: String, val apkUrl: String, val size: Long, val sha256: String?)

/** Reading GitHub's "latest release" answer, and comparing versions like 6.5.3. */
object UpdateRelease {
    /** "v6.5.3" or "6.5.3" as [6, 5, 3]; null when it is not dotted numbers (up to four). */
    fun parseVersion(text: String): List<Int>? {
        val parts = text.trim().removePrefix("v").removePrefix("V").split('.')
        if (parts.size !in 1..4) return null
        return parts.map { part -> part.toIntOrNull()?.takeIf { it >= 0 } ?: return null }
    }

    /** True when [remote] is a higher version than [local]; false when either is not a version. */
    fun isNewer(remote: String, local: String): Boolean {
        val r = parseVersion(remote) ?: return false
        val l = parseVersion(local) ?: return false
        for (i in 0 until maxOf(r.size, l.size)) {
            val a = r.getOrElse(i) { 0 }
            val b = l.getOrElse(i) { 0 }
            if (a != b) return a > b
        }
        return false
    }

    /**
     * The release in [json] (GitHub's `releases/latest`), or null for a draft, a pre-release, a tag that is not a version,
     * or a release with no APK. Only an https link on github.com is accepted for the APK.
     */
    fun parseLatest(json: String): UpdateInfo? {
        val root = runCatching { JSONObject(json) }.getOrNull() ?: return null
        if (root.optBoolean("draft") || root.optBoolean("prerelease")) return null
        val version = root.optString("tag_name").trim().removePrefix("v")
        if (parseVersion(version) == null) return null
        val assets = root.optJSONArray("assets") ?: return null
        for (i in 0 until assets.length()) {
            val asset = assets.optJSONObject(i) ?: continue
            val url = asset.optString("browser_download_url")
            if (!asset.optString("name").endsWith(".apk") || !url.startsWith("https://github.com/")) continue
            val digest = asset.optString("digest")
            val sha = digest.removePrefix("sha256:").lowercase().takeIf { digest.startsWith("sha256:") && it.length == 64 }
            return UpdateInfo(version = version, apkUrl = url, size = asset.optLong("size"), sha256 = sha)
        }
        return null
    }
}
