// SPDX-License-Identifier: GPL-3.0-only
package dev.agentkeyboard.core

/** One file attached to a GitHub release, as listed by the releases API. */
data class ReleaseAsset(val name: String, val size: Long, val downloadUrl: String)

/** The parts of a GitHub release the update check looks at; [body] is its Markdown text ("" without one). */
data class ReleaseInfo(val tag: String, val draft: Boolean, val assets: List<ReleaseAsset>, val body: String = "")

/** A published build with both of its files: the APK and its SHA-256 line, and its release text (stage 54). */
data class AvailableBuild(val number: Long, val apk: ReleaseAsset, val checksum: ReleaseAsset, val notes: String = "")

/**
 * Rules of the opt-in update check. Pure and deterministic: the app supplies the parsed
 * release list and the installed version code. The release layout comes from
 * `.github/workflows/agent-apk.yml`: tag `build-<n>`, assets `AgentKeyboard-build-<n>.apk`
 * and `AgentKeyboard-build-<n>.apk.sha256`, version code `<n>`, published in the public
 * release repository [REPOSITORY].
 */
object UpdateReleases {
    /** The only repository the check ever asks. Fixed in code, never configurable. Public, no token. */
    const val REPOSITORY = "Tra1c0/SecondHandAgenticKeyboard"
    const val RELEASES_PAGE = "https://github.com/$REPOSITORY/releases"
    const val MAX_APK_BYTES = 120L * 1024 * 1024
    const val MAX_CHECKSUM_BYTES = 4096L
    private val TAG = Regex("build-([1-9][0-9]{0,8})")
    private val CHECKSUM_LINE = Regex("([0-9a-fA-F]{64})[ \\t]+\\*?(\\S+)")

    /** The build number of a tag like `build-7`, null for every other tag. */
    fun buildNumber(tag: String): Long? = TAG.matchEntire(tag)?.groupValues?.get(1)?.toLong()

    fun apkName(number: Long) = "AgentKeyboard-build-$number.apk"
    fun checksumName(number: Long) = apkName(number) + ".sha256"

    /** The only download URL accepted for an asset: the public release download of this repository. */
    fun downloadUrl(tag: String, name: String) = "$RELEASES_PAGE/download/$tag/$name"

    /**
     * The highest published build with an APK of plausible size and its checksum file, both with
     * exactly the expected download URL (never a URL from elsewhere in the answer).
     */
    fun newest(releases: List<ReleaseInfo>): AvailableBuild? = releases.mapNotNull { release ->
        if (release.draft) return@mapNotNull null
        val number = buildNumber(release.tag) ?: return@mapNotNull null
        fun asset(name: String) = release.assets.singleOrNull { it.name == name }
            ?.takeIf { it.downloadUrl == downloadUrl(release.tag, name) }
        val apk = asset(apkName(number)) ?: return@mapNotNull null
        val checksum = asset(checksumName(number)) ?: return@mapNotNull null
        if (apk.size !in 1..MAX_APK_BYTES || checksum.size !in 1..MAX_CHECKSUM_BYTES) return@mapNotNull null
        AvailableBuild(number, apk, checksum, release.body)
    }.maxByOrNull { it.number }

    /** True only for a strictly higher build: Android refuses equal or lower version codes. */
    fun isNewer(build: AvailableBuild?, installedVersionCode: Long): Boolean =
        build != null && build.number > installedVersionCode

    /**
     * The lowercase hex digest from a `sha256sum` line, but only if the line names [fileName].
     * Anything else (other file, several lines, wrong length) is null.
     */
    fun parseChecksum(text: String, fileName: String): String? {
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val match = CHECKSUM_LINE.matchEntire(lines.singleOrNull() ?: return null) ?: return null
        if (match.groupValues[2] != fileName) return null
        return match.groupValues[1].lowercase()
    }
}
